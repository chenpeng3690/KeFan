package com.kefan.app.feature.mistake

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChip
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfSwitch
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfThumbnail
import com.kefan.app.core.util.formatDate
import com.kefan.app.core.util.formatDateShort
import com.kefan.app.data.local.entity.ExportJob
import com.kefan.app.data.local.entity.MistakeImage
import com.kefan.app.data.local.entity.MistakeRecord
import java.io.File
import java.util.Locale
import kotlin.math.ceil

@Composable
fun MistakeExportScreen(
    onBack: () -> Unit,
    viewModel: MistakeExportViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val filtered by viewModel.filtered.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        ExportDatePickerDialog(
            initialMillis = ui.options.customStart,
            onDismiss = { showStartPicker = false },
            onPick = { millis ->
                viewModel.updateOptions { it.copy(range = ExportRange.CUSTOM, customStart = millis) }
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        ExportDatePickerDialog(
            initialMillis = ui.options.customEnd,
            onDismiss = { showEndPicker = false },
            onPick = { millis ->
                viewModel.updateOptions { it.copy(range = ExportRange.CUSTOM, customEnd = millis) }
                showEndPicker = false
            }
        )
    }

    val missingAnswers = filtered.count { it.correctAnswer.isBlank() }
    val pages = when {
        filtered.isEmpty() -> 0
        ui.options.includeFigures -> ceil(filtered.size / 2.0).toInt()
        else -> ceil(filtered.size / 4.0).toInt()
    }
    val figureMistakes = filtered.filter { it.hasFigure }
    val currentJob = jobs.firstOrNull { it.id == ui.currentJobId } ?: jobs.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = "导出错题本",
            subtitle = ui.studentName.takeIf { it.isNotBlank() },
            onBack = onBack
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
                Spacer(Modifier.height(6.dp))
                KfSectionLabel(text = "时间范围（必选）")
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ExportRange.entries.forEach { range ->
                        KfChip(
                            text = range.label,
                            selected = ui.options.range == range,
                            onClick = {
                                viewModel.updateOptions { it.copy(range = range) }
                            }
                        )
                    }
                }
                if (ui.options.range == ExportRange.CUSTOM) {
                    Spacer(Modifier.height(7.dp))
                    KfCard(flat = true, padding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 2.dp
                    )) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("开始日期", fontSize = 11.5.sp, color = KfMuted)
                            Spacer(Modifier.weight(1f))
                            KfTextButton(
                                text = formatDate(ui.options.customStart),
                                onClick = { showStartPicker = true },
                                fontSize = 11
                            )
                        }
                        KfDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("结束日期", fontSize = 11.5.sp, color = KfMuted)
                            Spacer(Modifier.weight(1f))
                            KfTextButton(
                                text = formatDate(ui.options.customEnd),
                                onClick = { showEndPicker = true },
                                fontSize = 11
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                KfCard(flat = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "命中 ${filtered.size} 题",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KfInk
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "预计 $pages 页 · 约 ${formatMb(filtered.size * 0.03)}",
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        KfChip(
                            text = "全部",
                            selected = ui.options.status == "ALL",
                            onClick = { viewModel.updateOptions { it.copy(status = "ALL") } }
                        )
                        mistakeStatusOptions.forEach { (value, label) ->
                            KfChip(
                                text = label,
                                selected = ui.options.status == value,
                                onClick = {
                                    viewModel.updateOptions { it.copy(status = value) }
                                }
                            )
                        }
                    }
                }
                if (filtered.size > 60 || pages > 20) {
                    Spacer(Modifier.height(8.dp))
                    KfBanner(
                        text = "题量较大，导出后不易翻阅，建议缩小时间范围或按知识点分批。",
                        type = KfBannerType.WARNING
                    )
                }

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "排版")
                Spacer(Modifier.height(7.dp))
                KfCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "空白重做版",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KfInk
                            )
                            Text(
                                text = "题号 + 题干 + 手写留白，纯文字题不插图",
                                fontSize = 10.5.sp,
                                color = KfMuted
                            )
                        }
                        KfTag("固定")
                    }
                    KfDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("含图题插入图片", fontSize = 12.sp, color = KfInk)
                            Text(
                                text = "共 ${figureMistakes.size} 题含图，直接使用已保存的配图",
                                fontSize = 10.5.sp,
                                color = KfMuted
                            )
                        }
                        KfSwitch(
                            checked = ui.options.includeFigures,
                            onCheckedChange = { value ->
                                viewModel.updateOptions { it.copy(includeFigures = value) }
                            }
                        )
                    }
                    KfDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("一页一题", fontSize = 12.sp, color = KfInk)
                            Text(
                                text = "默认连续排版，省纸",
                                fontSize = 10.5.sp,
                                color = KfMuted
                            )
                        }
                        KfSwitch(
                            checked = ui.options.onePerPage,
                            onCheckedChange = { value ->
                                viewModel.updateOptions { it.copy(onePerPage = value) }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "答案与解析")
                Spacer(Modifier.height(7.dp))
                KfCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        KfChip(
                            text = "不包含",
                            selected = ui.options.answerMode == ExportJob.ANSWER_NONE,
                            onClick = {
                                viewModel.updateOptions {
                                    it.copy(answerMode = ExportJob.ANSWER_NONE)
                                }
                            }
                        )
                        KfChip(
                            text = "生成缺失的",
                            selected = ui.options.answerMode == ExportJob.ANSWER_GENERATE,
                            onClick = {
                                viewModel.updateOptions {
                                    it.copy(answerMode = ExportJob.ANSWER_GENERATE)
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (ui.options.answerMode == ExportJob.ANSWER_GENERATE) {
                            if (missingAnswers == 0) {
                                "所有题都有答案，会直接生成「_含答案」版本。"
                            } else {
                                "有 $missingAnswers 题缺答案，主文档先生成，解析在后台补全。"
                            }
                        } else {
                            "只导出题目与手写留白，不附答案。"
                        },
                        fontSize = 10.5.sp,
                        lineHeight = 17.sp,
                        color = KfMuted
                    )
                }

                val message = ui.message
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    KfBanner(
                        text = message,
                        type = KfBannerType.OK,
                        trailing = {
                            KfTextButton(
                                text = "知道了",
                                onClick = viewModel::dismissMessage,
                                color = KfMuted
                            )
                        }
                    )
                }
                val error = ui.error
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    KfBanner(
                        text = error,
                        type = KfBannerType.ERROR,
                        trailing = {
                            KfTextButton(
                                text = "关闭",
                                onClick = viewModel::dismissMessage,
                                color = KfMuted
                            )
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))
                KfPrimaryButton(
                    text = if (ui.generating) "正在生成…" else "生成 Word",
                    onClick = viewModel::generate,
                    enabled = !ui.generating && filtered.isNotEmpty(),
                    loading = ui.generating,
                    modifier = Modifier.fillMaxWidth()
                )

                if (currentJob != null) {
                    Spacer(Modifier.height(12.dp))
                    JobCard(job = currentJob, onShare = { shareDocx(context, it) })
                }

                if (jobs.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    KfSectionLabel(text = "导出记录")
                    Spacer(Modifier.height(7.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        jobs.take(5).forEach { job ->
                            KfCard(flat = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${formatDateShort(job.createdAt)} · ${job.totalCount} 题",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = KfInk
                                        )
                                        Text(
                                            text = jobStatusLabel(job.status),
                                            fontSize = 10.sp,
                                            color = KfMuted
                                        )
                                    }
                                    val path = job.docxWithAnswerPath ?: job.docxPath
                                    if (path != null) {
                                        KfTextButton(
                                            text = "分享",
                                            onClick = { shareDocx(context, path) },
                                            fontSize = 11
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun JobCard(job: ExportJob, onShare: (String) -> Unit) {
    KfCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("最近一次导出", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = KfInk)
                Text(
                    text = "${formatDateShort(job.createdAt)} · ${job.totalCount} 题",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
            }
            KfTag(
                text = jobStatusLabel(job.status),
                type = jobStatusTagType(job.status)
            )
        }
        if (job.status == ExportJob.STATUS_ANSWERING) {
            Spacer(Modifier.height(8.dp))
            val total = job.totalCount.coerceAtLeast(1)
            val progress = (job.answerDoneCount.toFloat() / total).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = KfPrimary,
                trackColor = Color(0xFFE9ECF1)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "正在补解析 ${job.answerDoneCount}/${job.totalCount}，主文档已可分享",
                fontSize = 10.5.sp,
                color = KfMuted
            )
        }
        if (job.status == ExportJob.STATUS_FAILED) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "导出失败，可以调整范围后重新生成。",
                fontSize = 10.5.sp,
                color = Color(0xFFC2410C)
            )
        }
        val mainPath = job.docxPath
        val answerPath = job.docxWithAnswerPath
        if (mainPath != null || answerPath != null) {
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mainPath != null) {
                    KfPrimaryButton(
                        text = "分享题目版",
                        onClick = { onShare(mainPath) },
                        modifier = Modifier.weight(1f),
                        height = 34.dp,
                        fontSize = 12
                    )
                }
                if (answerPath != null) {
                    KfGhostButton(
                        text = "分享含答案版",
                        onClick = { onShare(answerPath) },
                        modifier = Modifier.weight(1f),
                        height = 34.dp,
                        fontSize = 12
                    )
                }
            }
        }
    }
}

private fun jobStatusLabel(status: String): String = when (status) {
    ExportJob.STATUS_BUILDING -> "生成中"
    ExportJob.STATUS_ANSWERING -> "补解析中"
    ExportJob.STATUS_DONE -> "已完成"
    ExportJob.STATUS_FAILED -> "失败"
    ExportJob.STATUS_CANCELED -> "已取消"
    else -> "准备中"
}

private fun jobStatusTagType(status: String): KfTagType = when (status) {
    ExportJob.STATUS_DONE -> KfTagType.GREEN
    ExportJob.STATUS_FAILED -> KfTagType.AMBER
    ExportJob.STATUS_ANSWERING, ExportJob.STATUS_BUILDING -> KfTagType.BLUE
    else -> KfTagType.NEUTRAL
}

private fun formatMb(value: Double): String =
    String.format(Locale.CHINA, "%.1f MB", value.coerceAtLeast(0.1))

private fun shareDocx(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在，请重新生成", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享 Word"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
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
