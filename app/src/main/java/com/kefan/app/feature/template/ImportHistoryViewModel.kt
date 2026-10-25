package com.kefan.app.feature.template

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.data.repository.TemplateExtractResult
import com.kefan.app.data.repository.TemplateRepository
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUi(
    val analyzing: Boolean = false,
    val error: String? = null,
    val needsModel: Boolean = false
)

sealed interface ImportEvent {
    data class Done(val sectionCount: Int) : ImportEvent
}

@HiltViewModel
class ImportHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templateRepository: TemplateRepository,
    studentRepository: StudentRepository
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val studentName = MutableStateFlow("")

    val samples: StateFlow<List<String>> = combine(_text, studentName) { raw, name ->
        splitSamples(raw, name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(ImportUi())
    val ui: StateFlow<ImportUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            studentName.value = studentRepository.getById(studentId)?.name.orEmpty()
        }
    }

    fun onTextChange(value: String) {
        _text.value = value
        if (_ui.value.error != null) {
            _ui.update { it.copy(error = null, needsModel = false) }
        }
    }

    fun extract() {
        if (_ui.value.analyzing) return
        val sampleList = samples.value
        if (sampleList.isEmpty()) {
            _ui.update { it.copy(error = "先粘贴至少一条历史反馈", needsModel = false) }
            return
        }

        _ui.update { it.copy(analyzing = true, error = null, needsModel = false) }
        viewModelScope.launch {
            when (val result = templateRepository.extractFromTexts(studentId, sampleList)) {
                is TemplateExtractResult.Success -> {
                    _ui.update { it.copy(analyzing = false) }
                    _events.emit(ImportEvent.Done(result.sectionCount))
                }
                is TemplateExtractResult.NeedsModel -> {
                    _ui.update {
                        it.copy(
                            analyzing = false,
                            error = result.message,
                            needsModel = true
                        )
                    }
                }
                is TemplateExtractResult.Failure -> {
                    _ui.update {
                        it.copy(analyzing = false, error = result.message, needsModel = false)
                    }
                }
            }
        }
    }
}

internal fun splitSamples(raw: String, studentName: String = ""): List<String> {
    val normalized = raw.replace("\r\n", "\n").trim()
    if (normalized.isBlank()) return emptyList()

    val byDashes = normalized
        .split(Regex("(?m)^\\s*-{3,}\\s*$"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (byDashes.size > 1) return byDashes

    // 先按空行切成块，再用日期 / 称呼判断哪些块是新的反馈开头
    val blocks = normalized
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (blocks.size <= 1) {
        // 用户可能只用单换行分隔，按行首的称呼 / 姓名 / 短日期再试一次
        return splitByStartLines(normalized, studentName) ?: listOf(normalized)
    }

    val starts = blocks.mapIndexedNotNull { index, block ->
        if (looksLikeFeedbackStart(block, studentName)) index else null
    }
    if (starts.isEmpty()) {
        // 没有日期/称呼可判断时：较长的块视为用户用空行分隔的多条
        return if (blocks.all { it.length >= 30 }) blocks else listOf(normalized)
    }

    val result = mutableListOf<StringBuilder>()
    blocks.forEachIndexed { index, block ->
        if (index == 0 || index in starts) {
            result.add(StringBuilder(block))
        } else {
            val current = result.last()
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(block)
        }
    }
    return result.map { it.toString().trim() }.filter { it.isNotBlank() }
}

private val feedbackDateRegex = Regex(
    "(\\d{4}\\s*[-/.年]\\s*\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}\\s*日?|\\d{1,2}\\s*[-/月]\\s*\\d{1,2}\\s*日?)"
)

private fun looksLikeFeedbackStart(block: String, studentName: String): Boolean {
    val first = block.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (first.isBlank()) return false
    if (feedbackDateRegex.containsMatchIn(first)) return true
    if (first.contains("家长您好") || first.contains("家长好") || first.contains("您好")) return true
    if (studentName.length >= 2 &&
        first.take(16).contains(studentName) &&
        !first.startsWith("【")
    ) {
        return true
    }
    return false
}

private fun splitByStartLines(text: String, studentName: String): List<String>? {
    val lines = text.split("\n")
    val starts = lines.indices.filter { index ->
        val line = lines[index].trim()
        line.isNotEmpty() && looksLikeLineStart(line, studentName)
    }
    if (starts.count { it > 0 } < 1) return null

    val result = mutableListOf<StringBuilder>()
    lines.forEachIndexed { index, line ->
        if (index == 0 || index in starts) {
            result.add(StringBuilder(line))
        } else {
            val current = result.lastOrNull() ?: return@forEachIndexed
            current.append("\n").append(line)
        }
    }
    return result.map { it.toString().trim() }
        .filter { it.isNotBlank() }
        .takeIf { it.size > 1 }
}

private fun looksLikeLineStart(line: String, studentName: String): Boolean {
    if (line.contains("家长您好") || line.contains("家长好") || line.contains("您好")) return true
    if (studentName.length >= 2 &&
        line.take(16).contains(studentName) &&
        !line.startsWith("【")
    ) {
        return true
    }
    val date = feedbackDateRegex.find(line)
    return date != null && date.range.first == 0 && line.length <= 24
}
