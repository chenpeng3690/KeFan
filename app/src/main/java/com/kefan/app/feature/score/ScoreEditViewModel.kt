package com.kefan.app.feature.score

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.dao.ScoreRecordDao
import com.kefan.app.data.local.entity.ScoreRecord
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

data class ScoreForm(
    val date: Long = System.currentTimeMillis(),
    val examName: String = "",
    val subject: String = "",
    val fullScore: String = "100",
    val score: String = "",
    val rank: String = "",
    val classAvg: String = "",
    val tags: String = "",
    val note: String = ""
)

data class ScoreEditUi(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

sealed interface ScoreEditEvent {
    data object Saved : ScoreEditEvent
    data object Deleted : ScoreEditEvent
    data class Message(val text: String) : ScoreEditEvent
}

@HiltViewModel
class ScoreEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scoreRecordDao: ScoreRecordDao,
    studentRepository: StudentRepository
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L
    private val scoreId: Long = savedStateHandle[KfRoutes.ARG_SCORE_ID] ?: -1L

    private val _form = MutableStateFlow(ScoreForm())
    val form: StateFlow<ScoreForm> = _form.asStateFlow()

    private val _ui = MutableStateFlow(ScoreEditUi())
    val ui: StateFlow<ScoreEditUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<ScoreEditEvent>(extraBufferCapacity = 3)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val student = studentRepository.getById(studentId)
            if (scoreId > 0) {
                val record = scoreRecordDao.getById(scoreId)
                if (record == null) {
                    _ui.update { it.copy(loading = false, error = "成绩记录不存在") }
                    return@launch
                }
                _form.value = ScoreForm(
                    date = record.date,
                    examName = record.examName,
                    subject = record.subject.ifBlank { student?.subject.orEmpty() },
                    fullScore = plainScore(record.fullScore),
                    score = plainScore(record.score),
                    rank = record.rank?.toString().orEmpty(),
                    classAvg = record.classAvg?.let { plainScore(it) }.orEmpty(),
                    tags = TemplateJson.decodeStrings(record.knowledgeTagsJson)
                        .joinToString("、"),
                    note = record.note
                )
                _ui.update { it.copy(loading = false, isEdit = true) }
            } else {
                _form.value = ScoreForm(
                    subject = student?.subject.orEmpty(),
                    examName = "",
                    fullScore = "100"
                )
                _ui.update { it.copy(loading = false, isEdit = false) }
            }
        }
    }

    fun updateForm(transform: (ScoreForm) -> ScoreForm) {
        _form.update(transform)
    }

    fun save() {
        val f = _form.value
        val examName = f.examName.trim()
        if (examName.isBlank()) {
            _events.tryEmit(ScoreEditEvent.Message("请填写考试名称"))
            return
        }
        val score = f.score.trim().toDoubleOrNull()
        if (score == null) {
            _events.tryEmit(ScoreEditEvent.Message("请填写有效的得分"))
            return
        }
        val fullScore = f.fullScore.trim().toDoubleOrNull() ?: 100.0
        if (fullScore <= 0.0) {
            _events.tryEmit(ScoreEditEvent.Message("满分必须大于 0"))
            return
        }
        if (_ui.value.saving) return

        val tags = f.tags.trim()
            .split(Regex("[、,，;；\\s]+"))
            .filter { it.isNotBlank() }
            .distinct()

        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val record = ScoreRecord(
                    id = if (scoreId > 0) scoreId else 0,
                    studentId = studentId,
                    date = f.date,
                    examName = examName,
                    subject = f.subject.trim(),
                    fullScore = fullScore,
                    score = score,
                    rank = f.rank.trim().toIntOrNull(),
                    classAvg = f.classAvg.trim().toDoubleOrNull(),
                    knowledgeTagsJson = TemplateJson.encodeStrings(tags),
                    note = f.note.trim()
                )
                if (scoreId > 0) scoreRecordDao.update(record) else scoreRecordDao.insert(record)
                _ui.update { it.copy(saving = false) }
                _events.emit(ScoreEditEvent.Saved)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(saving = false, error = "保存失败：${e.message.orEmpty()}")
                }
            }
        }
    }

    fun delete() {
        if (scoreId <= 0 || _ui.value.saving) return
        viewModelScope.launch {
            try {
                val record = scoreRecordDao.getById(scoreId)
                if (record != null) scoreRecordDao.delete(record)
                _events.emit(ScoreEditEvent.Deleted)
            } catch (e: Exception) {
                _ui.update { it.copy(error = "删除失败：${e.message.orEmpty()}") }
            }
        }
    }

    private fun plainScore(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(java.util.Locale.CHINA, "%.1f", value)
}
