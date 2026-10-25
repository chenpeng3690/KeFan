package com.kefan.app.feature.mistake

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kefan.app.core.util.ImageProcessor
import com.kefan.app.data.local.AppDatabase
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.dao.MistakeImageDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.dao.ReviewLogDao
import com.kefan.app.data.local.entity.MistakeImage
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.ReviewLog
import com.kefan.app.data.repository.MistakeExtractionRepository
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class MistakeImageDraft(
    val originalUri: String,
    val croppedUri: String? = null,
    val cropRectJson: String? = null,
    val figureUri: String? = null,
    val figureRectJson: String? = null,
    val figureConfirmed: Boolean = false,
    val orderIndex: Int = 0
)

data class MistakeForm(
    val subject: String = "",
    val stemText: String = "",
    val studentAnswer: String = "",
    val correctAnswer: String = "",
    val analysis: String = "",
    val tags: String = "",
    val errorType: String = "",
    val sourceType: String = MistakeRecord.SOURCE_CLASS,
    val difficulty: Int = 3,
    val note: String = "",
    val reviewStatus: String = MistakeRecord.REVIEW_TODO,
    val hasFigure: Boolean = false
)

data class MistakeEditUi(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val saving: Boolean = false,
    val studentName: String = "",
    val reviewCount: Int = 0,
    val correctStreak: Int = 0,
    val recognizing: Boolean = false,
    val error: String? = null,
    val duplicateOf: String? = null
)

sealed interface MistakeEditEvent {
    data class Saved(val mistakeId: Long) : MistakeEditEvent
    data object Deleted : MistakeEditEvent
    data class Message(val text: String) : MistakeEditEvent
    data class FigureCropReady(
        val index: Int,
        val initialRect: List<Float>
    ) : MistakeEditEvent
}

