package com.kefan.app.data.remote.prompts

import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.Student

/**
 * §8.5 错题答案解析提示词，用于导出后台补全缺失答案。
 */
object MistakeAnswerPrompt {

    val SYSTEM_PROMPT = """
你在为一道学生的错题补全标准答案与解析。

【严格输出以下 JSON，不要多余文字】
{
  "correctAnswer": "最终答案，简洁，只写结果",
  "analysis": "解析，分步骤，3~6 行。说明考什么、关键一步在哪、学生容易错在哪",
  "pitfall": "一句话点出这个学生犯此错的原因"
}

【硬约束】
1. 题干信息不完整（缺条件、缺选项、图看不清）时不要硬做，输出：
   {"error": "题干信息不足", "reason": "具体缺什么，如缺图/缺选项/缺已知条件"}
2. 不许编造题干里没有的条件、数字、图形信息。
3. 解析用学生能听懂的话，不用超纲方法。
4. 公式用纯文本：x^2、1/2、(a+b)/(c-d)、√3、≤、≠、∴
5. analysis 控制在 200 字内，不要写成教案。
6. 这道题可能有多个解法，只给最标准的一种。
7. 不要输出 JSON 以外的任何文字。
""".trimIndent()

    fun userPrompt(student: Student?, mistake: MistakeRecord): String = """
【学生信息】${student?.grade.orEmpty().ifBlank { "未填写" }} · ${student?.subject.orEmpty().ifBlank { "未填写" }}
【题目原文】${mistake.stemText.ifBlank { "（题干缺失）" }}
【学生的错误答案】${mistake.studentAnswer.ifBlank { "未填写" }}
【已知错因类型】${mistake.errorType.ifBlank { "未填写" }}
【题目图片】${if (mistake.hasFigure) "本题含图，图在错题本里，导出时会插入 Word；请只根据题干文字作答，信息不足就报错。" else "无"}
""".trimIndent()
}
