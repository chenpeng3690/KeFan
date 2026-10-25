package com.kefan.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * 反馈全文检索虚表。rowId 与 Feedback.id 一一对应。
 */
@Fts4
@Entity(tableName = "feedback_fts")
data class FeedbackFts(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    val title: String,
    val content: String
)

/**
 * 错题全文检索虚表。rowId 与 MistakeRecord.id 一一对应。
 */
@Fts4
@Entity(tableName = "mistake_fts")
data class MistakeFts(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    val stemText: String,
    val analysis: String
)
