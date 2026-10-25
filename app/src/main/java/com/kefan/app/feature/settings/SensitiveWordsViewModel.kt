package com.kefan.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.repository.SensitiveWordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SensitiveWordsUi(
    val loading: Boolean = true,
    val words: List<String> = emptyList(),
    val input: String = "",
    val message: String? = null
)

@HiltViewModel
class SensitiveWordsViewModel @Inject constructor(
    private val repository: SensitiveWordsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(SensitiveWordsUi())
    val ui: StateFlow<SensitiveWordsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.words.collect { words ->
                _ui.update { it.copy(loading = false, words = words) }
            }
        }
    }

    fun updateInput(value: String) {
        _ui.update { it.copy(input = value) }
    }

    fun addWord() {
        val word = _ui.value.input.trim()
        if (word.isBlank()) return
        if (_ui.value.words.contains(word)) {
            _ui.update { it.copy(message = "词库里已经有「$word」了") }
            return
        }
        viewModelScope.launch {
            repository.addWord(word)
            _ui.update { it.copy(input = "", message = "已添加「$word」") }
        }
    }

    fun removeWord(word: String) {
        viewModelScope.launch {
            repository.removeWord(word)
            _ui.update { it.copy(message = "已删除「$word」") }
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            repository.resetToDefault()
            _ui.update { it.copy(message = "已恢复内置词库") }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }
}
