package com.kefan.app.core.math

/**
 * 错题文本里的轻量数学语法。
 *
 * 约定：公式用 $...$ 包裹，支持 ^、_、\frac{}{}、\sqrt{} 和常用符号。
 * 解析不了的复杂公式保留原文，由界面按普通文本显示。
 */
enum class MathRunStyle { NORMAL, SUP, SUB }

data class MathRun(
    val text: String,
    val style: MathRunStyle = MathRunStyle.NORMAL
)

data class MathSegment(
    val text: String,
    val isFormula: Boolean
)

data class NormalizedMath(
    val runs: List<MathRun>,
    val supported: Boolean
)

object MathSyntax {

    private val symbols = mapOf(
        "\\le" to "≤",
        "\\leq" to "≤",
        "\\ge" to "≥",
        "\\geq" to "≥",
        "\\ne" to "≠",
        "\\neq" to "≠",
        "\\times" to "×",
        "\\div" to "÷",
        "\\pm" to "±",
        "\\cdot" to "·",
        "\\pi" to "π",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\theta" to "θ",
        "\\angle" to "∠",
        "\\triangle" to "△",
        "\\parallel" to "∥",
        "\\perp" to "⊥",
        "\\infty" to "∞",
        "\\because" to "∵",
        "\\therefore" to "∴",
        "\\degree" to "°"
    )

    private val simpleToken = Regex("^[A-Za-z0-9.]+$")

    /** 去掉题干开头的题号，选项和填空下划线保持不变。 */
    private val leadingNumber = Regex(
        "^\\s*(?:第\\s*\\d+\\s*题\\s*[.、．:：]?|" +
            "[（(]\\s*\\d+\\s*[）)]|" +
            "\\d+\\s*[、．)）]|" +
            "\\d+\\.(?!\\d)|" +
            "[①-⑳])\\s*"
    )

    fun cleanStem(text: String): String = text.replace(leadingNumber, "").trim()

    /** 文本里是否有需要按公式渲染的内容（填空下划线不算）。 */
    private val scriptMarker = Regex("[\\^_]\\s*(?:[A-Za-z0-9{]|\\\\)")

    fun hasMath(text: String): Boolean =
        text.contains('$') || scriptMarker.containsMatchIn(text)

    /** 把文本拆成普通段和 $...$ 公式段。 */
    fun splitSegments(text: String): List<MathSegment> {
        if (!text.contains('$')) return listOf(MathSegment(text, false))
        val result = mutableListOf<MathSegment>()
        var index = 0
        var plainStart = 0
        while (index < text.length) {
            if (text[index] == '$') {
                val double = index + 1 < text.length && text[index + 1] == '$'
                val delimiter = if (double) "$$" else "$"
                val contentStart = index + delimiter.length
                val close = text.indexOf(delimiter, contentStart)
                if (close > contentStart) {
                    if (index > plainStart) {
                        result.add(MathSegment(text.substring(plainStart, index), false))
                    }
                    result.add(MathSegment(text.substring(contentStart, close), true))
                    index = close + delimiter.length
                    plainStart = index
                    continue
                }
            }
            index++
        }
        if (plainStart < text.length) {
            result.add(MathSegment(text.substring(plainStart), false))
        }
        return result
    }

    fun normalizeMath(raw: String): NormalizedMath {
        val parser = Parser(raw, allowCommands = true)
        val runs = parser.parse(null)
        return if (parser.supported) {
            NormalizedMath(runs, true)
        } else {
            NormalizedMath(listOf(MathRun(raw)), false)
        }
    }

    /** 普通文本只处理 ^/_，不把反斜杠当公式命令。 */
    fun normalizePlain(text: String): NormalizedMath {
        val parser = Parser(text, allowCommands = false)
        return NormalizedMath(parser.parse(null), true)
    }

