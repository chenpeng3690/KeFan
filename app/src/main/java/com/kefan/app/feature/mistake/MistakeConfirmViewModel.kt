package com.kefan.app.feature.mistake

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kefan.app.core.util.ImageProcessor
import com.kefan.app.data.local.AppDatabase
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.dao.MistakeImageDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.entity.MistakeImage
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.repository.MistakeExtractionRepository
import com.kefan.app.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class MistakeConfirmDraft(
    val index: Int,
    val selected: Boolean,
    val stemText: String,
    val studentAnswer: String,
    val correctAnswer: String,
    val tags: String,
    val errorType: String,
    val difficulty: Int,
    val hasFigure: Boolean,
    val figureNote: String,
    val figureBox: List<Float>,
    val figureUri: String? = null,
    val figureRectJson: String? = null,
    val pageIndex: Int,
    val confidence: Double,
    val cropHint: String
)

data class MistakeConfirmUi(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val studentName: String = "",
    val studentSubject: String = "",
    val imageUris: List<String> = emptyList(),
    val drafts: List<MistakeConfirmDraft> = emptyList(),
    val error: String? = null
) {
    val selectedCount: Int get() = drafts.count { it.selected && it.stemText.isNotBlank() }
}

sealed interface MistakeConfirmEvent {
    data class Saved(val count: Int) : MistakeConfirmEvent
    data class Message(val text: String) : MistakeConfirmEvent
}

