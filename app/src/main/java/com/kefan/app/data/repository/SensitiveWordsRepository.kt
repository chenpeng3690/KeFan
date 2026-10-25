package com.kefan.app.data.repository

import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.preferences.AppPreferences
import com.kefan.app.data.quality.SensitiveDictionary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 敏感词库：未自定义时使用内置词库，自定义后只使用用户自己的词条。
 */
@Singleton
class SensitiveWordsRepository @Inject constructor(
    private val preferences: AppPreferences
) {

    val words: Flow<List<String>> = preferences.sensitiveWordsJson.map { raw ->
        val stored = raw?.let { json ->
            runCatching { TemplateJson.decodeStrings(json) }.getOrNull()
        }
        stored ?: SensitiveDictionary.defaultWords
    }

    suspend fun currentWords(): List<String> = words.first()

    suspend fun setWords(words: List<String>) {
        val cleaned = words.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        preferences.setSensitiveWordsJson(TemplateJson.encodeStrings(cleaned))
    }

    suspend fun addWord(word: String) {
        val value = word.trim()
        if (value.isBlank()) return
        val current = currentWords()
        if (current.contains(value)) return
        setWords(current + value)
    }

    suspend fun removeWord(word: String) {
        setWords(currentWords().filterNot { it == word })
    }

    suspend fun resetToDefault() {
        preferences.setSensitiveWordsJson(null)
    }
}
