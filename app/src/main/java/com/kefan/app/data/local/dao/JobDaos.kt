package com.kefan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kefan.app.data.local.entity.ExportJob
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportJobDao {
    @Query("SELECT * FROM export_job ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExportJob>>

    @Query("SELECT * FROM export_job WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun observeByStudent(studentId: Long): Flow<List<ExportJob>>

    @Query("SELECT * FROM export_job WHERE id = :id")
    fun observeById(id: Long): Flow<ExportJob?>

    @Query("SELECT * FROM export_job WHERE id = :id")
    suspend fun getById(id: Long): ExportJob?

    @Insert
    suspend fun insert(job: ExportJob): Long

    @Update
    suspend fun update(job: ExportJob)

    @Delete
    suspend fun delete(job: ExportJob)
}
