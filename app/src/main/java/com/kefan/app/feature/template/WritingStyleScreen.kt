package com.kefan.app.feature.template

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimaryDeep
import com.kefan.app.core.design.KfPrimaryInk
import com.kefan.app.core.design.KfPrimarySoft
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.util.formatDateShort
import com.kefan.app.domain.model.TemplateSection
import com.kefan.app.domain.model.TemplateSpec

@Composable
fun WritingStyleScreen(
    onBack: () -> Unit,
    onEditTemplate: () -> Unit,
    onImportHistory: () -> Unit,
    onOpenModels: () -> Unit,
    viewModel: WritingStyleViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WritingStyleEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = "写作风格",
            onBack = onBack,
            actions = {
                KfTextButton(text = "编辑", onClick = onEditTemplate)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            val spec = ui.spec
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = KfPrimarySoft,
                border = BorderStroke(1.dp, Color(0xFFDBE6FF))
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI 读到的你",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KfPrimaryDeep
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (ui.samples.isEmpty()) {
                                "还没有样例"
                            } else {
                                "${ui.samples.size} 条样例"
                            },
                            fontSize = 10.5.sp,
                            color = KfPrimaryDeep
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = ui.spec.styleSummary.ifBlank {
                            if (ui.isFallback) {
                                "还没有分析过历史反馈。导入 2 条以上旧反馈后，AI 会在这里用一段人话说明它读到的写法。"
                            } else {
                                "AI 没有返回风格摘要，可以点「重新分析」再试一次。"
                            }
                        },
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        color = KfPrimaryInk
                    )
                }
            }

            if (ui.isFallback) {
                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = "临时模板，导入历史反馈后可替换。",
                    type = KfBannerType.WARNING
                )
            } else if (ui.isGlobal) {
                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = "当前继承全局模板；编辑后会保存为该生专属模板。",
                    type = KfBannerType.INFO
                )
            }

            Spacer(Modifier.height(14.dp))
            KfSectionLabel(
                text = "模板结构",
                trailing = {
                    Text(
                        text = "${spec.sections.size} 段 · ${spec.aiSectionCount} 段 AI 写",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                }
            )
            Spacer(Modifier.height(7.dp))
            TemplateStructureCard(spec)

            Spacer(Modifier.height(14.dp))
            KfSectionLabel(
                text = "样例库",
                trailing = {
                    Text(
                        text = "${ui.samples.size} 条",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                }
            )
            Spacer(Modifier.height(7.dp))
            if (ui.samples.isEmpty()) {
                KfCard(flat = true) {
                    Text(
                        text = "还没有样例。导入旧反馈后，这里会保留原文，用于提取模板和模仿语气。",
                        fontSize = 11.5.sp,
                        color = KfMuted,
                        lineHeight = 18.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.samples.take(5).forEach { sample ->
                        KfCard(flat = true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDateShort(sample.createdAt),
                                    fontSize = 10.5.sp,
                                    color = KfMuted
                                )
                                Spacer(Modifier.weight(1f))
                                KfTag(
                                    text = if (sample.analyzed) "已分析" else "待分析",
                                    type = if (sample.analyzed) KfTagType.GREEN else KfTagType.AMBER
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = sample.content.replace("\n", " "),
                                fontSize = 11.sp,
                                color = Color(0xFF3F4753),
                                lineHeight = 18.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (ui.samples.size > 5) {
                        Text(
                            text = "还有 ${ui.samples.size - 5} 条样例",
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
                    }
                }
            }

            val versions = ui.versions.take(5)
            if (versions.size > 1) {
                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "历史版本")
                Spacer(Modifier.height(7.dp))
                KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                    Column {
                        versions.forEachIndexed { index, version ->
                            if (index > 0) KfDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "v${version.version} · ${formatDateShort(version.updatedAt)}",
                                    fontSize = 12.sp,
                                    color = KfInk,
                                    modifier = Modifier.weight(1f)
                                )
                                if (version.isActive) {
                                    KfTag("当前", KfTagType.BLUE)
                                } else {
                                    KfTextButton(
                                        text = "回滚",
                                        onClick = {
                                            viewModel.rollback(version.id, version.version)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val error = ui.error
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = error,
                    type = KfBannerType.ERROR,
                    trailing = if (ui.needsModel) {
                        { KfTextButton("去配置", onClick = onOpenModels) }
                    } else {
                        null
                    }
                )
            }

            if (ui.reanalyzing) {
                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = "AI 正在重新读样例，通常需要 10~30 秒。",
                    type = KfBannerType.INFO
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KfGhostButton(
                    text = if (ui.reanalyzing) "分析中…" else "重新分析",
                    onClick = viewModel::reanalyze,
                    enabled = ui.samples.isNotEmpty() && !ui.reanalyzing,
                    modifier = Modifier.weight(1f),
                    height = 38.dp,
                    fontSize = 12
                )
                KfGhostButton(
                    text = if (ui.samples.isEmpty()) "导入历史反馈" else "导入更多样例",
                    onClick = onImportHistory,
                    modifier = Modifier.weight(1f),
                    height = 38.dp,
                    fontSize = 12
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "模板随时能改，加一段减一段都不用改代码。改了会存为新版本，可回滚。",
                fontSize = 10.5.sp,
                color = KfMuted,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TemplateStructureCard(spec: TemplateSpec) {
    KfCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Column {
            spec.sections.forEachIndexed { index, section ->
                if (index > 0) {
                    KfDivider()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(0xFFF2F4F7), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 10.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = section.title,
                            fontSize = 12.5.sp,
                            color = KfInk
                        )
                        if (section.source == TemplateSection.SOURCE_AI &&
                            section.hint.isNotBlank()
                        ) {
                            Text(
                                text = if (section.items > 1) {
                                    "${section.items} 条 · ${section.hint}"
                                } else {
                                    section.hint
                                },
                                fontSize = 10.5.sp,
                                color = KfMuted,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    KfTag(
                        text = if (section.source == TemplateSection.SOURCE_AI) "AI 写" else "程序填",
                        type = if (section.source == TemplateSection.SOURCE_AI) {
                            KfTagType.BLUE
                        } else {
                            KfTagType.NEUTRAL
                        }
                    )
                }
            }
        }
    }
}
