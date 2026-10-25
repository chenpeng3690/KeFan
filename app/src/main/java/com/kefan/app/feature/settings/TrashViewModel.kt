package com.kefan.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.preferences.AppPreferences
import com.kefan.app.data.repository.FeedbackRepository
import com.kefan.app.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashItem(
    val feedback: Feedback,
    val studentName: String,
    val remainingDays: Int
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    studentRepository: StudentRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val retentionDays: StateFlow<Int> = preferences.trashRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 7)

    val items: StateFlow<List<TrashItem>> = combine(
        feedbackRepository.observeTrash(),
        studentRepository.observeAll(),
        preferences.trashRetentionDays
    ) { feedbacks, students, days ->
        val names = students.associate { it.id to it.name }
        val dayMillis = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        feedbacks.map { feedback ->
            val deletedAt = feedback.deletedAt ?: now
            val expireAt = deletedAt + days * dayMillis
            val remaining = ((expireAt - now) / dayMillis).toInt().coerceAtLeast(0)
            TrashItem(
                feedback = feedback,
                studentName = names[feedback.studentId] ?: "未知学生",
                remainingDays = remaining
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val days = preferences.trashRetentionDays.first()
            runCatching { feedbackRepository.purgeExpired(days) }
        }
    }

    fun restore(feedbackId: Long) {
        viewModelScope.launch {
            runCatching { feedbackRepository.restore(feedbackId) }
                .onSuccess { _message.value = "已恢复，回到学生反馈列表" }
                .onFailure { _message.value = "恢复失败：${it.message.orEmpty()}" }
        }
    }

    fun deleteForever(feedbackId: Long) {
        viewModelScope.launch {
            runCatching { feedbackRepository.permanentlyDelete(feedbackId) }
                .onSuccess { _message.value = "已彻底删除" }
                .onFailure { _message.value = "删除失败：${it.message.orEmpty()}" }
        }
    }

    fun setRetentionDays(days: Int) {
        val safe = days.coerceIn(1, 365)
        viewModelScope.launch {
            preferences.setTrashRetentionDays(safe)
            val removed = runCatching { feedbackRepository.purgeExpired(safe) }.getOrDefault(0)
            _message.value = if (removed > 0) {
                "已设为 $safe 天，并清理了 $removed 条过期反馈"
            } else {
                "已设为 $safe 天自动清理"
            }
        }
    }

    fun dismissMessage() {
        _message.update { null }
    }
}
