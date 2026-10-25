package com.kefan.app.feature.student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.dao.FeedbackDao
import com.kefan.app.data.local.dao.FeedbackImageDao
import com.kefan.app.data.local.dao.MemoryCardDao
import com.kefan.app.data.local.dao.MistakeImageDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.dao.ScoreRecordDao
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.FeedbackImage
import com.kefan.app.data.local.entity.FeedbackTemplate
import com.kefan.app.data.local.entity.ManualSample
import com.kefan.app.data.local.entity.MemoryCard
import com.kefan.app.data.local.entity.MistakeImage
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.ScoreRecord
import com.kefan.app.data.local.entity.Student
import com.kefan.app.data.repository.MemoryMaintenance
import com.kefan.app.data.repository.MemoryTaskState
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.data.repository.TemplateRepository
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AlbumUi(
    val student: Student? = null,
    val feedbacks: List<Feedback> = emptyList(),
    val scores: List<ScoreRecord> = emptyList(),
    val mistakes: List<MistakeRecord> = emptyList(),
    val images: List<FeedbackImage> = emptyList(),
    val mistakeImages: List<MistakeImage> = emptyList(),
    val studentTemplate: FeedbackTemplate? = null,
    val globalTemplate: FeedbackTemplate? = null,
    val samples: List<ManualSample> = emptyList(),
    val memory: MemoryCard? = null,
    val loading: Boolean = true
) {
    val latestFeedback: Feedback? get() = feedbacks.firstOrNull()
    val latestScore: ScoreRecord? get() = scores.firstOrNull()
    val previousScore: ScoreRecord? get() = scores.getOrNull(1)
    val pendingMistakeCount: Int get() = mistakes.count {
        it.reviewStatus == MistakeRecord.REVIEW_TODO
    }
    val effectiveTemplate: FeedbackTemplate? get() = studentTemplate ?: globalTemplate
    val hasFallbackTemplate: Boolean get() = effectiveTemplate == null
}

private data class AlbumCore(
    val student: Student?,
    val feedbacks: List<Feedback>,
    val scores: List<ScoreRecord>,
    val mistakes: List<MistakeRecord>
)

private data class AlbumExtra(
    val studentTemplate: FeedbackTemplate?,
    val globalTemplate: FeedbackTemplate?,
    val samples: List<ManualSample>,
    val memory: MemoryCard?,
    val images: List<FeedbackImage>,
    val mistakeImages: List<MistakeImage>
)

@HiltViewModel
class StudentAlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    studentRepository: StudentRepository,
    feedbackDao: FeedbackDao,
    scoreRecordDao: ScoreRecordDao,
    mistakeRecordDao: MistakeRecordDao,
    feedbackImageDao: FeedbackImageDao,
    mistakeImageDao: MistakeImageDao,
    memoryCardDao: MemoryCardDao,
    templateRepository: TemplateRepository,
    private val memoryMaintenance: MemoryMaintenance
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L

    private val coreFlow = combine(
        studentRepository.observeById(studentId),
        feedbackDao.observeByStudent(studentId),
        scoreRecordDao.observeByStudent(studentId),
        mistakeRecordDao.observeByStudent(studentId)
    ) { student, feedbacks, scores, mistakes ->
        AlbumCore(student, feedbacks, scores, mistakes)
    }

    private val extraFlow = combine(
        templateRepository.observeActiveTemplate(studentId),
        templateRepository.observeGlobalTemplate(),
        templateRepository.observeSamples(studentId),
        memoryCardDao.observe(studentId),
        combine(
            feedbackImageDao.observeByStudent(studentId),
            mistakeImageDao.observeByStudent(studentId)
        ) { feedbackImages, mistakeImages -> feedbackImages to mistakeImages }
    ) { studentTemplate, globalTemplate, samples, memory, images ->
        AlbumExtra(
            studentTemplate = studentTemplate,
            globalTemplate = globalTemplate,
            samples = samples,
            memory = memory,
            images = images.first,
            mistakeImages = images.second
        )
    }

    val ui: StateFlow<AlbumUi> = combine(coreFlow, extraFlow) { core, extra ->
        val visibleFeedbackIds = core.feedbacks.mapTo(mutableSetOf()) { it.id }
        AlbumUi(
            student = core.student,
            feedbacks = core.feedbacks,
            scores = core.scores,
            mistakes = core.mistakes,
            images = extra.images.filter { it.feedbackId in visibleFeedbackIds },
            mistakeImages = extra.mistakeImages,
            studentTemplate = extra.studentTemplate,
            globalTemplate = extra.globalTemplate,
            samples = extra.samples,
            memory = extra.memory,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUi())

    val memoryState: StateFlow<MemoryTaskState> = memoryMaintenance.states
        .map { it[studentId] ?: MemoryTaskState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryTaskState())

    fun compactMemory() {
        memoryMaintenance.compact(studentId, force = true)
    }

    fun rebuildMemoryProfile() {
        memoryMaintenance.rebuildProfile(studentId)
    }

    fun clearMemoryMessage() {
        memoryMaintenance.clearMessage(studentId)
    }
}
