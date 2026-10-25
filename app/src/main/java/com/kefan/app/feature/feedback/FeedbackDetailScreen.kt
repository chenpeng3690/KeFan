package com.kefan.app.feature.feedback

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfImagePagerDialog
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfThumbnail
import com.kefan.app.core.util.formatDate
import com.kefan.app.data.quality.WeChatFormatter

@Composable
fun FeedbackDetailScreen(
    onBack: () -> Unit,
    onOpenMistakeCrop: (Long) -> Unit,
    viewModel: FeedbackDetailViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showReviewDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    viewerIndex?.let { index ->
        KfImagePagerDialog(
            uris = ui.images.map { it.uri },
            initialIndex = index,
            onDismiss = { viewerIndex = null }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FeedbackDetailEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }

                FeedbackDetailEvent.Deleted -> {
                    Toast.makeText(context, "已移到回收站", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = ui.feedback?.title ?: "反馈详情",
            onBack = onBack,
            actions = {
                if (ui.feedback?.status == "FINAL") {
                    KfTextButton(
                        text = "删除",
                        onClick = { showDeleteConfirm = true },
                        color = KfDanger,
                        fontSize = 12
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

            ui.feedback == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ui.error ?: "反馈不存在",
                        fontSize = 12.5.sp,
                        color = KfMuted
                    )
                }
            }

            else -> {
                val feedback = ui.feedback!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                append(formatDate(feedback.classDate))
                                if (ui.student != null) {
                                    append(" · ").append(ui.student!!.name)
                                }
                                append(" · ").append(feedback.lessonCount).append(" 课时")
                            },
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
                        Spacer(Modifier.weight(1f))
                        KfTag(
                            text = if (feedback.status == "FINAL") "已保存" else "草稿",
                            type = KfTagType.GREEN
                        )
                    }

                    if (ui.images.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ui.images.forEachIndexed { index, image ->
                                Box(
                                    modifier = Modifier
                                        .width(62.dp)
                                        .height(80.dp)
                                        .clickable { viewerIndex = index }
                                ) {
                                    KfThumbnail(
                                        uri = Uri.parse(image.uri),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 8.sp,
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        KfCard(
                            flat = true,
                            onClick = { onOpenMistakeCrop(feedback.id) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "识别错题",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KfInk
                                    )
                                    Text(
                                        text = "打开教案图片，逐张框选错题；识别后确认入库",
                                        fontSize = 10.5.sp,
                                        color = KfMuted
                                    )
                                }
                                KfTag("AI", KfTagType.BLUE)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    ReviewStatusBar(
                        issues = ui.unresolvedIssues,
                        onOpen = { showReviewDialog = true }
                    )

                    Spacer(Modifier.height(10.dp))
                    KfCard(padding = PaddingValues(13.dp)) {
                        BasicTextField(
                            value = ui.content,
                            onValueChange = viewModel::updateContent,
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

                    if (ui.error != null) {
                        Spacer(Modifier.height(8.dp))
                        KfBanner(
                            text = ui.error!!,
                            type = KfBannerType.ERROR
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KfPrimaryButton(
                            text = "复制",
                            onClick = {
                                previewText = WeChatFormatter.format(ui.content, ui.keepEmoji)
                            },
                            enabled = ui.content.isNotBlank() && !ui.strictBlocked,
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                        KfGhostButton(
                            text = "分享",
                            onClick = {
                                val text = WeChatFormatter.format(ui.content, ui.keepEmoji)
                                shareText(context, ui.feedback?.title ?: "课后反馈", text)
                            },
                            enabled = ui.content.isNotBlank() && !ui.strictBlocked,
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    KfGhostButton(
                        text = if (ui.saving) "保存中…" else "保存修改",
                        onClick = viewModel::save,
                        enabled = !ui.saving && ui.content.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        height = 38.dp
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除这条反馈？", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "删除后会进入回收站，可在「设置 → 回收站」恢复；超过保留天数会自动清理。",
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
