package com.kefan.app.data.remote.prompts

/**
 * §8.3 模板提取提示词。一次提取、存成数据、之后复用。
 */
object TemplateExtractionPrompt {

    const val SYSTEM_PROMPT =
        "你是一位严谨的教培写作风格分析师。你只输出严格 JSON，不输出 Markdown 代码块以外的任何解释。"

    fun userPrompt(samples: List<String>): String {
        val joined = samples.mapIndexed { index, content ->
            "【样例 ${index + 1}】\n$content"
        }.joinToString("\n\n")

        return """
你在分析一位教培老师写给某个学生的历史课后反馈，目的是反推出他写反馈时遵循的结构和风格。

【输入】以下是 ${samples.size} 条该老师亲手写的反馈（同一个学生，可能跨越不同课题）：

$joined

━━━━━━ 任务 ━━━━━━

把这 ${samples.size} 条反馈的共同结构提取成一份模板。要求：

1. 先找“每一条都有”的固定成分，再看顺序。只在部分样例里出现的成分，不要写进模板。
2. sections 数组按实际出现顺序排列，title 保留原文的括号形式（【】或其他形式都照原样）。
3. 判断每段由谁来写：
   - 如果这段内容每次都不同（如主要讲的知识点、学生表现）→ source = "AI"
   - 如果这段每次都是套话或固定值（如日期、课时、“复习错题”）→ source = "APP"
   - 硬规则：标题含“上课时间”或“课后任务”的段落，无论样例内容是否变化，一律 source = "APP"（由 App 注入，绝不能标成 AI）
4. 每一段写一句 hint，说明这段该写什么、怎么写，用你自己的话，但要贴合样例的实际写法。
5. 判断每段的排版：只有一行内容用 inline=true、block=false；多条内容或整段正文用 block=true、inline=false。
6. 统计每段的平均字数，据此推算整篇的 length 区间（允许上下浮动 15%）。
7. bannedWords 从这个老师从未使用、但 AI 爱用的套话里选（如“表现良好”“继续努力”），再加上样例里明显不会出现的词。不超过 10 个。
8. profileDraft 用 1~2 句概括从这些反馈里能看出的学情：优势、薄弱点、学习习惯、待跟进。只写样例中有依据的内容，不要编造。

━━━━━━ 输出严格 JSON，不要任何多余文字 ━━━━━━

{
  "opening": "开头那一句的模板，姓名用 {{姓名}}、科目用 {{科目}} 占位",
  "blankLineAfterOpening": true,
  "sections": [
    {
      "title": "【上课时间】",
      "source": "APP",
      "inline": true,
      "block": false,
      "items": 1,
      "hint": "日期(课时)，由 App 注入",
      "default": ""
    },
    {
      "title": "【课堂反馈】",
      "source": "AI",
      "inline": false,
      "block": true,
      "items": 2,
      "hint": "第 1 条写这节课教了什么，必须给具体方法；第 2 条写学生表现：一句肯定+一个转折+一个具体问题+一个可操作方法",
      "default": ""
    }
  ],
  "tone": "一句话描述语气，如：平实、专业、不煽情，像真人手打",
  "address": "老师对这个学生的称呼方式，如：孩子",
  "length": [280, 340],
  "bannedWords": ["表现良好", "继续努力", "该生", "学生"],
  "useEmoji": false,
  "styleSummary": "用 2~3 句人话说明你观察到的写作习惯，给老师看，让他判断你有没有读懂",
  "profileDraft": "用 1~2 句写学情初判，只写样例里有依据的内容"
}

━━━━━━ 硬约束 ━━━━━━

· 结构必须来自样例，不许套用你心目中的“标准课后反馈格式”。
· 不要新增样例里从未出现过的固定段落。
· 样例只有 1 条时，sections 可能不准，在 styleSummary 里明确说明“样本太少，建议再补几条”。
· 不要输出任何 JSON 以外的文字。
""".trimIndent()
    }
}
