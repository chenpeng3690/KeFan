package com.kefan.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 反馈文档：保存的是用户终稿 finalContent，aiDraft 仅用于差异比对。
 */
@Entity(
    tableName = "feedback",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId"), Index(value = ["studentId", "classDate"])]
)
data class Feedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    /** YYYY-MM-DD 学生姓名反馈 (2) */
    val title: String,
    /** 列表副标题，如“比和比例复习” */
    val summary: String = "",
    val classDate: Long,
    val lessonCount: Int = 2,
    /** 【课后任务】的值，由 App 拼接，可手改 */
    val taskText: String = "复习错题",
    /** 只发家长，保留字段兼容 */
    val toneMode: String = "PARENT",
    val aiDraft: String = "",
    val finalContent: String = "",
    /** DRAFT / FINAL */
    val status: String = STATUS_DRAFT,
    val modelConfigId: Long? = null,
    val templateId: Long? = null,
    val templateVersion: Int = 1,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val sentAt: Long? = null,
    /** 软删除时间；null = 正常显示，非 null = 在回收站 */
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_FINAL = "FINAL"
    }
}

/**
 * 课堂（教案）照片，按顺序编号为第 1 页…第 N 页。
 */
@Entity(
    tableName = "feedback_image",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Feedback::class,
            parentColumns = ["id"],
            childColumns = ["feedbackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feedbackId"), Index("studentId")]
)
data class FeedbackImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedbackId: Long,
    val studentId: Long,
    val uri: String,
    val ocrText: String = "",
    val orderIndex: Int = 0
)

/**
 * 上下文审计日志，也是压缩记录。
 */
@Entity(
    tableName = "context_log",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId"), Index(value = ["studentId", "compacted"])]
)
data class ContextLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    /** FEEDBACK / SCORE / SAMPLE / COMPACT */
    val type: String,
    val refId: Long? = null,
    val tokenCount: Int = 0,
    val compacted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