    private class Parser(
        private val source: String,
        private val allowCommands: Boolean
    ) {
        private var index = 0
        var supported = true
            private set

        fun parse(until: Char?): List<MathRun> {
            val result = mutableListOf<MathRun>()
            val text = StringBuilder()

            fun flush() {
                if (text.isNotEmpty()) {
                    result.add(MathRun(text.toString()))
                    text.clear()
                }
            }

            while (index < source.length) {
                val c = source[index]
                if (until != null && c == until) {
                    index++
                    break
                }
                when (c) {
                    '^', '_' -> {
                        val target = index + 1
                        if (target >= source.length || !isScriptTarget(source[target])) {
                            // 填空题的连续下划线、句尾 ^/_ 按普通字符保留
                            text.append(c)
                            index++
                        } else {
                            flush()
                            index++
                            val group = readGroup()
                            val child = Parser(group, allowCommands)
                            val childRuns = child.parse(null)
                            if (!child.supported) supported = false
                            val style = if (c == '^') MathRunStyle.SUP else MathRunStyle.SUB
                            result.addAll(childRuns.map { it.copy(style = style) })
                        }
                    }

                    '{' -> {
                        if (!allowCommands) {
                            // 普通文本里的花括号按原样保留（如集合 {1,2}）
                            text.append(c)
                            index++
                        } else {
                            index++
                            result.addAll(parse('}'))
                        }
                    }

                    '}' -> {
                        text.append(c)
                        index++
                    }

                    '\\' -> {
                        if (!allowCommands) {
                            text.append(c)
                            index++
                        } else {
                            parseCommand(result, text, ::flush)
                        }
                    }

                    else -> {
                        text.append(c)
                        index++
                    }
                }
            }
            flush()
            return result
        }

        private fun parseCommand(
            result: MutableList<MathRun>,
            text: StringBuilder,
            flush: () -> Unit
        ) {
            index++ // 跳过反斜杠
            if (index >= source.length) {
                supported = false
                return
            }
            val next = source[index]
            if (!next.isLetter()) {
                when (next) {
                    ',', ';', ':', '!', ' ' -> index++
                    else -> {
                        text.append(next)
                        index++
                    }
                }
                return
            }

            val commandStart = index
            while (index < source.length && source[index].isLetter()) index++
            val command = "\\" + source.substring(commandStart, index)
            when (command) {
                "\\frac" -> {
                    flush()
                    val numerator = readGroup()
                    val denominator = readGroup()
                    val numParser = Parser(numerator, true)
                    val numRuns = numParser.parse(null)
                    if (!numParser.supported) supported = false
                    val denParser = Parser(denominator, true)
                    val denRuns = denParser.parse(null)
                    if (!denParser.supported) supported = false
                    result.addAll(wrap(numRuns))
                    result.add(MathRun("/"))
                    result.addAll(wrap(denRuns))
                }

                "\\sqrt" -> {
                    flush()
                    val argument = readGroup()
                    val argParser = Parser(argument, true)
                    val argRuns = argParser.parse(null)
                    if (!argParser.supported) supported = false
                    result.add(MathRun("√("))
                    result.addAll(argRuns)
                    result.add(MathRun(")"))
                }

                else -> {
                    val symbol = symbols[command]
                    if (symbol != null) {
                        text.append(symbol)
                    } else {
                        supported = false
                        text.append(command)
                    }
                }
            }
        }

        /** 只有字母、数字、{ 或反斜杠开头才当作上下标。 */
        private fun isScriptTarget(c: Char): Boolean =
            c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '{' || c == '\\'

        private fun readGroup(): String {
            if (index >= source.length) return ""
            if (source[index] != '{') {
                val c = source[index]
                index++
                return c.toString()
            }
            index++ // 跳过 {
            val start = index
            var depth = 1
            while (index < source.length) {
                val c = source[index]
                if (c == '\\' && index + 1 < source.length) {
                    index += 2
                    continue
                }
                if (c == '{') {
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        val value = source.substring(start, index)
                        index++
                        return value
                    }
                }
                index++
            }
            supported = false
            return source.substring(start)
        }

        private fun wrap(runs: List<MathRun>): List<MathRun> {
            if (runs.size == 1 && runs[0].style == MathRunStyle.NORMAL &&
                simpleToken.matches(runs[0].text)
            ) {
                return runs
            }
            return listOf(MathRun("(")) + runs + listOf(MathRun(")"))
        }
    }
}
