package com.kefan.app.feature.mistake

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfThumbnail
import kotlinx.coroutines.launch

@Composable
fun MistakeCropScreen(
    onBack: () -> Unit,
    onOpenConfirm: () -> Unit,
    viewModel: MistakeCropViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ui.images.size })
    var cropPage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pagerState.currentPage, ui.images.size) {
        if (ui.images.isNotEmpty()) viewModel.setCurrentPage(pagerState.currentPage)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                MistakeCropEvent.OpenConfirm -> onOpenConfirm()
                is MistakeCropEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    cropPage?.let { page ->
        val image = ui.images.getOrNull(page)
        if (image != null) {
            MistakeCropDialog(
                sourceUri = Uri.parse(image),
                title = "框选错题区域（可含多道题）",
                onDismiss = { cropPage = null },
                onConfirm = { left, top, width, height ->
                    viewModel.addCrop(page, left, top, width, height)
                    cropPage = null
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = "框选错题",
            subtitle = buildString {
                if (ui.studentName.isNotBlank()) append(ui.studentName)
                if (ui.feedbackTitle.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(ui.feedbackTitle)
                }
            }.takeIf { it.isNotBlank() },
            onBack = onBack
        )

        if (ui.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
            }
        } else if (ui.images.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                KfCard(flat = true) {
                    Text(
                        text = "这条反馈没有教案照片",
                        fontSize = 12.5.sp,
                        color = KfInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "没有照片就无法框选错题，可以回到学生专辑手动添加。",
                        fontSize = 10.5.sp,
                        color = KfMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0F131A))
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    KfThumbnail(
                        uri = Uri.parse(ui.images[page]),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        maxPx = 2048
                    )
                }

                Text(
                    text = "第 ${pagerState.currentPage + 1} / ${ui.images.size} 张",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .background(Color(0x99000000), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )

                if (pagerState.currentPage > 0) {
                    Text(
                        text = "‹",
                        fontSize = 30.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .background(Color(0x66000000), RoundedCornerShape(18.dp))
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 2.dp)
                    )
                }
                if (pagerState.currentPage < ui.images.lastIndex) {
                    Text(
                        text = "›",
                        fontSize = 30.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .background(Color(0x66000000), RoundedCornerShape(18.dp))
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "左右滑动切图 · 每框选一处就进入下方列表",
                    fontSize = 9.5.sp,
                    color = Color(0xFF9AA2AE),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KfPrimaryButton(
                        text = "框选当前页",
                        onClick = { cropPage = pagerState.currentPage },
                        modifier = Modifier.weight(1f),
                        height = 36.dp,
                        fontSize = 13
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "已框 ${ui.drafts.size} 处",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                }

                if (ui.drafts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ui.drafts.forEach { draft ->
                            CropDraftCard(
                                draft = draft,
                                onDelete = { viewModel.removeCrop(draft.id) }
                            )
                        }
                    }
                }

                val error = ui.error
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    KfBanner(
                        text = error,
                        type = KfBannerType.ERROR,
                        trailing = {
                            if (ui.drafts.any { it.status == CropDraft.STATUS_FAILED }) {
                                KfTextButton("重试失败项", onClick = viewModel::recognizeAll)
                            }
                        }
                    )
                }
                val message = ui.message
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
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

                Spacer(Modifier.height(6.dp))
                val pending = ui.drafts.count { it.results.isEmpty() }
                val done = ui.drafts.sumOf { it.results.size }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KfPrimaryButton(
                        text = when {
                            ui.recognizing -> "识别中 ${ui.done}/${ui.total}"
                            pending > 0 -> "识别 $pending 处"
                            else -> "全部已识别"
                        },
                        onClick = viewModel::recognizeAll,
                        enabled = pending > 0 && !ui.recognizing,
                        loading = ui.recognizing,
                        modifier = Modifier.weight(1f),
                        height = 38.dp,
                        fontSize = 13
                    )
                    if (done > 0) {
                        KfGhostButton(
                            text = "去确认 $done 题",
                            onClick = viewModel::proceedToConfirm,
                            enabled = !ui.recognizing,
                            modifier = Modifier.weight(1f),
                            height = 38.dp,
                            fontSize = 12
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CropDraftCard(
    draft: CropDraft,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.width(70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(82.dp)
                .background(Color(0xFFF2F4F7), RoundedCornerShape(7.dp))
        ) {
            KfThumbnail(
                uri = Uri.parse(draft.croppedUri),
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = "第 ${draft.pageIndex + 1} 页",
                fontSize = 8.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .background(Color(0x99000000), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = statusLabel(draft),
            fontSize = 9.sp,
            color = statusColor(draft),
            maxLines = 1
        )
        KfTextButton(text = "删除", onClick = onDelete, fontSize = 9, color = KfMuted)
    }
}

private fun statusLabel(draft: CropDraft): String = when (draft.status) {
    CropDraft.STATUS_RECOGNIZING -> "识别中…"
    CropDraft.STATUS_DONE -> "已识别 ${draft.results.size} 题"
    CropDraft.STATUS_FAILED -> "失败"
    else -> "待识别"
}

private fun statusColor(draft: CropDraft): Color = when (draft.status) {
    CropDraft.STATUS_DONE -> Color(0xFF0F7B5F)
    CropDraft.STATUS_FAILED -> Color(0xFFC2410C)
    CropDraft.STATUS_RECOGNIZING -> KfPrimary
    else -> KfMuted
}
