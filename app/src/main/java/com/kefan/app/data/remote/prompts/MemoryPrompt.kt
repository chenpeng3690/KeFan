package com.kefan.app.data.remote.prompts

import com.kefan.app.core.util.formatDateShort
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.Student

/**
 * 记忆压缩提示词（§8.2）与长期画像重算提示词。
 */
object MemoryPrompt {

    private const val L3_LIMIT = 600
    private const val L2_LIMIT = 800

    /** 步骤 A：把旧摘要 + 新增原始记录压成新的 L3 */
    fun compactRecentSummary(
        oldSummary: String,
        records: List<Feedback>
    ): Pair<String, String> {
        val system = """
            你在维护一个学生的教学档案摘要。请把「旧摘要」和「新增的若干次课堂记录」合并成一份新的摘要。

            【硬性要求】
            1. 只做压缩，不做发挥。不新增任何原文没有的信息。
            2. 必须保留：每次课的日期、涉及的知识点、明确的结论（掌握/不熟/待巩固）、布置的作业、未解决的遗留问题。
            3. 删除：过程性描述、重复表述、客套话、情绪化评价。
            4. 若新旧信息冲突，以新的为准，并在该项后标注「（已更新）」。
            5. 输出为纯文本，每次课一行，格式：
               MM-DD｜知识点｜表现结论｜遗留/待跟进
            6. 总字数不超过 $L3_LIMIT 字。超出时优先合并早期的、已解决的条目。
        """.trimIndent()

        val recordText = records.joinToString("\n\n") { feedback ->
            "【${formatDateShort(feedback.classDate)}】\n" +
                feedback.finalContent.take(900)
        }

        val user = """
            【旧摘要】
            ${oldSummary.ifBlank { "（暂无，这是第一批）" }}

            【新增记录】
            $recordText

            【输出】
            只输出新的摘要正文，不要任何解释。
        """.trimIndent()

        return system to user
    }

    /** 步骤 B：新 L3 超长时，把早期一半并入 L2 */
    fun mergeLongTermProfile(
        oldProfile: String,
        recentSummary: String
    ): Pair<String, String> {
        val system = """
            你在维护一个学生的长期学情画像。请把「旧画像」和「近期摘要」合并成一份新的长期画像。

            【硬性要求】
            1. 只保留结论性、长期有效的信息，丢弃单次事件和过程性描述。
            2. 必须覆盖：优势、薄弱知识点、学习习惯、沟通注意点、进步曲线判断。
            3. 新旧信息冲突时以新的为准，并在该项后标注「（已更新）」。
            4. 不编造材料里没有的信息。
            5. 纯文本，不超过 $L2_LIMIT 字，只输出正文。
        """.trimIndent()

        val user = """
            【旧画像】
            ${oldProfile.ifBlank { "（暂无）" }}

            【近期摘要】
            ${recentSummary.ifBlank { "（暂无）" }}

            【输出】
            只输出新的长期画像正文。
        """.trimIndent()

        return system to user
    }

    /** 手动「重新生成长期画像」：基于档案 + L3 + 全部原始记录重算 */
    fun rebuildLongTermProfile(
        student: Student,
        currentProfile: String,
        recentSummary: String,
        feedbacks: List<Feedback>
    ): Pair<String, String> {
        val system = """
            你在维护一个学生的长期学情画像。请基于档案、近期摘要和历史课堂记录，
            重新写一份长期画像。

            【硬性要求】
            1. 只保留结论性、长期有效的信息：优势、薄弱知识点、学习习惯、沟通注意点、进步曲线判断。
            2. 丢弃单次事件、客套话和过程性描述；不编造材料里没有的信息。
            3. 新旧信息冲突时以最新的课堂记录为准，并标注「（已更新）」。
            4. 纯文本，不超过 $L2_LIMIT 字，只输出正文。
        """.trimIndent()

        val archive = buildString {
            append("姓名：${student.name}")
            if (student.grade.isNotBlank()) append("；年级：${student.grade}")
            if (student.subject.isNotBlank()) append("；科目：${student.subject}")
            if (student.goal.isNotBlank()) append("；目标：${student.goal}")
            if (student.personality.isNotBlank()) append("；性格：${student.personality}")
            if (student.parentNote.isNotBlank()) append("；家长诉求：${student.parentNote}")
        }

        val records = feedbacks.takeLast(10).joinToString("\n\n") { feedback ->
            "【${formatDateShort(feedback.classDate)}】\n" + feedback.finalContent.take(700)
        }

        val user = """
            【档案】
            $archive

            【当前画像】
            ${currentProfile.ifBlank { "（暂无）" }}

            【近期摘要】
            ${recentSummary.ifBlank { "（暂无）" }}

            【历史课堂记录】
            ${records.ifBlank { "（暂无）" }}

            【输出】
            只输出新的长期画像正文。
        """.trimIndent()

        return system to user
    }
}
