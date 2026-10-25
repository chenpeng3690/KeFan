package com.kefan.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "score_record",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId"), Index(value = ["studentId", "date"])]
)
data class ScoreRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val date: Long,
    val examName: String,
    val subject: String,
    val fullScore: Double = 100.0,
    val score: Double,
    val rank: Int? = null,
    val classAvg: Double? = null,
    /** 知识点标签 JSON */
    val knowledgeTagsJson: String = "[]",
    val mistakes: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mistake_record",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("studentId"),
        Index(value = ["studentId", "createdAt"]),
        Index(value = ["studentId", "reviewStatus"])
    ]
)
data class MistakeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val subject: String = "",
    val stemText: String = "",
    val studentAnswer: String = "",
    val correctAnswer: String = "",
    /** NONE / MANUAL / AI_GENERATED / AI_FAILED */
    val answerStatus: String = ANSWER_NONE,
    val analysis: String = "",
    /** NONE / AI_GENERATED / AI_FAILED */
    val analysisStatus: String = ANSWER_NONE,
    /** 知识点标签 JSON */
    val knowledgeTagsJson: String = "[]",
    /** 概念不清 / 审题失误 / 计算失误 / 方法不会 / 粗心遗漏 */
    val errorType: String = "",
    /** CLASS / HOMEWORK / EXAM / OTHER */
    val sourceType: String = SOURCE_OTHER,
    val sourceFeedbackId: Long? = null,
    val sourceScoreId: Long? = null,
    val difficulty: Int = 3,
    /** TODO / REVIEWING / MASTERED */
    val reviewStatus: String = REVIEW_TODO,
    val reviewCount: Int = 0,
    val correctStreak: Int = 0,
    val lastReviewedAt: Long? = null,
    val note: String = "",
    /** 是否含图，导出时只插图形本身 */
    val hasFigure: Boolean = false,
    val ocrConfidence: Double = 1.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ANSWER_NONE = "NONE"
        const val ANSWER_MANUAL = "MANUAL"
        const val ANSWER_AI_GENERATED = "AI_GENERATED"
        const val ANSWER_AI_FAILED = "AI_FAILED"

        const val SOURCE_CLASS = "CLASS"
        const val SOURCE_HOMEWORK = "HOMEWORK"
        const val SOURCE_EXAM = "EXAM"
        const val SOURCE_OTHER = "OTHER"

        const val REVIEW_TODO = "TODO"
        const val REVIEW_REVIEWING = "REVIEWING"
        const val REVIEW_MASTERED = "MASTERED"
    }
}

@Entity(
    tableName = "mistake_image",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MistakeRecord::class,
            parentColumns = ["id"],
            childColumns = ["mistakeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mistakeId"), Index("studentId")]
)
data class MistakeImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mistakeId: Long,
    val studentId: Long,
    val originalUri: String,
    val croppedUri: String? = null,
    /** 归一化裁剪框 JSON：[left, top, width, height] */
    val cropRectJson: String? = null,
    /** 只含图形的区域，导出用 */
    val figureUri: String? = null,
    val figureRectJson: String? = null,
    val figureConfirmed: Boolean = false,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "review_log",
    foreignKeys = [
        ForeignKey(
            entity = MistakeRecord::class,
            parentColumns = ["id"],
            childColumns = ["mistakeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mistakeId")]
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mistakeId: Long,
    val date: Long,
    /** CORRECT / WRONG */
    val result: String,
    val note: String = ""
) {
    companion object {
        const val RESULT_CORRECT = "CORRECT"
        const val RESULT_WRONG = "WRONG"
    }
}
