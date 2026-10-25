package com.kefan.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun todayMillis(): Long = System.currentTimeMillis()

/** 2026-10-21 */
fun formatDate(millis: Long): String =
    millis.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA))

/** 10-21 */
fun formatDateShort(millis: Long): String =
    millis.toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA))

/** 10月21日 */
fun formatMonthDay(millis: Long): String =
    millis.toLocalDate().format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))

/** 2026年10月22日 */
fun formatChineseDate(millis: Long): String =
    millis.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))

/** 10月22日 周四 */
fun todayHeader(): String {
    val date = LocalDate.now()
    val week = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
    return "${date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))} $week"
}

/** 距今天数（过去为正） */
fun daysSince(millis: Long): Long =
    java.time.temporal.ChronoUnit.DAYS.between(millis.toLocalDate(), LocalDate.now())

/** 今天 / 1 天前 / 9 天未写（isActiveStudent 为 true 时超过 7 天换成“N 天未写”） */
fun relativeDayText(millis: Long?, activeStudent: Boolean = false): String {
    if (millis == null) return "还没有反馈"
    val days = daysSince(millis)
    return when {
        days <= 0L -> "今天"
        activeStudent && days > 7L -> "$days 天未写"
        else -> "$days 天前"
    }
}
