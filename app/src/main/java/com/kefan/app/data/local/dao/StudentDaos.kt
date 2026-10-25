package com.kefan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kefan.app.data.local.entity.ManualSample
import com.kefan.app.data.local.entity.MemoryCard
import com.kefan.app.data.local.entity.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query(
        """
        SELECT * FROM student
        ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PAUSED' THEN 1 ELSE 2 END,
                 COALESCE(lastFeedbackAt, 0) DESC, createdAt DESC
        """
    )
    fun observeAll(): Flow<List<Student>>

    @Query("SELECT * FROM student")
    suspend fun listAll(): List<Student>

    @Query("SELECT * FROM student WHERE id = :id")
    fun observeById(id: Long): Flow<Student?>

    @Query("SELECT * FROM student WHERE id = :id")
    suspend fun getById(id: Long): Student?

    @Insert
    suspend fun insert(student: Student): Long

    @Update
    suspend fun update(student: Student)

    @Delete
    suspend fun delete(student: Student)

    @Query("UPDATE student SET lastFeedbackAt = :time WHERE id = :id")
    suspend fun setLastFeedback(id: Long, time: Long?)

    @Query(
        """
        UPDATE student
        SET lastFeedbackAt = MAX(COALESCE(lastFeedbackAt, 0), :time)
        WHERE id = :id
        """
    )
    suspend fun applyFeedback(id: Long, time: Long)

    @Query("SELECT COUNT(*) FROM student")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM student WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>
}

@Dao
interface MemoryCardDao {
    @Query("SELECT * FROM memory_card WHERE studentId = :studentId")
    fun observe(studentId: Long): Flow<MemoryCard?>

    @Query("SELECT * FROM memory_card WHERE studentId = :studentId")
    suspend fun get(studentId: Long): MemoryCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: MemoryCard)

    @Query("DELETE FROM memory_card WHERE studentId = :studentId")
    suspend fun delete(studentId: Long)
}

@Dao
interface ManualSampleDao {
    @Query("SELECT * FROM manual_sample WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun observeByStudent(studentId: Long): Flow<List<ManualSample>>

    @Query("SELECT * FROM manual_sample WHERE studentId = :studentId ORDER BY createdAt ASC")
    suspend fun listByStudent(studentId: Long): List<ManualSample>

    @Query("SELECT * FROM manual_sample WHERE id = :id")
    suspend fun getById(id: Long): ManualSample?

    @Insert
    suspend fun insert(sample: ManualSample): Long

    @Insert
    suspend fun insertAll(samples: List<ManualSample>): List<Long>

    @Query("UPDATE manual_sample SET analyzed = :analyzed WHERE id = :id")
    suspend fun markAnalyzed(id: Long, analyzed: Boolean = true)

    @Query("UPDATE manual_sample SET analyzed = 1 WHERE studentId = :studentId")
    suspend fun markAllAnalyzed(studentId: Long)

    @Delete
    suspend fun delete(sample: ManualSample)

    @Query("SELECT COUNT(*) FROM manual_sample WHERE studentId = :studentId")
    suspend fun countByStudent(studentId: Long): Int
}
