package com.kefan.app.data.quality

enum class ReviewIssueType { FACT, TONE }

/**
 * 审阅项：FACT = 素材中找不到依据的断言；TONE = 敏感 / 偏重表述。
 * start/end 是正文中的字符下标，用于编辑器高亮与替换。
 */
data class ReviewIssue(
    val type: ReviewIssueType,
    val start: Int,
    val end: Int,
    val text: String,
    val reason: String,
    val suggestions: List<String> = emptyList()
) {
    /** 处理状态以“类型 + 原文”为键，正文轻微改动后仍能保留已确认状态 */
    val key: String get() = "${type.name}|$text"
}

/**
 * 本地、零成本的输出质量检查：
 * ① 正则抽取“数字 + 单位”的断言，与本次素材比对；
 * ② 内置敏感词库比对，给出改写建议。
 */
object FeedbackReview {

    private data class Line(val start: Int, val text: String)

    private val numberUnitRegex = Regex(
        """(\d+(?:\.\d+)?)\s*(分|题|道|次|课时|天|%|％|名|页|遍|个|小时|分钟|秒)"""
    )

    private val skipLinePrefixes = listOf("【上课时间】", "【课后任务】")
    private val sectionTitleRegex = Regex("""^【[^】]+】$""")
    private val headTitleRegex = Regex("""^【[^】]+】\s*[：:]?$""")

    private val benignChaPhrases = listOf("差不多", "差别", "差异", "差距", "时差", "不好不差")

    fun analyze(
        text: String,
        sources: List<String>,
        sensitiveWords: List<String> = SensitiveDictionary.defaultWords
    ): List<ReviewIssue> {
        if (text.isBlank()) return emptyList()
        val tone = findToneIssues(text, sensitiveWords)
        val toneRanges = tone.map { it.start to it.end }
        val fact = findFactIssues(text, sources).filterNot { issue ->
            toneRanges.any { (start, end) -> issue.start < end && issue.end > start }
        }
        return (tone + fact).sortedBy { it.start }
    }

    fun unresolved(issues: List<ReviewIssue>, dismissedKeys: Set<String>): List<ReviewIssue> =
        issues.filter { it.key !in dismissedKeys }

    /** 删掉断言 / 表述所在的一整句（含句末标点，保留前后文） */
    fun deleteSentence(text: String, issue: ReviewIssue): String {
        if (text.isEmpty()) return text
        val delimiters = charArrayOf('。', '！', '？', '；', '\n', '!', '?', ';')
        var start = issue.start.coerceIn(0, text.length)
        while (start > 0 && text[start - 1] !in delimiters) start--
        var end = issue.end.coerceIn(start, text.length)
        while (end < text.length && text[end] !in delimiters) end++
        if (end < text.length && text[end] != '\n') end++

        val before = text.substring(0, start).trimEnd(' ', '\t')
        val after = text.substring(end).trimStart(' ', '\t')
        return (before + after)
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun replaceIssue(text: String, issue: ReviewIssue, replacement: String): String {
        val start = issue.start.coerceIn(0, text.length)
        val end = issue.end.coerceIn(start, text.length)
        return text.replaceRange(start, end, replacement)
    }

    /* ------------------------------------------------------------------ */

    private fun findFactIssues(text: String, sources: List<String>): List<ReviewIssue> {
        val normalizedSources = normalize(sources.joinToString(" "))
        val issues = mutableListOf<ReviewIssue>()

        linesOf(text).forEach { line ->
            val trimmed = line.text.trim()
            if (trimmed.isEmpty()) return@forEach
            if (skipLinePrefixes.any { trimmed.startsWith(it) }) return@forEach
            if (sectionTitleRegex.matches(trimmed)) return@forEach
            if (headTitleRegex.matches(trimmed) && !numberUnitRegex.containsMatchIn(trimmed)) {
                return@forEach
            }

            numberUnitRegex.findAll(line.text).forEach { match ->
                val number = match.groupValues[1]
                val unit = normalizeUnit(match.groupValues[2])
                val token = normalize(number + unit)
                if (!normalizedSources.contains(token)) {
                    issues += ReviewIssue(
                        type = ReviewIssueType.FACT,
                        start = line.start + match.range.first,
                        end = line.start + match.range.last + 1,
                        text = match.value.trim(),
                        reason = "素材中未找到依据：$number$unit"
                    )
                }
            }
        }
        return issues
    }

    private fun findToneIssues(text: String, words: List<String>): List<ReviewIssue> {
        val issues = mutableListOf<ReviewIssue>()
        words.forEach { word ->
            if (word.isBlank()) return@forEach
            val suggestions = SensitiveDictionary.words[word].orEmpty()
            var from = 0
            while (from <= text.length - word.length) {
                val index = text.indexOf(word, from)
                if (index < 0) break
                val after = index + word.length
                if (isBenign(text, word, index, after)) {
                    from = index + 1
                    continue
                }
                issues += ReviewIssue(
                    type = ReviewIssueType.TONE,
                    start = index,
                    end = after,
                    text = word,
                    reason = "表述偏重，容易被家长理解成否定孩子",
                    suggestions = suggestions
                )
                from = index + word.length
            }
        }

        // 重叠时保留更长的词（如「很差」优先于「差」）
        return issues
            .sortedWith(compareByDescending<ReviewIssue> { it.end - it.start }.thenBy { it.start })
            .fold(mutableListOf<ReviewIssue>()) { kept, issue ->
                val overlaps = kept.any { it.start < issue.end && it.end > issue.start }
                if (!overlaps) kept.add(issue)
                kept
            }
            .sortedBy { it.start }
    }

    private fun isBenign(text: String, word: String, start: Int, end: Int): Boolean {
        if (word != "差") return false
        val from = (start - 1).coerceAtLeast(0)
        val to = (end + 1).coerceAtMost(text.length)
        val window = text.substring(from, to)
        return benignChaPhrases.any { window.contains(it) }
    }

    private fun linesOf(text: String): List<Line> {
        val lines = mutableListOf<Line>()
        var start = 0
        text.forEachIndexed { index, ch ->
            if (ch == '\n') {
                lines += Line(start, text.substring(start, index))
                start = index + 1
            }
        }
        lines += Line(start, text.substring(start))
        return lines
    }

    private fun normalize(value: String): String =
        value.replace(Regex("\\s+"), "").replace('％', '%')

    private fun normalizeUnit(unit: String): String =
        if (unit == "％") "%" else unit
}
