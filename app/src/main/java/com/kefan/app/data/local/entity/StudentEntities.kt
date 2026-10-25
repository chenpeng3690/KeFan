package com.kefan.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学生档案（L1 档案卡的一部分）。
 */
@Entity(
    tableName = "student",
    indices = [Index("status"), Index("lastFeedbackAt")]
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarUri: String? = null,
    val grade: String = "",
    val subject: String = "",
    val school: String = "",
    val startDate: Long = System.currentTimeMillis(),
    /** 旧版“已带课时”，仅保留字段兼容数据库，界面已不再使用 */
    val totalLessons: Int = 0,
    val goal: String = "",
    val personality: String = "",
    val parentNote: String = "",
    /** 错题自动提取开关，默认关闭 */
    val autoExtractMistakes: Boolean = false,
    /** 新建反馈时默认课时数 */
    val defaultLessons: Int = 2,
    /** 每周上课日：1=周一 … 7=周日；0=未设置 */
    val classDay: Int = 0,
    /** 上课时间 HH:mm；空=未设置 */
    val classTime: String = "",
    /** ACTIVE / PAUSED / FINISHED */
    val status: String = STATUS_ACTIVE,
    val lastFeedbackAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_FINISHED = "FINISHED"
    }
}

/**
 * 每个学生一份记忆卡：L2 长期画像 + L3 近期摘要 + 压缩游标。
 */
@Entity(
    tableName = "memory_card",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId")]
)
data class MemoryCard(
    @PrimaryKey val studentId: Long,
    val longTermProfile: String = "",
    val recentSummary: String = "",
    val compactedUntilRecordId: Long = 0,
    val tokenEstimate: Int = 0,
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 老师粘贴 / OCR 进来的人工历史反馈，是模板提取与 few-shot 的原始素材。
 */
@Entity(
    tableName = "manual_sample",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId"), Index(value = ["studentId", "analyzed"])]
)
data class ManualSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val content: String,
    /** PASTE / OCR */
    val sourceType: String = SOURCE_PASTE,
    val analyzed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_PASTE = "PASTE"
        const val SOURCE_OCR = "OCR"
    }
}

/**
 * 反馈模板：结构是数据，不是代码。
 * scope = GLOBAL / STUDENT；同一学生保留多版本，isActive 标记当前版本。
 */
@Entity(
    tableName = "feedback_template",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId"), Index(value = ["studentId", "isActive"])]
)
data class FeedbackTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long? = null,
    /** GLOBAL / STUDENT */
    val scope: String = SCOPE_STUDENT,
    val opening: String = "{{姓名}}家长您好，{{科目}}课情况如下",
    val blankLineAfterOpening: Boolean = true,
    /** sections 数组的 JSON 字符串 */
    val sectionsJson: String = "[]",
    val tone: String = "平实、专业、不煽情，像真人手打",
    val address: String = "孩子",
    val lengthMin: Int = 280,
    val lengthMax: Int = 340,
    /** bannedWords 数组的 JSON 字符串 */
    val bannedWordsJson: String = "[]",
    val useEmoji: Boolean = false,
    /** 参与提取的样例 id 列表 JSON */
    val sourceSampleIdsJson: String = "[]",
    /** AI 给老师看的人话摘要 */
    val styleSummary: String = "",
    val version: Int = 1,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCOPE_GLOBAL = "GLOBAL"
        const val SCOPE_STUDENT = "STUDENT"
    }
}
