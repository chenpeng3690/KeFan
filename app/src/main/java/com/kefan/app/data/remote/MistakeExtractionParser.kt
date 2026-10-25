package com.kefan.app.data.remote

import com.kefan.app.core.math.MathSyntax
import com.kefan.app.domain.model.ExtractedMistake
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 解析 §8.4 错题提取结果。
 * 兼容模型常见的几种“不听话”输出：
 * - 正常 {"items":[...]}
 * - 顶层直接是数组
 * - items 被写成 JSON 字符串
 * - 输出被截断、只完成了前面的对象
 */
object MistakeExtractionParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): List<ExtractedMistake> {
        val cleaned = extractJsonBlock(raw)
        val root = runCatching { json.parseToJsonElement(cleaned) }.getOrNull()

        val direct = root
            ?.let { runCatching { collectItems(it) }.getOrNull() }
            .orEmpty()
            .mapNotNull { parseItem(it) }
        if (direct.isNotEmpty()) return direct.take(20)

        // 结果被截断或格式不标准时，从原文里捞出所有完整的 { ... } 对象
        val salvaged = salvageObjects(cleaned)
            .mapNotNull { parseItem(it) }
        if (salvaged.isNotEmpty()) return salvaged.take(20)

        val hint = (root as? JsonObject)
            ?.get("items")
            .let { it as? JsonPrimitive }
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.trim()
        if (!hint.isNullOrBlank() && !hint.startsWith("[") && !hint.startsWith("{")) {
            throw IllegalArgumentException("AI 返回说明：${hint.take(160)}")
        }
        return emptyList()
    }

    private fun collectItems(element: JsonElement): List<JsonElement> = when (element) {
        is JsonArray -> element.toList()

        is JsonObject -> {
            val candidate = element["items"]
                ?: element["mistakes"]
                ?: element["data"]
                ?: element["questions"]
            when (candidate) {
                is JsonArray -> candidate.toList()
                is JsonObject -> {
                    if (candidate["stemText"] != null) {
                        listOf(candidate)
                    } else {
                        candidate.values.filterIsInstance<JsonObject>()
                    }
                }
                is JsonPrimitive -> {
                    val text = candidate.contentOrNull?.trim().orEmpty()
                    if (text.startsWith("[") || text.startsWith("{")) {
                        val inner = runCatching {
                            json.parseToJsonElement(extractJsonBlock(text))
                        }.getOrNull()
                        if (inner != null) collectItems(inner) else emptyList()
                    } else if (element["stemText"] != null) {
                        listOf(element)
                    } else {
                        emptyList()
                    }
                }

                else -> if (element["stemText"] != null) listOf(element) else emptyList()
            }
        }

        is JsonPrimitive -> {
            val text = element.contentOrNull?.trim().orEmpty()
            if (text.startsWith("[") || text.startsWith("{")) {
                val inner = runCatching {
                    json.parseToJsonElement(extractJsonBlock(text))
                }.getOrNull()
                if (inner != null) collectItems(inner) else emptyList()
            } else {
                emptyList()
            }
        }

        else -> emptyList()
    }

    /**
     * 扫描所有成对的 { ... }，截断的输出也能救回前面完整的题目。
     */
    private fun salvageObjects(text: String): List<JsonElement> {
        val results = mutableListOf<JsonElement>()
        val starts = ArrayDeque<Int>()
        var inString = false
        var escaped = false

        text.forEachIndexed { index, ch ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                return@forEachIndexed
            }
            when (ch) {
                '"' -> inString = true
                '{' -> starts.addLast(index)
                '}' -> {
                    val start = starts.removeLastOrNull() ?: return@forEachIndexed
                    val fragment = text.substring(start, index + 1)
                    runCatching { json.parseToJsonElement(fragment) }
                        .getOrNull()
                        ?.let { results.add(it) }
                }
            }
        }
        return results
    }

    private fun parseItem(element: JsonElement): ExtractedMistake? {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        val stem = MathSyntax.cleanStem(obj.string("stemText")?.trim().orEmpty())
        if (stem.isBlank()) return null

        val tags = (obj["knowledgeTags"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(3)
            .orEmpty()

        val box = (obj["figureBox"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
            ?.take(4)
            ?.map { it.coerceIn(0f, 1f) }
            .orEmpty()

        return ExtractedMistake(
            stemText = stem,
            studentAnswer = obj.string("studentAnswer").orEmpty().trim(),
            correctAnswer = obj.string("correctAnswer").orEmpty().trim(),
            knowledgeTags = tags,
            errorType = obj.string("errorType").orEmpty().trim(),
            difficulty = (obj.int("difficulty") ?: 3).coerceIn(1, 5),
            hasFigure = obj.bool("hasFigure") ?: false,
            figureNote = obj.string("figureNote").orEmpty().trim(),
            figureBox = if (box.size == 4) box else emptyList(),
            pageIndex = (obj.int("pageIndex") ?: 1).coerceAtLeast(1),
            confidence = (obj.double("confidence") ?: 0.5).coerceIn(0.0, 1.0),
            cropHint = obj.string("cropHint").orEmpty().trim()
        )
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

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull
}
