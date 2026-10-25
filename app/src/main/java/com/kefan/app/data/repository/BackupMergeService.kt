package com.kefan.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import com.kefan.app.data.local.AppDatabase
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.FeedbackTemplate
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.ScoreRecord
import com.kefan.app.data.local.entity.Student
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class MergeResult(
    val studentsAdded: Int,
    val feedbacksAdded: Int,
    val scoresAdded: Int,
    val mistakesAdded: Int
)

/**
 * 合并恢复：保留当前数据，把备份包里缺少的学生、反馈、成绩、错题和模板补进来。
 * 主键全部重新生成，图片按新文件名复制，避免覆盖现有数据。
 */
@Singleton
class BackupMergeService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {

    suspend fun merge(backupDb: File, imageRoot: File): MergeResult = withContext(Dispatchers.IO) {
        val tempName = "kefan_merge_${System.currentTimeMillis()}.db"
        val tempDbFile = context.getDatabasePath(tempName)
        tempDbFile.parentFile?.mkdirs()
        backupDb.copyTo(tempDbFile, overwrite = true)
        File(backupDb.path + "-wal").takeIf { it.exists() }?.copyTo(
            File(tempDbFile.path + "-wal"),
            overwrite = true
        )
        File(backupDb.path + "-shm").takeIf { it.exists() }?.copyTo(
            File(tempDbFile.path + "-shm"),
            overwrite = true
        )

        val tempDb = Room.databaseBuilder(context, AppDatabase::class.java, tempName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

        val copier = ImageCopier(context, imageRoot)
        try {
            database.withTransaction { mergeInside(tempDb, copier) }
        } catch (e: Exception) {
            copier.cleanup()
            throw e
        } finally {
            tempDb.close()
            tempDbFile.delete()
            File(tempDbFile.path + "-wal").delete()
            File(tempDbFile.path + "-shm").delete()
        }
    }

    private suspend fun mergeInside(temp: AppDatabase, copier: ImageCopier): MergeResult {
        var studentsAdded = 0
        var feedbacksAdded = 0
        var scoresAdded = 0
        var mistakesAdded = 0

        val existingStudents = database.studentDao().listAll()
        val studentNameToId = existingStudents.associate { it.name to it.id }.toMutableMap()
        val tempStudents = temp.studentDao().listAll()
        val studentIdMap = mutableMapOf<Long, Long>()

        tempStudents.forEach { old ->
            val existingId = studentNameToId[old.name]
            val newId = if (existingId != null) {
                existingId
            } else {
                val inserted = database.studentDao().insert(
                    old.copy(id = 0, lastFeedbackAt = null)
                )
                studentNameToId[old.name] = inserted
                studentsAdded++
                inserted
            }
            studentIdMap[old.id] = newId
        }

        mergeMemoryAndTemplates(temp, tempStudents, studentIdMap)

        val feedbackIdMap = mutableMapOf<Long, Long>()
        tempStudents.forEach { old ->
            val newStudentId = studentIdMap.getValue(old.id)
            val existingKeys = database.feedbackDao().listAllByStudent(newStudentId)
                .map { "${it.classDate}|${it.title}" }
                .toMutableSet()

            temp.feedbackDao().listVisibleByStudent(old.id).forEach { oldFeedback ->
                val key = "${oldFeedback.classDate}|${oldFeedback.title}"
                if (!existingKeys.add(key)) return@forEach

                val newFeedbackId = database.feedbackDao().insert(
                    oldFeedback.copy(
                        id = 0,
                        studentId = newStudentId,
                        deletedAt = null,
                        modelConfigId = null,
                        templateId = null
                    )
                )
                feedbackIdMap[oldFeedback.id] = newFeedbackId
                feedbacksAdded++
                copyFeedbackImages(temp, copier, oldFeedback, newFeedbackId, newStudentId)
            }
        }

        tempStudents.forEach { old ->
            val newStudentId = studentIdMap.getValue(old.id)
            val existingScoreKeys =
                database.scoreRecordDao().listInRange(newStudentId, 0, Long.MAX_VALUE)
                    .map { scoreKey(it) }
                    .toMutableSet()

            temp.scoreRecordDao().listInRange(old.id, 0, Long.MAX_VALUE).forEach { oldScore ->
                if (!existingScoreKeys.add(scoreKey(oldScore))) return@forEach
                database.scoreRecordDao().insert(oldScore.copy(id = 0, studentId = newStudentId))
                scoresAdded++
            }
        }

        tempStudents.forEach { old ->
            val newStudentId = studentIdMap.getValue(old.id)
            val existingMistakeKeys = database.mistakeRecordDao().listByStudent(newStudentId)
                .map { mistakeKey(it) }
                .toMutableSet()

            temp.mistakeRecordDao().listByStudent(old.id).forEach { oldMistake ->
                if (!existingMistakeKeys.add(mistakeKey(oldMistake))) return@forEach

                val newMistakeId = database.mistakeRecordDao().insert(
                    oldMistake.copy(
                        id = 0,
                        studentId = newStudentId,
                        sourceFeedbackId = oldMistake.sourceFeedbackId?.let {
                            feedbackIdMap[it]
                        },
                        sourceScoreId = null
                    )
                )
                mistakesAdded++
                copyMistakeImages(temp, copier, oldMistake, newMistakeId, newStudentId)
                temp.reviewLogDao().listByMistake(oldMistake.id).forEach { log ->
                    database.reviewLogDao().insert(log.copy(id = 0, mistakeId = newMistakeId))
                }
            }
        }

        studentIdMap.values.distinct().forEach { studentId ->
            database.studentDao().setLastFeedback(
                studentId,
                database.feedbackDao().maxVisibleClassDate(studentId)
            )
        }

        return MergeResult(studentsAdded, feedbacksAdded, scoresAdded, mistakesAdded)
    }

    private suspend fun mergeMemoryAndTemplates(
        temp: AppDatabase,
        tempStudents: List<Student>,
        studentIdMap: Map<Long, Long>
    ) {
        tempStudents.forEach { old ->
            val newStudentId = studentIdMap.getValue(old.id)

            if (database.memoryCardDao().get(newStudentId) == null) {
                temp.memoryCardDao().get(old.id)?.let { memory ->
                    database.memoryCardDao().upsert(
                        memory.copy(studentId = newStudentId, compactedUntilRecordId = 0)
                    )
                }
            }

            val existingContents = database.manualSampleDao().listByStudent(newStudentId)
                .map { it.content }
                .toMutableSet()
            val newSamples = temp.manualSampleDao().listByStudent(old.id)
                .filter { existingContents.add(it.content) }
                .map { it.copy(id = 0, studentId = newStudentId) }
            if (newSamples.isNotEmpty()) {
                database.manualSampleDao().insertAll(newSamples)
            }

            if (database.feedbackTemplateDao().getActiveForStudent(newStudentId) == null) {
                temp.feedbackTemplateDao().getActiveForStudent(old.id)?.let { template ->
                    database.feedbackTemplateDao().insert(
                        template.copy(
                            id = 0,
                            studentId = newStudentId,
                            scope = FeedbackTemplate.SCOPE_STUDENT,
                            sourceSampleIdsJson = "[]",
                            version = database.feedbackTemplateDao()
                                .maxVersionForStudent(newStudentId) + 1,
                            isActive = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        if (database.feedbackTemplateDao().getActiveGlobal() == null) {
            temp.feedbackTemplateDao().getActiveGlobal()?.let { template ->
                database.feedbackTemplateDao().insert(
                    template.copy(
                        id = 0,
                        studentId = null,
                        scope = FeedbackTemplate.SCOPE_GLOBAL,
                        sourceSampleIdsJson = "[]",
                        version = database.feedbackTemplateDao().maxGlobalVersion() + 1,
                        isActive = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun copyFeedbackImages(
        temp: AppDatabase,
        copier: ImageCopier,
        oldFeedback: Feedback,
        newFeedbackId: Long,
        newStudentId: Long
    ) {
        val images = temp.feedbackImageDao().listByFeedback(oldFeedback.id)
            .mapNotNull { image ->
                val newUri = copier.copy(image.uri, preferFeedback = true) ?: return@mapNotNull null
                image.copy(
                    id = 0,
                    feedbackId = newFeedbackId,
                    studentId = newStudentId,
                    uri = newUri
                )
            }
        if (images.isNotEmpty()) {
            database.feedbackImageDao().insertAll(images)
        }
    }

    private suspend fun copyMistakeImages(
        temp: AppDatabase,
        copier: ImageCopier,
        oldMistake: MistakeRecord,
        newMistakeId: Long,
        newStudentId: Long
    ) {
        val images = temp.mistakeImageDao().listByMistake(oldMistake.id)
            .mapNotNull { image ->
                val original = copier.copy(image.originalUri, preferFeedback = true)
                    ?: return@mapNotNull null
                image.copy(
                    id = 0,
                    mistakeId = newMistakeId,
                    studentId = newStudentId,
                    originalUri = original,
                    croppedUri = image.croppedUri?.let {
                        copier.copy(it, preferFeedback = false)
                    },
                    figureUri = image.figureUri?.let {
                        copier.copy(it, preferFeedback = false)
                    }
                )
            }
        if (images.isNotEmpty()) {
            database.mistakeImageDao().insertAll(images)
        }
    }

    private fun scoreKey(score: ScoreRecord): String =
        "${score.date}|${score.examName}|${score.score}|${score.fullScore}"

    private fun mistakeKey(mistake: MistakeRecord): String =
        "${mistake.createdAt}|${mistake.stemText}"

    private class ImageCopier(
        private val context: Context,
        private val root: File
    ) {
        private val cache = mutableMapOf<String, String?>()
        private val createdFiles = mutableListOf<File>()
        private var counter = 0

        fun copy(uri: String, preferFeedback: Boolean): String? {
            if (cache.containsKey(uri)) return cache[uri]
            val name = runCatching { Uri.parse(uri).lastPathSegment }.getOrNull()
            if (name.isNullOrBlank()) {
                cache[uri] = null
                return null
            }
            val folders = if (preferFeedback) {
                listOf("feedback_images", "mistake_images")
            } else {
                listOf("mistake_images", "feedback_images")
            }
            val source = folders.asSequence()
                .map { File(File(root, it), name) }
                .firstOrNull { it.isFile }
            if (source == null) {
                cache[uri] = null
                return null
            }

            val folder = source.parentFile?.name ?: "feedback_images"
            val targetDir = File(context.filesDir, folder).apply { mkdirs() }
            val target = File(
                targetDir,
                "merge_${System.currentTimeMillis()}_${counter++}_${source.name}"
            )
            return runCatching {
                source.copyTo(target, overwrite = false)
                createdFiles.add(target)
                Uri.fromFile(target).toString().also { cache[uri] = it }
            }.getOrElse {
                cache[uri] = null
                null
            }
        }

        fun cleanup() {
            createdFiles.forEach { runCatching { it.delete() } }
            createdFiles.clear()
            cache.clear()
        }
    }
}
