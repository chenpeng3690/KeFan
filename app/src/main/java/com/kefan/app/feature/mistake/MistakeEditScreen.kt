package com.kefan.app.feature.mistake

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChip
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfDropdownHeader
import com.kefan.app.core.design.KfDropdownItem
import com.kefan.app.core.design.KfDropdownMenu
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfSettingRow
import com.kefan.app.core.design.KfSwitch
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfThumbnail
import com.kefan.app.core.design.MathText
import com.kefan.app.core.math.MathSyntax
import com.kefan.app.core.util.ImageProcessor
import com.kefan.app.core.util.formatDateShort
import com.kefan.app.data.local.entity.MistakeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun MistakeEditScreen(
    onBack: () -> Unit,
    viewModel: MistakeEditViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val reviewLogs by viewModel.reviewLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showErrorTypeMenu by remember { mutableStateOf(false) }
    var showSourceMenu by remember { mutableStateOf(false) }
    var previewUri by remember { mutableStateOf<String?>(null) }
    var cropTarget by remember { mutableStateOf<CropTarget?>(null) }
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
            pendingCameraFile?.let { viewModel.addImages(listOf(Uri.fromFile(it))) }
        }
        pendingCameraFile = null
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "mistake_${System.currentTimeMillis()}.jpg")
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
                is MistakeEditEvent.Saved -> onBack()
                MistakeEditEvent.Deleted -> onBack()
                is MistakeEditEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                is MistakeEditEvent.FigureCropReady -> {
                    cropTarget = CropTarget(
                        index = event.index,
                        kind = CropKind.FIGURE,
                        initialRect = event.initialRect
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除这道错题？", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "题目照片、复习记录会一并删除，无法恢复。",
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF3F4753)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("删除", color = KfDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = KfMuted)
                }
            }
        )
    }

    ui.duplicateOf?.let { duplicate ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = { Text("疑似重复错题", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "已有相似度很高的错题：\n$duplicate\n\n仍然要保存吗？",
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF3F4753)
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.save(force = true) }) {
                    Text("仍然保存", color = KfPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicate) {
                    Text("返回修改", color = KfMuted)
                }
            }
        )
    }

    previewUri?.let { uri ->
        ImagePreviewDialog(uri = Uri.parse(uri), onDismiss = { previewUri = null })
    }

    cropTarget?.let { target ->
        val draft = images.getOrNull(target.index)
        if (draft != null) {
            val source = when (target.kind) {
                CropKind.QUESTION -> draft.originalUri
                CropKind.FIGURE -> draft.croppedUri ?: draft.originalUri
            }
            MistakeCropDialog(
                sourceUri = Uri.parse(source),
                initialRect = target.initialRect,
                title = if (target.kind == CropKind.QUESTION) {
                    "框选题目区域"
                } else {
                    "调整配图区域"
                },
                onDismiss = { cropTarget = null },
                onConfirm = { left, top, width, height ->
                    when (target.kind) {
                        CropKind.QUESTION ->
                            viewModel.applyCrop(target.index, left, top, width, height)

                        CropKind.FIGURE ->
                            viewModel.applyFigureCrop(target.index, left, top, width, height)
                    }
                    cropTarget = null
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = if (ui.isEdit) "错题详情" else "添加错题",
            subtitle = if (ui.studentName.isBlank()) {
                null
            } else {
                "${ui.studentName} · ${if (ui.isEdit) "可编辑" else "手动录入"}"
            },
            onBack = onBack,
            actions = {
                if (ui.isEdit) {
                    KfTextButton(
                        text = "删除",
                        onClick = { showDeleteConfirm = true },
                        color = KfDanger
                    )
                }
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
                val error = ui.error
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    KfBanner(text = error, type = KfBannerType.ERROR)
                }

                Spacer(Modifier.height(6.dp))
                KfSectionLabel(
                    text = "题目照片",
                    trailing = {
                        Text(
                            text = "${images.size}/12",
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
                    }
                )
                Spacer(Modifier.height(7.dp))
                KfCard(flat = true) {
                    if (images.isEmpty()) {
                        Text(
                            text = "可不加图片，直接录纯文字题。\n一页有多题时，选图后点「框选」裁出单题。",
                            fontSize = 10.5.sp,
                            lineHeight = 17.sp,
                            color = KfMuted
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        images.chunked(3).forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEachIndexed { column, draft ->
                                    val index = rowIndex * 3 + column
                                    ImageDraftCard(
                                        modifier = Modifier.weight(1f),
                                        draft = draft,
                                        onPreview = {
                                            previewUri = draft.croppedUri ?: draft.originalUri
                                        },
                                        onCrop = {
                                            cropTarget = CropTarget(
                                                index = index,
                                                kind = CropKind.QUESTION
                                            )
                                        },
                                        onAdjustFigure = {
                                            cropTarget = CropTarget(
                                                index = index,
                                                kind = CropKind.FIGURE,
                                                initialRect = parseCropRect(draft.figureRectJson)
                                            )
                                        },
                                        onRemove = { viewModel.removeImage(index) }
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                        }
                    }
                    val hasCrop = images.any { !it.croppedUri.isNullOrBlank() }
                    KfPrimaryButton(
                        text = if (ui.recognizing) "AI 识别中…" else "AI 识别题干",
                        onClick = viewModel::recognizeQuestion,
                        enabled = hasCrop && !ui.recognizing,
                        loading = ui.recognizing,
                        modifier = Modifier.fillMaxWidth(),
                        height = 36.dp,
                        fontSize = 13
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "先「框题」再识别。带图题会自动框出配图，可点「配图」调整范围。",
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = KfMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KfGhostButton(
                            text = "拍照",
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f),
                            height = 34.dp,
                            fontSize = 12
                        )
                        KfGhostButton(
                            text = "从相册选",
                            onClick = {
                                pickImages.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            height = 34.dp,
                            fontSize = 12
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "题目信息")
                Spacer(Modifier.height(7.dp))
                KfCard {
                    KfTextField(
                        value = form.stemText,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(stemText = value) }
                        },
                        label = "题干",
                        placeholder = "可 OCR 识别后修改，也可以手动输入",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 8
                    )
                    if (MathSyntax.hasMath(form.stemText)) {
                        Spacer(Modifier.height(4.dp))
                        MathText(
                            text = form.stemText,
                            fontSize = 11.sp,
                            color = KfMuted,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    KfTextField(
                        value = form.subject,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(subject = value) }
                        },
                        label = "学科",
                        placeholder = "如：数学"
                    )
                    Spacer(Modifier.height(9.dp))
                    KfTextField(
                        value = form.tags,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(tags = value) }
                        },
                        label = "知识点",
                        placeholder = "多个用、分隔，如：比例、解方程"
                    )
                    Spacer(Modifier.height(9.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        KfSettingRow(
                            title = "错误类型",
                            value = form.errorType.ifBlank { "未分类" },
                            showChevron = true,
                            onClick = { showErrorTypeMenu = true }
                        )
                        KfDropdownMenu(
                            expanded = showErrorTypeMenu,
                            onDismissRequest = { showErrorTypeMenu = false }
                        ) {
                            KfDropdownHeader(text = "选择错误类型")
                            KfDropdownItem(
                                text = "未分类",
                                selected = form.errorType.isBlank(),
                                onClick = {
                                    viewModel.updateForm { it.copy(errorType = "") }
                                    showErrorTypeMenu = false
                                }
                            )
                            mistakeErrorTypes.forEach { type ->
                                KfDropdownItem(
                                    text = type,
                                    selected = form.errorType == type,
                                    onClick = {
                                        viewModel.updateForm { it.copy(errorType = type) }
                                        showErrorTypeMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("难度", fontSize = 11.5.sp, color = KfMuted)
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..5).forEach { level ->
                            KfChip(
                                text = "$level 星",
                                selected = form.difficulty == level,
                                onClick = {
                                    viewModel.updateForm { it.copy(difficulty = level) }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        KfSettingRow(
                            title = "来源",
                            value = mistakeSourceLabel(form.sourceType),
                            showChevron = true,
                            onClick = { showSourceMenu = true }
                        )
                        KfDropdownMenu(
                            expanded = showSourceMenu,
                            onDismissRequest = { showSourceMenu = false }
                        ) {
                            KfDropdownHeader(text = "错题来源")
                            mistakeSourceOptions.forEach { (value, label) ->
                                KfDropdownItem(
                                    text = label,
                                    selected = form.sourceType == value,
                                    onClick = {
                                        viewModel.updateForm { it.copy(sourceType = value) }
                                        showSourceMenu = false
                                    }
                                )
                            }
                        }
                    }
                    KfDivider()
                    KfSettingRow(
                        title = "含图题",
                        subtitle = "几何/统计题，导出 Word 时只插入图形",
                        trailing = {
                            KfSwitch(
                                checked = form.hasFigure,
                                onCheckedChange = { value ->
                                    viewModel.updateForm { it.copy(hasFigure = value) }
                                }
                            )
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "答案与解析")
                Spacer(Modifier.height(7.dp))
                KfCard {
                    KfTextField(
                        value = form.studentAnswer,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(studentAnswer = value) }
                        },
                        label = "学生原答案",
                        placeholder = "可不填",
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(9.dp))
                    KfTextField(
                        value = form.correctAnswer,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(correctAnswer = value) }
                        },
                        label = "正确答案",
                        placeholder = "可留空，导出时可后台生成",
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(9.dp))
                    KfTextField(
                        value = form.analysis,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(analysis = value) }
                        },
                        label = "解析",
                        placeholder = "可留空",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(9.dp))
                    KfTextField(
                        value = form.note,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(note = value) }
                        },
                        label = "备注",
                        placeholder = "如：同类题第 3 次错",
                        singleLine = false,
                        minLines = 2,
                        maxLines = 3
                    )
                }

                if (ui.isEdit) {
                    Spacer(Modifier.height(14.dp))
                    KfSectionLabel(text = "复习")
                    Spacer(Modifier.height(7.dp))
                    KfCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "复习状态",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KfInk
                                )
                                Text(
                                    text = "重做 ${ui.reviewCount} 次 · 连续正确 ${ui.correctStreak} 次",
                                    fontSize = 10.5.sp,
                                    color = KfMuted
                                )
                            }
                            KfTag(
                                text = mistakeStatusLabel(form.reviewStatus),
                                type = mistakeStatusTagType(form.reviewStatus)
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KfPrimaryButton(
                                text = "做对一次",
                                onClick = { viewModel.recordReview(true) },
                                modifier = Modifier.weight(1f),
                                height = 34.dp,
                                fontSize = 12
                            )
                            KfGhostButton(
                                text = "做错一次",
                                onClick = { viewModel.recordReview(false) },
                                modifier = Modifier.weight(1f),
                                height = 34.dp,
                                fontSize = 12
                            )
                        }
                        Text(
                            text = "连续 2 次做对会自动标记为「已掌握」",
                            fontSize = 10.sp,
                            color = KfMuted
                        )
                        if (reviewLogs.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            reviewLogs.take(10).forEachIndexed { index, log ->
                                if (index > 0) KfDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatDateShort(log.date),
                                        fontSize = 10.5.sp,
                                        color = KfMuted
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = if (log.result == "CORRECT") "做对" else "做错",
                                        fontSize = 10.5.sp,
                                        color = if (log.result == "CORRECT") {
                                            Color(0xFF0F7B5F)
                                        } else {
                                            Color(0xFFC2410C)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                KfPrimaryButton(
                    text = "保存错题",
                    onClick = { viewModel.save() },
                    enabled = !ui.saving,
                    loading = ui.saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ImageDraftCard(
    draft: MistakeImageDraft,
    modifier: Modifier = Modifier,
    onPreview: () -> Unit,
    onCrop: () -> Unit,
    onAdjustFigure: () -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(Color(0xFFF2F4F7), RoundedCornerShape(8.dp))
                .clickable(onClick = onPreview)
        ) {
            KfThumbnail(
                uri = Uri.parse(draft.croppedUri ?: draft.originalUri),
                modifier = Modifier.fillMaxSize()
            )
            if (draft.croppedUri != null) {
                Text(
                    text = "已框题",
                    fontSize = 8.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (draft.figureUri != null) {
                Text(
                    text = "有配图",
                    fontSize = 8.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(KfPrimary.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KfTextButton(text = "框题", onClick = onCrop, fontSize = 10)
                if (draft.croppedUri != null || draft.figureUri != null) {
                    KfTextButton(text = "配图", onClick = onAdjustFigure, fontSize = 10)
                }
            }
            KfTextButton(text = "删除", onClick = onRemove, fontSize = 10, color = KfMuted)
        }
    }
}

@Composable
private fun ImagePreviewDialog(uri: Uri, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            KfThumbnail(
                uri = uri,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                maxPx = 2048
            )
            Text(
                text = "点击任意位置关闭",
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )
        }
    }
}

private enum class CropKind { QUESTION, FIGURE }

private data class CropTarget(
    val index: Int,
    val kind: CropKind,
    val initialRect: List<Float> = emptyList()
)

private fun parseCropRect(raw: String?): List<Float> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().toFloat() }
    }.getOrNull()?.takeIf { it.size == 4 }.orEmpty()
}

private data class CropSelection(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

@Composable
fun MistakeCropDialog(
    sourceUri: Uri,
    initialRect: List<Float> = emptyList(),
    title: String = "框选题目区域",
    onDismiss: () -> Unit,
    onConfirm: (Float, Float, Float, Float) -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, sourceUri) {
        value = withContext(Dispatchers.IO) {
            ImageProcessor.loadThumbnail(context, sourceUri, 2400)?.asImageBitmap()
        }
    }

    val initialSelection = remember(initialRect) {
        if (initialRect.size == 4) {
            val left = initialRect[0].coerceIn(0f, 1f)
            val top = initialRect[1].coerceIn(0f, 1f)
            CropSelection(
                left = left,
                top = top,
                width = initialRect[2].coerceIn(0f, 1f - left),
                height = initialRect[3].coerceIn(0f, 1f - top)
            )
        } else {
            CropSelection(0f, 0f, 0f, 0f)
        }
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var selection by remember(initialRect) { mutableStateOf(initialSelection) }
    val currentSelection by rememberUpdatedState(selection)
    var mode by remember { mutableStateOf(CropMode.NEW) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var startSelection by remember { mutableStateOf(initialSelection) }

    val imageRect = remember(bitmap, boxSize) {
        val bmp = bitmap
        if (bmp == null || boxSize.width == 0 || boxSize.height == 0) {
            null
        } else {
            val scale = min(
                boxSize.width.toFloat() / bmp.width,
                boxSize.height.toFloat() / bmp.height
            )
            val width = bmp.width * scale
            val height = bmp.height * scale
            Rect(
                left = (boxSize.width - width) / 2f,
                top = (boxSize.height - height) / 2f,
                right = (boxSize.width + width) / 2f,
                bottom = (boxSize.height + height) / 2f
            )
        }
    }
    val canConfirm = selection.width > 0.02f && selection.height > 0.02f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1218))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KfTextButton(text = "取消", onClick = onDismiss, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                KfTextButton(
                    text = "确认",
                    onClick = {
                        if (canConfirm) {
                            onConfirm(
                                selection.left,
                                selection.top,
                                selection.width,
                                selection.height
                            )
                        }
                    },
                    color = if (canConfirm) KfPrimary else Color(0xFF6B7280)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .onSizeChanged { boxSize = it }
                    .pointerInput(imageRect) {
                        val rect = imageRect ?: return@pointerInput
                        val handleThreshold = 26.dp.toPx() /
                            min(rect.width, rect.height).coerceAtLeast(1f)
                        detectDragGestures(
                            onDragStart = { offset ->
                                val point = fractionOf(offset, rect)
                                val selected = currentSelection
                                val nearLeft = abs(point.x - selected.left) <= handleThreshold
                                val nearRight =
                                    abs(point.x - (selected.left + selected.width)) <= handleThreshold
                                val nearTop = abs(point.y - selected.top) <= handleThreshold
                                val nearBottom =
                                    abs(point.y - (selected.top + selected.height)) <= handleThreshold
                                mode = when {
                                    selected.width < 0.02f || selected.height < 0.02f ->
                                        CropMode.NEW

                                    nearLeft && nearTop -> CropMode.TOP_LEFT
                                    nearRight && nearTop -> CropMode.TOP_RIGHT
                                    nearLeft && nearBottom -> CropMode.BOTTOM_LEFT
                                    nearRight && nearBottom -> CropMode.BOTTOM_RIGHT
                                    point.x in selected.left..(selected.left + selected.width) &&
                                        point.y in selected.top..(selected.top + selected.height) ->
                                        CropMode.MOVE

                                    else -> CropMode.NEW
                                }
                                dragStart = point
                                startSelection = selected
                                if (mode == CropMode.NEW) {
                                    selection = CropSelection(point.x, point.y, 0f, 0f)
                                }
                            },
                            onDrag = { change, _ ->
                                val point = fractionOf(change.position, rect)
                                selection = when (mode) {
                                    CropMode.NEW -> selectionOf(dragStart, point)

                                    CropMode.MOVE -> {
                                        val dx = point.x - dragStart.x
                                        val dy = point.y - dragStart.y
                                        val left = (startSelection.left + dx)
                                            .coerceIn(0f, 1f - startSelection.width)
                                        val top = (startSelection.top + dy)
                                            .coerceIn(0f, 1f - startSelection.height)
                                        startSelection.copy(left = left, top = top)
                                    }

                                    else -> resizeSelection(mode, startSelection, point)
                                }
                            },
                            onDragEnd = {},
                            onDragCancel = {}
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Canvas(modifier = Modifier.matchParentSize()) {
                    val rect = imageRect ?: return@Canvas
                    if (selection.width <= 0f || selection.height <= 0f) return@Canvas
                    val left = rect.left + selection.left * rect.width
                    val top = rect.top + selection.top * rect.height
                    val width = selection.width * rect.width
                    val height = selection.height * rect.height
                    drawRect(
                        color = KfPrimary.copy(alpha = 0.18f),
                        topLeft = Offset(left, top),
                        size = Size(width, height)
                    )
                    drawRect(
                        color = KfPrimary,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    val radius = 7.dp.toPx()
                    val corners = listOf(
                        Offset(left, top),
                        Offset(left + width, top),
                        Offset(left, top + height),
                        Offset(left + width, top + height)
                    )
                    corners.forEach { corner ->
                        drawCircle(color = Color.White, radius = radius, center = corner)
                        drawCircle(
                            color = KfPrimary,
                            radius = radius,
                            center = corner,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                if (bitmap == null) {
                    CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
                }
            }

            Text(
                text = "拖动方框可移动，拖四角可缩放；点空白处重新框选。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = Color(0xFF9AA2AE),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
    }
}

private enum class CropMode {
    NEW,
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

private fun resizeSelection(
    mode: CropMode,
    start: CropSelection,
    point: Offset
): CropSelection {
    val minSize = 0.03f
    var left = start.left
    var top = start.top
    var right = start.left + start.width
    var bottom = start.top + start.height

    when (mode) {
        CropMode.TOP_LEFT -> {
            left = point.x.coerceIn(0f, (right - minSize).coerceAtLeast(0f))
            top = point.y.coerceIn(0f, (bottom - minSize).coerceAtLeast(0f))
        }

        CropMode.TOP_RIGHT -> {
            right = point.x.coerceIn((left + minSize).coerceAtMost(1f), 1f)
            top = point.y.coerceIn(0f, (bottom - minSize).coerceAtLeast(0f))
        }

        CropMode.BOTTOM_LEFT -> {
            left = point.x.coerceIn(0f, (right - minSize).coerceAtLeast(0f))
            bottom = point.y.coerceIn((top + minSize).coerceAtMost(1f), 1f)
        }

        CropMode.BOTTOM_RIGHT -> {
            right = point.x.coerceIn((left + minSize).coerceAtMost(1f), 1f)
            bottom = point.y.coerceIn((top + minSize).coerceAtMost(1f), 1f)
        }

        else -> Unit
    }

    return CropSelection(
        left = left,
        top = top,
        width = right - left,
        height = bottom - top
    )
}

private fun fractionOf(offset: Offset, rect: Rect): Offset {
    if (rect.width <= 0f || rect.height <= 0f) return Offset.Zero
    return Offset(
        x = ((offset.x - rect.left) / rect.width).coerceIn(0f, 1f),
        y = ((offset.y - rect.top) / rect.height).coerceIn(0f, 1f)
    )
}

private fun selectionOf(start: Offset, end: Offset): CropSelection {
    val left = min(start.x, end.x)
    val top = min(start.y, end.y)
    val right = max(start.x, end.x)
    val bottom = max(start.y, end.y)
    return CropSelection(
        left = left,
        top = top,
        width = right - left,
        height = bottom - top
    )
}
