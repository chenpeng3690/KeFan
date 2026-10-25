package com.kefan.app.feature.feedback

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfDropdownHeader
import com.kefan.app.core.design.KfDropdownItem
import com.kefan.app.core.design.KfDropdownMenu
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfGhostLine
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfInk2
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfThumbnail
import com.kefan.app.core.design.KfFlat
import com.kefan.app.core.util.formatDate
import com.kefan.app.data.quality.WeChatFormatter
import java.io.File
import java.util.Locale

private sealed interface PhotoCell {
    data class Photo(val item: ComposeImageItem, val index: Int) : PhotoCell
    data object Add : PhotoCell
}

@Composable
fun FeedbackComposeScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenWritingStyle: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: FeedbackComposeViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showLessonMenu by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        viewModel.addImages(uris)
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraFile?.let { file ->
                viewModel.addImages(listOf(Uri.fromFile(file)))
            }
        }
        pendingCameraFile = null
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        takePicture.launch(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ComposeEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }
                is ComposeEvent.Saved -> onSaved(event.feedbackId)
            }
        }
    }

    if (showDatePicker) {
        ComposeDatePickerDialog(
            initialMillis = ui.classDate,
            onDismiss = { showDatePicker = false },
            onPick = { millis ->
                viewModel.updateClassDate(millis)
                showDatePicker = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = if (ui.resultMode) {
                ui.generatedTitle.ifBlank { "生成结果" }
            } else {
                "新建反馈"
            },
            onBack = onBack
        )

        when {
            ui.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
                }
            }

            ui.student == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ui.error ?: "学生档案不存在",
                        fontSize = 12.5.sp,
                        color = KfMuted
                    )
                }
            }

            ui.resultMode -> {
                FeedbackResultContent(
                    ui = ui,
                    onContentChange = viewModel::updateFinalContent,
                    onTaskChange = viewModel::updateTask,
                    onRegenerate = viewModel::regenerate,
                    onSave = viewModel::save,
                    onOpenModels = onOpenModels,
                    onDismissError = viewModel::dismissError,
                    onOpenReview = { showReviewDialog = true },
                    onCopy = {
                        previewText = WeChatFormatter.format(ui.finalContent, ui.keepEmoji)
                    },
                    onShare = {
                        val text = WeChatFormatter.format(ui.finalContent, ui.keepEmoji)
                        shareText(context, ui.generatedTitle.ifBlank { "课后反馈" }, text)
                    }
                )
            }

            else -> {
                FeedbackInputContent(
                    ui = ui,
                    onClassDateClick = { showDatePicker = true },
                    onLessonClick = { showLessonMenu = true },
                    onLessonCount = viewModel::updateLessonCount,
                    showLessonMenu = showLessonMenu,
                    onDismissLessonMenu = { showLessonMenu = false },
                    onKeyPointsChange = viewModel::updateKeyPoints,
                    onRemoveImage = viewModel::removeImage,
                    onCamera = ::launchCamera,
                    onGallery = { pickImages.launch(PickMultipleVisualMediaRequest) },
                    onOpenWritingStyle = onOpenWritingStyle,
                    onOpenModels = onOpenModels,
                    onDismissError = viewModel::dismissError,
                    onGenerate = viewModel::generate,
                    onStop = viewModel::stopGeneration
                )
            }
        }
    }

    if (showReviewDialog) {
        ReviewIssueDialog(
            issues = ui.unresolvedIssues,
            onDismiss = { showReviewDialog = false },
            onDeleteSentence = viewModel::deleteIssueSentence,
            onApplySuggestion = viewModel::applySuggestion,
            onKeep = viewModel::keepIssue
        )
    }

    previewText?.let { text ->
        CopyPreviewDialog(
            text = text,
            onDismiss = { previewText = null },
            onConfirm = {
                copyTextToClipboard(context, text)
                previewText = null
            }
        )
    }
}

private val PickMultipleVisualMediaRequest = androidx.activity.result.PickVisualMediaRequest(
    ActivityResultContracts.PickVisualMedia.ImageOnly
)

