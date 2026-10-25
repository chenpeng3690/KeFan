package com.kefan.app.domain.model

/**
 * 模板里的一个段落。结构是数据，不是代码。
 */
data class TemplateSection(
    val title: String,
    /** AI = 模型写；APP = 程序填 */
    val source: String = SOURCE_AI,
    val inline: Boolean = false,
    val block: Boolean = true,
    val items: Int = 1,
    val hint: String = "",
    val defaultValue: String = ""
) {
    companion object {
        const val SOURCE_AI = "AI"
        const val SOURCE_APP = "APP"
    }
}

/**
 * 模板硬规则：这两段永远由 App 填，AI 只负责写“不同”的内容。
 * 兼容旧数据：即使历史模板把它们标成 AI，在读取时也会强制纠正为 APP。
 */
object TemplateRules {
    private val fixedAppTitles = listOf("上课时间", "课后任务")

    fun normalizeSections(sections: List<TemplateSection>): List<TemplateSection> =
        sections.map { section ->
            if (fixedAppTitles.any { section.title.contains(it) }) {
                section.copy(source = TemplateSection.SOURCE_APP)
            } else {
                section
            }
        }
}

data class TemplateSpec(
    val opening: String,
    val blankLineAfterOpening: Boolean,
    val sections: List<TemplateSection>,
    val tone: String,
    val address: String,
    val lengthMin: Int,
    val lengthMax: Int,
    val bannedWords: List<String>,
    val useEmoji: Boolean,
    val styleSummary: String = "",
    val sourceSampleIds: List<Long> = emptyList(),
    /** 仅用于模板提取后写入 L2，不落模板表 */
    val profileDraft: String = ""
) {
    val aiSectionCount: Int get() = sections.count { it.source == TemplateSection.SOURCE_AI }
}

object TemplateDefaults {
    val bannedWords = listOf(
        "表现良好",
        "继续努力",
        "再接再厉",
        "希望今后",
        "相信一定会",
        "该生",
        "学生",
        "加强练习",
        "注意审题",
        "敷衍"
    )

    /** 一条历史反馈都没导入时使用的极简兜底，不写入数据库。 */
    fun fallback(): TemplateSpec = TemplateSpec(
        opening = "{{姓名}}家长您好，{{科目}}课情况如下",
        blankLineAfterOpening = true,
        sections = listOf(
            TemplateSection(
                title = "【课堂反馈】",
                source = TemplateSection.SOURCE_AI,
                inline = false,
                block = true,
                items = 2,
                hint = "第 1 条写这节课教了什么，给具体方法；第 2 条写学生表现：一句肯定 + 一个转折 + 一个具体问题 + 一个可操作方法"
            ),
            TemplateSection(
                title = "【课后任务】",
                source = TemplateSection.SOURCE_APP,
                inline = true,
                block = false,
                defaultValue = "复习错题"
            )
        ),
        tone = "平实、专业、不煽情，像真人手打",
        address = "孩子",
        lengthMin = 200,
        lengthMax = 360,
        bannedWords = bannedWords,
        useEmoji = false
    )
}
