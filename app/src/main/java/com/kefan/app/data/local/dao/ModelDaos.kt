package com.kefan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kefan.app.data.local.entity.ModelConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_config ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ModelConfig>>

    @Query("SELECT * FROM model_config WHERE id = :id")
    fun observeById(id: Long): Flow<ModelConfig?>

    @Query("SELECT * FROM model_config WHERE id = :id")
    suspend fun getById(id: Long): ModelConfig?

    @Insert
    suspend fun insert(config: ModelConfig): Long

    @Update
    suspend fun update(config: ModelConfig)

    @Delete
    suspend fun delete(config: ModelConfig)

    @Query("SELECT COUNT(*) FROM model_config")
    fun observeCount(): Flow<Int>
}
