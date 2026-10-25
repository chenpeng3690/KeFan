package com.kefan.app.feature.model

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfAddIconButton
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfEmptyState
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfOk
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextButton

@Composable
fun ModelListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ModelListViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val activeConfig by viewModel.activeConfig.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        KfPageHeader(
            title = "模型",
            onBack = onBack,
            actions = { KfAddIconButton(onClick = onAdd) }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (cards.isEmpty()) {
                KfCard(flat = true) {
                    KfEmptyState(
                        title = "还没有模型配置",
                        hint = "填上 Base URL、Key 和模型名\n写反馈、压缩、解析都用它",
                        icon = Icons.Outlined.SmartToy
                    )
                }
                Spacer(Modifier.height(10.dp))
                KfPrimaryButton(
                    text = "添加模型",
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column {
                    cards.forEachIndexed { index, card ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        ModelConfigCard(
                            card = card,
                            isActive = activeConfig?.id == card.config.id,
                            test = tests[card.config.id],
                            onEdit = { onEdit(card.config.id) },
                            onSetActive = { viewModel.setActive(card.config.id) },
                            onTest = { viewModel.testConnection(card.config) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                KfGhostButton(
                    text = "＋ 添加另一个服务商",
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    height = 34.dp
                )

                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = "写反馈、压缩记忆、算答案解析，全用这一个模型。换服务商就新建一张卡片并设为使用中。"
                )

                Spacer(Modifier.height(12.dp))
                KfCard(flat = true, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "本月消耗", fontSize = 10.5.sp, color = KfMuted)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "¥0.00 · 0 次",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KfInk
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModelConfigCard(
    card: ModelCardUi,
    isActive: Boolean,
    test: ModelTestState?,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onTest: () -> Unit
) {
    val config = card.config
    val lacksKey = card.maskedKey == "未填写" && config.provider != "Ollama 本地"
    val tagText = when {
        isActive -> "使用中"
        test?.success == true -> "已连接"
        config.modelName.isBlank() || lacksKey -> "待补全"
        else -> "已配置"
    }
    val tagType = when {
        isActive -> KfTagType.GREEN
        test?.success == true -> KfTagType.GREEN
        config.modelName.isBlank() || lacksKey -> KfTagType.AMBER
        else -> KfTagType.GREEN
    }

    KfCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = config.name.ifBlank { "未命名模型" },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Spacer(Modifier.width(7.dp))
                KfTag(tagText, tagType)
                Spacer(Modifier.weight(1f))
                if (!isActive) {
                    KfTextButton(text = "设为使用中", onClick = onSetActive)
                    Spacer(Modifier.width(8.dp))
                }
                KfTextButton(text = "编辑", onClick = onEdit, color = KfMuted)
            }

            Spacer(Modifier.height(6.dp))
            ModelField("地址", prettyUrl(config.baseUrl))
            KfDivider()
            ModelField("密钥", card.maskedKey)
            KfDivider()
            ModelField("模型", config.modelName.ifBlank { "未填写" })
            KfDivider()
            ModelField("上下文", formatContext(config.contextWindow))
            KfDivider()
            ModelField(
                "支持识图",
                if (config.supportsVision) "✓ 已开启" else "未开启",
                valueColor = if (config.supportsVision) KfOk else KfMuted
            )

            Spacer(Modifier.height(10.dp))
            KfGhostButton(
                text = if (test?.running == true) "测试中…" else "测试连接",
                onClick = onTest,
                enabled = test?.running != true,
                modifier = Modifier.fillMaxWidth(),
                height = 34.dp
            )

            if (test != null && !test.running && test.success != null) {
                Spacer(Modifier.height(7.dp))
                KfBanner(
                    text = test.message,
                    type = if (test.success) KfBannerType.OK else KfBannerType.ERROR
                )
            }
        }
    }
}

@Composable
private fun ModelField(label: String, value: String, valueColor: Color = KfInk) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = KfMuted,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            fontSize = 10.5.sp,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun prettyUrl(url: String): String =
    url.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
        .ifBlank { "未填写" }

private fun formatContext(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M tokens"
    tokens >= 1_000 -> "${tokens / 1_000}K tokens"
    else -> "$tokens tokens"
}
