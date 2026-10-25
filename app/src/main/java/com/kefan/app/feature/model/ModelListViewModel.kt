package com.kefan.app.feature.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.entity.ModelConfig
import com.kefan.app.data.remote.ApiResult
import com.kefan.app.data.repository.ModelConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelCardUi(
    val config: ModelConfig,
    val maskedKey: String
)

data class ModelTestState(
    val running: Boolean = false,
    val success: Boolean? = null,
    val message: String = ""
)

@HiltViewModel
class ModelListViewModel @Inject constructor(
    private val repository: ModelConfigRepository
) : ViewModel() {

    val cards: StateFlow<List<ModelCardUi>> = repository.observeConfigs()
        .map { list -> list.map { ModelCardUi(it, repository.maskedKey(it)) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeConfig: StateFlow<ModelConfig?> = repository.observeActiveConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tests = MutableStateFlow<Map<Long, ModelTestState>>(emptyMap())
    val tests: StateFlow<Map<Long, ModelTestState>> = _tests.asStateFlow()

    fun testConnection(config: ModelConfig) {
        if (_tests.value[config.id]?.running == true) return
        updateTest(config.id, ModelTestState(running = true))

        viewModelScope.launch {
            when (val result = repository.testConnection(config)) {
                is ApiResult.Success -> updateTest(
                    config.id,
                    ModelTestState(
                        success = true,
                        message = "连接成功 · ${result.data.latencyMs} ms · ${result.data.reply.take(40)}"
                    )
                )
                is ApiResult.Failure -> updateTest(
                    config.id,
                    ModelTestState(success = false, message = result.message)
                )
            }
        }
    }

    fun setActive(id: Long) {
        viewModelScope.launch { repository.setActive(id) }
    }

    private fun updateTest(id: Long, state: ModelTestState) {
        _tests.value = _tests.value + (id to state)
    }
}
