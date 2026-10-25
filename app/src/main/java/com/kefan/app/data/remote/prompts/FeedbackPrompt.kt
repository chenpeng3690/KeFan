package com.kefan.app.data.remote.prompts

import android.util.Base64
import com.kefan.app.core.util.formatChineseDate
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.ManualSample
import com.kefan.app.data.local.entity.MemoryCard
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.ScoreRecord
import com.kefan.app.data.local.entity.Student
import com.kefan.app.domain.model.TemplateSection
import com.kefan.app.domain.model.TemplateSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.Locale

data class FeedbackPromptInputs(
    val student: Student,
    val spec: TemplateSpec,
    val memory: MemoryCard?,
    val lastFeedback: Feedback?,
    val samples: List<ManualSample>,
    val scores: List<ScoreRecord>,
    val mistakes: List<MistakeRecord> = emptyList(),
    val classDate: Long,
    val lessonCount: Int,
    val keyPoints: String,
    val imageFiles: List<File> = emptyList(),
    val ocrTexts: List<String> = emptyList()
)

/**
 * §8.1 反馈生成提示词。只写模板中 source=AI 的段落，其余由 App 拼。
 */
object FeedbackPrompt {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun buildMessagesJson(inputs: FeedbackPromptInputs): String =
        withContext(Dispatchers.IO) {
            val system = buildSystemPrompt(inputs)
            val userText = buildUserText(inputs)

            buildJsonArray {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                if (inputs.imageFiles.isEmpty()) {
                    addJsonObject {
                        put("role", "user")
                        put("content", userText)
                    }
                } else {
                    addJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            addJsonObject {
                                put("type", "text")
                                put("text", userText)
                            }
                            inputs.imageFiles.forEach { file ->
                                val bytes = file.readBytes()
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:image/jpeg;base64,$base64")
                                    }
                                }
                            }
                        })
                    }
                }
            }.toString()
        }

    private fun buildSystemPrompt(inputs: FeedbackPromptInputs): String {
        val student = inputs.student
        val spec = inputs.spec
        val templateJson = buildJsonObject {
            put("opening", spec.opening)
            put("blankLineAfterOpening", spec.blankLineAfterOpening)
            put("sections", json.parseToJsonElement(TemplateJson.encodeSections(spec.sections)))
            put("tone", spec.tone)
            put("address", spec.address)
            putJsonArray("length") {
                add(spec.lengthMin)
                add(spec.lengthMax)
            }
            put("bannedWords", json.parseToJsonElement(TemplateJson.encodeStrings(spec.bannedWords)))
            put("useEmoji", spec.useEmoji)
        }.toString()

        val aiSections = spec.sections.filter { it.source == TemplateSection.SOURCE_AI }
        val aiSectionList = aiSections.joinToString("\n") { section ->
            buildString {
                append("- ").append(section.title)
                if (!section.inline && section.items > 1) append("（写 ").append(section.items).append(" 条）")
                if (section.hint.isNotBlank()) append("：").append(section.hint)
            }
        }
        val banned = spec.bannedWords.joinToString("、")
        val sampleText = inputs.samples.takeLast(2).mapIndexed { index, sample ->
            "【样例 ${index + 1}】\n${sample.content.take(700)}"
        }.joinToString("\n\n").ifBlank { "无" }
        val lastFeedbackText = inputs.lastFeedback?.finalContent
            ?.take(1200)
            ?.ifBlank { "无" }
            ?: "无"
        val profile = buildString {
            append("姓名：").append(student.name)
            if (student.grade.isNotBlank()) append("；年级：").append(student.grade)
            if (student.subject.isNotBlank()) append("；科目：").append(student.subject)
            if (student.school.isNotBlank()) append("；学校：").append(student.school)
            if (student.goal.isNotBlank()) append("；学习目标：").append(student.goal)
            if (student.personality.isNotBlank()) append("；性格：").append(student.personality)
            if (student.parentNote.isNotBlank()) append("；家长诉求：").append(student.parentNote)
        }

        return """
你是${student.name}的${student.subject.ifBlank { "" }}老师，正在给家长写这节课的课后反馈。
这段文字会被直接复制到微信发给家长，必须像真人手打出来的，不能有一点点 AI 味。

━━━━━━ 这位老师的写作模板 ━━━━━━

$templateJson

━━━━━━ 你只需要写其中标了 AI 的段落 ━━━━━━

$aiSectionList

· 只输出上面这些 source = "AI" 的段落，按原顺序。
· 每个段落先用它自己的 title 起头（例如【课堂反馈】），再写内容。
· 标了 items = 2 的段落，里面要写「1. …」和「2. …」两条；items = 1 就写一段话，不加编号。
· 不要输出 source = "APP" 的段落（日期、课后任务由程序填写）。
· 不要新增模板里没有的段落，也不要漏掉任何一段。

━━━━━━ 通用写作规则 ━━━━━━

称谓：${spec.address}
语气：${spec.tone}
篇幅：${spec.lengthMin}~${spec.lengthMax} 字
禁用词（出现任意一个即为不合格）：$banned
禁用符号：emoji、感叹号、破折号
禁止内容：结尾展望、要求家长配合、学习习惯说教

━━━━━━ 事实约束 ━━━━━━

1. 只写下面素材里有的东西。素材里没有的数字、题号、分数、事件，一律不写；看不清、不确定的，跳过，不许猜。
2. 教案照片里有什么知识点就写什么，不要自己扩充章节、不要提前讲没教的内容。
3. 不主动提分数，除非【本次成绩】确实有记录。
4. 不评价智力、性格、态度。只写可观察的行为和具体的知识点表现。
5. 写“教了什么”的段落，必须出现该学科真会用的具体方法、公式、规则或步骤。数学给公式，英语给语法规则，语文给答题步骤。不能只写“讲解了 xx 的意义”。
6. 写“学生表现”的段落，必须给可操作的建议（具体做法、二选一），不能写“多加练习”“注意审题”这类空话。

━━━━━━ 这个学生的情况 ━━━━━━

档案：$profile
学情画像：${inputs.memory?.longTermProfile?.ifBlank { "暂无" } ?: "暂无"}
近期摘要：${inputs.memory?.recentSummary?.ifBlank { "暂无" } ?: "暂无"}
上次反馈（只用于接续进度，不要重复其内容）：$lastFeedbackText
本次成绩：${scoreLine(inputs.scores)}

━━━━━━ 错题记录（真实数据） ━━━━━━

${mistakeLines(inputs.mistakes)}

· 只有上面列出的知识点才能写“同类题累计错 N 次”，数字必须与这里一致；没有列出的不许编造。

━━━━━━ 写作参考（该生最近 ${inputs.samples.takeLast(2).size} 条人工反馈，只学语气和节奏，严禁抄用任何一句） ━━━━━━

$sampleText

⚠ 样例里出现的具体知识点名词，除非本次教案确实讲了，否则全文绝对不许出现。只借语感，内容必须全部来自本次素材。

━━━━━━ 输出 ━━━━━━

从第一个 AI 段落的 title 开始，到最后一个 AI 段落结束。
不要前言、不要“以下是反馈”、不要解释、不要 Markdown 符号。
""".trimIndent()
    }

    private fun buildUserText(inputs: FeedbackPromptInputs): String {
        val imageLine = if (inputs.imageFiles.isNotEmpty()) {
            "教案照片：共 ${inputs.imageFiles.size} 张，已按第 1 页到第 ${inputs.imageFiles.size} 页的顺序附在下面。请直接阅读图片，保证跨页知识点的连贯性。"
        } else if (inputs.ocrTexts.isNotEmpty()) {
            "教案照片文字：\n" + inputs.ocrTexts.mapIndexed { index, text ->
                "第 ${index + 1} 页：$text"
            }.joinToString("\n")
        } else {
            "教案照片：本次没有可读的图片或文字。"
        }

        return """
━━━━━━ 本次素材 ━━━━━━

上课日期：${formatChineseDate(inputs.classDate)}（日期由 App 填，你不要写【上课时间】）
课时：${inputs.lessonCount} 课时
老师补充：${inputs.keyPoints.ifBlank { "无" }}
本次成绩：${scoreLine(inputs.scores)}
$imageLine

请严格按系统提示的模板与事实约束输出。
""".trimIndent()
    }

    private fun mistakeLines(mistakes: List<MistakeRecord>): String {
        if (mistakes.isEmpty()) return "无（不得编造错题数量和“累计错过 N 次”）"
        val tags = linkedMapOf<String, IntArray>()
        mistakes.forEach { mistake ->
            TemplateJson.decodeStrings(mistake.knowledgeTagsJson).forEach { tag ->
                val counts = tags.getOrPut(tag) { IntArray(2) }
                counts[0] += 1
                if (mistake.reviewStatus != MistakeRecord.REVIEW_MASTERED) counts[1] += 1
            }
        }
        val tagLine = tags.entries
            .sortedByDescending { it.value[0] }
            .take(8)
            .joinToString("；") { (tag, counts) ->
                "「$tag」累计 ${counts[0]} 题（待复习 ${counts[1]}）"
            }
            .ifBlank { "无知识点标签" }
        val recentStems = mistakes
            .sortedByDescending { it.createdAt }
            .mapNotNull { it.stemText.takeIf { text -> text.isNotBlank() }?.take(40) }
            .take(5)
            .joinToString("；")
            .ifBlank { "无" }
        return "按知识点：$tagLine\n最近错题：$recentStems"
    }

    private fun scoreLine(scores: List<ScoreRecord>): String {
        if (scores.isEmpty()) return "无（不得编造成绩）"
        val latest = scores.first()
        val days = (System.currentTimeMillis() - latest.date) / (24L * 60 * 60 * 1000)
        if (days > 7) return "无（不得编造成绩）"
        val previous = scores.getOrNull(1)
        val latestScore = formatScore(latest.score)
        return if (previous == null) {
            "${latest.examName} $latestScore 分"
        } else {
            val delta = latest.score - previous.score
            val deltaText = when {
                delta > 0 -> "↑${formatScore(delta)}"
                delta < 0 -> "↓${formatScore(-delta)}"
                else -> "持平"
            }
            "${latest.examName} $latestScore 分（上次 ${formatScore(previous.score)}，$deltaText）"
        }
    }

    private fun formatScore(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.CHINA, "%.1f", value)
}
