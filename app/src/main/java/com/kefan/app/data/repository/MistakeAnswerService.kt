package com.kefan.app.data.repository

import android.content.Context
import com.kefan.app.core.export.MistakeExportAssembler
import com.kefan.app.data.local.dao.ExportJobDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.entity.ExportJob
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.remote.ApiResult
import com.kefan.app.data.remote.MistakeAnswerParser
import com.kefan.app.data.remote.prompts.MistakeAnswerPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导出后台补答案：主文档先交付，缺失答案在这里逐题补全，
 * 完成后自动再生成一份「_含答案.docx」。没有引入 WorkManager，进程存活期间执行。
 */
@Singleton
class MistakeAnswerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mistakeRecordDao: MistakeRecordDao,
    private val exportJobDao: ExportJobDao,
    private val modelConfigRepository: ModelConfigRepository,
    private val studentRepository: StudentRepository,
    private val exportAssembler: MistakeExportAssembler
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = Collections.synchronizedSet(mutableSetOf<Long>())

    fun start(
        jobId: Long,
        studentId: Long,
        mistakeIds: List<Long>,
        includeFigures: Boolean,
        onePerPage: Boolean
    ) {
        if (!runningJobs.add(jobId)) return
        scope.launch {
            try {
                runJob(
                    jobId = jobId,
                    studentId = studentId,
                    mistakeIds = mistakeIds,
                    includeFigures = includeFigures,
                    onePerPage = onePerPage
                )
            } finally {
                runningJobs.remove(jobId)
            }
        }
    }

    private suspend fun runJob(
        jobId: Long,
        studentId: Long,
        mistakeIds: List<Long>,
        includeFigures: Boolean,
        onePerPage: Boolean
    ) {
        val job = exportJobDao.getById(jobId) ?: return
        val config = modelConfigRepository.activeConfigOnce()
        if (config == null) {
            exportJobDao.update(
                job.copy(
                    status = ExportJob.STATUS_FAILED,
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }
        val student = studentRepository.getById(studentId)
        exportJobDao.update(
            job.copy(
                status = ExportJob.STATUS_ANSWERING,
                updatedAt = System.currentTimeMillis()
            )
        )

        var done = 0
        var failed = 0
        try {
            mistakeIds.forEach { id ->
                val record = mistakeRecordDao.getById(id) ?: return@forEach
                if (record.correctAnswer.isNotBlank()) return@forEach

                val result = modelConfigRepository.chatWith(
                    config = config,
                    systemPrompt = MistakeAnswerPrompt.SYSTEM_PROMPT,
                    userPrompt = MistakeAnswerPrompt.userPrompt(student, record),
                    maxTokens = 900,
                    temperature = 0.2
                )
                if (result is ApiResult.Success) {
                    val parsed = MistakeAnswerParser.parse(result.data.content)
                    if (parsed == null) {
                        failed += 1
                        markFailed(record)
                    } else {
                        done += 1
                        mistakeRecordDao.update(
                            record.copy(
                                correctAnswer = parsed.correctAnswer,
                                analysis = parsed.analysis,
                                answerStatus = MistakeRecord.ANSWER_AI_GENERATED,
                                analysisStatus = MistakeRecord.ANSWER_AI_GENERATED,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } else {
                    failed += 1
                    markFailed(record)
                }

                val current = exportJobDao.getById(jobId) ?: return
                exportJobDao.update(
                    current.copy(
                        answerDoneCount = done,
                        answerFailedCount = failed,
                        status = ExportJob.STATUS_ANSWERING,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                delay(150)
            }

            val refreshed = mistakeIds.mapNotNull { mistakeRecordDao.getById(it) }
            val baseName = job.docxPath
                ?.let { File(it).nameWithoutExtension }
                ?: "错题本_${System.currentTimeMillis()}"
            val answerFile = File(context.filesDir, "exports/${baseName}_含答案.docx")
            exportAssembler.buildDocx(
                context = context,
                studentName = student?.name.orEmpty(),
                mistakes = refreshed,
                includeFigures = includeFigures,
                includeAnswers = true,
                onePerPage = onePerPage,
                outputFile = answerFile
            )

            val current = exportJobDao.getById(jobId) ?: return
            exportJobDao.update(
                current.copy(
                    status = ExportJob.STATUS_DONE,
                    docxWithAnswerPath = answerFile.absolutePath,
                    answerDoneCount = done,
                    answerFailedCount = failed,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            val current = exportJobDao.getById(jobId) ?: return
            exportJobDao.update(
                current.copy(
                    status = ExportJob.STATUS_FAILED,
                    answerFailedCount = failed,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun markFailed(record: MistakeRecord) {
        mistakeRecordDao.update(
            record.copy(
                answerStatus = MistakeRecord.ANSWER_AI_FAILED,
                analysisStatus = MistakeRecord.ANSWER_AI_FAILED,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
