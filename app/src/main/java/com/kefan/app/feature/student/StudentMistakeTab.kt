package com.kefan.app.feature.student

import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChip
import com.kefan.app.core.design.KfDropdownHeader
import com.kefan.app.core.design.KfDropdownItem
import com.kefan.app.core.design.KfDropdownMenu
import com.kefan.app.core.design.KfEmptyState
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfThumbnail
import com.kefan.app.core.design.MathText
import com.kefan.app.core.util.formatDateShort
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.entity.MistakeImage
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.feature.mistake.mistakeSourceLabel
import com.kefan.app.feature.mistake.mistakeStatusLabel
import com.kefan.app.feature.mistake.mistakeStatusTagType

private const val FILTER_ALL = "ALL"
private const val UNTAGGED = "未标注知识点"

@Composable
fun AlbumMistakesTab(
    mistakes: List<MistakeRecord>,
    images: List<MistakeImage>,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onExport: () -> Unit
) {
    var statusFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var tagFilter by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var aggregate by rememberSaveable { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }

    val tagsById = remember(mistakes) {
        mistakes.associate { it.id to TemplateJson.decodeStrings(it.knowledgeTagsJson) }
    }
    val allTags = remember(mistakes) {
        tagsById.values.flatten().distinct().sorted()
    }
    val imageByMistake = remember(images) {
        images.groupBy { it.mistakeId }.mapValues { (_, list) ->
            list.firstOrNull { it.croppedUri != null } ?: list.firstOrNull()
        }
    }

    val pendingCount = mistakes.count { it.reviewStatus == MistakeRecord.REVIEW_TODO }
    val reviewingCount = mistakes.count { it.reviewStatus == MistakeRecord.REVIEW_REVIEWING }
    val masteredCount = mistakes.count { it.reviewStatus == MistakeRecord.REVIEW_MASTERED }

    val keyword = query.trim()
    val filtered = mistakes.filter { mistake ->
        val tags = tagsById[mistake.id].orEmpty()
        val statusOk = statusFilter == FILTER_ALL || mistake.reviewStatus == statusFilter
        val tagOk = tagFilter.isBlank() || tags.contains(tagFilter)
        val queryOk = keyword.isBlank() ||
            mistake.stemText.contains(keyword, ignoreCase = true) ||
            mistake.analysis.contains(keyword, ignoreCase = true) ||
            tags.any { it.contains(keyword, ignoreCase = true) }
        statusOk && tagOk && queryOk
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("待复习 ", fontSize = 10.5.sp, color = KfMuted)
            Text(
                text = "$pendingCount",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFC2410C)
            )
            Text(" 道 · 共 ${mistakes.size} 道", fontSize = 10.5.sp, color = KfMuted)
            Spacer(Modifier.weight(1f))
            KfTextButton(text = "导出", onClick = onExport, fontSize = 10)
        }

        Spacer(Modifier.height(8.dp))
        KfTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索题干、解析或知识点",
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            KfChip(
                text = "全部 ${mistakes.size}",
                selected = statusFilter == FILTER_ALL,
                onClick = { statusFilter = FILTER_ALL }
            )
            KfChip(
                text = "待复习 $pendingCount",
                selected = statusFilter == MistakeRecord.REVIEW_TODO,
                onClick = { statusFilter = MistakeRecord.REVIEW_TODO }
            )
            KfChip(
                text = "复习中 $reviewingCount",
                selected = statusFilter == MistakeRecord.REVIEW_REVIEWING,
                onClick = { statusFilter = MistakeRecord.REVIEW_REVIEWING }
            )
            KfChip(
                text = "已掌握 $masteredCount",
                selected = statusFilter == MistakeRecord.REVIEW_MASTERED,
                onClick = { statusFilter = MistakeRecord.REVIEW_MASTERED }
            )
            Box {
                KfChip(
                    text = tagFilter.ifBlank { "知识点 ▾" },
                    outlined = tagFilter.isBlank(),
                    selected = tagFilter.isNotBlank(),
                    onClick = { showTagMenu = true }
                )
                KfDropdownMenu(
                    expanded = showTagMenu,
                    onDismissRequest = { showTagMenu = false }
                ) {
                    KfDropdownHeader(text = "按知识点筛选")
                    KfDropdownItem(
                        text = "全部知识点",
                        selected = tagFilter.isBlank(),
                        onClick = {
                            tagFilter = ""
                            showTagMenu = false
                        }
                    )
                    allTags.forEach { tag ->
                        KfDropdownItem(
                            text = tag,
                            selected = tagFilter == tag,
                            onClick = {
                                tagFilter = tag
                                showTagMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KfChip(
                text = "列表",
                selected = !aggregate,
                onClick = { aggregate = false }
            )
            KfChip(
                text = "按知识点聚合",
                selected = aggregate,
                onClick = { aggregate = true }
            )
        }

        Spacer(Modifier.height(10.dp))
        when {
            mistakes.isEmpty() -> {
                KfCard(flat = true) {
                    KfEmptyState(
                        title = "还没有错题",
                        hint = "点右下角「添加错题」手动录入\n也可以在已保存反馈的详情页点「识别错题」",
                        icon = Icons.Outlined.ChatBubbleOutline
                    )
                    KfGhostButton(
                        text = "添加第一道错题",
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                        height = 34.dp
                    )
                }
            }

            filtered.isEmpty() -> {
                KfCard(flat = true) {
                    KfEmptyState(
                        title = "没有符合条件的错题",
                        hint = "换个筛选条件或清空搜索试试",
                        icon = Icons.Outlined.ChatBubbleOutline
                    )
                }
            }

            aggregate -> {
                val groups = remember(filtered, tagsById) {
                    val map = linkedMapOf<String, MutableList<MistakeRecord>>()
                    filtered.forEach { mistake ->
                        val tags = tagsById[mistake.id].orEmpty()
                        if (tags.isEmpty()) {
                            map.getOrPut(UNTAGGED) { mutableListOf() }.add(mistake)
                        } else {
                            tags.forEach { tag ->
                                map.getOrPut(tag) { mutableListOf() }.add(mistake)
                            }
                        }
                    }
                    map.entries.sortedWith(
                        compareByDescending<Map.Entry<String, MutableList<MistakeRecord>>> {
                            it.value.size
                        }.thenBy { it.key }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { (tag, records) ->
                        KnowledgeGroupCard(
                            tag = tag,
                            records = records,
                            onClick = if (tag == UNTAGGED) {
                                null
                            } else {
                                {
                                    tagFilter = tag
                                    aggregate = false
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "共 ${filtered.size} 题 · ${groups.size} 个知识点",
                    fontSize = 10.sp,
                    color = KfMuted,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { mistake ->
                        MistakeListCard(
                            mistake = mistake,
                            image = imageByMistake[mistake.id],
                            tags = tagsById[mistake.id].orEmpty(),
                            onClick = { onOpen(mistake.id) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "共 ${filtered.size} 题",
                    fontSize = 10.sp,
                    color = KfMuted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MistakeListCard(
    mistake: MistakeRecord,
    image: MistakeImage?,
    tags: List<String>,
    onClick: () -> Unit
) {
    KfCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            val uri = image?.croppedUri ?: image?.originalUri
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(62.dp)
                    .background(Color(0xFFF2F4F7), RoundedCornerShape(7.dp))
            ) {
                if (uri != null) {
                    KfThumbnail(
                        uri = Uri.parse(uri),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "文",
                        fontSize = 11.sp,
                        color = KfMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                MathText(
                    text = mistake.stemText.ifBlank { "（题干待补全）" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.take(2).forEach { tag ->
                        KfTag(tag, KfTagType.BLUE)
                    }
                    KfTag(
                        text = mistakeStatusLabel(mistake.reviewStatus),
                        type = mistakeStatusTagType(mistake.reviewStatus)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(formatDateShort(mistake.createdAt))
                        append(" · ")
                        append(mistakeSourceLabel(mistake.sourceType))
                        if (mistake.errorType.isNotBlank()) {
                            append(" · ")
                            append(mistake.errorType)
                        }
                    },
                    fontSize = 10.sp,
                    color = KfMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun KnowledgeGroupCard(
    tag: String,
    records: List<MistakeRecord>,
    onClick: (() -> Unit)?
) {
    val pending = records.count { it.reviewStatus != MistakeRecord.REVIEW_MASTERED }
    val errors = records
        .map { it.errorType.ifBlank { "未分类" } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(3)
    KfCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tag,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KfInk
            )
            Spacer(Modifier.weight(1f))
            KfTag("${records.size} 题", KfTagType.BLUE)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "待复习 $pending 题 · 已掌握 ${records.size - pending} 题",
            fontSize = 10.5.sp,
            color = KfMuted
        )
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = errors.joinToString(" · ") { "${it.key} ${it.value}" },
                fontSize = 10.sp,
                color = KfMuted
            )
        }
    }
}
