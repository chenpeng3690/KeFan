package com.kefan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kefan.app.data.local.entity.ContextLog
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.FeedbackFts
import com.kefan.app.data.local.entity.FeedbackImage
import com.kefan.app.data.local.entity.FeedbackTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback WHERE studentId = :studentId AND deletedAt IS NULL ORDER BY classDate DESC, createdAt DESC")
    fun observeByStudent(studentId: Long): Flow<List<Feedback>>

    @Query("SELECT * FROM feedback WHERE studentId = :studentId AND deletedAt IS NULL ORDER BY classDate DESC, createdAt DESC")
    suspend fun listVisibleByStudent(studentId: Long): List<Feedback>

    @Query("SELECT * FROM feedback WHERE studentId = :studentId ORDER BY classDate DESC, createdAt DESC")
    suspend fun listAllByStudent(studentId: Long): List<Feedback>

    @Query("SELECT * FROM feedback WHERE deletedAt IS NULL ORDER BY classDate DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Feedback>>

    @Query("SELECT * FROM feedback WHERE classDate >= :start AND classDate < :end AND deletedAt IS NULL")
    fun observeInRange(start: Long, end: Long): Flow<List<Feedback>>

    @Query("SELECT * FROM feedback WHERE id = :id")
    fun observeById(id: Long): Flow<Feedback?>

    @Query("SELECT * FROM feedback WHERE id = :id")
    suspend fun getById(id: Long): Feedback?

    @Query("SELECT * FROM feedback WHERE studentId = :studentId AND deletedAt IS NULL ORDER BY classDate DESC, createdAt DESC LIMIT 1")
    suspend fun latestForStudent(studentId: Long): Feedback?

    @Query(
        """
        SELECT * FROM feedback
        WHERE studentId = :studentId AND status = 'FINAL' AND deletedAt IS NULL
        ORDER BY classDate ASC, createdAt ASC
        """
    )
    suspend fun listFinalAsc(studentId: Long): List<Feedback>

    @Query("SELECT * FROM feedback WHERE studentId = :studentId AND classDate >= :start AND classDate < :end AND deletedAt IS NULL")
    suspend fun listInRange(studentId: Long, start: Long, end: Long): List<Feedback>

    @Query("SELECT COUNT(*) FROM feedback WHERE studentId = :studentId AND classDate >= :start AND classDate < :end AND deletedAt IS NULL")
    suspend fun countInRange(studentId: Long, start: Long, end: Long): Int

    @Query("SELECT * FROM feedback WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Feedback>>

    @Query("SELECT * FROM feedback WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff ORDER BY deletedAt ASC")
    suspend fun listExpired(cutoff: Long): List<Feedback>

    @Query("SELECT MAX(classDate) FROM feedback WHERE studentId = :studentId AND deletedAt IS NULL")
    suspend fun maxVisibleClassDate(studentId: Long): Long?

    @Query("UPDATE feedback SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun markDeleted(id: Long, deletedAt: Long)

    @Query("UPDATE feedback SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    @Query("DELETE FROM feedback WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insert(feedback: Feedback): Long

    @Update
    suspend fun update(feedback: Feedback)

    @Delete
    suspend fun delete(feedback: Feedback)
}

@Dao
interface FeedbackImageDao {
    @Query("SELECT * FROM feedback_image WHERE feedbackId = :feedbackId ORDER BY orderIndex ASC")
    fun observeByFeedback(feedbackId: Long): Flow<List<FeedbackImage>>

    @Query("SELECT * FROM feedback_image WHERE feedbackId = :feedbackId ORDER BY orderIndex ASC")
    suspend fun listByFeedback(feedbackId: Long): List<FeedbackImage>

    @Query("SELECT * FROM feedback_image WHERE studentId = :studentId ORDER BY feedbackId DESC, orderIndex ASC")
    fun observeByStudent(studentId: Long): Flow<List<FeedbackImage>>

    @Insert
    suspend fun insertAll(images: List<FeedbackImage>): List<Long>

    @Query("DELETE FROM feedback_image WHERE feedbackId = :feedbackId")
    suspend fun deleteByFeedback(feedbackId: Long)

    @Query("UPDATE feedback_image SET ocrText = :ocrText WHERE id = :id")
    suspend fun updateOcrText(id: Long, ocrText: String)
}

@Dao
interface FeedbackTemplateDao {
    @Query(
        """
        SELECT * FROM feedback_template
        WHERE studentId = :studentId AND isActive = 1
        ORDER BY version DESC LIMIT 1
        """
    )
    fun observeActiveForStudent(studentId: Long): Flow<FeedbackTemplate?>

    @Query(
        """
        SELECT * FROM feedback_template
        WHERE studentId = :studentId AND isActive = 1
        ORDER BY version DESC LIMIT 1
        """
    )
    suspend fun getActiveForStudent(studentId: Long): FeedbackTemplate?

    @Query(
        """
        SELECT * FROM feedback_template
        WHERE scope = 'GLOBAL' AND isActive = 1
        ORDER BY version DESC LIMIT 1
        """
    )
    fun observeActiveGlobal(): Flow<FeedbackTemplate?>

    @Query(
        """
        SELECT * FROM feedback_template
        WHERE scope = 'GLOBAL' AND isActive = 1
        ORDER BY version DESC LIMIT 1
        """
    )
    suspend fun getActiveGlobal(): FeedbackTemplate?

    @Query("SELECT * FROM feedback_template WHERE studentId = :studentId ORDER BY version DESC")
    fun observeVersionsForStudent(studentId: Long): Flow<List<FeedbackTemplate>>

    @Query("SELECT * FROM feedback_template WHERE id = :id")
    suspend fun getById(id: Long): FeedbackTemplate?

    @Insert
    suspend fun insert(template: FeedbackTemplate): Long

    @Update
    suspend fun update(template: FeedbackTemplate)

    @Query("UPDATE feedback_template SET isActive = 0 WHERE studentId = :studentId")
    suspend fun deactivateForStudent(studentId: Long)

    @Query("UPDATE feedback_template SET isActive = 0 WHERE scope = 'GLOBAL'")
    suspend fun deactivateGlobal()

    @Query("SELECT COALESCE(MAX(version), 0) FROM feedback_template WHERE studentId = :studentId")
    suspend fun maxVersionForStudent(studentId: Long): Int

    @Query("SELECT COALESCE(MAX(version), 0) FROM feedback_template WHERE scope = 'GLOBAL'")
    suspend fun maxGlobalVersion(): Int
}

@Dao
interface ContextLogDao {
    @Query("SELECT * FROM context_log WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun observeByStudent(studentId: Long): Flow<List<ContextLog>>

    @Query("SELECT * FROM context_log WHERE studentId = :studentId ORDER BY createdAt ASC")
    suspend fun listByStudent(studentId: Long): List<ContextLog>

    @Insert
    suspend fun insert(log: ContextLog): Long

    @Query("UPDATE context_log SET compacted = 1 WHERE id = :id")
    suspend fun markCompacted(id: Long)

    @Query("SELECT COALESCE(SUM(tokenCount), 0) FROM context_log WHERE studentId = :studentId")
    suspend fun totalTokens(studentId: Long): Int
}

@Dao
interface FeedbackFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FeedbackFts)

    @Query("DELETE FROM feedback_fts WHERE rowid = :rowId")
    suspend fun delete(rowId: Long)

    @Query("SELECT rowid, title, content FROM feedback_fts WHERE feedback_fts MATCH :query")
    suspend fun search(query: String): List<FeedbackFts>
}
