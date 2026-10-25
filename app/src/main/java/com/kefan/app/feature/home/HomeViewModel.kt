package com.kefan.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.dao.FeedbackDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.Student
import com.kefan.app.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class PendingMistakeSummary(
    val total: Int = 0,
    val studentCount: Int = 0,
    val topStudents: List<Pair<String, Int>> = emptyList()
)

data class TodayStudentUi(
    val student: Student,
    val writtenToday: Boolean
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    studentRepository: StudentRepository,
    feedbackDao: FeedbackDao,
    mistakeRecordDao: MistakeRecordDao
) : ViewModel() {

    private val today = LocalDate.now()
    private val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val todayEnd = today.plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    private val todayWeekday = today.dayOfWeek.value

    val recentFeedbacks: StateFlow<List<Feedback>> = feedbackDao.observeRecent(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayStudents: StateFlow<List<TodayStudentUi>> = combine(
        studentRepository.observeAll(),
        feedbackDao.observeInRange(todayStart, todayEnd)
    ) { students, todaysFeedbacks ->
        val written = todaysFeedbacks.map { it.studentId }.toSet()
        students
            .filter {
                it.status == Student.STATUS_ACTIVE && it.classDay == todayWeekday
            }
            .map { TodayStudentUi(student = it, writtenToday = it.id in written) }
            .sortedWith(
                compareBy<TodayStudentUi>(
                    { classTimeMinutes(it.student.classTime) },
                    { it.student.name }
                )
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val studentCount: StateFlow<Int> = studentRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pendingMistakes: StateFlow<PendingMistakeSummary> = combine(
        studentRepository.observeAll(),
        mistakeRecordDao.observeAll()
    ) { students, mistakes ->
        val pending = mistakes.filter { it.reviewStatus == MistakeRecord.REVIEW_TODO }
        val names = students.associate { it.id to it.name }
        val grouped = pending.groupBy { it.studentId }
        PendingMistakeSummary(
            total = pending.size,
            studentCount = grouped.size,
            topStudents = grouped.entries
                .sortedByDescending { it.value.size }
                .mapNotNull { (id, list) -> names[id]?.let { name -> name to list.size } }
                .take(2)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PendingMistakeSummary()
    )
}

/** 上课时间转分钟；未设置排最后。 */
private fun classTimeMinutes(raw: String): Int {
    val parts = raw.trim().replace("：", ":").split(":")
    if (parts.size < 2) return Int.MAX_VALUE
    val hour = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
    val minute = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
    return hour * 60 + minute
}
