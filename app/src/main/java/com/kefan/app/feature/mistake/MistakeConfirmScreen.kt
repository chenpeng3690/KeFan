package com.kefan.app.feature.mistake

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfEmptyState
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfThumbnail
import com.kefan.app.core.design.MathText
import com.kefan.app.core.math.MathSyntax
import kotlin.math.roundToInt

@Composable
fun MistakeConfirmScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    viewModel: MistakeConfirmViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cropFigureIndex by remember { mutableStateOf<Int?>(null) }

    cropFigureIndex?.let { index ->
        val draft = ui.drafts.firstOrNull { it.index == index }
        val pageUri = draft?.let { ui.imageUris.getOrNull(it.pageIndex - 1) }
        if (draft != null && pageUri != null) {
            MistakeCropDialog(
                sourceUri = Uri.parse(pageUri),
                initialRect = parseRect(draft.figureRectJson) ?: draft.figureBox,
                title = "调整配图区域",
                onDismiss = { cropFigureIndex = null },
                onConfirm = { left, top, width, height ->
                    viewModel.applyFigureCrop(index, left, top, width, height)
                    cropFigureIndex = null
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MistakeConfirmEvent.Saved -> {
                    Toast.makeText(context, "已入库 ${event.count} 道错题", Toast.LENGTH_SHORT).show()
                    onSaved()
                }
                is MistakeConfirmEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = "确认错题",
            subtitle = if (ui.studentName.isBlank()) {
                null
            } else {
                "${ui.studentName} · 识别到 ${ui.drafts.size} 题"
            },
            onBack = onBack
        )

        when {
            ui.loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
                }
            }

            ui.drafts.isEmpty() -> {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (ui.error != null) {
                        KfBanner(text = ui.error!!, type = KfBannerType.ERROR)
                        Spacer(Modifier.height(10.dp))
                    }
                    KfCard(flat = true) {
                        KfEmptyState(
                            title = "没有待确认的错题",
                            hint = "重新从教案照片提取一次，或手动添加错题",
                            icon = Icons.AutoMirrored.Outlined.FactCheck
                        )
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    KfBanner(
                        text = "确认后才会入库。默认勾选置信度 ≥ 0.8 的题，可逐条修改题干和知识点。",
                        type = KfBannerType.INFO
                    )
                    if (ui.drafts.any { it.hasFigure }) {
                        Spacer(Modifier.height(6.dp))
                        KfBanner(
                            text = "含图题请检查配图框选，保存时会和题目一起入库，导出时直接使用。",
                            type = KfBannerType.WARNING
                        )
                    }
                    if (ui.drafts.size >= 12) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "已达单次识别上限 12 题，可能还有题未列出；遗漏的题可以手动补充。",
                            fontSize = 10.sp,
                            lineHeight = 16.sp,
                            color = KfMuted
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已选 ${ui.selectedCount} / ${ui.drafts.size} 题",
                            fontSize = 11.sp,
                            color = KfMuted
                        )
                        Spacer(Modifier.weight(1f))
                        KfTextButton(
                            text = "全选",
                            onClick = { viewModel.selectAll(true) },
                            fontSize = 11
                        )
                        KfTextButton(
                            text = "取消全选",
                            onClick = { viewModel.selectAll(false) },
                            color = KfMuted,
                            fontSize = 11
                        )
                    }
                }

                val error = ui.error
                if (error != null) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        KfBanner(text = error, type = KfBannerType.ERROR)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 10.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ui.drafts, key = { it.index }) { draft ->
                        DraftCard(
                            draft = draft,
                            pageUri = ui.imageUris.getOrNull(draft.pageIndex - 1),
                            onToggle = { viewModel.toggleDraft(draft.index) },
                            onStemChange = { value ->
                                viewModel.updateDraft(draft.index) {
                                    it.copy(stemText = value)
                                }
                            },
                            onTagsChange = { value ->
                                viewModel.updateDraft(draft.index) {
                                    it.copy(tags = value)
                                }
                            },
                            onAdjustFigure = { cropFigureIndex = draft.index }
                        )
                    }
                }

                KfPrimaryButton(
                    text = "确认入库 ${ui.selectedCount} 题",
                    onClick = viewModel::confirm,
                    enabled = !ui.saving && ui.selectedCount > 0,
                    loading = ui.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: MistakeConfirmDraft,
    pageUri: String?,
    onToggle: () -> Unit,
    onStemChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onAdjustFigure: () -> Unit
) {
    KfCard(flat = !draft.selected) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = draft.selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = KfPrimary,
                    uncheckedColor = KfMuted
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KfTag(
                        text = "${(draft.confidence * 100).roundToInt()}% 把握",
                        type = if (draft.confidence >= 0.8) {
                            KfTagType.GREEN
                        } else {
                            KfTagType.AMBER
                        }
                    )
                    KfTag("第 ${draft.pageIndex} 题")
                    KfTag("难度 ${draft.difficulty}")
                    if (draft.hasFigure) KfTag("含图", KfTagType.BLUE)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        if (draft.errorType.isNotBlank()) append(draft.errorType)
                        if (draft.cropHint.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(draft.cropHint)
                        }
                    }.ifBlank { "未填写错因" },
                    fontSize = 10.sp,
                    color = KfMuted
                )
                if (draft.hasFigure && draft.figureNote.isNotBlank()) {
                    Text(
                        text = "图形：${draft.figureNote}",
                        fontSize = 10.sp,
                        color = KfMuted
                    )
                }
            }
        }
        if (draft.hasFigure) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewUri = draft.figureUri ?: pageUri
                if (previewUri != null) {
                    KfThumbnail(
                        uri = Uri.parse(previewUri),
                        modifier = Modifier
                            .width(54.dp)
                            .height(42.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (draft.figureUri != null) "配图已框选" else "配图待框选",
                        fontSize = 10.5.sp,
                        color = if (draft.figureUri != null) {
                            KfMuted
                        } else {
                            Color(0xFFC2410C)
                        }
                    )
                    Text(
                        text = draft.figureNote.ifBlank { "只框图形本身，不要带题干文字" },
                        fontSize = 10.sp,
                        color = KfMuted
                    )
                }
                if (pageUri != null) {
                    KfTextButton(
                        text = if (draft.figureUri != null) "调整" else "框选",
                        onClick = onAdjustFigure,
                        fontSize = 11
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        KfTextField(
            value = draft.stemText,
            onValueChange = onStemChange,
            label = "题干",
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            enabled = draft.selected
        )
        if (MathSyntax.hasMath(draft.stemText)) {
            Spacer(Modifier.height(3.dp))
            MathText(
                text = draft.stemText,
                fontSize = 11.sp,
                color = KfMuted,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        KfTextField(
            value = draft.tags,
            onValueChange = onTagsChange,
            label = "知识点",
            placeholder = "多个用、分隔",
            enabled = draft.selected
        )
        if (draft.studentAnswer.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            MathText(
                text = "学生答案：${draft.studentAnswer}",
                fontSize = 10.5.sp,
                color = KfMuted
            )
        }
        if (draft.studentAnswer.isBlank() && draft.correctAnswer.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            MathText(
                text = "识别答案：${draft.correctAnswer}",
                fontSize = 10.5.sp,
                color = KfMuted
            )
        }
    }
}

private fun parseRect(raw: String?): List<Float>? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().toFloat() }
    }.getOrNull()?.takeIf { it.size == 4 }
}
