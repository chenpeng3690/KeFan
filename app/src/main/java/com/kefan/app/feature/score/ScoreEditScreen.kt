package com.kefan.app.feature.score

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSettingRow
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.util.formatDate

@Composable
fun ScoreEditScreen(
    onBack: () -> Unit,
    viewModel: ScoreEditViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ScoreEditEvent.Saved, ScoreEditEvent.Deleted -> onBack()
                is ScoreEditEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showDatePicker) {
        ScoreDatePickerDialog(
            initialMillis = form.date,
            onDismiss = { showDatePicker = false },
            onPick = { millis ->
                viewModel.updateForm { it.copy(date = millis) }
                showDatePicker = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除这条成绩？", fontSize = 16.sp) },
            text = {
                Text(
                    text = "删除后不可恢复，反馈里的成绩引用也不会再带上它。",
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF3F4753)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("删除", color = Color(0xFFDC2626)) }
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
            title = if (ui.isEdit) "编辑成绩" else "录入成绩",
            onBack = onBack,
            actions = {
                if (ui.isEdit) {
                    KfTextButton(
                        text = "删除",
                        onClick = { showDeleteConfirm = true },
                        color = Color(0xFFDC2626)
                    )
                }
            }
        )

        when {
            ui.loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    KfCard(padding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 13.dp,
                        vertical = 2.dp
                    )) {
                        KfSettingRow(
                            title = "考试日期",
                            subtitle = "成绩按这个日期进入趋势",
                            value = formatDate(form.date),
                            showChevron = true,
                            onClick = { showDatePicker = true }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    KfTextField(
                        value = form.examName,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(examName = value) }
                        },
                        label = "考试名称",
                        placeholder = "如：十月月考 / 第三单元测验"
                    )

                    Spacer(Modifier.height(10.dp))
                    KfTextField(
                        value = form.subject,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(subject = value) }
                        },
                        label = "科目",
                        placeholder = "如：数学"
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KfTextField(
                            value = form.fullScore,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(fullScore = value) }
                            },
                            label = "满分",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        KfTextField(
                            value = form.score,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(score = value) }
                            },
                            label = "得分",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KfTextField(
                            value = form.rank,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(rank = value) }
                            },
                            label = "班级排名（可选）",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        KfTextField(
                            value = form.classAvg,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(classAvg = value) }
                            },
                            label = "班级平均（可选）",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    KfTextField(
                        value = form.tags,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(tags = value) }
                        },
                        label = "知识点（可选）",
                        placeholder = "用顿号或逗号分隔，如：比例、单位换算"
                    )

                    Spacer(Modifier.height(10.dp))
                    KfTextField(
                        value = form.note,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(note = value) }
                        },
                        label = "备注（可选）",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                        placeholder = "这次考试的丢分点、需要跟进的地方"
                    )

                    if (ui.error != null) {
                        Spacer(Modifier.height(10.dp))
                        KfBanner(text = ui.error!!, type = KfBannerType.ERROR)
                    }

                    Spacer(Modifier.height(16.dp))
                    KfPrimaryButton(
                        text = if (ui.saving) "保存中…" else "保存成绩",
                        onClick = viewModel::save,
                        enabled = !ui.saving,
                        loading = ui.saving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis ?: initialMillis) }) {
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
