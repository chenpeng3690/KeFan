package com.kefan.app.domain.model

import com.kefan.app.core.util.formatChineseDate
import com.kefan.app.data.local.entity.Student

data class AssemblyResult(
    val content: String,
    val missingSections: List<String>,
    val summary: String
)

/**
 * 按模板把 AI 写的段落与 App 填的段落拼成最终微信正文。
 */
object FeedbackAssembly {

    fun assemble(
        spec: TemplateSpec,
        student: Student,
        classDate: Long,
        lessonCount: Int,
        taskText: String,
        aiText: String
    ): AssemblyResult {
        val sections = TemplateRules.normalizeSections(spec.sections)
        val aiSections = sections.filter { it.source == TemplateSection.SOURCE_AI }
        val appTitles = sections
            .filter { it.source == TemplateSection.SOURCE_APP }
            .map { it.title }
        val parsed = parseAiSections(aiSections, appTitles, aiText)

        val builder = StringBuilder()
        builder.append(replacePlaceholders(spec.opening, student))
        builder.append(if (spec.blankLineAfterOpening) "\n\n" else "\n")

        sections.forEach { section ->
            if (section.source == TemplateSection.SOURCE_APP) {
                val value = appSectionValue(section, classDate, lessonCount, taskText)
                if (section.inline) {
                    builder.append(section.title).append(value).append('\n')
                } else {
                    builder.append(section.title).append('\n')
                    if (value.isNotBlank()) builder.append(value).append('\n')
                }
            } else {
                val body = parsed.contents[section.title].orEmpty().trim()
                if (section.inline) {
                    builder.append(section.title).append(body).append('\n')
                } else {
                    builder.append(section.title).append('\n')
                    if (body.isNotBlank()) builder.append(body).append('\n')
                }
            }
        }

        return AssemblyResult(
            content = builder.toString().trimEnd(),
            missingSections = parsed.missing,
            summary = extractSummary(aiSections, parsed.contents, aiText)
        )
    }

    fun replacePlaceholders(text: String, student: Student): String {
        return text
            .replace("{{姓名}}", student.name)
            .replace("{{科目}}", student.subject.ifBlank { "课" })
            .replace("{{年级}}", student.grade)
    }

    fun appSectionValue(
        section: TemplateSection,
        classDate: Long,
        lessonCount: Int,
        taskText: String
    ): String {
        return when {
            section.title.contains("上课时间") -> {
                "${formatChineseDate(classDate)}(${lessonCount}课时)"
            }
            section.title.contains("课后任务") -> taskText
            else -> section.defaultValue
        }
    }

    fun replaceTaskInContent(
        content: String,
        spec: TemplateSpec,
        newTask: String
    ): String {
        val sections = TemplateRules.normalizeSections(spec.sections)
        val taskSection = sections.firstOrNull {
            it.source == TemplateSection.SOURCE_APP && it.title.contains("课后任务")
        } ?: TemplateSection(
            title = "【课后任务】",
            source = TemplateSection.SOURCE_APP,
            inline = true,
            block = false
        )
        return replaceSectionValue(content, taskSection, sections.map { it.title }, newTask)
    }

    fun replaceTimeInContent(
        content: String,
        spec: TemplateSpec,
        classDate: Long,
        lessonCount: Int
    ): String {
        val sections = TemplateRules.normalizeSections(spec.sections)
        val timeSection = sections.firstOrNull {
            it.source == TemplateSection.SOURCE_APP && it.title.contains("上课时间")
        } ?: return content
        val value = "${formatChineseDate(classDate)}(${lessonCount}课时)"
        return replaceSectionValue(content, timeSection, sections.map { it.title }, value)
    }