@HiltViewModel
class MistakeConfirmViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val mistakeRecordDao: MistakeRecordDao,
    private val mistakeImageDao: MistakeImageDao,
    private val studentRepository: StudentRepository,
    private val extractionRepository: MistakeExtractionRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(MistakeConfirmUi())
    val ui = _ui.asStateFlow()

    private val _events = MutableSharedFlow<MistakeConfirmEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var studentId: Long = -1L

    /** 本次确认页里裁剪出来的配图，取消时清理，入库后只保留用到的 */
    private val cropFiles = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val session = extractionRepository.currentSession()
            if (session == null) {
                _ui.update {
                    it.copy(loading = false, error = "没有待确认的错题，请重新提取")
                }
                return@launch
            }
            studentId = session.studentId
            val student = studentRepository.getById(studentId)
            _ui.value = MistakeConfirmUi(
                loading = false,
                studentName = student?.name.orEmpty(),
                studentSubject = student?.subject.orEmpty(),
                imageUris = session.imageUris,
                drafts = session.items.mapIndexed { index, item ->
                    MistakeConfirmDraft(
                        index = index,
                        selected = item.confidence >= 0.8,
                        stemText = item.stemText,
                        studentAnswer = item.studentAnswer,
                        correctAnswer = item.correctAnswer,
                        tags = item.knowledgeTags.joinToString("、"),
                        errorType = item.errorType,
                        difficulty = item.difficulty,
                        hasFigure = item.hasFigure,
                        figureNote = item.figureNote,
                        figureBox = item.figureBox,
                        pageIndex = item.pageIndex,
                        confidence = item.confidence,
                        cropHint = item.cropHint
                    )
                }
            )
        }
    }

    fun applyFigureCrop(
        index: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val draft = _ui.value.drafts.firstOrNull { it.index == index } ?: return
        val session = extractionRepository.currentSession() ?: return
        val pageUri = session.imageUris.getOrNull(draft.pageIndex - 1) ?: return
        val oldFigure = draft.figureUri
        viewModelScope.launch {
            val crop = cropTo(
                pageUri = pageUri,
                box = listOf(left, top, width, height),
                imageDir = File(context.filesDir, "mistake_images")
            )
            if (crop == null) {
                _events.tryEmit(MistakeConfirmEvent.Message("配图框选失败，请重试"))
                return@launch
            }
            Uri.parse(crop.first).path?.let { cropFiles.add(it) }
            oldFigure?.let { Uri.parse(it).path?.let { path -> cropFiles.add(path) } }
            updateDraft(index) {
                it.copy(figureUri = crop.first, figureRectJson = crop.second)
            }
        }
    }

    fun toggleDraft(index: Int) {
        updateDraft(index) { it.copy(selected = !it.selected) }
    }

    fun selectAll(selected: Boolean) {
        _ui.update { ui ->
            ui.copy(drafts = ui.drafts.map { it.copy(selected = selected) })
        }
    }

    fun updateDraft(index: Int, transform: (MistakeConfirmDraft) -> MistakeConfirmDraft) {
        _ui.update { ui ->
            ui.copy(
                drafts = ui.drafts.map {
                    if (it.index == index) transform(it) else it
                }
            )
        }
    }

    private suspend fun cropTo(
        pageUri: String,
        box: List<Float>,
        imageDir: File
    ): Pair<String, String>? {
        if (box.size != 4) return null
        return runCatching {
            val file = ImageProcessor.cropToFile(
                context = context,
                source = Uri.parse(pageUri),
                left = box[0],
                top = box[1],
                width = box[2],
                height = box[3],
                targetDir = imageDir
            )
            val rectJson = box.joinToString(
                prefix = "[",
                postfix = "]"
            ) { String.format(Locale.US, "%.6f", it) }
            Uri.fromFile(file).toString() to rectJson
        }.getOrNull()
    }

    fun confirm() {
        val ui = _ui.value
        if (ui.saving) return
        val selected = ui.drafts.filter { it.selected && it.stemText.isNotBlank() }
        if (selected.isEmpty()) {
            _events.tryEmit(MistakeConfirmEvent.Message("至少勾选一道错题"))
            return
        }
        val missingFigures = selected.count { it.hasFigure && it.figureUri.isNullOrBlank() }
        if (missingFigures > 0) {
            _events.tryEmit(
                MistakeConfirmEvent.Message("还有 $missingFigures 道含图题没有框选配图")
            )
            return
        }

        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val session = extractionRepository.currentSession()
                val feedbackId = session?.feedbackId?.takeIf { it > 0 }
                val now = System.currentTimeMillis()

                database.withTransaction {
                    selected.forEachIndexed { position, draft ->
                        val tags = draft.tags.trim()
                            .split(Regex("[、,，;；\\s]+"))
                            .filter { it.isNotBlank() }
                            .distinct()
                        val record = MistakeRecord(
                            studentId = studentId,
                            subject = ui.studentSubject,
                            stemText = draft.stemText.trim(),
                            studentAnswer = draft.studentAnswer.trim(),
                            correctAnswer = draft.correctAnswer.trim(),
                            answerStatus = if (draft.correctAnswer.isNotBlank()) {
                                MistakeRecord.ANSWER_AI_GENERATED
                            } else {
                                MistakeRecord.ANSWER_NONE
                            },
                            knowledgeTagsJson = TemplateJson.encodeStrings(tags),
                            errorType = draft.errorType,
                            sourceType = MistakeRecord.SOURCE_CLASS,
                            sourceFeedbackId = feedbackId,
                            difficulty = draft.difficulty.coerceIn(1, 5),
                            hasFigure = draft.hasFigure,
                            ocrConfidence = draft.confidence,
                            note = draft.cropHint,
                            createdAt = now + position,
                            updatedAt = now
                        )
                        val mistakeId = mistakeRecordDao.insert(record)

                        val pageUri = ui.imageUris.getOrNull(draft.pageIndex - 1)
                        if (pageUri != null) {
                            mistakeImageDao.insertAll(
                                listOf(
                                    MistakeImage(
                                        mistakeId = mistakeId,
                                        studentId = studentId,
                                        originalUri = pageUri,
                                        figureUri = draft.figureUri,
                                        figureRectJson = draft.figureRectJson,
                                        figureConfirmed = !draft.figureUri.isNullOrBlank(),
                                        cropRectJson = draft.figureRectJson,
                                        orderIndex = 0
                                    )
                                )
                            )
                        }
                    }
                }

                val usedFigures = _ui.value.drafts
                    .mapNotNull { it.figureUri }
                    .mapNotNull { Uri.parse(it).path }
                    .toSet()
                cropFiles.filterNot { it in usedFigures }.forEach { path ->
                    runCatching { File(path).delete() }
                }
                cropFiles.clear()
                extractionRepository.clear()
                _ui.update { it.copy(saving = false) }
                _events.tryEmit(MistakeConfirmEvent.Saved(selected.size))
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        saving = false,
                        error = "入库失败：${e.message.orEmpty()}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cropFiles.forEach { path ->
            runCatching { File(path).delete() }
        }
        cropFiles.clear()
    }
}
