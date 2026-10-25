package com.kefan.app.feature.student

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChevron
import com.kefan.app.core.design.KfChip
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfDropdownItem
import com.kefan.app.core.design.KfDropdownMenu
import com.kefan.app.core.design.KfEmptyState
import com.kefan.app.core.design.KfFab
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfLine
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfOk
import com.kefan.app.core.design.KfOkSoft
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfThumbnail
import com.kefan.app.core.util.formatDate
import com.kefan.app.core.util.formatDateShort
import com.kefan.app.core.util.relativeDayText
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.FeedbackImage
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.ScoreRecord
import com.kefan.app.data.local.entity.Student
import com.kefan.app.data.repository.MemoryTaskKind
import com.kefan.app.data.repository.MemoryTaskState
import com.kefan.app.domain.model.TemplateDefaults
import com.kefan.app.feature.score.KnowledgeRadarCard
import com.kefan.app.feature.score.ScoreMetric
import com.kefan.app.feature.score.ScoreTrendChart
import java.util.Locale

private val albumTabs = listOf("概览", "反馈", "照片", "成绩", "错题", "记忆")

@Composable
fun StudentAlbumScreen(
    onBack: () -> Unit,
    onEditStudent: (Long) -> Unit,
    onOpenWritingStyle: (Long) -> Unit,
    onOpenImport: (Long) -> Unit,
    onNewFeedback: (Long) -> Unit,
    onOpenFeedback: (Long) -> Unit,
    onOpenScoreEdit: (Long, Long) -> Unit,
    onOpenMistake: (Long, Long) -> Unit,
    onExportMistakes: (Long) -> Unit,
    initialTab: Int = 0,
    viewModel: StudentAlbumViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val memoryState by viewModel.memoryState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable {
        mutableIntStateOf(initialTab.coerceIn(0, albumTabs.lastIndex))
    }
    var menuExpanded by remember { mutableStateOf(false) }
    val student = ui.student

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = student?.name ?: "学生专辑",
            subtitle = student?.let { studentSubtitle(it) },
            onBack = onBack,
            actions = {
                if (student != null) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "更多",
                                tint = KfMuted,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        KfDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            KfDropdownItem(
                                text = "编辑档案",
                                supporting = "姓名、年级、科目、目标与错题开关",
                                onClick = {
                                    menuExpanded = false
                                    onEditStudent(student.id)
                                }
                            )
                            KfDropdownItem(
                                text = "写作风格",
                                supporting = "查看模板结构、样例库与历史版本",
                                onClick = {
                                    menuExpanded = false
                                    onOpenWritingStyle(student.id)
                                }
                            )
                            KfDropdownItem(
                                text = "导入历史反馈",
                                supporting = "贴几条旧反馈，让 AI 学你的写法",
                                onClick = {
                                    menuExpanded = false
                                    onOpenImport(student.id)
                                }
                            )
                        }
                    }
                }
            }
        )

        when {
            ui.loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
                }
            }

            student == null -> {
                KfEmptyState(
                    title = "学生档案不存在",
                    hint = "可能已被删除，返回学生列表看看",
                    icon = Icons.Outlined.ChatBubbleOutline
                )
            }

            else -> {
                AlbumTabRow(
                    selected = selectedTab,
                    onSelect = { selectedTab = it }
                )
                Box(modifier = Modifier.weight(1f).padding(bottom = 56.dp)) {
                    when (selectedTab) {
                        0 -> AlbumOverviewTab(
                            ui = ui,
                            student = student,
                            onSwitchTab = { selectedTab = it },
                            onOpenWritingStyle = { onOpenWritingStyle(student.id) },
                            onOpenFeedback = onOpenFeedback
                        )
                        1 -> AlbumFeedbackTab(
                            feedbacks = ui.feedbacks,
                            onOpenFeedback = onOpenFeedback
                        )
                        2 -> AlbumPhotosTab(ui)
                        3 -> AlbumScoresTab(
                            ui = ui,
                            onAddScore = { onOpenScoreEdit(student.id, -1L) },
                            onEditScore = { scoreId -> onOpenScoreEdit(student.id, scoreId) }
                        )
                        4 -> AlbumMistakesTab(
                            mistakes = ui.mistakes,
                            images = ui.mistakeImages,
                            onAdd = { onOpenMistake(student.id, -1L) },
                            onOpen = { mistakeId -> onOpenMistake(student.id, mistakeId) },
                            onExport = { onExportMistakes(student.id) }
                        )
                        else -> AlbumMemoryTab(
                            ui = ui,
                            state = memoryState,
                            onOpenFeedback = onOpenFeedback,
                            onEditStudent = { onEditStudent(student.id) },
                            onCompact = viewModel::compactMemory,
                            onRebuildProfile = viewModel::rebuildMemoryProfile,
                            onDismissStatus = viewModel::clearMemoryMessage
                        )
                    }
                }
            }
        }
        }

        if (student != null) {
            val scoresTab = selectedTab == 3
            val mistakesTab = selectedTab == 4
            KfFab(
                text = when {
                    scoresTab -> "录入成绩"
                    mistakesTab -> "添加错题"
                    else -> "新建反馈"
                },
                onClick = {
                    when {
                        scoresTab -> onOpenScoreEdit(student.id, -1L)
                        mistakesTab -> onOpenMistake(student.id, -1L)
                        else -> onNewFeedback(student.id)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun AlbumTabRow(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            albumTabs.forEachIndexed { index, label ->
                val isSelected = selected == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(top = 9.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) KfPrimary else KfMuted
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 9.dp)
                            .height(2.dp)
                            .background(
                                if (isSelected) KfPrimary else Color.Transparent,
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(KfLine)
        )
    }
}

@Composable
private fun AlbumOverviewTab(
    ui: AlbumUi,
    student: Student,
    onSwitchTab: (Int) -> Unit,
    onOpenWritingStyle: () -> Unit,
    onOpenFeedback: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        val latest = ui.latestFeedback
        KfCard(onClick = { latest?.let { onOpenFeedback(it.id) } }) {
            if (latest == null) {
                KfEmptyState(
                    title = "还没有反馈记录",
                    hint = "下课后生成第一条反馈\n这里会出现最近一次的摘要",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("最近一次反馈", fontSize = 10.5.sp, color = KfMuted)
                    Spacer(Modifier.weight(1f))
                    Text(formatDateShort(latest.classDate), fontSize = 10.5.sp, color = KfMuted)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = latest.summary.ifBlank { latest.title },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = feedbackExcerpt(latest),
                    fontSize = 10.5.sp,
                    color = KfMuted,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KfTag(
                        text = if (latest.status == Feedback.STATUS_FINAL) "已保存" else "草稿",
                        type = if (latest.status == Feedback.STATUS_FINAL) {
                            KfTagType.GREEN
                        } else {
                            KfTagType.AMBER
                        }
                    )
                    val template = ui.effectiveTemplate
                    if (template != null) {
                        val sections = TemplateJson.decodeSections(template.sectionsJson).size
                        KfTag("$sections 段模板")
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        KfCard(onClick = { onSwitchTab(3) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("成绩", fontSize = 10.5.sp, color = KfMuted)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (ui.scores.isEmpty()) "还没有记录" else "最近 ${minOf(ui.scores.size, 6)} 次 ›",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
            }
            Spacer(Modifier.height(6.dp))
            val latestScore = ui.latestScore
            if (latestScore == null) {
                Text(
                    text = "录一次考试成绩，趋势会从这里长出来",
                    fontSize = 11.5.sp,
                    color = KfMuted
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatScore(latestScore.score),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KfInk
                            )
                            Text(
                                text = " 分",
                                fontSize = 11.5.sp,
                                color = KfMuted
                            )
                        }
                        Text(
                            text = scoreDeltaText(ui.latestScore, ui.previousScore),
                            fontSize = 10.5.sp,
                            color = if ((ui.previousScore?.let { latestScore.score - it.score } ?: 0.0) >= 0.0) {
                                KfOk
                            } else {
                                Color(0xFFC2410C)
                            }
                        )
                    }
                    ScoreBars(scores = ui.scores)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        KfCard(onClick = { onSwitchTab(4) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "待复习错题",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Spacer(Modifier.weight(1f))
                KfTag(
                    text = "${ui.pendingMistakeCount} 题",
                    type = if (ui.pendingMistakeCount > 0) KfTagType.AMBER else KfTagType.NEUTRAL
                )
                Spacer(Modifier.width(6.dp))
                KfChevron()
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (ui.mistakes.isEmpty()) {
                    "错题本还是空的"
                } else {
                    "共 ${ui.mistakes.size} 题，其中 ${ui.pendingMistakeCount} 题待复习"
                },
                fontSize = 10.5.sp,
                color = KfMuted
            )
        }

        Spacer(Modifier.height(10.dp))
        KfCard(flat = true, onClick = onOpenWritingStyle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "写作风格",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KfInk
                    )
                    Text(
                        text = templateSummary(ui, student),
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                }
                KfChevron()
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = relativeDayText(student.lastFeedbackAt, activeStudent = true),
            fontSize = 10.5.sp,
            color = KfMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AlbumFeedbackTab(
    feedbacks: List<Feedback>,
    onOpenFeedback: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        if (feedbacks.isEmpty()) {
            KfCard(flat = true) {
                KfEmptyState(
                    title = "还没有反馈",
                    hint = "生成并保存的反馈会按日期倒序排在这里",
                    icon = Icons.Outlined.ChatBubbleOutline
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                feedbacks.forEach { feedback ->
                    KfCard(flat = true, onClick = { onOpenFeedback(feedback.id) }) {
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatDate(feedback.classDate)} · " +
                                        feedback.summary.ifBlank { "已归档" },
                                    fontSize = 10.5.sp,
                                    color = KfMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            KfTag(
                                text = if (feedback.status == Feedback.STATUS_FINAL) "已保存" else "草稿",
                                type = if (feedback.status == Feedback.STATUS_FINAL) {
                                    KfTagType.GREEN
                                } else {
                                    KfTagType.AMBER
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private data class PhotoFeedbackGroup(
    val feedbackId: Long,
    val monthKey: String,
    val dayKey: String,
    val title: String,
    val photos: List<FeedbackImage>
)

private data class PhotoMonthGroup(
    val monthKey: String,
    val feedbacks: List<PhotoFeedbackGroup>
) {
    val photoCount: Int get() = feedbacks.sumOf { it.photos.size }
}

private fun monthLabel(millis: Long): String {
    val date = formatDate(millis)
    return if (date.length >= 7) {
        date.substring(0, 7).replace("-", "年") + "月"
    } else {
        date
    }
}

@Composable
private fun AlbumPhotosTab(ui: AlbumUi) {
    val feedbackById = ui.feedbacks.associateBy { it.id }
    val groups = ui.images
        .groupBy { it.feedbackId }
        .map { (feedbackId, photos) ->
            val feedback = feedbackById[feedbackId]
            val date = feedback?.classDate?.takeIf { it > 0 }
            PhotoFeedbackGroup(
                feedbackId = feedbackId,
                monthKey = date?.let { monthLabel(it) } ?: "未归档",
                dayKey = date?.let { formatDate(it) } ?: "未归档",
                title = feedback?.title.orEmpty(),
                photos = photos.sortedBy { it.orderIndex }
            )
        }
        .sortedByDescending { if (it.dayKey == "未归档") "" else it.dayKey }

    val months = groups
        .groupBy { it.monthKey }
        .map { (monthKey, feedbacks) ->
            PhotoMonthGroup(
                monthKey = monthKey,
                feedbacks = feedbacks.sortedByDescending { it.dayKey }
            )
        }
        .sortedByDescending { if (it.monthKey == "未归档") "" else it.monthKey }

    var preview by remember { mutableStateOf<FeedbackImage?>(null) }
    var expandedMonth by rememberSaveable { mutableStateOf("") }
    var expandedFeedback by rememberSaveable { mutableStateOf(-1L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        if (ui.images.isEmpty()) {
            KfCard(flat = true) {
                KfEmptyState(
                    title = "还没有课堂照片",
                    hint = "生成反馈时上传的教案照片会按月份和日期归档在这里",
                    icon = Icons.Outlined.ChatBubbleOutline
                )
            }
        } else {
            Text(
                text = "共 ${ui.images.size} 张 · 按月份归档",
                fontSize = 10.5.sp,
                color = KfMuted,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            months.forEach { month ->
                Spacer(Modifier.height(10.dp))
                KfCard(
                    onClick = {
                        if (expandedMonth == month.monthKey) {
                            expandedMonth = ""
                        } else {
                            expandedMonth = month.monthKey
                        }
                        expandedFeedback = -1L
                    },
                    padding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (month.monthKey == "未归档") "未归档照片" else "${month.monthKey}教案照片",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KfInk
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${month.feedbacks.size} 次 · ${month.photoCount} 张",
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (expandedMonth == month.monthKey) "▾" else "▸",
                            fontSize = 11.sp,
                            color = KfMuted
                        )
                    }
                }

                if (expandedMonth != month.monthKey) return@forEach

                month.feedbacks.forEach { group ->
                    Spacer(Modifier.height(7.dp))
                    KfCard(
                        flat = true,
                        onClick = {
                            expandedFeedback = if (expandedFeedback == group.feedbackId) {
                                -1L
                            } else {
                                group.feedbackId
                            }
                        },
                        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.dayKey,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KfInk
                                )
                                val subtitle = group.title.substringAfter(' ').ifBlank { group.title }
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        fontSize = 9.5.sp,
                                        color = KfMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "${group.photos.size} 张",
                                fontSize = 10.5.sp,
                                color = KfMuted
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (expandedFeedback == group.feedbackId) "▾" else "▸",
                                fontSize = 11.sp,
                                color = KfMuted
                            )
                        }
                    }

                    if (expandedFeedback != group.feedbackId) return@forEach

                    Spacer(Modifier.height(7.dp))
                    group.photos.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { image ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .background(Color(0xFFF2F4F7), RoundedCornerShape(8.dp))
                                            .clickable { preview = image }
                                    ) {
                                        KfThumbnail(
                                            uri = Uri.parse(image.uri),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .background(
                                                    Color(0x99000000),
                                                    RoundedCornerShape(3.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "第 ${image.orderIndex + 1} 页",
                                                fontSize = 8.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    preview?.let { image ->
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { preview = null }
            ) {
                KfThumbnail(
                    uri = Uri.parse(image.uri),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    maxPx = 2048
                )
                Text(
                    text = "第 ${image.orderIndex + 1} 页 · 点击任意位置关闭",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumScoresTab(
    ui: AlbumUi,
    onAddScore: () -> Unit,
    onEditScore: (Long) -> Unit
) {
    val scores = ui.scores
    var metric by remember { mutableStateOf(ScoreMetric.SCORE) }
    var subject by remember { mutableStateOf<String?>(null) }

    val subjects = scores.map { it.subject.ifBlank { "未填科目" } }.distinct()
    val currentSubject = subject?.takeIf { it in subjects } ?: subjects.firstOrNull()
    val records = currentSubject?.let { selected ->
        scores.filter { it.subject.ifBlank { "未填科目" } == selected }
    }.orEmpty()
    val weakPoints = remember(scores, ui.mistakes) {
        aggregateWeakPoints(scores, ui.mistakes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        if (scores.isEmpty()) {
            KfCard(flat = true) {
                KfEmptyState(
                    title = "还没有成绩记录",
                    hint = "录一次月考或测验，折线趋势会从这里长出来",
                    icon = Icons.Outlined.ChatBubbleOutline
                )
            }
            Spacer(Modifier.height(10.dp))
            KfGhostButton(
                text = "录入第一次成绩",
                onClick = onAddScore,
                modifier = Modifier.fillMaxWidth(),
                height = 38.dp
            )
        } else {
            KfCard(flat = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("最近一次", fontSize = 10.5.sp, color = KfMuted)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatScore(scores.first().score),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KfInk
                            )
                            Text(
                                text = " / ${formatScore(scores.first().fullScore)}",
                                fontSize = 10.5.sp,
                                color = KfMuted
                            )
                        }
                        Text(
                            text = scores.first().examName,
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
                    }
                    ScoreBars(scores = scores)
                }
            }

            Spacer(Modifier.height(10.dp))
            KfCard {
                Text(
                    text = "成绩趋势",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Text(
                    text = "${records.size} 次 · ${currentSubject.orEmpty()}",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ScoreMetric.entries.forEach { item ->
                        KfChip(
                            text = item.label,
                            selected = metric == item,
                            onClick = { metric = item }
                        )
                    }
                }
                if (subjects.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        subjects.forEach { item ->
                            KfChip(
                                text = item,
                                outlined = true,
                                selected = currentSubject == item,
                                onClick = { subject = item }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                ScoreTrendChart(records = records, metric = metric)
            }

            Spacer(Modifier.height(10.dp))
            KfCard {
                Text(
                    text = "知识点薄弱项",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Text(
                    text = "近 3 个月 · 成绩与错题标签聚合",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
                Spacer(Modifier.height(8.dp))
                KnowledgeRadarCard(points = weakPoints)
            }

            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                records.sortedByDescending { it.date }.forEach { record ->
                    ScoreRecordCard(
                        record = record,
                        onClick = { onEditScore(record.id) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun aggregateWeakPoints(
    scores: List<ScoreRecord>,
    mistakes: List<MistakeRecord>
): List<Pair<String, Int>> {
    val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
    val counts = mutableMapOf<String, Int>()
    scores.filter { it.date >= cutoff }.forEach { record ->
        TemplateJson.decodeStrings(record.knowledgeTagsJson).forEach { tag ->
            counts[tag] = (counts[tag] ?: 0) + 1
        }
    }
    mistakes.filter { it.createdAt >= cutoff }.forEach { mistake ->
        TemplateJson.decodeStrings(mistake.knowledgeTagsJson).forEach { tag ->
            counts[tag] = (counts[tag] ?: 0) + 1
        }
    }
    return counts.entries
        .sortedByDescending { it.value }
        .take(6)
        .map { it.key to it.value }
}

@Composable
private fun ScoreRecordCard(record: ScoreRecord, onClick: () -> Unit) {
    KfCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.examName.ifBlank { "考试成绩" },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Text(
                    text = formatDate(record.date) +
                        if (record.rank != null) " · 班级第 ${record.rank} 名" else "",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
            }
            Text(
                text = "${formatScore(record.score)} / ${formatScore(record.fullScore)}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KfPrimary
            )
        }
        val tags = TemplateJson.decodeStrings(record.knowledgeTagsJson)
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.take(3).forEach { tag ->
                    KfTag(tag, KfTagType.BLUE)
                }
            }
        }
        if (record.classAvg != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "班级平均 ${formatScore(record.classAvg)} 分",
                fontSize = 10.5.sp,
                color = KfMuted
            )
        }
    }
}

@Composable
private fun AlbumMemoryTab(
    ui: AlbumUi,
    state: MemoryTaskState,
    onOpenFeedback: (Long) -> Unit,
    onEditStudent: () -> Unit,
    onCompact: () -> Unit,
    onRebuildProfile: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val memory = ui.memory
    val student = ui.student
    val rebuilding = state.running && state.kind == MemoryTaskKind.PROFILE
    val compacting = state.running && state.kind == MemoryTaskKind.COMPACT
    var showAllRecords by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        when {
            state.running -> KfBanner(
                text = if (rebuilding) "AI 正在重新生成长期画像…" else "AI 正在整理近期摘要…",
                type = KfBannerType.INFO
            )

            state.message != null -> KfBanner(
                text = state.message!!,
                type = if (state.isError) KfBannerType.ERROR else KfBannerType.OK,
                trailing = {
                    if (state.isError) {
                        KfTextButton(
                            text = "重试",
                            onClick = {
                                if (state.kind == MemoryTaskKind.PROFILE) onRebuildProfile()
                                else onCompact()
                            }
                        )
                    } else {
                        KfTextButton(
                            text = "知道了",
                            onClick = onDismissStatus,
                            color = KfMuted
                        )
                    }
                }
            )
        }
        if (state.running || state.message != null) {
            Spacer(Modifier.height(10.dp))
        }

        KfCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "长期画像",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KfInk
                    )
                    Text(
                        text = "L2 · AI 维护 · 上限 800 字",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                }
                KfTextButton(
                    text = if (rebuilding) "生成中…" else "重新生成",
                    onClick = onRebuildProfile,
                    color = if (rebuilding) KfMuted else KfPrimary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = memory?.longTermProfile?.takeIf { it.isNotBlank() }
                    ?: "还没有长期画像。导入历史反馈后会自动生成初稿，也可以点「重新生成」。",
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = if (memory?.longTermProfile.isNullOrBlank()) KfMuted else Color(0xFF1F2530)
            )
        }

        Spacer(Modifier.height(10.dp))
        KfCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "近期摘要",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KfInk
            )
            Text(
                text = "L3 · 最近 6~10 次课，每次一行",
                fontSize = 10.5.sp,
                color = KfMuted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = memory?.recentSummary?.takeIf { it.isNotBlank() }
                    ?: "还没有近期摘要。保存反馈后会自动整理。",
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = if (memory?.recentSummary.isNullOrBlank()) KfMuted else Color(0xFF1F2530)
            )
        }

        Spacer(Modifier.height(10.dp))
        KfCard(flat = true, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "记忆整理",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KfInk
            )
            Text(
                text = "新记录满 5 条自动整理；L4 原文永不删除",
                fontSize = 10.5.sp,
                color = KfMuted
            )
            Spacer(Modifier.height(8.dp))
            KfGhostButton(
                text = if (compacting) "整理中…" else "立即整理记忆",
                onClick = onCompact,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
                height = 34.dp
            )
            if (memory != null && memory.updatedAt > 0) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "上次更新 ${formatDateShort(memory.updatedAt)} · 约 ${memory.tokenEstimate} tokens",
                    fontSize = 10.sp,
                    color = KfMuted
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        KfCard(flat = true, modifier = Modifier.fillMaxWidth(), onClick = onEditStudent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "档案卡",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KfInk
                    )
                    Text(
                        text = "L1 · 人工维护",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                }
                KfTextButton(text = "编辑", onClick = onEditStudent)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = studentProfileSummary(student),
                fontSize = 11.sp,
                color = KfMuted,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAllRecords = !showAllRecords }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "原始记录",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KfMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${ui.feedbacks.size} 条",
                fontSize = 10.5.sp,
                color = KfMuted
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (showAllRecords) "▾" else "▸",
                fontSize = 11.sp,
                color = KfMuted
            )
        }
        Spacer(Modifier.height(7.dp))

        if (ui.feedbacks.isEmpty()) {
            KfCard(flat = true, modifier = Modifier.fillMaxWidth()) {
                KfEmptyState(
                    title = "还没有课堂记录",
                    hint = "每次保存的反馈都会原始保留在这里",
                    icon = Icons.Outlined.ChatBubbleOutline
                )
            }
        } else {
            KfCard(flat = true, padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                Column {
                    val visibleRecords = if (showAllRecords) {
                        ui.feedbacks
                    } else {
                        ui.feedbacks.take(6)
                    }
                    visibleRecords.forEachIndexed { index, feedback ->
                        if (index > 0) KfDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenFeedback(feedback.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feedback.title,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KfInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatDateShort(feedback.classDate)} · " +
                                        feedback.summary.ifBlank { "已归档" },
                                    fontSize = 10.sp,
                                    color = KfMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            KfChevron()
                        }
                    }
                }
            }
            if (!showAllRecords && ui.feedbacks.size > 6) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "还有 ${ui.feedbacks.size - 6} 条，点上方「原始记录」展开",
                    fontSize = 10.sp,
                    color = KfMuted
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        KfBanner(text = "记忆由 AI 维护，这里只读。长期画像可一键重算，L4 原文不会删除。")
        Spacer(Modifier.height(16.dp))
    }
}

private fun studentProfileSummary(student: Student?): String {
    if (student == null) return ""
    val parts = mutableListOf<String>()
    if (student.grade.isNotBlank()) parts.add(student.grade)
    if (student.subject.isNotBlank()) parts.add(student.subject)
    if (student.school.isNotBlank()) parts.add(student.school)
    if (student.goal.isNotBlank()) parts.add("目标：${student.goal}")
    if (student.personality.isNotBlank()) parts.add("性格：${student.personality}")
    if (student.parentNote.isNotBlank()) parts.add("家长诉求：${student.parentNote}")
    return parts.ifEmpty { listOf("还没有填写档案信息") }.joinToString(" · ")
}

@Composable
private fun ScoreBars(scores: List<ScoreRecord>) {
    val values = scores.take(6).reversed().map { it.score }
    if (values.isEmpty()) return
    val max = values.maxOrNull() ?: 1.0
    val min = values.minOrNull() ?: 0.0
    val span = (max - min).coerceAtLeast(1.0)
    Row(
        modifier = Modifier
            .width(110.dp)
            .height(38.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        values.forEachIndexed { index, value ->
            val ratio = ((value - min) / span).toFloat()
            val barHeight = (10f + 28f * ratio).dp
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(barHeight)
                    .background(
                        if (index == values.lastIndex) KfPrimary else Color(0xFFDBE6FF),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

private fun studentSubtitle(student: Student): String {
    val parts = mutableListOf<String>()
    if (student.grade.isNotBlank()) parts.add(student.grade)
    if (student.subject.isNotBlank()) parts.add(student.subject)
    parts.add("${student.defaultLessons} 课时")
    return parts.joinToString(" · ")
}

private fun feedbackExcerpt(feedback: Feedback): String {
    val content = feedback.finalContent.replace("\n", " ").trim()
    return content.ifBlank { feedback.summary.ifBlank { "已归档" } }.take(90)
}

private fun templateSummary(ui: AlbumUi, student: Student): String {
    val template = ui.effectiveTemplate
    return when {
        template == null -> {
            if (ui.samples.isEmpty()) {
                "临时模板 · 导入历史反馈后可替换"
            } else {
                "已导入 ${ui.samples.size} 条 · 尚未完成提取"
            }
        }
        ui.studentTemplate != null -> {
            val count = TemplateJson.decodeSections(template.sectionsJson).size
            "${student.name}的模板 · $count 段 · v${template.version}"
        }
        else -> {
            val count = TemplateJson.decodeSections(template.sectionsJson).size
            "继承全局模板 · $count 段"
        }
    }
}

private fun formatScore(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.CHINA, "%.1f", value)

private fun scoreDeltaText(latest: ScoreRecord?, previous: ScoreRecord?): String {
    if (latest == null) return ""
    if (previous == null) return "第一次记录"
    val delta = latest.score - previous.score
    return when {
        delta > 0.0 -> "↑ ${formatScore(delta)} 分 vs 上次 ${formatScore(previous.score)}"
        delta < 0.0 -> "↓ ${formatScore(-delta)} 分 vs 上次 ${formatScore(previous.score)}"
        else -> "持平 · 上次 ${formatScore(previous.score)}"
    }
}
