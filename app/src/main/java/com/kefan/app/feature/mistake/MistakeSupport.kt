package com.kefan.app.feature.mistake

import com.kefan.app.core.design.KfTagType
import com.kefan.app.data.local.entity.MistakeRecord

val mistakeErrorTypes = listOf(
    "概念不清",
    "审题失误",
    "计算失误",
    "方法不会",
    "粗心遗漏"
)

val mistakeSourceOptions = listOf(
    MistakeRecord.SOURCE_CLASS to "课堂",
    MistakeRecord.SOURCE_HOMEWORK to "作业",
    MistakeRecord.SOURCE_EXAM to "考试",
    MistakeRecord.SOURCE_OTHER to "其他"
)

val mistakeStatusOptions = listOf(
    MistakeRecord.REVIEW_TODO to "待复习",
    MistakeRecord.REVIEW_REVIEWING to "复习中",
    MistakeRecord.REVIEW_MASTERED to "已掌握"
)

fun mistakeStatusLabel(status: String): String = when (status) {
    MistakeRecord.REVIEW_MASTERED -> "已掌握"
    MistakeRecord.REVIEW_REVIEWING -> "复习中"
    else -> "待复习"
}

fun mistakeStatusTagType(status: String): KfTagType = when (status) {
    MistakeRecord.REVIEW_MASTERED -> KfTagType.GREEN
    MistakeRecord.REVIEW_REVIEWING -> KfTagType.BLUE
    else -> KfTagType.AMBER
}

fun mistakeSourceLabel(source: String): String =
    mistakeSourceOptions.firstOrNull { it.first == source }?.second ?: "其他"
