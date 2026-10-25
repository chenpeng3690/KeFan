package com.kefan.app.data.repository

import com.kefan.app.data.local.dao.StudentDao
import com.kefan.app.data.local.entity.Student
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val dao: StudentDao
) {
    fun observeAll(): Flow<List<Student>> = dao.observeAll()

    fun observeById(id: Long): Flow<Student?> = dao.observeById(id)

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeActiveCount(): Flow<Int> = dao.observeCountByStatus(Student.STATUS_ACTIVE)

    suspend fun getById(id: Long): Student? = dao.getById(id)

    suspend fun add(student: Student): Long = dao.insert(student)

    suspend fun update(student: Student) = dao.update(student)

    suspend fun delete(student: Student) = dao.delete(student)
}
