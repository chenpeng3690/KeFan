package com.kefan.app.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.kefan.app.core.util.formatDate
import com.kefan.app.core.util.toLocalDate
import com.kefan.app.data.local.AppDatabase
import com.kefan.app.data.local.dao.FeedbackDao
import com.kefan.app.data.local.dao.FeedbackImageDao
import com.kefan.app.data.local.dao.MistakeImageDao
import com.kefan.app.data.local.dao.StudentDao
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.FeedbackImage
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(
    private val database: AppDatabase,
    private val feedbackDao: FeedbackDao,
    private val feedbackImageDao: FeedbackImageDao,
    private val mistakeImageDao: MistakeImageDao,
    private val studentDao: StudentDao
) {
    fun observeById(id: Long) = feedbackDao.observeById(id)

    /** 回收站里的反馈，按删除时间倒序。 */
    fun observeTrash(): Flow<List<Feedback>> = feedbackDao.observeTrash()

    suspend fun getById(id: Long): Feedback? = feedbackDao.getById(id)

    /**
     * 2026-10-22 张三反馈；同一天同一学生第 2 条起追加 (2)、(3)。
     */
    suspend fun nextTitle(studentId: Long, classDate: Long): String {
        val student = studentDao.getById(studentId)
        val date = classDate.toLocalDate()
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val count = feedbackDao.countInRange(studentId, start, end)
        val base = "${formatDate(classDate)} ${student?.name.orEmpty()}反馈".trim()
        return if (count == 0) base else "$base (${count + 1})"
    }

    suspend fun save(feedback: Feedback, imageFiles: List<File>): Long =
        database.withTransaction {
            val id = feedbackDao.insert(feedback)
            if (imageFiles.isNotEmpty()) {
                feedbackImageDao.insertAll(
                    imageFiles.mapIndexed { index, file ->
                        FeedbackImage(
                            feedbackId = id,
                            studentId = feedback.studentId,
                            uri = Uri.fromFile(file).toString(),
                            orderIndex = index
                        )
                    }
                )
            }
            studentDao.applyFeedback(
                id = feedback.studentId,
                time = feedback.classDate
            )
            id
        }

    suspend fun updateContent(feedbackId: Long, content: String) {
        val feedback = feedbackDao.getById(feedbackId) ?: return
        feedbackDao.update(
            feedback.copy(
                finalContent = content,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 删除到回收站，并回退学生的“最近反馈”时间。 */
    suspend fun softDelete(feedbackId: Long) {
        database.withTransaction {
            val feedback = feedbackDao.getById(feedbackId) ?: return@withTransaction
            if (feedback.deletedAt != null) return@withTransaction
            feedbackDao.markDeleted(feedbackId, System.currentTimeMillis())
            studentDao.setLastFeedback(
                feedback.studentId,
                feedbackDao.maxVisibleClassDate(feedback.studentId)
            )
        }
    }

    suspend fun restore(feedbackId: Long) {
        database.withTransaction {
            val feedback = feedbackDao.getById(feedbackId) ?: return@withTransaction
            if (feedback.deletedAt == null) return@withTransaction
            feedbackDao.restore(feedbackId, System.currentTimeMillis())
            studentDao.applyFeedback(feedback.studentId, feedback.classDate)
        }
    }

    /** 彻底删除反馈及其照片文件。 */
    suspend fun permanentlyDelete(feedbackId: Long) {
        val feedback = feedbackDao.getById(feedbackId) ?: return
        val images = feedbackImageDao.listByFeedback(feedbackId)
        database.withTransaction {
            feedbackDao.deleteById(feedbackId)
            studentDao.setLastFeedback(
                feedback.studentId,
                feedbackDao.maxVisibleClassDate(feedback.studentId)
            )
        }
        images.forEach { deleteImageFile(it.uri) }
    }

    /** 清理超过保留天数的回收站记录，返回删除条数。 */
    suspend fun purgeExpired(retentionDays: Int): Int {
        val days = retentionDays.coerceIn(1, 365)
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val expired = feedbackDao.listExpired(cutoff)
        if (expired.isEmpty()) return 0

        val affectedStudents = expired.map { it.studentId }.distinct()
        expired.forEach { feedback ->
            val images = feedbackImageDao.listByFeedback(feedback.id)
            feedbackDao.deleteById(feedback.id)
            images.forEach { deleteImageFile(it.uri) }
        }
        affectedStudents.forEach { studentId ->
            studentDao.setLastFeedback(
                studentId,
                feedbackDao.maxVisibleClassDate(studentId)
            )
        }
        return expired.size
    }

    private suspend fun deleteImageFile(uriText: String) {
        // 错题可能仍在引用这张教案照片，不能删
        if (mistakeImageDao.countByUri(uriText) > 0) return
        runCatching {
            val path = Uri.parse(uriText).path ?: return@runCatching
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }
}
