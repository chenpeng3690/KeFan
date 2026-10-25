package com.kefan.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.entity.ModelConfig
import com.kefan.app.data.preferences.AppPreferences
import com.kefan.app.data.remote.ApiResult
import com.kefan.app.data.repository.ModelConfigRepository
import com.kefan.app.data.repository.SensitiveWordsRepository
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.data.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BalanceUi(
    val loading: Boolean = false,
    val amount: String? = null,
    val detail: String? = null,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val modelConfigRepository: ModelConfigRepository,
    studentRepository: StudentRepository,
    templateRepository: TemplateRepository,
    sensitiveWordsRepository: SensitiveWordsRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    val activeModel: StateFlow<ModelConfig?> = modelConfigRepository.observeActiveConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val modelCount: StateFlow<Int> = modelConfigRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val studentCount: StateFlow<Int> = studentRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sensitiveWordCount: StateFlow<Int> = sensitiveWordsRepository.words
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** null = 还没有全局模板 */
    val globalTemplateSections: StateFlow<Int?> = templateRepository.observeGlobalTemplate()
        .map { template ->
            template?.let { TemplateJson.decodeSections(it.sectionsJson).size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val keepEmoji: StateFlow<Boolean> = preferences.copyKeepEmoji
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val strictFactCheck: StateFlow<Boolean> = preferences.strictFactCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val trashRetentionDays: StateFlow<Int> = preferences.trashRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 7)

    private val _balance = MutableStateFlow(BalanceUi())
    val balance: StateFlow<BalanceUi> = _balance.asStateFlow()

    fun queryBalance() {
        if (_balance.value.loading) return
        _balance.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val config = modelConfigRepository.activeConfigOnce()
            if (config == null) {
                _balance.update {
                    it.copy(loading = false, error = "还没有配置模型，先到「模型」里添加")
                }
                return@launch
            }
            when (val result = modelConfigRepository.fetchBalance(config)) {
                is ApiResult.Success -> {
                    val info = result.data
                    val symbol = if (info.currency.equals("USD", ignoreCase = true)) "$" else "¥"
                    val detail = buildList {
                        if (info.granted.isNotBlank() && info.granted.toDoubleOrNull() != 0.0) {
                            add("赠送 $symbol${info.granted}")
                        }
                        if (info.toppedUp.isNotBlank()) {
                            add("充值 $symbol${info.toppedUp}")
                        }
                    }.joinToString(" · ")
                    _balance.value = BalanceUi(
                        loading = false,
                        amount = "$symbol${info.total}",
                        detail = detail.takeIf { it.isNotBlank() },
                        error = if (info.available) null else "余额不足，请前往平台充值"
                    )
                }

                is ApiResult.Failure -> _balance.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun setKeepEmoji(value: Boolean) {
        viewModelScope.launch { preferences.setKeepEmoji(value) }
    }

    fun setStrictFactCheck(value: Boolean) {
        viewModelScope.launch { preferences.setStrictFactCheck(value) }
    }
}
