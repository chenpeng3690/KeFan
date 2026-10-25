package com.kefan.app.feature.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.core.util.formatDate
import com.kefan.app.data.local.dao.FeedbackImageDao
import com.kefan.app.data.local.dao.MemoryCardDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.dao.ScoreRecordDao
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.FeedbackImage
import com.kefan.app.data.local.entity.Student
import com.kefan.app.data.preferences.AppPreferences
import com.kefan.app.data.quality.FeedbackReview
import com.kefan.app.data.quality.ReviewIssue
import com.kefan.app.data.quality.SensitiveDictionary
import com.kefan.app.data.repository.FeedbackRepository
import com.kefan.app.data.repository.SensitiveWordsRepository
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedbackDetailUi(
    val loading: Boolean = true,
    val feedback: Feedback? = null,
    val student: Student? = null,
    val images: List<FeedbackImage> = emptyList(),
    val content: String = "",
    val reviewIssues: List<ReviewIssue> = emptyList(),
    val dismissedIssueKeys: Set<String> = emptySet(),
    val keepEmoji: Boolean = false,
    val strictFactCheck: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
) {
    val unresolvedIssues: List<ReviewIssue>
        get() = FeedbackReview.unresolved(reviewIssues, dismissedIssueKeys)

    val strictBlocked: Boolean
        get() = strictFactCheck && unresolvedIssues.isNotEmpty()
}

sealed interface FeedbackDetailEvent {
    data class Message(val text: String) : FeedbackDetailEvent
    data object Deleted : FeedbackDetailEvent
}

@HiltViewModel
class FeedbackDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedbackRepository: FeedbackRepository,
    private val studentRepository: StudentRepository,
    private val feedbackImageDao: FeedbackImageDao,
    private val scoreRecordDao: ScoreRecordDao,
    private val mistakeRecordDao: MistakeRecordDao,
    private val memoryCardDao: MemoryCardDao,
    private val preferences: AppPreferences,
    private val sensitiveWordsRepository: SensitiveWordsRepository
) : ViewModel() {

    private val feedbackId: Long = savedStateHandle[KfRoutes.ARG_FEEDBACK_ID] ?: -1L

    private val _ui = MutableStateFlow(FeedbackDetailUi())
    val ui: StateFlow<FeedbackDetailUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<FeedbackDetailEvent>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()

    private var loaded = false
    private var reviewSources: List<String> = emptyList()
    private var sensitiveWords: List<String> = SensitiveDictionary.defaultWords

    init {
        viewModelScope.launch {
            preferences.copyKeepEmoji.collect { keep ->
                _ui.update { it.copy(keepEmoji = keep) }
            }
        }
        viewModelScope.launch {
            preferences.strictFactCheck.collect { strict ->
                _ui.update { it.copy(strictFactCheck = strict) }
            }
        }
        viewModelScope.launch {
            sensitiveWordsRepository.words.collect { words ->
                sensitiveWords = words
            }
        }
        viewModelScope.launch {
            feedbackRepository.observeById(feedbackId).collect { feedback ->
                if (feedback == null) {
                    _ui.update { it.copy(loading = false, error = "反馈不存在") }
                    return@collect
                }
                val student = studentRepository.getById(feedback.studentId)
                val images = feedbackImageDao.listByFeedback(feedbackId)
                if (!loaded) {
                    loaded = true
                    reviewSources = buildSources(feedback, student)
                    _ui.value = FeedbackDetailUi(
                        loading = false,
                        feedback = feedback,
                        student = student,
                        images = images,
                        content = feedback.finalContent,
                        reviewIssues = FeedbackReview.analyze(
                            feedback.finalContent,
                            reviewSources,
                            sensitiveWords
                        ),
                        keepEmoji = _ui.value.keepEmoji,
                        strictFactCheck = _ui.value.strictFactCheck
                    )
                } else {
                    _ui.update {
                        it.copy(
                            loading = false,
                            feedback = feedback,
                            student = student,
                            images = images
                        )
                    }
                }
            }
        }
    }

    fun updateContent(value: String) {
        _ui.update {
            it.copy(
                content = value,
                reviewIssues = FeedbackReview.analyze(value, reviewSources, sensitiveWords)
            )
        }
    }

    fun keepIssue(issue: ReviewIssue) {
        _ui.update {
            it.copy(dismissedIssueKeys = it.dismissedIssueKeys + issue.key)
        }
    }

    fun deleteIssueSentence(issue: ReviewIssue) {
        val state = _ui.value
        val updated = FeedbackReview.deleteSentence(state.content, issue)
        updateContent(updated)
    }

    fun applySuggestion(issue: ReviewIssue, suggestion: String) {
        val state = _ui.value
        val updated = FeedbackReview.replaceIssue(state.content, issue, suggestion)
        updateContent(updated)
    }

    fun save() {
        val state = _ui.value
        if (state.saving || state.content.isBlank()) return
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                feedbackRepository.updateContent(feedbackId, state.content)
                _ui.update { it.copy(saving = false) }
                _events.emit(FeedbackDetailEvent.Message("已保存"))
            } catch (e: Exception) {
                _ui.update {
                    it.copy(saving = false, error = "保存失败：${e.message.orEmpty()}")
                }
            }
        }
    }

    fun delete() {
        val state = _ui.value
        if (state.saving) return
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                feedbackRepository.softDelete(feedbackId)
                _ui.update { it.copy(saving = false) }
                _events.emit(FeedbackDetailEvent.Deleted)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(saving = false, error = "删除失败：${e.message.orEmpty()}")
                }
            }
        }
    }

    private suspend fun buildSources(feedback: Feedback, student: Student?): List<String> {
        val sources = mutableListOf<String>()
        sources += feedback.title
        sources += formatDate(feedback.classDate)
        sources += "${feedback.lessonCount} 课时"
        sources += feedback.summary
        student?.let {
            sources += it.name
            sources += it.grade
            sources += it.subject
            sources += it.school
            sources += it.goal
            sources += it.personality
            sources += it.parentNote
        }
        val scoreStart = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        scoreRecordDao.listInRange(feedback.studentId, scoreStart, System.currentTimeMillis() + 1)
            .sortedByDescending { it.date }
            .take(3)
            .forEach { score ->
                sources += "${score.examName} ${formatScore(score.score)} 分 / " +
                    "${formatScore(score.fullScore)} 分"
                sources += formatDate(score.date)
                score.rank?.let { sources += "第 $it 名" }
                sources += score.knowledgeTagsJson
            }
        mistakeRecordDao.observeByStudent(feedback.studentId).first().forEach { mistake ->
            sources += mistake.stemText
            sources += mistake.knowledgeTagsJson
            sources += mistake.errorType
            sources += formatDate(mistake.createdAt)
        }
        memoryCardDao.get(feedback.studentId)?.let { memory ->
            sources += memory.longTermProfile
            sources += memory.recentSummary
        }
        return sources.filter { it.isNotBlank() }
    }

    private fun formatScore(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(java.util.Locale.CHINA, "%.1f", value)
}
