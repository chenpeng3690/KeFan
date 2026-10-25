package com.kefan.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 错题导出任务。答案生成走后台队列，主文档先交付。
 */
@Entity(
    tableName = "export_job",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId"), Index("status")]
)
data class ExportJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    /** MISTAKE_DOCX */
    val type: String = TYPE_MISTAKE_DOCX,
    val rangeStart: Long,
    val rangeEnd: Long,
    val filterJson: String = "{}",
    /** NONE / GENERATE */
    val answerMode: String = ANSWER_NONE,
    val docxPath: String? = null,
    val docxWithAnswerPath: String? = null,
    val totalCount: Int = 0,
    val answerDoneCount: Int = 0,
    val answerFailedCount: Int = 0,
    /** BUILDING / READY / ANSWERING / DONE / FAILED / CANCELED */
    val status: String = STATUS_BUILDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_MISTAKE_DOCX = "MISTAKE_DOCX"
        const val ANSWER_NONE = "NONE"
        const val ANSWER_GENERATE = "GENERATE"
        const val STATUS_BUILDING = "BUILDING"
        const val STATUS_READY = "READY"
        const val STATUS_ANSWERING = "ANSWERING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELED = "CANCELED"
    }
}
