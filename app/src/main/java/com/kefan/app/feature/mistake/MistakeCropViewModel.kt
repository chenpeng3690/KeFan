package com.kefan.app.feature.mistake

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.core.util.ImageProcessor
import com.kefan.app.data.local.dao.FeedbackImageDao
import com.kefan.app.data.repository.FeedbackRepository
import com.kefan.app.data.repository.MistakeExtractionRepository
import com.kefan.app.data.repository.MistakeExtractionSession
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.domain.model.ExtractedMistake
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class CropDraft(
    val id: Long,
    val pageIndex: Int,
    val croppedUri: String,
    val rectJson: String,
    val status: String = STATUS_READY,
    val error: String? = null,
    val results: List<ExtractedMistake> = emptyList()
) {
    companion object {
        const val STATUS_READY = "READY"
        const val STATUS_RECOGNIZING = "RECOGNIZING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}

data class MistakeCropUi(
    val loading: Boolean = true,
    val feedbackTitle: String = "",
    val studentName: String = "",
    val images: List<String> = emptyList(),
    val currentPage: Int = 0,
    val drafts: List<CropDraft> = emptyList(),
    val recognizing: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val message: String? = null
)

sealed interface MistakeCropEvent {
    data object OpenConfirm : MistakeCropEvent
    data class Message(val text: String) : MistakeCropEvent
}

@HiltViewModel
class MistakeCropViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val feedbackRepository: FeedbackRepository,
    private val feedbackImageDao: FeedbackImageDao,
    private val studentRepository: StudentRepository,
    private val extractionRepository: MistakeExtractionRepository
) : ViewModel() {

    private val feedbackId: Long = savedStateHandle[KfRoutes.ARG_FEEDBACK_ID] ?: -1L
    private var studentId: Long = -1L
    private var nextDraftId = 1L
    private var handedOff = false
    private val draftFiles = mutableSetOf<String>()

    private val _ui = MutableStateFlow(MistakeCropUi())
    val ui = _ui.asStateFlow()

    private val _events = MutableSharedFlow<MistakeCropEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val feedback = feedbackRepository.getById(feedbackId)
            if (feedback == null) {
                _ui.update { it.copy(loading = false, error = "反馈不存在") }
                return@launch
            }
            studentId = feedback.studentId
            val student = studentRepository.getById(studentId)
            val images = feedbackImageDao.listByFeedback(feedbackId).map { it.uri }
            _ui.value = MistakeCropUi(
                loading = false,
                feedbackTitle = feedback.title,
                studentName = student?.name.orEmpty(),
                images = images
            )
        }
    }

    fun setCurrentPage(index: Int) {
        _ui.update { ui ->
            ui.copy(currentPage = index.coerceIn(0, ui.images.lastIndex.coerceAtLeast(0)))
        }
    }

    fun addCrop(pageIndex: Int, left: Float, top: Float, width: Float, height: Float) {
        val image = _ui.value.images.getOrNull(pageIndex) ?: return
        viewModelScope.launch {
            try {
                val file = ImageProcessor.cropToFile(
                    context = context,
                    source = Uri.parse(image),
                    left = left,
                    top = top,
                    width = width,
                    height = height,
                    targetDir = File(context.filesDir, "mistake_images")
                )
                draftFiles.add(file.absolutePath)
                val rectJson = listOf(left, top, width, height).joinToString(
                    prefix = "[",
                    postfix = "]"
                ) { String.format(Locale.US, "%.6f", it) }
                val draft = CropDraft(
                    id = nextDraftId++,
                    pageIndex = pageIndex,
                    croppedUri = Uri.fromFile(file).toString(),
                    rectJson = rectJson
                )
                if (handedOff) {
                    handedOff = false
                    extractionRepository.clear()
                }
                _ui.update { it.copy(drafts = it.drafts + draft, message = null) }
                _events.tryEmit(MistakeCropEvent.Message("已框选 1 处，可以继续框选或切到下一张"))
            } catch (e: Exception) {
                _events.tryEmit(MistakeCropEvent.Message("框选失败：${e.message.orEmpty()}"))
            }
        }
    }

    fun removeCrop(id: Long) {
        val draft = _ui.value.drafts.firstOrNull { it.id == id } ?: return
        Uri.parse(draft.croppedUri).path?.let { path ->
            runCatching { File(path).delete() }
            draftFiles.remove(path)
        }
        _ui.update { ui -> ui.copy(drafts = ui.drafts.filterNot { it.id == id }) }
        if (handedOff) {
            handedOff = false
            extractionRepository.clear()
        }
    }

    fun recognizeAll() {
        if (_ui.value.recognizing) return
        val targets = _ui.value.drafts.filter { it.results.isEmpty() }
        if (targets.isEmpty()) {
            _events.tryEmit(MistakeCropEvent.Message("所有框选都已识别"))
            return
        }

        targets.forEach { target ->
            updateDraft(target.id) {
                it.copy(status = CropDraft.STATUS_RECOGNIZING, error = null)
            }
        }
        _ui.update {
            it.copy(recognizing = true, done = 0, total = targets.size, error = null, message = null)
        }

        viewModelScope.launch {
            var done = 0
            var failed = 0
            var needsModel = false
            try {
                coroutineScope {
                    targets.chunked(2).forEach { batch ->
                        val results = batch.map { draft ->
                            async {
                                draft to extractionRepository.recognizeQuestion(draft.croppedUri)
                            }
                        }.awaitAll()

                        results.forEach { (draft, outcome) ->
                            val items = outcome.items
                            if (items.isNotEmpty()) {
                                updateDraft(draft.id) {
                                    it.copy(
                                        status = CropDraft.STATUS_DONE,
                                        results = items,
                                        error = null
                                    )
                                }
                            } else {
                                updateDraft(draft.id) {
                                    it.copy(
                                        status = CropDraft.STATUS_FAILED,
                                        results = emptyList(),
                                        error = outcome.error ?: "识别失败"
                                    )
                                }
                                failed += 1
                                if (outcome.needsModel) needsModel = true
                            }
                            done += 1
                            _ui.update { it.copy(done = done) }
                        }
                    }
                }
            } finally {
                _ui.update { it.copy(recognizing = false) }
            }

            val successCount = _ui.value.drafts.sumOf { it.results.size }
            when {
                needsModel -> _ui.update {
                    it.copy(error = "没有可用的识图模型，先到设置里配置")
                }

                failed > 0 -> _ui.update {
                    it.copy(
                        error = "有 $failed 处识别失败，可以点「重试失败项」",
                        message = "已识别 $successCount 题"
                    )
                }

                else -> _ui.update {
                    it.copy(message = "识别完成，共 $successCount 题，去确认入库")
                }
            }
        }
    }

    fun proceedToConfirm() {
        val successful = _ui.value.drafts
            .filter { it.results.isNotEmpty() }
            .sortedBy { it.id }
        if (successful.isEmpty()) {
            _events.tryEmit(MistakeCropEvent.Message("还没有识别成功的题目"))
            return
        }
        val pairs = successful.flatMap { draft ->
            draft.results.map { item -> item to draft.croppedUri }
        }
        val items = pairs.mapIndexed { index, pair ->
            pair.first.copy(pageIndex = index + 1)
        }
        extractionRepository.setSession(
            MistakeExtractionSession(
                studentId = studentId,
                feedbackId = feedbackId,
                imageUris = pairs.map { it.second },
                items = items
            )
        )
        handedOff = true
        _events.tryEmit(MistakeCropEvent.OpenConfirm)
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }

    private fun updateDraft(id: Long, transform: (CropDraft) -> CropDraft) {
        _ui.update { ui ->
            ui.copy(
                drafts = ui.drafts.map { if (it.id == id) transform(it) else it }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (!handedOff) {
            draftFiles.forEach { path ->
                runCatching { File(path).delete() }
            }
        }
        draftFiles.clear()
    }
}
