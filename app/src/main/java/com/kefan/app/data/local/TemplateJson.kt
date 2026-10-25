package com.kefan.app.data.local

import com.kefan.app.data.local.entity.FeedbackTemplate
import com.kefan.app.domain.model.TemplateDefaults
import com.kefan.app.domain.model.TemplateRules
import com.kefan.app.domain.model.TemplateSection
import com.kefan.app.domain.model.TemplateSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 模板 JSON 的读写与 AI 提取结果解析。结构存成数据，代码只做搬运与校验。
 */
object TemplateJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encodeSections(sections: List<TemplateSection>): String = buildJsonArray {
        sections.forEach { section ->
            addJsonObject {
                put("title", section.title)
                put("source", section.source)
                put("inline", section.inline)
                put("block", section.block)
                put("items", section.items)
                put("hint", section.hint)
                if (section.defaultValue.isNotBlank()) {
                    put("default", section.defaultValue)
                }
            }
        }
    }.toString()

    fun decodeSections(raw: String): List<TemplateSection> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { parseSection(it) }
        }.getOrDefault(emptyList())
    }

    fun encodeStrings(values: List<String>): String = buildJsonArray {
        values.forEach { add(it) }
    }.toString()

    fun decodeStrings(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull {
                it.jsonPrimitive.contentOrNull?.takeIf { value -> value.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeLongs(values: List<Long>): String = buildJsonArray {
        values.forEach { add(it) }
    }.toString()

    fun decodeLongs(raw: String): List<Long> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull {
                it.jsonPrimitive.longOrNull
            }
        }.getOrDefault(emptyList())
    }

    fun toSpec(template: FeedbackTemplate): TemplateSpec {
        val fallback = TemplateDefaults.fallback()
        val sections = TemplateRules.normalizeSections(
            decodeSections(template.sectionsJson).ifEmpty { fallback.sections }
        )
        val banned = decodeStrings(template.bannedWordsJson)
            .ifEmpty { TemplateDefaults.bannedWords }
        return TemplateSpec(
            opening = template.opening.ifBlank { fallback.opening },
            blankLineAfterOpening = template.blankLineAfterOpening,
            sections = sections,
            tone = template.tone.ifBlank { fallback.tone },
            address = template.address.ifBlank { fallback.address },
            lengthMin = template.lengthMin,
            lengthMax = template.lengthMax,
            bannedWords = banned,
            useEmoji = template.useEmoji,
            styleSummary = template.styleSummary,
            sourceSampleIds = decodeLongs(template.sourceSampleIdsJson)
        )
    }

    /**
     * 解析 §8.3 的提取结果。允许模型包 ```json 代码块或在 JSON 前后多说话。
     */
    fun parseExtraction(raw: String): TemplateSpec {
        val cleaned = extractJsonBlock(raw)
        val root = json.parseToJsonElement(cleaned).jsonObject
        val fallback = TemplateDefaults.fallback()

        val sectionsRaw = root["sections"]?.jsonArray
            ?: throw IllegalArgumentException("缺少 sections")
        val sections = TemplateRules.normalizeSections(
            sectionsRaw.mapNotNull { parseSection(it) }
        )
        if (sections.isEmpty()) {
            throw IllegalArgumentException("sections 为空")
        }

        val lengthValues = root["length"]?.jsonArray
        val rawMin = lengthValues?.getOrNull(0)?.jsonPrimitive?.intOrNull ?: fallback.lengthMin
        val rawMax = lengthValues?.getOrNull(1)?.jsonPrimitive?.intOrNull ?: fallback.lengthMax
        val min = minOf(rawMin, rawMax).coerceIn(50, 5_000)
        val max = maxOf(rawMin, rawMax).coerceIn(50, 5_000)

        val banned = root["bannedWords"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(10)
            .orEmpty()

        return TemplateSpec(
            opening = root.string("opening")?.trim().takeUnless { it.isNullOrBlank() }
                ?: fallback.opening,
            blankLineAfterOpening = root.bool("blankLineAfterOpening") ?: true,
            sections = sections,
            tone = root.string("tone")?.trim().takeUnless { it.isNullOrBlank() }
                ?: fallback.tone,
            address = root.string("address")?.trim().takeUnless { it.isNullOrBlank() }
                ?: fallback.address,
            lengthMin = min,
            lengthMax = max,
            bannedWords = banned.ifEmpty { TemplateDefaults.bannedWords },
            useEmoji = root.bool("useEmoji") ?: false,
            styleSummary = root.string("styleSummary").orEmpty().trim(),
            profileDraft = root.string("profileDraft").orEmpty().trim()
        )
    }

    private fun parseSection(element: JsonElement): TemplateSection? {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        val title = obj.string("title")?.trim().orEmpty()
        if (title.isBlank()) return null

        val source = if (obj.string("source")?.uppercase() == TemplateSection.SOURCE_APP) {
            TemplateSection.SOURCE_APP
        } else {
            TemplateSection.SOURCE_AI
        }
        val inline = obj.bool("inline") ?: (source == TemplateSection.SOURCE_APP)
        val block = obj.bool("block") ?: !inline
        val items = (obj.int("items") ?: 1).coerceIn(1, 20)

        return TemplateSection(
            title = title,
            source = source,
            inline = inline,
            block = block,
            items = items,
            hint = obj.string("hint").orEmpty(),
            defaultValue = obj.string("default").orEmpty()
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
}