@HiltViewModel
class MistakeEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val mistakeRecordDao: MistakeRecordDao,
    private val mistakeImageDao: MistakeImageDao,
    private val reviewLogDao: ReviewLogDao,
    private val studentRepository: StudentRepository,
    private val extractionRepository: MistakeExtractionRepository
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L
    private val mistakeId: Long = savedStateHandle[KfRoutes.ARG_MISTAKE_ID] ?: -1L

    private var loaded: MistakeRecord? = null

    /** 本次编辑新生成、还未被数据库引用的文件，取消编辑时要删掉 */
    private val draftFiles = mutableSetOf<String>()

    /** 已入库但本次被移除/替换的文件，保存成功后再删，取消编辑则保留 */
    private val retiredFiles = mutableSetOf<String>()

    private val _form = MutableStateFlow(MistakeForm())
    val form: StateFlow<MistakeForm> = _form.asStateFlow()

    private val _images = MutableStateFlow<List<MistakeImageDraft>>(emptyList())
    val images: StateFlow<List<MistakeImageDraft>> = _images.asStateFlow()

    private val _ui = MutableStateFlow(MistakeEditUi())
    val ui: StateFlow<MistakeEditUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<MistakeEditEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val reviewLogs: StateFlow<List<ReviewLog>> = if (mistakeId > 0) {
        reviewLogDao.observeByMistake(mistakeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    } else {
        MutableStateFlow<List<ReviewLog>>(emptyList())
    }

    private var studentName: String = ""
    private var studentSubject: String = ""

    init {
        viewModelScope.launch {
            val student = studentRepository.getById(studentId)
            studentName = student?.name.orEmpty()
            studentSubject = student?.subject.orEmpty()

            if (mistakeId > 0) {
                val record = mistakeRecordDao.getById(mistakeId)
                if (record == null) {
                    _ui.update {
                        it.copy(loading = false, error = "错题记录不存在", studentName = studentName)
                    }
                    return@launch
                }
                loaded = record
                _form.value = MistakeForm(
                    subject = record.subject.ifBlank { studentSubject },
                    stemText = record.stemText,
                    studentAnswer = record.studentAnswer,
                    correctAnswer = record.correctAnswer,
                    analysis = record.analysis,
                    tags = TemplateJson.decodeStrings(record.knowledgeTagsJson).joinToString("、"),
                    errorType = record.errorType,
                    sourceType = record.sourceType,
                    difficulty = record.difficulty,
                    note = record.note,
                    reviewStatus = record.reviewStatus,
                    hasFigure = record.hasFigure
                )
                _images.value = mistakeImageDao.listByMistake(mistakeId).mapIndexed { index, image ->
                    MistakeImageDraft(
                        originalUri = image.originalUri,
                        croppedUri = image.croppedUri,
                        cropRectJson = image.cropRectJson,
                        figureUri = image.figureUri,
                        figureRectJson = image.figureRectJson,
                        figureConfirmed = image.figureConfirmed,
                        orderIndex = index
                    )
                }
                _ui.update {
                    it.copy(
                        loading = false,
                        isEdit = true,
                        studentName = studentName,
                        reviewCount = record.reviewCount,
                        correctStreak = record.correctStreak
                    )
                }
            } else {
                _form.value = MistakeForm(subject = studentSubject)
                _ui.update {
                    it.copy(loading = false, isEdit = false, studentName = studentName)
                }
            }
        }
    }

    fun updateForm(transform: (MistakeForm) -> MistakeForm) {
        _form.update(transform)
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remaining = 12 - _images.value.size
        if (remaining <= 0) {
            _events.tryEmit(MistakeEditEvent.Message("每题最多 12 张照片"))
            return
        }
        viewModelScope.launch {
            val dir = File(context.filesDir, "mistake_images")
            val added = mutableListOf<MistakeImageDraft>()
            uris.take(remaining).forEach { uri ->
                val file = runCatching { ImageProcessor.compressToFile(context, uri, dir) }.getOrNull()
                if (file != null) {
                    draftFiles.add(file.absolutePath)
                    added += MistakeImageDraft(originalUri = Uri.fromFile(file).toString())
                }
            }
            if (added.isEmpty()) {
                _events.tryEmit(MistakeEditEvent.Message("图片读取失败，请换一张试试"))
                return@launch
            }
            _images.update { list ->
                (list + added).mapIndexed { index, draft -> draft.copy(orderIndex = index) }
            }
            if (added.size < uris.size) {
                _events.tryEmit(MistakeEditEvent.Message("已添加 ${added.size} 张，最多 12 张"))
            }
        }
    }

    fun removeImage(index: Int) {
        val draft = _images.value.getOrNull(index) ?: return
        retireImageFiles(listOf(draft.originalUri, draft.croppedUri, draft.figureUri))
        _images.update { list ->
            list.filterIndexed { i, _ -> i != index }
                .mapIndexed { i, item -> item.copy(orderIndex = i) }
        }
    }

    fun applyCrop(index: Int, left: Float, top: Float, width: Float, height: Float) {
        val draft = _images.value.getOrNull(index) ?: return
        val oldCroppedUri = draft.croppedUri
        val oldFigureUri = draft.figureUri
        viewModelScope.launch {
            try {
                val target = ImageProcessor.cropToFile(
                    context = context,
                    source = Uri.parse(draft.originalUri),
                    left = left,
                    top = top,
                    width = width,
                    height = height,
                    targetDir = File(context.filesDir, "mistake_images")
                )
                val rectJson = listOf(left, top, width, height).joinToString(
                    prefix = "[",
                    postfix = "]"
                ) { String.format(Locale.US, "%.6f", it) }
                draftFiles.add(target.absolutePath)
                _images.update { list ->
                    list.mapIndexed { i, item ->
                        if (i == index) {
                            item.copy(
                                croppedUri = Uri.fromFile(target).toString(),
                                cropRectJson = rectJson,
                                figureUri = null,
                                figureRectJson = null,
                                figureConfirmed = false
                            )
                        } else {
                            item
                        }
                    }
                }
                if (oldCroppedUri != null) {
                    retireImageFiles(listOf(oldCroppedUri))
                }
                if (oldFigureUri != null) {
                    retireImageFiles(listOf(oldFigureUri))
                }
            } catch (e: Exception) {
                _events.tryEmit(
                    MistakeEditEvent.Message("框选失败：${e.message.orEmpty()}")
                )
            }
        }
    }

    private fun retireImageFiles(uris: List<String?>) {
        uris.filterNotNull().toSet().forEach { uri ->
            val path = Uri.parse(uri).path ?: return@forEach
            if (draftFiles.remove(path)) {
                runCatching { File(path).delete() }
            } else {
                retiredFiles.add(path)
            }
        }
    }

    fun applyFigureCrop(
        index: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val draft = _images.value.getOrNull(index) ?: return
        val source = draft.croppedUri ?: draft.originalUri
        val oldFigure = draft.figureUri
        viewModelScope.launch {
            val crop = cropAndTrack(
                sourceUri = source,
                box = listOf(left, top, width, height)
            )
            if (crop == null) {
                _events.tryEmit(MistakeEditEvent.Message("配图框选失败，请重试"))
                return@launch
            }
            _images.update { list ->
                list.mapIndexed { i, item ->
                    if (i == index) {
                        item.copy(
                            figureUri = crop.first,
                            figureRectJson = crop.second,
                            figureConfirmed = true
                        )
                    } else {
                        item
                    }
                }
            }
            if (oldFigure != null) retireImageFiles(listOf(oldFigure))
            _form.update { it.copy(hasFigure = true) }
            _events.tryEmit(MistakeEditEvent.Message("配图已更新"))
        }
    }

    fun recognizeQuestion() {
        if (_ui.value.recognizing) return
        val images = _images.value
        val index = images.indexOfFirst { !it.croppedUri.isNullOrBlank() }
        if (index < 0) {
            _events.tryEmit(MistakeEditEvent.Message("请先框选题目区域，再点 AI 识别"))
            return
        }
        val draft = images[index]
        val source = draft.croppedUri ?: draft.originalUri

        _ui.update { it.copy(recognizing = true, error = null) }
        viewModelScope.launch {
            val outcome = extractionRepository.recognizeQuestion(source)
            _ui.update { it.copy(recognizing = false) }
            val item = outcome.items.firstOrNull()
            if (item == null) {
                _events.tryEmit(
                    MistakeEditEvent.Message(outcome.error ?: "识别失败，请重试")
                )
                return@launch
            }
            val extra = outcome.items.size - 1

            _form.update { current ->
                current.copy(
                    stemText = item.stemText.ifBlank { current.stemText },
                    studentAnswer = item.studentAnswer.ifBlank { current.studentAnswer },
                    correctAnswer = item.correctAnswer.ifBlank { current.correctAnswer },
                    tags = item.knowledgeTags.joinToString("、").ifBlank { current.tags },
                    errorType = item.errorType.ifBlank { current.errorType },
                    difficulty = item.difficulty.coerceIn(1, 5),
                    hasFigure = item.hasFigure
                )
            }

            if (item.hasFigure) {
                val box = item.figureBox
                val crop = if (box.size == 4) {
                    cropAndTrack(sourceUri = source, box = box)
                } else {
                    null
                }
                if (crop != null) {
                    val oldFigure = _images.value.getOrNull(index)?.figureUri
                    _images.update { list ->
                        list.mapIndexed { i, draftItem ->
                            if (i == index) {
                                draftItem.copy(
                                    figureUri = crop.first,
                                    figureRectJson = crop.second,
                                    figureConfirmed = true
                                )
                            } else {
                                draftItem
                            }
                        }
                    }
                    if (oldFigure != null) retireImageFiles(listOf(oldFigure))
                }
                _events.tryEmit(
                    MistakeEditEvent.FigureCropReady(
                        index = index,
                        initialRect = if (box.size == 4) box else emptyList()
                    )
                )
            }
            _events.tryEmit(
                MistakeEditEvent.Message(
                    if (extra > 0) {
                        "识别出 ${outcome.items.size} 题，手动录入只取第一题；其余题请分别框选"
                    } else {
                        "已识别，请检查题干和配图"
                    }
                )
            )
        }
    }

    private suspend fun cropAndTrack(
        sourceUri: String,
        box: List<Float>
    ): Pair<String, String>? {
        if (box.size != 4) return null
        return runCatching {
            val file = ImageProcessor.cropToFile(
                context = context,
                source = Uri.parse(sourceUri),
                left = box[0],
                top = box[1],
                width = box[2],
                height = box[3],
                targetDir = File(context.filesDir, "mistake_images")
            )
            draftFiles.add(file.absolutePath)
            val rectJson = box.joinToString(
                prefix = "[",
                postfix = "]"
            ) { String.format(Locale.US, "%.6f", it) }
            Uri.fromFile(file).toString() to rectJson
        }.getOrNull()
    }

    fun save(force: Boolean = false) {
        val f = _form.value
        if (_ui.value.saving) return
        if (f.stemText.isBlank() && _images.value.isEmpty()) {
            _events.tryEmit(MistakeEditEvent.Message("请至少填写题干或添加题目照片"))
            return
        }

        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                if (!force) {
                    val duplicate = findDuplicate(f.stemText)
                    if (duplicate != null) {
                        _ui.update {
                            it.copy(
                                saving = false,
                                duplicateOf = duplicate.stemText.take(80)
                            )
                        }
                        return@launch
                    }
                }

                val now = System.currentTimeMillis()
                val existing = loaded
                val tags = f.tags.trim()
                    .split(Regex("[、,，;；\\s]+"))
                    .filter { it.isNotBlank() }
                    .distinct()
                val record = MistakeRecord(
                    id = if (mistakeId > 0) mistakeId else 0,
                    studentId = studentId,
                    subject = f.subject.trim().ifBlank { studentSubject },
                    stemText = f.stemText.trim(),
                    studentAnswer = f.studentAnswer.trim(),
                    correctAnswer = f.correctAnswer.trim(),
                    answerStatus = when {
                        f.correctAnswer.isNotBlank() -> MistakeRecord.ANSWER_MANUAL
                        existing?.answerStatus == MistakeRecord.ANSWER_AI_GENERATED ->
                            MistakeRecord.ANSWER_AI_GENERATED
                        else -> MistakeRecord.ANSWER_NONE
                    },
                    analysis = f.analysis.trim(),
                    analysisStatus = when {
                        f.analysis.isNotBlank() -> MistakeRecord.ANSWER_MANUAL
                        existing?.analysisStatus == MistakeRecord.ANSWER_AI_GENERATED ->
                            MistakeRecord.ANSWER_AI_GENERATED
                        else -> MistakeRecord.ANSWER_NONE
                    },
                    knowledgeTagsJson = TemplateJson.encodeStrings(tags),
                    errorType = f.errorType,
                    sourceType = f.sourceType,
                    sourceFeedbackId = existing?.sourceFeedbackId,
                    sourceScoreId = existing?.sourceScoreId,
                    difficulty = f.difficulty.coerceIn(1, 5),
                    reviewStatus = existing?.reviewStatus ?: f.reviewStatus,
                    reviewCount = existing?.reviewCount ?: 0,
                    correctStreak = existing?.correctStreak ?: 0,
                    lastReviewedAt = existing?.lastReviewedAt,
                    note = f.note.trim(),
                    hasFigure = f.hasFigure,
                    ocrConfidence = existing?.ocrConfidence ?: 1.0,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )

                val drafts = _images.value
                val savedId = database.withTransaction {
                    val id = if (mistakeId > 0) {
                        mistakeRecordDao.update(record)
                        mistakeId
                    } else {
                        mistakeRecordDao.insert(record)
                    }
                    mistakeImageDao.deleteByMistake(id)
                    if (drafts.isNotEmpty()) {
                        mistakeImageDao.insertAll(
                            drafts.mapIndexed { index, draft ->
                                MistakeImage(
                                    mistakeId = id,
                                    studentId = studentId,
                                    originalUri = draft.originalUri,
                                    croppedUri = draft.croppedUri,
                                    cropRectJson = draft.cropRectJson,
                                    figureUri = draft.figureUri,
                                    figureRectJson = draft.figureRectJson,
                                    figureConfirmed = draft.figureConfirmed,
                                    orderIndex = index
                                )
                            }
                        )
                    }
                    id
                }

                loaded = record.copy(id = savedId)
                draftFiles.clear()
                if (retiredFiles.isNotEmpty()) {
                    retiredFiles.forEach { path ->
                        runCatching { File(path).delete() }
                    }
                    retiredFiles.clear()
                }
                _ui.update {
                    it.copy(saving = false, duplicateOf = null, isEdit = true)
                }
                _events.tryEmit(MistakeEditEvent.Saved(savedId))
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        saving = false,
                        error = "保存失败：${e.message.orEmpty()}"
                    )
                }
            }
        }
    }

    fun dismissDuplicate() {
        _ui.update { it.copy(duplicateOf = null) }
    }

    fun recordReview(correct: Boolean) {
        val id = mistakeId
        if (id <= 0) return
        viewModelScope.launch {
            try {
                val record = mistakeRecordDao.getById(id) ?: return@launch
                val now = System.currentTimeMillis()
                val streak = if (correct) record.correctStreak + 1 else 0
                val status = when {
                    streak >= 2 -> MistakeRecord.REVIEW_MASTERED
                    record.reviewStatus == MistakeRecord.REVIEW_MASTERED && correct ->
                        MistakeRecord.REVIEW_MASTERED
                    else -> MistakeRecord.REVIEW_REVIEWING
                }
                val updated = record.copy(
                    reviewStatus = status,
                    reviewCount = record.reviewCount + 1,
                    correctStreak = streak,
                    lastReviewedAt = now,
                    updatedAt = now
                )
                mistakeRecordDao.update(updated)
                reviewLogDao.insert(
                    ReviewLog(
                        mistakeId = id,
                        date = now,
                        result = if (correct) ReviewLog.RESULT_CORRECT else ReviewLog.RESULT_WRONG
                    )
                )
                loaded = updated
                _form.update { it.copy(reviewStatus = status) }
                _ui.update {
                    it.copy(reviewCount = updated.reviewCount, correctStreak = streak)
                }
                _events.tryEmit(
                    MistakeEditEvent.Message(
                        if (status == MistakeRecord.REVIEW_MASTERED && correct) {
                            "连续做对 2 次，已标记为已掌握"
                        } else {
                            "已记录这次复习"
                        }
                    )
                )
            } catch (e: Exception) {
                _events.tryEmit(MistakeEditEvent.Message("记录失败：${e.message.orEmpty()}"))
            }
        }
    }

    fun delete() {
        if (mistakeId <= 0 || _ui.value.saving) return
        viewModelScope.launch {
            try {
                val record = mistakeRecordDao.getById(mistakeId) ?: return@launch
                val files = _images.value.flatMap { draft ->
                    listOfNotNull(draft.originalUri, draft.croppedUri, draft.figureUri)
                }
                database.withTransaction { mistakeRecordDao.delete(record) }
                files.forEach { uri ->
                    Uri.parse(uri).path?.let { path ->
                        runCatching { File(path).delete() }
                    }
                }
                draftFiles.clear()
                retiredFiles.clear()
                _events.tryEmit(MistakeEditEvent.Deleted)
            } catch (e: Exception) {
                _ui.update { it.copy(error = "删除失败：${e.message.orEmpty()}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        draftFiles.forEach { path ->
            runCatching { File(path).delete() }
        }
        draftFiles.clear()
        retiredFiles.clear()
    }

    private suspend fun findDuplicate(stem: String): MistakeRecord? {
        val normalized = normalizeStem(stem)
        if (normalized.length < 4) return null
        return mistakeRecordDao.listByStudent(studentId).firstOrNull { other ->
            other.id != mistakeId &&
                other.stemText.isNotBlank() &&
                similarity(normalized, normalizeStem(other.stemText)) >= 0.9
        }
    }

    private fun normalizeStem(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    private fun similarity(a: String, b: String): Double {
        val x = a.take(240)
        val y = b.take(240)
        if (x == y) return 1.0
        val maxLen = maxOf(x.length, y.length)
        if (maxLen == 0) return 1.0
        val dp = IntArray(y.length + 1) { it }
        for (i in 1..x.length) {
            var previous = dp[0]
            dp[0] = i
            for (j in 1..y.length) {
                val temp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    previous + if (x[i - 1] == y[j - 1]) 0 else 1
                )
                previous = temp
            }
        }
        return 1.0 - dp[y.length].toDouble() / maxLen
    }
}
