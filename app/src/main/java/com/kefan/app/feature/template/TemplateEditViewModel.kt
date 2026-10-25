package com.kefan.app.feature.template

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.repository.TemplateExtractResult
import com.kefan.app.data.repository.TemplateRepository
import com.kefan.app.domain.model.TemplateDefaults
import com.kefan.app.domain.model.TemplateSection
import com.kefan.app.domain.model.TemplateSpec
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

data class TemplateSectionForm(
    val title: String = "",
    val source: String = TemplateSection.SOURCE_AI,
    val inline: Boolean = false,
    val items: String = "1",
    val hint: String = "",
    val defaultValue: String = ""
)

data class TemplateEditForm(
    val opening: String = "",
    val blankLineAfterOpening: Boolean = true,
    val sections: List<TemplateSectionForm> = emptyList(),
    val tone: String = "",
    val address: String = "",
    val lengthMin: String = "280",
    val lengthMax: String = "340",
    val bannedWordsText: String = "",
    val useEmoji: Boolean = false,
    val styleSummary: String = "",
    val sourceSampleIds: List<Long> = emptyList()
)

data class TemplateEditUi(
    val loading: Boolean = true,
    val saving: Boolean = false
)

sealed interface TemplateEditEvent {
    data class Saved(val version: Int) : TemplateEditEvent
    data class Message(val text: String) : TemplateEditEvent
}

@HiltViewModel
class TemplateEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L
    private val isGlobal: Boolean = savedStateHandle[KfRoutes.ARG_GLOBAL] ?: false

    private val _form = MutableStateFlow(TemplateEditForm())
    val form: StateFlow<TemplateEditForm> = _form.asStateFlow()

    private val _ui = MutableStateFlow(TemplateEditUi())
    val ui: StateFlow<TemplateEditUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<TemplateEditEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val spec = if (isGlobal) {
                templateRepository.loadGlobalEditingSpec()
            } else {
                templateRepository.loadEditingSpec(studentId)
            }
            _form.value = spec.toForm()
            _ui.value = TemplateEditUi(loading = false)
        }
    }

    fun updateForm(transform: (TemplateEditForm) -> TemplateEditForm) {
        _form.update(transform)
    }

    fun updateSection(index: Int, transform: (TemplateSectionForm) -> TemplateSectionForm) {
        _form.update { form ->
            if (index !in form.sections.indices) return@update form
            form.copy(
                sections = form.sections.mapIndexed { i, section ->
                    if (i == index) transform(section) else section
                }
            )
        }
    }

    fun addSection() {
        _form.update {
            it.copy(
                sections = it.sections + TemplateSectionForm(
                    title = "【新段落】",
                    source = TemplateSection.SOURCE_AI,
                    inline = false,
                    items = "1",
                    hint = ""
                )
            )
        }
    }

    fun removeSection(index: Int) {
        _form.update { form ->
            if (form.sections.size <= 1 || index !in form.sections.indices) return@update form
            form.copy(sections = form.sections.filterIndexed { i, _ -> i != index })
        }
    }

    fun moveSection(index: Int, delta: Int) {
        _form.update { form ->
            val target = index + delta
            if (index !in form.sections.indices || target !in form.sections.indices) {
                return@update form
            }
            val mutable = form.sections.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(target, item)
            form.copy(sections = mutable)
        }
    }

    fun save() {
        if (_ui.value.saving) return
        val form = _form.value
        if (form.sections.isEmpty()) {
            _events.tryEmit(TemplateEditEvent.Message("模板至少要有一段"))
            return
        }
        if (form.sections.any { it.title.isBlank() }) {
            _events.tryEmit(TemplateEditEvent.Message("段落标题不能为空"))
            return
        }

        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = if (isGlobal) {
                templateRepository.saveGlobalTemplate(form.toSpec())
            } else {
                templateRepository.saveEditedTemplate(studentId, form.toSpec())
            }
            when (result) {
                is TemplateExtractResult.Success -> {
                    _ui.update { it.copy(saving = false) }
                    _events.emit(TemplateEditEvent.Saved(result.version))
                }
                is TemplateExtractResult.Failure -> {
                    _ui.update { it.copy(saving = false) }
                    _events.emit(TemplateEditEvent.Message(result.message))
                }
                is TemplateExtractResult.NeedsModel -> {
                    _ui.update { it.copy(saving = false) }
                    _events.emit(TemplateEditEvent.Message(result.message))
                }
            }
        }
    }
}

private fun TemplateSpec.toForm(): TemplateEditForm = TemplateEditForm(
    opening = opening,
    blankLineAfterOpening = blankLineAfterOpening,
    sections = sections.map { section ->
        TemplateSectionForm(
            title = section.title,
            source = section.source,
            inline = section.inline,
            items = section.items.toString(),
            hint = section.hint,
            defaultValue = section.defaultValue
        )
    },
    tone = tone,
    address = address,
    lengthMin = lengthMin.toString(),
    lengthMax = lengthMax.toString(),
    bannedWordsText = bannedWords.joinToString("、"),
    useEmoji = useEmoji,
    styleSummary = styleSummary,
    sourceSampleIds = sourceSampleIds
)

private fun TemplateEditForm.toSpec(): TemplateSpec {
    val fallback = TemplateDefaults.fallback()
    val rawMin = lengthMin.toIntOrNull()?.coerceIn(50, 5_000) ?: fallback.lengthMin
    val rawMax = lengthMax.toIntOrNull()?.coerceIn(50, 5_000) ?: fallback.lengthMax

    val words = bannedWordsText
        .split(Regex("[、,，\\n]"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    return TemplateSpec(
        opening = opening.trim().ifBlank { fallback.opening },
        blankLineAfterOpening = blankLineAfterOpening,
        sections = sections.map { section ->
            TemplateSection(
                title = section.title.trim(),
                source = if (section.source == TemplateSection.SOURCE_APP) {
                    TemplateSection.SOURCE_APP
                } else {
                    TemplateSection.SOURCE_AI
                },
                inline = section.inline,
                block = !section.inline,
                items = section.items.toIntOrNull()?.coerceIn(1, 20) ?: 1,
                hint = section.hint.trim(),
                defaultValue = section.defaultValue.trim()
            )
        },
        tone = tone.trim().ifBlank { fallback.tone },
        address = address.trim().ifBlank { fallback.address },
        lengthMin = minOf(rawMin, rawMax),
        lengthMax = maxOf(rawMin, rawMax),
        bannedWords = words.ifEmpty { TemplateDefaults.bannedWords },
        useEmoji = useEmoji,
        styleSummary = styleSummary,
        sourceSampleIds = sourceSampleIds
    )
}
