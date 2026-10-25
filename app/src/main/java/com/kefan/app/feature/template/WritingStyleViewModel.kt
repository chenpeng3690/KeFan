package com.kefan.app.feature.template

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.entity.FeedbackTemplate
import com.kefan.app.data.local.entity.ManualSample
import com.kefan.app.data.local.entity.Student
import com.kefan.app.data.repository.TemplateExtractResult
import com.kefan.app.data.repository.TemplateRepository
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.domain.model.TemplateDefaults
import com.kefan.app.domain.model.TemplateSpec
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

data class WritingStyleUi(
    val student: Student? = null,
    val studentTemplate: FeedbackTemplate? = null,
    val globalTemplate: FeedbackTemplate? = null,
    val samples: List<ManualSample> = emptyList(),
    val versions: List<FeedbackTemplate> = emptyList(),
    val reanalyzing: Boolean = false,
    val error: String? = null,
    val needsModel: Boolean = false,
    val loading: Boolean = true
) {
    val effectiveTemplate: FeedbackTemplate? get() = studentTemplate ?: globalTemplate
    val isFallback: Boolean get() = effectiveTemplate == null
    val isGlobal: Boolean get() = studentTemplate == null && globalTemplate != null
    val spec: TemplateSpec
        get() = effectiveTemplate?.let { TemplateJson.toSpec(it) }
            ?: TemplateDefaults.fallback()
}

sealed interface WritingStyleEvent {
    data class Message(val text: String) : WritingStyleEvent
}

@HiltViewModel
class WritingStyleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    studentRepository: StudentRepository,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L

    private val state = combine(
        studentRepository.observeById(studentId),
        templateRepository.observeActiveTemplate(studentId),
        templateRepository.observeGlobalTemplate(),
        templateRepository.observeSamples(studentId),
        templateRepository.observeVersions(studentId)
    ) { student, studentTemplate, globalTemplate, samples, versions ->
        WritingStyleUi(
            student = student,
            studentTemplate = studentTemplate,
            globalTemplate = globalTemplate,
            samples = samples,
            versions = versions,
            loading = false
        )
    }

    private val _local = MutableStateFlow(WritingStyleUi())
    val ui: StateFlow<WritingStyleUi> = combine(state, _local) { remote, local ->
        remote.copy(
            reanalyzing = local.reanalyzing,
            error = local.error,
            needsModel = local.needsModel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WritingStyleUi())

    private val _events = MutableSharedFlow<WritingStyleEvent>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()

    fun reanalyze() {
        if (_local.value.reanalyzing) return
        if (ui.value.samples.isEmpty()) {
            _local.update { it.copy(error = "还没有历史反馈，先导入几条") }
            return
        }

        _local.update { it.copy(reanalyzing = true, error = null, needsModel = false) }
        viewModelScope.launch {
            when (val result = templateRepository.reanalyze(studentId)) {
                is TemplateExtractResult.Success -> {
                    _local.update { it.copy(reanalyzing = false) }
                    _events.emit(
                        WritingStyleEvent.Message("重新分析完成 · v${result.version} · ${result.sectionCount} 段")
                    )
                }
                is TemplateExtractResult.NeedsModel -> {
                    _local.update {
                        it.copy(
                            reanalyzing = false,
                            error = result.message,
                            needsModel = true
                        )
                    }
                }
                is TemplateExtractResult.Failure -> {
                    _local.update {
                        it.copy(reanalyzing = false, error = result.message, needsModel = false)
                    }
                }
            }
        }
    }

    fun rollback(templateId: Long, version: Int) {
        viewModelScope.launch {
            if (templateRepository.activateVersion(studentId, templateId)) {
                _events.emit(WritingStyleEvent.Message("已回滚到 v$version"))
            } else {
                _events.emit(WritingStyleEvent.Message("回滚失败，版本不存在"))
            }
        }
    }

    fun clearError() {
        _local.update { it.copy(error = null, needsModel = false) }
    }
}
