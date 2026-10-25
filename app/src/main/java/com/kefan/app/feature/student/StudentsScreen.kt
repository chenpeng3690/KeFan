package com.kefan.app.feature.student

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfAddIconButton
import com.kefan.app.core.design.KfAvatar
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChevron
import com.kefan.app.core.design.KfChip
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfEmptyState
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfSearchField
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.util.daysSince
import com.kefan.app.core.util.relativeDayText
import com.kefan.app.data.local.entity.Student
import com.kefan.app.navigation.KfRoutes

@Composable
fun StudentsScreen(
    onAddStudent: () -> Unit,
    onOpenStudent: (Long) -> Unit,
    onOpenStudentMistakes: (Long) -> Unit,
    viewModel: StudentsViewModel = hiltViewModel()
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        KfPageHeader(
            title = "学生",
            actions = {
                KfAddIconButton(onClick = onAddStudent)
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            KfSearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = "搜姓名、年级、科目"
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KfChip(
                    text = "上课时间",
                    selected = sortMode == KfRoutes.SORT_CLASS_TIME,
                    onClick = { viewModel.setSortMode(KfRoutes.SORT_CLASS_TIME) },
                    modifier = Modifier.weight(1f)
                )
                KfChip(
                    text = "最近反馈",
                    selected = sortMode == KfRoutes.SORT_RECENT,
                    onClick = { viewModel.setSortMode(KfRoutes.SORT_RECENT) },
                    modifier = Modifier.weight(1f)
                )
                KfChip(
                    text = "待复习",
                    selected = sortMode == KfRoutes.SORT_PENDING,
                    onClick = { viewModel.setSortMode(KfRoutes.SORT_PENDING) },
                    modifier = Modifier.weight(1f)
                )
                KfChip(
                    text = "姓名",
                    selected = sortMode == KfRoutes.SORT_NAME,
                    onClick = { viewModel.setSortMode(KfRoutes.SORT_NAME) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            if (students.isEmpty()) {
                KfCard(flat = true) {
                    KfEmptyState(
                        title = if (query.isBlank()) "还没有学生" else "没有匹配的学生",
                        hint = if (query.isBlank()) {
                            "点击右上角 + 建一份学生档案\n导入旧反馈后，AI 会学你的写法"
                        } else {
                            "换个关键词试试"
                        },
                        icon = Icons.Outlined.People
                    )
                }
            } else {
                val active = students.filter { it.student.status != Student.STATUS_FINISHED }
                val finished = students.filter { it.student.status == Student.STATUS_FINISHED }

                if (active.isNotEmpty()) {
                    KfSectionLabel(
                        text = "在读 ${active.size}",
                        trailing = {
                            Text(
                                text = when (sortMode) {
                                    KfRoutes.SORT_PENDING -> "按待复习排序"
                                    KfRoutes.SORT_NAME -> "按姓名排序"
                                    KfRoutes.SORT_RECENT -> "按最近反馈排序"
                                    else -> "按上课时间排序"
                                },
                                fontSize = 10.5.sp,
                                color = KfMuted
                            )
                        }
                    )
                    Spacer(Modifier.height(7.dp))
                    StudentGroupCard(
                        students = active,
                        finished = false,
                        onOpenStudent = onOpenStudent,
                        onOpenMistakes = onOpenStudentMistakes
                    )
                }

                if (finished.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    KfSectionLabel(text = "结课 ${finished.size}")
                    Spacer(Modifier.height(7.dp))
                    Column(modifier = Modifier.alpha(0.6f)) {
                        StudentGroupCard(
                            students = finished,
                            finished = true,
                            onOpenStudent = onOpenStudent,
                            onOpenMistakes = onOpenStudentMistakes
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "点学生进入专辑",
                fontSize = 10.5.sp,
                color = KfMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StudentGroupCard(
    students: List<StudentRowUi>,
    finished: Boolean,
    onOpenStudent: (Long) -> Unit,
    onOpenMistakes: (Long) -> Unit
) {
    KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
        Column {
            students.forEachIndexed { index, row ->
                if (index > 0) {
                    KfDivider()
                }
                StudentRow(
                    row = row,
                    finished = finished,
                    onClick = { onOpenStudent(row.student.id) },
                    onOpenMistakes = { onOpenMistakes(row.student.id) }
                )
            }
        }
    }
}

@Composable
private fun StudentRow(
    row: StudentRowUi,
    finished: Boolean,
    onClick: () -> Unit,
    onOpenMistakes: () -> Unit
) {
    val student = row.student
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KfAvatar(name = student.name)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = student.name,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KfInk
            )
            Text(
                text = studentMeta(student),
                fontSize = 10.5.sp,
                color = KfMuted,
                maxLines = 1
            )
        }
        val lastFeedbackAt = student.lastFeedbackAt
        val warn = lastFeedbackAt != null && !finished && daysSince(lastFeedbackAt) > 7
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (finished) {
                    "已结课"
                } else {
                    relativeDayText(lastFeedbackAt, activeStudent = true)
                },
                fontSize = 10.5.sp,
                fontWeight = if (warn) FontWeight.SemiBold else FontWeight.Normal,
                color = if (warn) Color(0xFFC2410C) else KfMuted
            )
            if (row.mistakeCount > 0) {
                Spacer(Modifier.height(4.dp))
                KfTag(
                    text = "错题 ${row.mistakeCount}",
                    type = KfTagType.AMBER,
                    modifier = Modifier.clickable(onClick = onOpenMistakes)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        KfChevron()
    }
}

private fun studentMeta(student: Student): String {
    val parts = mutableListOf<String>()
    if (student.grade.isNotBlank()) parts.add(student.grade)
    if (student.subject.isNotBlank()) parts.add(student.subject)
    parts.add("${student.defaultLessons} 课时")
    return parts.joinToString(" · ")
}
