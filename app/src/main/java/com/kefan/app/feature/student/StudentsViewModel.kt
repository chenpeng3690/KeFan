package com.kefan.app.feature.student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.Student
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

data class StudentRowUi(
    val student: Student,
    val mistakeCount: Int,
    val pendingMistakeCount: Int
)

@HiltViewModel
class StudentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: StudentRepository,
    mistakeRecordDao: MistakeRecordDao
) : ViewModel() {

    private val _sortMode = MutableStateFlow(
        savedStateHandle.get<String>(KfRoutes.ARG_SORT)
            ?.takeIf {
                it == KfRoutes.SORT_PENDING ||
                    it == KfRoutes.SORT_NAME ||
                    it == KfRoutes.SORT_RECENT ||
                    it == KfRoutes.SORT_CLASS_TIME
            }
            ?: KfRoutes.SORT_CLASS_TIME
    )
    val sortMode: StateFlow<String> = _sortMode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val students: StateFlow<List<StudentRowUi>> = combine(
        repository.observeAll(),
        mistakeRecordDao.observeAll(),
        _query,
        _sortMode
    ) { list, mistakes, keyword, sortMode ->
        val grouped = mistakes.groupBy { it.studentId }
        val filtered = if (keyword.isBlank()) {
            list
        } else {
            list.filter { student ->
                student.name.contains(keyword, ignoreCase = true) ||
                    student.grade.contains(keyword, ignoreCase = true) ||
                    student.subject.contains(keyword, ignoreCase = true) ||
                    student.school.contains(keyword, ignoreCase = true)
            }
        }
        val rows = filtered.map { student ->
            val own = grouped[student.id].orEmpty()
            StudentRowUi(
                student = student,
                mistakeCount = own.size,
                pendingMistakeCount = own.count { it.reviewStatus == MistakeRecord.REVIEW_TODO }
            )
        }
        when (sortMode) {
            KfRoutes.SORT_PENDING -> rows.sortedWith(
                compareByDescending<StudentRowUi> { it.pendingMistakeCount }
                    .thenByDescending { it.student.lastFeedbackAt ?: 0L }
                    .thenByDescending { it.student.createdAt }
            )

            KfRoutes.SORT_NAME -> {
                val collator = Collator.getInstance(Locale.CHINA)
                rows.sortedWith { left, right ->
                    collator.compare(left.student.name, right.student.name)
                }
            }

            KfRoutes.SORT_RECENT -> rows.sortedWith(
                compareByDescending<StudentRowUi> { it.student.lastFeedbackAt ?: 0L }
                    .thenByDescending { it.student.createdAt }
            )

            else -> rows.sortedWith(
                compareBy<StudentRowUi> { classTimeMinutes(it.student.classTime) }
                    .thenBy { it.student.name }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun setSortMode(mode: String) {
        if (mode == KfRoutes.SORT_RECENT ||
            mode == KfRoutes.SORT_PENDING ||
            mode == KfRoutes.SORT_NAME ||
            mode == KfRoutes.SORT_CLASS_TIME
        ) {
            _sortMode.value = mode
        }
    }
}

/** 上课时间转分钟；未设置排最后。 */
private fun classTimeMinutes(raw: String): Int {
    val parts = raw.trim().replace("：", ":").split(":")
    if (parts.size < 2) return Int.MAX_VALUE
    val hour = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
    val minute = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
    return hour * 60 + minute
}
