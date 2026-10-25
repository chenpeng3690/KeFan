package com.kefan.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class MistakeAnswer(
    val correctAnswer: String,
    val analysis: String
)

/**
 * 解析 §8.5 的答案补全 JSON。
 */
object MistakeAnswerParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): MistakeAnswer? {
        val text = extractJsonBlock(raw)
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null
        if (root.string("error") != null) return null
        val answer = root.string("correctAnswer").orEmpty().trim()
        val analysis = root.string("analysis").orEmpty().trim()
        val pitfall = root.string("pitfall").orEmpty().trim()
        if (answer.isBlank() && analysis.isBlank()) return null
        val merged = buildString {
            append(analysis)
            if (pitfall.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("易错点：").append(pitfall)
            }
        }
        return MistakeAnswer(correctAnswer = answer, analysis = merged)
    }

    private fun extractJsonBlock(raw: String): String {
        var text = raw.trim()
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(text)
        if (fence != null) {
            text = fence.groupValues[1].trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1)
        }
        return text
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
