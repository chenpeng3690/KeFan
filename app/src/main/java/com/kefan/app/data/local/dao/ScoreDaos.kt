package com.kefan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kefan.app.data.local.entity.ScoreRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreRecordDao {
    @Query("SELECT * FROM score_record WHERE studentId = :studentId ORDER BY date DESC, createdAt DESC")
    fun observeByStudent(studentId: Long): Flow<List<ScoreRecord>>

    @Query("SELECT * FROM score_record WHERE studentId = :studentId ORDER BY date DESC, createdAt DESC LIMIT :limit")
    suspend fun listRecent(studentId: Long, limit: Int): List<ScoreRecord>

    @Query("SELECT * FROM score_record WHERE studentId = :studentId AND date >= :start AND date <= :end ORDER BY date ASC")
    suspend fun listInRange(studentId: Long, start: Long, end: Long): List<ScoreRecord>

    @Query("SELECT * FROM score_record WHERE studentId = :studentId ORDER BY date DESC, createdAt DESC LIMIT 1")
    suspend fun latestForStudent(studentId: Long): ScoreRecord?

    @Query("SELECT * FROM score_record WHERE id = :id")
    suspend fun getById(id: Long): ScoreRecord?

    @Insert
    suspend fun insert(record: ScoreRecord): Long

    @Update
    suspend fun update(record: ScoreRecord)

    @Delete
    suspend fun delete(record: ScoreRecord)

    @Query("SELECT COUNT(*) FROM score_record WHERE studentId = :studentId")
    fun observeCount(studentId: Long): Flow<Int>
}
