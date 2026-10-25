package com.kefan.app.feature.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.entity.ModelConfig
import com.kefan.app.data.remote.ApiResult
import com.kefan.app.data.repository.ModelConfigRepository
import com.kefan.app.domain.model.AiProviderPresets
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

data class ModelForm(
    val name: String = "",
    val provider: String = "DeepSeek",
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val supportsVision: Boolean = false,
    val contextWindow: String = "64000",
    val temperature: String = "0.7",
    val maxTokens: String = "2048",
    val timeoutSeconds: String = "120",
    val customHeadersJson: String = "{}"
)

data class ModelEditUi(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val testMessage: String? = null,
    val testOk: Boolean? = null,
    val fetchingModels: Boolean = false,
    val models: List<String> = emptyList(),
    val showModels: Boolean = false,
    val maskedKey: String = "",
    val isEdit: Boolean = false
)

sealed interface ModelEditEvent {
    data object Saved : ModelEditEvent
    data object Deleted : ModelEditEvent
    data class Message(val text: String) : ModelEditEvent
}

@HiltViewModel
class ModelEditViewModel @Inject constructor(
    private val repository: ModelConfigRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val configId: Long = savedStateHandle[KfRoutes.ARG_CONFIG_ID] ?: -1L

    private val _form = MutableStateFlow(ModelForm())
    val form: StateFlow<ModelForm> = _form.asStateFlow()

    private val _ui = MutableStateFlow(ModelEditUi())
    val ui: StateFlow<ModelEditUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<ModelEditEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var loadedConfig: ModelConfig? = null
    private var apiKeyAlias: String = ""

    init {
        if (configId > 0) {
            loadConfig(configId)
        } else {
            val preset = AiProviderPresets.byName("DeepSeek")
            _form.value = ModelForm(
                name = preset.name,
                provider = preset.name,
                baseUrl = preset.baseUrl,
                modelName = preset.defaultModel,
                contextWindow = preset.contextWindow.toString(),
                supportsVision = preset.supportsVision
            )
            _ui.value = _ui.value.copy(loading = false, isEdit = false)
        }
    }

    private fun loadConfig(id: Long) {
        viewModelScope.launch {
            val config = repository.getById(id)
            if (config == null) {
                _ui.update { it.copy(loading = false) }
                _events.emit(ModelEditEvent.Message("配置不存在"))
                return@launch
            }
            loadedConfig = config
            apiKeyAlias = config.apiKeyAlias
            _form.value = ModelForm(
                name = config.name,
                provider = config.provider,
                baseUrl = config.baseUrl,
                modelName = config.modelName,
                apiKey = "",
                supportsVision = config.supportsVision,
                contextWindow = config.contextWindow.toString(),
                temperature = config.temperature.toString(),
                maxTokens = config.maxTokens.toString(),
                timeoutSeconds = config.timeoutSeconds.toString(),
                customHeadersJson = config.customHeadersJson
            )
            _ui.update {
                it.copy(
                    loading = false,
                    isEdit = true,
                    maskedKey = repository.maskedKey(config)
                )
            }
        }
    }

    fun updateForm(transform: (ModelForm) -> ModelForm) {
        _form.update(transform)
    }

    fun applyPreset(name: String) {
        val preset = AiProviderPresets.byName(name)
        _form.update { current ->
            val presetNames = AiProviderPresets.all.map { it.name }
            current.copy(
                name = if (current.name.isBlank() || current.name in presetNames) {
                    preset.name
                } else {
                    current.name
                },
                provider = preset.name,
                baseUrl = preset.baseUrl,
                modelName = preset.defaultModel,
                contextWindow = preset.contextWindow.toString(),
                supportsVision = preset.supportsVision
            )
        }
    }

    private fun currentApiKey(): String {
        val draft = _form.value.apiKey.trim()
        if (draft.isNotEmpty()) return draft
        return loadedConfig?.let { repository.apiKeyOf(it) } ?: ""
    }

    private fun validateBase(): String? {
        val f = _form.value
        if (f.baseUrl.isBlank()) return "请先填写 Base URL"
        if (f.modelName.isBlank()) return "请先选择或填写模型名"
        return null
    }

    fun testConnection() {
        val error = validateBase()
        if (error != null) {
            _events.tryEmit(ModelEditEvent.Message(error))
            return
        }
        if (_ui.value.testing) return

        val f = _form.value
        _ui.update { it.copy(testing = true, testMessage = null, testOk = null) }
        viewModelScope.launch {
            val result = repository.testDraft(
                baseUrl = f.baseUrl,
                apiKey = currentApiKey(),
                modelName = f.modelName,
                customHeadersJson = f.customHeadersJson.ifBlank { "{}" },
                timeoutSeconds = f.timeoutSeconds.toIntOrNull() ?: 120
            )
            when (result) {
                is ApiResult.Success -> _ui.update {
                    it.copy(
                        testing = false,
                        testOk = true,
                        testMessage = "连接成功 · ${result.data.latencyMs} ms · ${result.data.reply.take(40)}"
                    )
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(testing = false, testOk = false, testMessage = result.message)
                }
            }
        }
    }

    fun fetchModels() {
        val f = _form.value
        if (f.baseUrl.isBlank()) {
            _events.tryEmit(ModelEditEvent.Message("请先填写 Base URL"))
            return
        }
        if (_ui.value.fetchingModels) return

        _ui.update { it.copy(fetchingModels = true) }
        viewModelScope.launch {
            when (val result = repository.fetchModels(
                baseUrl = f.baseUrl,
                apiKey = currentApiKey(),
                customHeadersJson = f.customHeadersJson.ifBlank { "{}" },
                timeoutSeconds = f.timeoutSeconds.toIntOrNull() ?: 60
            )) {
                is ApiResult.Success -> _ui.update {
                    it.copy(
                        fetchingModels = false,
                        models = result.data,
                        showModels = true
                    )
                }
                is ApiResult.Failure -> {
                    _ui.update { it.copy(fetchingModels = false) }
                    _events.emit(ModelEditEvent.Message(result.message))
                }
            }
        }
    }

    fun dismissModels() {
        _ui.update { it.copy(showModels = false) }
    }

    fun chooseModel(model: String) {
        _form.update { it.copy(modelName = model) }
        dismissModels()
    }

    fun save() {
        val f = _form.value
        if (f.baseUrl.isBlank()) {
            _events.tryEmit(ModelEditEvent.Message("请先填写 Base URL"))
            return
        }
        if (f.modelName.isBlank()) {
            _events.tryEmit(ModelEditEvent.Message("请先选择或填写模型名"))
            return
        }
        if (_ui.value.saving) return

        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val config = ModelConfig(
                id = if (configId > 0) configId else 0,
                name = f.name.ifBlank { f.modelName },
                provider = f.provider,
                baseUrl = f.baseUrl.trim(),
                apiKeyAlias = apiKeyAlias,
                modelName = f.modelName.trim(),
                supportsVision = f.supportsVision,
                contextWindow = f.contextWindow.toIntOrNull() ?: 64_000,
                temperature = f.temperature.toDoubleOrNull() ?: 0.7,
                maxTokens = f.maxTokens.toIntOrNull() ?: 2048,
                timeoutSeconds = f.timeoutSeconds.toIntOrNull() ?: 120,
                customHeadersJson = f.customHeadersJson.ifBlank { "{}" }
            )
            repository.save(config, f.apiKey)
            _ui.update { it.copy(saving = false) }
            _events.emit(ModelEditEvent.Saved)
        }
    }

    fun delete() {
        val config = loadedConfig ?: return
        viewModelScope.launch {
            repository.delete(config)
            _events.emit(ModelEditEvent.Deleted)
        }
    }
}
