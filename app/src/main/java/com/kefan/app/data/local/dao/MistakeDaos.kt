package com.kefan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kefan.app.data.local.entity.MistakeFts
import com.kefan.app.data.local.entity.MistakeImage
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.ReviewLog
import kotlinx.coroutines.flow.Flow

@Dao
interface MistakeRecordDao {
    @Query("SELECT * FROM mistake_record ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MistakeRecord>>

    @Query("SELECT * FROM mistake_record WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun observeByStudent(studentId: Long): Flow<List<MistakeRecord>>

    @Query(
        """
        SELECT * FROM mistake_record
        WHERE studentId = :studentId AND reviewStatus = :status
        ORDER BY createdAt DESC
        """
    )
    fun observeByStatus(studentId: Long, status: String): Flow<List<MistakeRecord>>

    @Query("SELECT * FROM mistake_record WHERE id = :id")
    fun observeById(id: Long): Flow<MistakeRecord?>

    @Query("SELECT * FROM mistake_record WHERE id = :id")
    suspend fun getById(id: Long): MistakeRecord?

    @Query(
        """
        SELECT * FROM mistake_record
        WHERE studentId = :studentId AND createdAt >= :start AND createdAt <= :end
        ORDER BY createdAt ASC
        """
    )
    suspend fun listInRange(studentId: Long, start: Long, end: Long): List<MistakeRecord>

    @Query("SELECT * FROM mistake_record WHERE studentId = :studentId ORDER BY createdAt DESC")
    suspend fun listByStudent(studentId: Long): List<MistakeRecord>

    @Query(
        """
        SELECT * FROM mistake_record
        WHERE studentId = :studentId AND createdAt >= :start
        ORDER BY createdAt DESC
        """
    )
    suspend fun listRecent(studentId: Long, start: Long): List<MistakeRecord>

    @Query("SELECT COUNT(*) FROM mistake_record WHERE studentId = :studentId")
    fun observeCount(studentId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM mistake_record WHERE studentId = :studentId AND reviewStatus = :status")
    fun observeCountByStatus(studentId: Long, status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM mistake_record WHERE studentId = :studentId AND reviewStatus = :status")
    suspend fun countByStatus(studentId: Long, status: String): Int

    @Insert
    suspend fun insert(record: MistakeRecord): Long

    @Insert
    suspend fun insertAll(records: List<MistakeRecord>): List<Long>

    @Update
    suspend fun update(record: MistakeRecord)

    @Delete
    suspend fun delete(record: MistakeRecord)
}

@Dao
interface MistakeImageDao {
    @Query("SELECT * FROM mistake_image WHERE mistakeId = :mistakeId ORDER BY orderIndex ASC")
    fun observeByMistake(mistakeId: Long): Flow<List<MistakeImage>>

    @Query("SELECT * FROM mistake_image WHERE studentId = :studentId ORDER BY orderIndex ASC")
    fun observeByStudent(studentId: Long): Flow<List<MistakeImage>>

    @Query("SELECT * FROM mistake_image WHERE mistakeId = :mistakeId ORDER BY orderIndex ASC")
    suspend fun listByMistake(mistakeId: Long): List<MistakeImage>

    @Query(
        """
        SELECT COUNT(*) FROM mistake_image
        WHERE originalUri = :uri OR croppedUri = :uri OR figureUri = :uri
        """
    )
    suspend fun countByUri(uri: String): Int

    @Insert
    suspend fun insertAll(images: List<MistakeImage>): List<Long>

    @Update
    suspend fun update(image: MistakeImage)

    @Query("DELETE FROM mistake_image WHERE mistakeId = :mistakeId")
    suspend fun deleteByMistake(mistakeId: Long)
}

@Dao
interface ReviewLogDao {
    @Query("SELECT * FROM review_log WHERE mistakeId = :mistakeId ORDER BY date DESC")
    fun observeByMistake(mistakeId: Long): Flow<List<ReviewLog>>

    @Query("SELECT * FROM review_log WHERE mistakeId = :mistakeId ORDER BY date ASC")
    suspend fun listByMistake(mistakeId: Long): List<ReviewLog>

    @Insert
    suspend fun insert(log: ReviewLog): Long
}

@Dao
interface MistakeFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MistakeFts)

    @Query("DELETE FROM mistake_fts WHERE rowid = :rowId")
    suspend fun delete(rowId: Long)

    @Query("SELECT rowid, stemText, analysis FROM mistake_fts WHERE mistake_fts MATCH :query")
    suspend fun search(query: String): List<MistakeFts>
}