@Composable
private fun FeedbackInputContent(
    ui: ComposeUi,
    onClassDateClick: () -> Unit,
    onLessonClick: () -> Unit,
    onLessonCount: (Int) -> Unit,
    showLessonMenu: Boolean,
    onDismissLessonMenu: () -> Unit,
    onKeyPointsChange: (String) -> Unit,
    onRemoveImage: (ComposeImageItem) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onOpenWritingStyle: () -> Unit,
    onOpenModels: () -> Unit,
    onDismissError: () -> Unit,
    onGenerate: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KfChip(
                text = ui.student?.name.orEmpty(),
                outlined = true,
                height = 30.dp
            )
            KfChip(
                text = formatDate(ui.classDate),
                outlined = true,
                height = 30.dp,
                onClick = onClassDateClick
            )
            Box {
                KfChip(
                    text = "${ui.lessonCount} 课时",
                    outlined = true,
                    height = 30.dp,
                    onClick = onLessonClick
                )
                KfDropdownMenu(
                    expanded = showLessonMenu,
                    onDismissRequest = onDismissLessonMenu
                ) {
                    KfDropdownHeader(text = "课时数")
                    (1..6).forEach { count ->
                        KfDropdownItem(
                            text = "$count 课时",
                            selected = ui.lessonCount == count,
                            onClick = {
                                onLessonCount(count)
                                onDismissLessonMenu()
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        KfSectionLabel(
            text = "教案照片",
            trailing = {
                Text(
                    text = "${ui.images.size} / 12",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
            }
        )
        Spacer(Modifier.height(7.dp))

        val cells = buildList<PhotoCell> {
            ui.images.forEachIndexed { index, item ->
                add(PhotoCell.Photo(item, index))
            }
            if (ui.images.size < 12) {
                add(PhotoCell.Add)
            }
        }
        cells.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { cell ->
                    when (cell) {
                        is PhotoCell.Photo -> PhotoCellView(
                            item = cell.item,
                            index = cell.index,
                            onRemove = { onRemoveImage(cell.item) },
                            modifier = Modifier.weight(1f)
                        )
                        PhotoCell.Add -> AddPhotoCell(
                            onCamera = onCamera,
                            onGallery = onGallery,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (ui.processing) {
            KfBanner(
                text = "正在压缩图片 ${ui.processingDone}/${ui.processingTotal}",
                type = KfBannerType.INFO
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        KfSectionLabel(text = "本次要点（可不填）")
        Spacer(Modifier.height(7.dp))
        KfTextField(
            value = ui.keyPoints,
            onValueChange = onKeyPointsChange,
            placeholder = "如：比例应用题，讲到单位换算的时候卡了一下，8 道题错了 3 道",
            singleLine = false,
            minLines = 4,
            maxLines = 8
        )

        Spacer(Modifier.height(12.dp))
        KfCard(flat = true, padding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本次成绩",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KfInk
                    )
                    Text(
                        text = ui.latestScoreText ?: "未记录 · 今天录过就不用再填",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                }
                if (ui.latestScoreText != null) {
                    KfTag("已带入", KfTagType.GREEN)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        KfBanner(
            text = "使用：${ui.templateLabel}",
            type = if (ui.templateLabel.startsWith("临时")) {
                KfBannerType.WARNING
            } else {
                KfBannerType.INFO
            },
            trailing = {
                KfTextButton("查看", onClick = onOpenWritingStyle)
            }
        )

        if (!ui.visionSupported && ui.images.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            KfBanner(
                text = "当前模型不支持识图，本次只会使用文字要点。建议在设置里换成支持视觉的模型。",
                type = KfBannerType.WARNING,
                trailing = {
                    KfTextButton("换模型", onClick = onOpenModels)
                }
            )
        }

        if (!ui.hasModel) {
            Spacer(Modifier.height(8.dp))
            KfBanner(
                text = "还没有配置模型，生成前需要先填 Base URL、Key 和模型名。",
                type = KfBannerType.ERROR,
                trailing = {
                    KfTextButton("去配置", onClick = onOpenModels)
                }
            )
        }

        val error = ui.error
        if (error != null && !ui.generating) {
            Spacer(Modifier.height(8.dp))
            KfBanner(
                text = error,
                type = KfBannerType.ERROR,
                trailing = {
                    if (ui.needsModel) {
                        KfTextButton("去配置", onClick = onOpenModels)
                    } else {
                        KfTextButton("关闭", onClick = onDismissError, color = KfMuted)
                    }
                }
            )
        }

        if (ui.generating) {
            Spacer(Modifier.height(10.dp))
            KfBanner(
                text = "正在生成…",
                type = KfBannerType.INFO,
                trailing = {
                    KfTextButton("停止", onClick = onStop, color = KfDanger)
                }
            )
            Spacer(Modifier.height(8.dp))
            KfCard(flat = true) {
                Text(
                    text = ui.rawAi.ifBlank { "正在读取教案照片…" },
                    fontSize = 11.5.sp,
                    lineHeight = 20.sp,
                    color = KfInk2
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        KfPrimaryButton(
            text = if (ui.generating) "生成中…" else "生成反馈",
            onClick = onGenerate,
            enabled = ui.hasModel &&
                !ui.generating &&
                !ui.processing &&
                (ui.images.isNotEmpty() || ui.keyPoints.isNotBlank()),
            loading = ui.generating,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PhotoCellView(
    item: ComposeImageItem,
    index: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f / 1.24f)
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, KfGhostLine, RoundedCornerShape(7.dp))
    ) {
        KfThumbnail(uri = item.uri, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .background(Color(0x99000000), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 8.sp,
                color = Color.White
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp)
                .background(Color(0x99000000), CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "删除照片",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun AddPhotoCell(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(1f / 1.24f)
            .clip(RoundedCornerShape(7.dp))
            .background(KfFlat)
            .border(1.dp, KfGhostLine, RoundedCornerShape(7.dp))
            .clickable { menuExpanded = true }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = Color(0xFFB9C1CC),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(text = "添加", fontSize = 9.5.sp, color = KfMuted)
        }
        KfDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            KfDropdownHeader(text = "添加教案照片")
            KfDropdownItem(
                text = "拍照",
                supporting = "用系统相机拍一页教案",
                onClick = {
                    menuExpanded = false
                    onCamera()
                }
            )
            KfDropdownItem(
                text = "从相册选择",
                supporting = "一次最多 12 张",
                onClick = {
                    menuExpanded = false
                    onGallery()
                }
            )
        }
    }
}

@Composable
private fun FeedbackResultContent(
    ui: ComposeUi,
    onContentChange: (String) -> Unit,
    onTaskChange: (String) -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onOpenModels: () -> Unit,
    onDismissError: () -> Unit,
    onOpenReview: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        KfBanner(
            text = "生成完成 · 请核对日期与知识点，改好再保存。",
            type = KfBannerType.INFO
        )

        Spacer(Modifier.height(8.dp))
        ReviewStatusBar(
            issues = ui.unresolvedIssues,
            onOpen = onOpenReview
        )

        if (ui.missingSections.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            KfBanner(
                text = "AI 未按模板输出：${ui.missingSections.joinToString("、")}，可手动补上。",
                type = KfBannerType.WARNING
            )
        }

        Spacer(Modifier.height(10.dp))
        KfCard(padding = PaddingValues(13.dp)) {
            BasicTextField(
                value = ui.finalContent,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    fontSize = 12.5.sp,
                    lineHeight = 23.sp,
                    color = Color(0xFF1F2530)
                ),
                visualTransformation = remember(ui.reviewIssues, ui.dismissedIssueKeys) {
                    ReviewHighlightTransformation(ui.unresolvedIssues)
                },
                cursorBrush = SolidColor(KfPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (ui.strictBlocked) {
            Spacer(Modifier.height(8.dp))
            KfBanner(
                text = "严格模式：还有 ${ui.unresolvedIssues.size} 处待处理，处理完才能复制或分享。",
                type = KfBannerType.WARNING
            )
        }

        Spacer(Modifier.height(10.dp))
        KfCard(flat = true, padding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "【课后任务】",
                    fontSize = 11.5.sp,
                    color = KfMuted
                )
                BasicTextField(
                    value = ui.taskText,
                    onValueChange = onTaskChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 12.5.sp,
                        color = KfInk,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(KfPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                Text(text = "可改", fontSize = 10.sp, color = KfMuted)
            }
        }

        val error = ui.error
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            KfBanner(
                text = error,
                type = KfBannerType.ERROR,
                trailing = {
                    if (ui.needsModel) {
                        KfTextButton("去配置", onClick = onOpenModels)
                    } else {
                        KfTextButton("关闭", onClick = onDismissError, color = KfMuted)
                    }
                }
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KfPrimaryButton(
                text = "复制",
                onClick = onCopy,
                enabled = ui.finalContent.isNotBlank() && !ui.strictBlocked,
                modifier = Modifier.weight(1f),
                height = 40.dp
            )
            KfGhostButton(
                text = "分享",
                onClick = onShare,
                enabled = ui.finalContent.isNotBlank() && !ui.strictBlocked,
                modifier = Modifier.weight(1f),
                height = 40.dp
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KfGhostButton(
                text = "重新生成",
                onClick = onRegenerate,
                modifier = Modifier.weight(1f),
                height = 40.dp
            )
            KfPrimaryButton(
                text = "保存",
                onClick = onSave,
                enabled = !ui.saving && ui.finalContent.isNotBlank(),
                loading = ui.saving,
                modifier = Modifier.weight(1f),
                height = 40.dp
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeDatePickerDialog(
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
