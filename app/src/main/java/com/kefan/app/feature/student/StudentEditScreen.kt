package com.kefan.app.feature.student

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChip
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfDropdownHeader
import com.kefan.app.core.design.KfDropdownItem
import com.kefan.app.core.design.KfDropdownMenu
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfSettingRow
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.util.formatDate
import com.kefan.app.data.local.entity.Student

@Composable
fun StudentEditScreen(
    onBack: () -> Unit,
    viewModel: StudentEditViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDayMenu by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudentEditEvent.Saved -> {
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                }
                StudentEditEvent.Deleted -> {
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    onBack()
                }
                is StudentEditEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showDatePicker) {
        StartDatePickerDialog(
            initialMillis = form.startDate,
            onDismiss = { showDatePicker = false },
            onPick = { millis ->
                viewModel.updateForm { it.copy(startDate = millis) }
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        ClassTimePickerDialog(
            initialTime = form.classTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { value ->
                viewModel.updateForm { it.copy(classTime = value) }
                showTimePicker = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "删除 ${form.name} 的档案？",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
            },
            text = {
                Text(
                    text = "该学生的反馈、照片、成绩、错题、模板和记忆会一并删除，无法恢复。",
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF3F4753)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                    }
                ) {
                    Text("删除", color = KfDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = KfMuted)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = if (ui.isEdit) "编辑档案" else "新建学生",
            onBack = onBack,
            actions = {
                KfTextButton(text = "保存", onClick = viewModel::save)
            }
        )

        if (ui.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                KfSectionLabel(text = "基本信息")
                Spacer(Modifier.height(7.dp))
                KfCard {
                    Column {
                        KfTextField(
                            value = form.name,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(name = value) }
                            },
                            label = "姓名（必填）",
                            placeholder = "如：张三"
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KfTextField(
                                value = form.grade,
                                onValueChange = { value ->
                                    viewModel.updateForm { it.copy(grade = value) }
                                },
                                label = "年级",
                                placeholder = "六年级",
                                modifier = Modifier.weight(1f)
                            )
                            KfTextField(
                                value = form.subject,
                                onValueChange = { value ->
                                    viewModel.updateForm { it.copy(subject = value) }
                                },
                                label = "科目",
                                placeholder = "数学",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        KfTextField(
                            value = form.school,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(school = value) }
                            },
                            label = "学校",
                            placeholder = "可不填"
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "教学信息")
                Spacer(Modifier.height(7.dp))
                KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                    Column {
                        KfTextField(
                            value = form.defaultLessons,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(defaultLessons = value) }
                            },
                            label = "默认课时/次",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                        KfDivider()
                        Box(modifier = Modifier.fillMaxWidth()) {
                            KfSettingRow(
                                title = "上课日",
                                subtitle = "首页会列出今天要上课的学生",
                                value = weekdayText(form.classDay),
                                showChevron = true,
                                onClick = { showDayMenu = true }
                            )
                            KfDropdownMenu(
                                expanded = showDayMenu,
                                onDismissRequest = { showDayMenu = false }
                            ) {
                                KfDropdownHeader(text = "每周上课日")
                                weekdayLabels.forEachIndexed { index, label ->
                                    KfDropdownItem(
                                        text = label,
                                        selected = form.classDay == index,
                                        onClick = {
                                            viewModel.updateForm { it.copy(classDay = index) }
                                            showDayMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        KfDivider()
                        KfSettingRow(
                            title = "上课时间",
                            subtitle = "上下滑动选择任意开始时间",
                            value = form.classTime.ifBlank { "未设置" },
                            showChevron = true,
                            onClick = { showTimePicker = true }
                        )
                        KfDivider()
                        KfSettingRow(
                            title = "开始日期",
                            subtitle = "开始上课的日期",
                            value = formatDate(form.startDate),
                            showChevron = true,
                            onClick = { showDatePicker = true }
                        )
                        KfDivider()
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            KfChip(
                                text = "在读",
                                selected = form.status == Student.STATUS_ACTIVE,
                                onClick = {
                                    viewModel.updateForm { it.copy(status = Student.STATUS_ACTIVE) }
                                }
                            )
                            KfChip(
                                text = "暂停",
                                selected = form.status == Student.STATUS_PAUSED,
                                onClick = {
                                    viewModel.updateForm { it.copy(status = Student.STATUS_PAUSED) }
                                }
                            )
                            KfChip(
                                text = "结课",
                                selected = form.status == Student.STATUS_FINISHED,
                                onClick = {
                                    viewModel.updateForm { it.copy(status = Student.STATUS_FINISHED) }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "学生情况")
                Spacer(Modifier.height(7.dp))
                KfCard {
                    Column {
                        KfTextField(
                            value = form.goal,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(goal = value) }
                            },
                            label = "学习目标",
                            placeholder = "如：期末稳定在 90 分以上",
                            singleLine = false,
                            minLines = 2,
                            maxLines = 3
                        )
                        Spacer(Modifier.height(9.dp))
                        KfTextField(
                            value = form.personality,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(personality = value) }
                            },
                            label = "性格特点",
                            placeholder = "如：吃软不吃硬，需要先肯定再提要求",
                            singleLine = false,
                            minLines = 2,
                            maxLines = 3
                        )
                        Spacer(Modifier.height(9.dp))
                        KfTextField(
                            value = form.parentNote,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(parentNote = value) }
                            },
                            label = "家长诉求 / 沟通注意",
                            placeholder = "如：家长关注计算正确率，不希望反馈里出现排名",
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                KfPrimaryButton(
                    text = "保存档案",
                    onClick = viewModel::save,
                    enabled = !ui.saving,
                    loading = ui.saving,
                    modifier = Modifier.fillMaxWidth()
                )

                if (ui.isEdit) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "删除学生",
                        fontSize = 12.sp,
                        color = KfDanger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteConfirm = true }
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val weekdayLabels = listOf(
    "未设置", "周一", "周二", "周三", "周四", "周五", "周六", "周日"
)

private fun weekdayText(day: Int): String =
    weekdayLabels.getOrElse(day) { "未设置" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPick(state.selectedDateMillis ?: initialMillis)
                }
            ) {
                Text("确定", color = KfPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = KfMuted)
            }
        }
    ) {
        DatePicker(state = state)
    }
}
