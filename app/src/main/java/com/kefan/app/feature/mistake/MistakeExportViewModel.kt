package com.kefan.app.feature.mistake

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.core.export.MistakeExportAssembler
import com.kefan.app.data.local.dao.ExportJobDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.entity.ExportJob
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.repository.MistakeAnswerService
import com.kefan.app.data.repository.ModelConfigRepository
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class ExportRange(val label: String) {
    TODAY("今天"),
    WEEK("近 7 天"),
    MONTH("近 30 天"),
    TERM("本学期"),
    CUSTOM("自定义")
}

data class ExportOptions(
    val range: ExportRange = ExportRange.MONTH,
    val customStart: Long = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
    val customEnd: Long = System.currentTimeMillis(),
    val status: String = "ALL",
    val includeFigures: Boolean = true,
    val answerMode: String = ExportJob.ANSWER_NONE,
    val onePerPage: Boolean = false
)

data class MistakeExportUi(
    val loading: Boolean = true,
    val studentName: String = "",
    val studentSubject: String = "",
    val options: ExportOptions = ExportOptions(),
    val generating: Boolean = false,
    val currentJobId: Long? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class MistakeExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val studentRepository: StudentRepository,
    private val mistakeRecordDao: MistakeRecordDao,
    private val exportJobDao: ExportJobDao,
    private val modelConfigRepository: ModelConfigRepository,
    private val exportAssembler: MistakeExportAssembler,
    private val answerService: MistakeAnswerService
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L

    private val _ui = MutableStateFlow(MistakeExportUi())
    val ui: StateFlow<MistakeExportUi> = _ui.asStateFlow()

    private val allMistakes: StateFlow<List<MistakeRecord>> =
        mistakeRecordDao.observeByStudent(studentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val jobs: StateFlow<List<ExportJob>> =
        exportJobDao.observeByStudent(studentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filtered: StateFlow<List<MistakeRecord>> = combine(allMistakes, _ui) { list, ui ->
        val (start, end) = rangeOf(ui.options)
        list.filter { mistake ->
            mistake.createdAt in start until end &&
                (ui.options.status == "ALL" || mistake.reviewStatus == ui.options.status)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val student = studentRepository.getById(studentId)
            _ui.update {
                it.copy(
                    loading = false,
                    studentName = student?.name.orEmpty(),
                    studentSubject = student?.subject.orEmpty()
                )
            }
        }
    }

    fun updateOptions(transform: (ExportOptions) -> ExportOptions) {
        _ui.update { it.copy(options = transform(it.options), message = null, error = null) }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }

    fun generate() {
        val ui = _ui.value
        if (ui.generating) return
        val mistakes = filtered.value
        if (mistakes.isEmpty()) {
            _ui.update { it.copy(error = "该时间范围内没有错题") }
            return
        }
        val missingAnswers = mistakes.count { it.correctAnswer.isBlank() }
        if (ui.options.answerMode == ExportJob.ANSWER_GENERATE && missingAnswers > 0) {
            // 先确认模型可用，避免主文档生成后才发现不能补答案
            _ui.update { it.copy(generating = true, error = null, message = null) }
            viewModelScope.launch {
                val config = modelConfigRepository.activeConfigOnce()
                if (config == null) {
                    _ui.update {
                        it.copy(generating = false, error = "还没有配置模型，无法补全答案")
                    }
                    return@launch
                }
                doGenerate(ui, mistakes, missingAnswers)
            }
        } else {
            viewModelScope.launch { doGenerate(ui, mistakes, missingAnswers) }
        }
    }

    private suspend fun doGenerate(
        ui: MistakeExportUi,
        mistakes: List<MistakeRecord>,
        missingAnswers: Int
    ) {
        val (start, end) = rangeOf(ui.options)
        _ui.update { it.copy(generating = true, error = null, message = null) }
        var jobId: Long? = null
        try {
            val needsBackgroundAnswers =
                ui.options.answerMode == ExportJob.ANSWER_GENERATE && missingAnswers > 0
            val job = ExportJob(
                studentId = studentId,
                rangeStart = start,
                rangeEnd = end,
                filterJson = """{"status":"${ui.options.status}","figures":${ui.options.includeFigures}}""",
                answerMode = ui.options.answerMode,
                totalCount = if (needsBackgroundAnswers) missingAnswers else mistakes.size,
                status = ExportJob.STATUS_BUILDING
            )
            val createdId = exportJobDao.insert(job)
            jobId = createdId
            _ui.update { it.copy(currentJobId = createdId) }

            val baseName = "错题本_${ui.studentName.ifBlank { "学生" }}_${System.currentTimeMillis()}"
            val mainFile = File(context.filesDir, "exports/$baseName.docx")
            exportAssembler.buildDocx(
                context = context,
                studentName = ui.studentName,
                mistakes = mistakes,
                includeFigures = ui.options.includeFigures,
                includeAnswers = false,
                onePerPage = ui.options.onePerPage,
                outputFile = mainFile
            )

            val current = exportJobDao.getById(createdId) ?: return
            if (needsBackgroundAnswers) {
                exportJobDao.update(
                    current.copy(
                        docxPath = mainFile.absolutePath,
                        status = ExportJob.STATUS_ANSWERING,
                        totalCount = missingAnswers,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                answerService.start(
                    jobId = createdId,
                    studentId = studentId,
                    mistakeIds = mistakes.map { it.id },
                    includeFigures = ui.options.includeFigures,
                    onePerPage = ui.options.onePerPage
                )
                _ui.update {
                    it.copy(
                        generating = false,
                        message = "题目版 Word 已生成，正在后台补全 $missingAnswers 道解析"
                    )
                }
            } else {
                var answerPath: String? = null
                if (ui.options.answerMode == ExportJob.ANSWER_GENERATE) {
                    val answerFile = File(context.filesDir, "exports/${baseName}_含答案.docx")
                    exportAssembler.buildDocx(
                        context = context,
                        studentName = ui.studentName,
                        mistakes = mistakes,
                        includeFigures = ui.options.includeFigures,
                        includeAnswers = true,
                        onePerPage = ui.options.onePerPage,
                        outputFile = answerFile
                    )
                    answerPath = answerFile.absolutePath
                }
                exportJobDao.update(
                    current.copy(
                        docxPath = mainFile.absolutePath,
                        docxWithAnswerPath = answerPath,
                        status = ExportJob.STATUS_DONE,
                        totalCount = mistakes.size,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _ui.update { it.copy(generating = false, message = "Word 已生成，可以直接分享") }
            }
        } catch (e: Exception) {
            val failedId = jobId
            if (failedId != null) {
                val current = exportJobDao.getById(failedId)
                if (current != null) {
                    exportJobDao.update(
                        current.copy(
                            status = ExportJob.STATUS_FAILED,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            _ui.update {
                it.copy(
                    generating = false,
                    error = "生成失败：${e.message.orEmpty()}"
                )
            }
        }
    }

    private fun rangeOf(options: ExportOptions): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        return when (options.range) {
            ExportRange.TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli() to
                today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            ExportRange.WEEK -> now - 7L * 24 * 60 * 60 * 1000 to now + 1

            ExportRange.MONTH -> now - 30L * 24 * 60 * 60 * 1000 to now + 1

            ExportRange.TERM -> {
                val month = today.monthValue
                val startYear = if (month >= 9) today.year else if (month >= 2) today.year else today.year - 1
                val startMonth = if (month >= 9) 9 else if (month >= 2) 2 else 9
                LocalDate.of(startYear, startMonth, 1)
                    .atStartOfDay(zone).toInstant().toEpochMilli() to
                    today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }

            ExportRange.CUSTOM -> {
                val start = LocalDate.ofEpochDay(
                    java.time.Instant.ofEpochMilli(options.customStart)
                        .atZone(zone).toLocalDate().toEpochDay()
                ).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = java.time.Instant.ofEpochMilli(options.customEnd)
                    .atZone(zone).toLocalDate().plusDays(1)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
        }
    }
}
