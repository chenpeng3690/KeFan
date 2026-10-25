package com.kefan.app.feature.student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.entity.Student
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentForm(
    val name: String = "",
    val grade: String = "",
    val subject: String = "",
    val school: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val defaultLessons: String = "2",
    val classDay: Int = 0,
    val classTime: String = "",
    val goal: String = "",
    val personality: String = "",
    val parentNote: String = "",
    val status: String = Student.STATUS_ACTIVE
)

data class StudentEditUi(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val isEdit: Boolean = false
)

sealed interface StudentEditEvent {
    data class Saved(val studentId: Long) : StudentEditEvent
    data object Deleted : StudentEditEvent
    data class Message(val text: String) : StudentEditEvent
}

@HiltViewModel
class StudentEditViewModel @Inject constructor(
    private val repository: StudentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L
    private var loaded: Student? = null

    private val _form = MutableStateFlow(StudentForm())
    val form: StateFlow<StudentForm> = _form.asStateFlow()

    private val _ui = MutableStateFlow(StudentEditUi())
    val ui: StateFlow<StudentEditUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<StudentEditEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    init {
        if (studentId > 0) {
            viewModelScope.launch {
                val student = repository.getById(studentId)
                if (student == null) {
                    _ui.update { it.copy(loading = false) }
                    _events.emit(StudentEditEvent.Message("学生档案不存在"))
                } else {
                    loaded = student
                    _form.value = StudentForm(
                        name = student.name,
                        grade = student.grade,
                        subject = student.subject,
                        school = student.school,
                        startDate = student.startDate,
                        defaultLessons = student.defaultLessons.toString(),
                        classDay = student.classDay,
                        classTime = student.classTime,
                        goal = student.goal,
                        personality = student.personality,
                        parentNote = student.parentNote,
                        status = student.status
                    )
                    _ui.value = StudentEditUi(loading = false, isEdit = true)
                }
            }
        } else {
            _ui.value = StudentEditUi(loading = false, isEdit = false)
        }
    }

    fun updateForm(transform: (StudentForm) -> StudentForm) {
        _form.update(transform)
    }

    fun save() {
        val form = _form.value
        val name = form.name.trim()
        if (name.isBlank()) {
            _events.tryEmit(StudentEditEvent.Message("请填写姓名"))
            return
        }
        if (_ui.value.saving) return

        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = loaded
            val student = Student(
                id = existing?.id ?: 0,
                name = name,
                avatarUri = existing?.avatarUri,
                grade = form.grade.trim(),
                subject = form.subject.trim(),
                school = form.school.trim(),
                startDate = form.startDate,
                totalLessons = existing?.totalLessons ?: 0,
                goal = form.goal.trim(),
                personality = form.personality.trim(),
                parentNote = form.parentNote.trim(),
                autoExtractMistakes = false,
                defaultLessons = (form.defaultLessons.toIntOrNull() ?: 2).coerceIn(1, 20),
                classDay = form.classDay.coerceIn(0, 7),
                classTime = form.classTime.trim(),
                status = form.status,
                lastFeedbackAt = existing?.lastFeedbackAt,
                createdAt = existing?.createdAt ?: now
            )

            val id = if (existing == null) {
                repository.add(student)
            } else {
                repository.update(student)
                student.id
            }
            _ui.update { it.copy(saving = false) }
            _events.emit(StudentEditEvent.Saved(id))
        }
    }

    fun delete() {
        val student = loaded ?: return
        viewModelScope.launch {
            repository.delete(student)
            _events.emit(StudentEditEvent.Deleted)
        }
    }
}