    /**
     * 按段落标题替换正文。兼容 AI 写的旧内容、括号形式不一致、多行正文。
     * 找不到段落时，课后任务会补在末尾，保证用户修改一定生效。
     */
    private fun replaceSectionValue(
        content: String,
        section: TemplateSection,
        allTitles: List<String>,
        newValue: String
    ): String {
        val lines = content.split("\n").toMutableList()
        val key = titleKey(section.title)
        val index = lines.indexOfFirst { titleKey(it).startsWith(key) }
        if (index < 0) {
            val append = if (section.inline) {
                section.title + newValue
            } else {
                section.title + "\n" + newValue
            }
            return if (content.isBlank()) append else content.trimEnd() + "\n" + append
        }

        val line = lines[index]
        val direct = line.indexOf(section.title)
        val valueStart = when {
            direct >= 0 -> {
                var end = direct + section.title.length
                if (line.getOrNull(end) == '】' || line.getOrNull(end) == ']') end += 1
                end
            }
            else -> {
                val close = line.indexOf('】').let { if (it >= 0) it else line.indexOf(']') }
                if (close >= 0) close + 1 else line.length
            }
        }
        val trailing = line.substring(valueStart)
        val inlineLayout = section.inline || trailing.isNotBlank()

        if (inlineLayout) {
            lines[index] = line.substring(0, valueStart) + newValue
            return lines.joinToString("\n")
        }

        val otherKeys = allTitles
            .filter { it != section.title }
            .map { titleKey(it) }
            .filter { it.isNotBlank() }
        var endExclusive = lines.size
        for (i in index + 1 until lines.size) {
            if (otherKeys.any { boundary -> titleKey(lines[i]).startsWith(boundary) }) {
                endExclusive = i
                break
            }
        }

        val rebuilt = mutableListOf<String>()
        rebuilt.addAll(lines.subList(0, index + 1))
        if (newValue.isNotBlank()) rebuilt.add(newValue)
        rebuilt.addAll(lines.subList(endExclusive, lines.size))
        return rebuilt.joinToString("\n")
    }

    private fun titleKey(text: String): String = text
        .replace("【", "")
        .replace("】", "")
        .replace("[", "")
        .replace("]", "")
        .replace("：", "")
        .replace(":", "")
        .trim()

    private data class ParsedAi(
        val contents: Map<String, String>,
        val missing: List<String>
    )

    private fun parseAiSections(
        aiSections: List<TemplateSection>,
        appTitles: List<String>,
        aiText: String
    ): ParsedAi {
        if (aiSections.isEmpty()) return ParsedAi(emptyMap(), emptyList())

        // 模型偶尔会自行写出 App 负责的段落（如【课后任务】），先整行剔除
        val appKeys = appTitles.map { titleKey(it) }.filter { it.isNotBlank() }
        val cleanText = aiText.split("\n")
            .filterNot { line ->
                val key = titleKey(line)
                appKeys.any { key.startsWith(it) }
            }
            .joinToString("\n")

        val positions = mutableListOf<Pair<TemplateSection, Int>>()
        var searchFrom = 0
        aiSections.forEach { section ->
            val index = cleanText.indexOf(section.title, searchFrom)
            if (index >= 0) {
                positions.add(section to index)
                searchFrom = index + section.title.length
            }
        }

        val contents = mutableMapOf<String, String>()
        positions.forEachIndexed { index, (section, start) ->
            val bodyStart = start + section.title.length
            var bodyEnd = if (index + 1 < positions.size) {
                positions[index + 1].second
            } else {
                cleanText.length
            }
            // 再按 App 段落标题切一次，防止模型把固定段落混进正文
            appTitles.forEach { appTitle ->
                val appIndex = cleanText.indexOf(appTitle, bodyStart)
                if (appIndex in bodyStart until bodyEnd) {
                    bodyEnd = appIndex
                }
            }
            contents[section.title] = cleanText.substring(bodyStart, bodyEnd).trim()
        }

        val missing = aiSections
            .filter { !contents.containsKey(it.title) }
            .map { it.title }

        if (contents.isEmpty() && cleanText.isNotBlank()) {
            contents[aiSections.first().title] = cleanText.trim()
        }

        return ParsedAi(contents, missing)
    }

    private fun extractSummary(
        aiSections: List<TemplateSection>,
        contents: Map<String, String>,
        aiText: String
    ): String {
        val preferred = aiSections.firstOrNull { it.title.contains("主要内容") }
            ?: aiSections.firstOrNull()
        val body = preferred?.let { contents[it.title] }.orEmpty()
            .ifBlank { aiText }
            .replace("\n", " ")
            .trim()
        return body.take(24).trim('，', '。', ' ', '：', ':', ',')
    }
}
