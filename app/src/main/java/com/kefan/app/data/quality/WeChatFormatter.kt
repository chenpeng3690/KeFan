package com.kefan.app.data.quality

/**
 * 微信友好格式：只做“还原”，不按气泡思维重排。
 * 规则（§4.7）：
 * - 剥离 Markdown 标记（** __ ## > 反引号 * 列表符号）
 * - 【】标题原样保留
 * - 只保留称呼行与正文之间的那一个空行，其余各行紧密相连
 * - emoji 是否保留由设置决定，默认关闭
 */
object WeChatFormatter {

    private val emojiRegex = Regex(
        "[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\uFE0F\u200D]+"
    )

    fun format(raw: String, keepEmoji: Boolean): String {
        if (raw.isBlank()) return ""

        val cleanedLines = raw.replace("\r\n", "\n")
            .replace("```", "")
            .lines()
            .map { line ->
                line.trimEnd()
                    .replace(Regex("^\\s*#{1,6}\\s*"), "")
                    .replace(Regex("^\\s*>\\s?"), "")
                    .replace(Regex("^\\s*[-*+]\\s+"), "")
                    .trimStart()
            }
            .filterNot { it.matches(Regex("^[-*_]{3,}$")) }

        var text = cleanedLines.joinToString("\n")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace("*", "")

        if (!keepEmoji) {
            text = emojiRegex.replace(text, "")
        }

        val nonEmpty = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return ""
        if (nonEmpty.size == 1) return nonEmpty.first()
        return nonEmpty.first() + "\n\n" + nonEmpty.drop(1).joinToString("\n")
    }

    fun isLong(text: String): Boolean = text.length > 500
}
