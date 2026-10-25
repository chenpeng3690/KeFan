package com.kefan.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChip
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfEmptyState
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.util.formatDateShort

private val retentionOptions = listOf(3, 7, 15, 30)

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = "回收站",
            subtitle = "删除的反馈会保留在这里",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            KfBanner(
                text = "超过自动清理天数后会永久删除，对应教案照片也会一起删除。",
                type = KfBannerType.INFO
            )

            Spacer(Modifier.height(14.dp))
            KfSectionLabel(
                text = "自动清理",
                trailing = {
                    Text(
                        text = "$retentionDays 天",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                }
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                retentionOptions.forEach { days ->
                    KfChip(
                        text = "$days 天",
                        selected = retentionDays == days,
                        onClick = { viewModel.setRetentionDays(days) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (message != null) {
                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = message!!,
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

            Spacer(Modifier.height(14.dp))
            if (items.isEmpty()) {
                KfCard(flat = true, modifier = Modifier.fillMaxWidth()) {
                    KfEmptyState(
                        title = "回收站是空的",
                        hint = "删除已保存的反馈后，会先放在这里\n超过设定天数才会永久清理",
                        icon = Icons.Outlined.DeleteOutline
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        KfCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.feedback.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KfInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.studentName} · " +
                                            "${formatDateShort(item.feedback.deletedAt ?: 0L)} 删除",
                                        fontSize = 10.sp,
                                        color = KfMuted
                                    )
                                }
                                KfTag(
                                    text = if (item.remainingDays <= 0) {
                                        "即将清理"
                                    } else {
                                        "${item.remainingDays} 天后清理"
                                    },
                                    type = KfTagType.AMBER
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                KfTextButton(
                                    text = "恢复",
                                    onClick = { viewModel.restore(item.feedback.id) }
                                )
                                Spacer(Modifier.width(10.dp))
                                KfTextButton(
                                    text = "永久删除",
                                    onClick = { viewModel.deleteForever(item.feedback.id) },
                                    color = KfDanger
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
