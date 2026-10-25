package com.kefan.app.feature.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfAvatar
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChevron
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfEmptyState
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.util.formatDateShort
import com.kefan.app.core.util.todayHeader
import com.kefan.app.data.local.entity.Student

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenStudents: () -> Unit = {},
    onOpenPendingMistakes: () -> Unit = {},
    onOpenStudent: (Long) -> Unit = {},
    onNewFeedback: (Long) -> Unit = {},
    onOpenFeedback: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recent by viewModel.recentFeedbacks.collectAsStateWithLifecycle()
    val todayStudents by viewModel.todayStudents.collectAsStateWithLifecycle()
    val pendingMistakes by viewModel.pendingMistakes.collectAsStateWithLifecycle()
    val todayCount = todayStudents.count { it.writtenToday }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "课返",
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = KfInk
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "设置",
                    tint = KfMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Text(
            text = "${todayHeader()} · 今天已写 $todayCount 人",
            fontSize = 11.5.sp,
            color = KfMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )

        KfSectionLabel(
            text = "今天要上课",
            trailing = {
                Text(
                    text = "${todayStudents.size} 人",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
            }
        )
        Spacer(Modifier.height(7.dp))

        if (todayStudents.isEmpty()) {
            KfCard(flat = true) {
                KfEmptyState(
                    title = "今天没有排课",
                    hint = "在学生档案里设置上课日和上课时间\n首页会自动按时间列出今天的学生",
                    icon = Icons.Outlined.People
                )
                KfGhostButton(
                    text = "去学生页",
                    onClick = onOpenStudents,
                    height = 32.dp
                )
            }
        } else {
            KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                Column {
                    todayStudents.forEachIndexed { index, item ->
                        if (index > 0) KfDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenStudent(item.student.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KfAvatar(name = item.student.name)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.student.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KfInk
                                )
                                Text(
                                    text = todayStudentMeta(item.student),
                                    fontSize = 10.5.sp,
                                    color = KfMuted,
                                    maxLines = 1
                                )
                            }
                            if (item.writtenToday) {
                                KfTag("已写", KfTagType.GREEN)
                            } else {
                                KfGhostButton(
                                    text = "新建反馈",
                                    onClick = { onNewFeedback(item.student.id) },
                                    height = 30.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pendingMistakes.total > 0) {
            Spacer(Modifier.height(14.dp))
            KfCard(onClick = onOpenPendingMistakes) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "待复习错题",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KfInk
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (pendingMistakes.topStudents.isEmpty()) {
                                "共 ${pendingMistakes.studentCount} 个学生"
                            } else {
                                pendingMistakes.topStudents.joinToString(" · ") { (name, count) ->
                                    "$name $count"
                                }
                            },
                            fontSize = 10.5.sp,
                            color = KfMuted,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    KfTag(
                        text = "${pendingMistakes.total} 道",
                        type = KfTagType.AMBER
                    )
                    Spacer(Modifier.width(6.dp))
                    KfChevron()
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        KfSectionLabel(
            text = "最近反馈",
            trailing = {
                Text(
                    text = "${recent.size} 条",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
            }
        )
        Spacer(Modifier.height(7.dp))

        if (recent.isEmpty()) {
            KfCard(flat = true) {
                KfEmptyState(
                    title = "还没有反馈记录",
                    hint = "生成的反馈会自动归档到这里",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { feedback ->
                    KfCard(flat = true, padding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    ), onClick = { onOpenFeedback(feedback.id) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feedback.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KfInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${formatDateShort(feedback.classDate)} · " +
                                        feedback.summary.ifBlank { "已归档" },
                                    fontSize = 10.5.sp,
                                    color = KfMuted
                                )
                            }
                            KfTag("已保存", KfTagType.GREEN)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

private fun todayStudentMeta(student: Student): String {
    val parts = mutableListOf<String>()
    if (student.grade.isNotBlank()) parts.add(student.grade)
    if (student.subject.isNotBlank()) parts.add(student.subject)
    parts.add(student.classTime.ifBlank { "时间未设置" })
    return parts.joinToString(" · ")
}
