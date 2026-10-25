package com.kefan.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.BuildConfig
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimaryInk
import com.kefan.app.core.design.KfPrimarySoft
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfSettingRow
import com.kefan.app.core.design.KfSwitch
import com.kefan.app.core.design.KfTextButton

@Composable
fun SettingsScreen(
    onOpenModels: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenGlobalTemplate: () -> Unit,
    onOpenSensitiveWords: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val modelCount by viewModel.modelCount.collectAsStateWithLifecycle()
    val studentCount by viewModel.studentCount.collectAsStateWithLifecycle()
    val keepEmoji by viewModel.keepEmoji.collectAsStateWithLifecycle()
    val strictFactCheck by viewModel.strictFactCheck.collectAsStateWithLifecycle()
    val globalTemplateSections by viewModel.globalTemplateSections.collectAsStateWithLifecycle()
    val sensitiveWordCount by viewModel.sensitiveWordCount.collectAsStateWithLifecycle()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    var showDataFlow by remember { mutableStateOf(false) }

    if (showDataFlow) {
        AlertDialog(
            onDismissRequest = { showDataFlow = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "数据流向说明",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "课返默认纯本地存储。\n\n" +
                        "• 学生档案、反馈、照片、错题、成绩只保存在这台手机上。\n" +
                        "• 只有在你点「生成反馈 / 分析模板 / 生成答案」时，才会把本次所需内容直接发送给你配置的 AI 服务商。\n" +
                        "• 不经过任何自建服务器，没有账号与云端同步。\n" +
                        "• API Key 以密文保存在系统加密存储中。",
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF3F4753)
                )
            },
            confirmButton = {
                TextButton(onClick = { showDataFlow = false }) {
                    Text("知道了", color = Color(0xFF2563EB))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        KfPageHeader(title = "设置")

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                            text = "余额",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D4ED8)
                        )
                        Spacer(Modifier.weight(1f))
                        KfTextButton(
                            text = if (balance.loading) "查询中…" else "查询余额",
                            onClick = viewModel::queryBalance,
                            color = Color(0xFF1D4ED8),
                            fontSize = 11
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = balance.amount ?: "—",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KfPrimaryInk
                        )
                        Spacer(Modifier.weight(1f))
                        balance.detail?.let { detail ->
                            Text(
                                text = detail,
                                fontSize = 10.5.sp,
                                color = Color(0xFF3B5BDB)
                            )
                        }
                    }
                    val error = balance.error
                    if (error != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = error,
                            fontSize = 10.5.sp,
                            color = KfDanger
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            KfSectionLabel(text = "写作")
            Spacer(Modifier.height(7.dp))

            KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                Column {
                    KfSettingRow(
                        title = "模型",
                        subtitle = "写反馈 · 压缩 · 错题解析",
                        value = when {
                            activeModel != null ->
                                "${activeModel!!.name} · ${activeModel!!.modelName}"
                            modelCount == 0 -> "未配置"
                            else -> "未选择"
                        },
                        showChevron = true,
                        onClick = onOpenModels
                    )
                    KfDivider()
                    KfSettingRow(
                        title = "全局模板",
                        subtitle = "新生未导入时继承",
                        value = globalTemplateSections?.let { "$it 段" } ?: "未设置",
                        showChevron = true,
                        onClick = onOpenGlobalTemplate
                    )
                    KfDivider()
                    KfSettingRow(
                        title = "敏感词库",
                        subtitle = "生成后标橙底提醒",
                        value = "$sensitiveWordCount 个词",
                        showChevron = true,
                        onClick = onOpenSensitiveWords
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            KfSectionLabel(text = "审阅")
            Spacer(Modifier.height(7.dp))

            KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                Column {
                    KfSettingRow(
                        title = "复制时保留 emoji",
                        subtitle = "默认关闭，避免表情干扰",
                        trailing = {
                            KfSwitch(
                                checked = keepEmoji,
                                onCheckedChange = viewModel::setKeepEmoji
                            )
                        }
                    )
                    KfDivider()
                    KfSettingRow(
                        title = "严格事实校验",
                        subtitle = "有未核对项时禁止复制和分享",
                        trailing = {
                            KfSwitch(
                                checked = strictFactCheck,
                                onCheckedChange = viewModel::setStrictFactCheck
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            KfSectionLabel(text = "数据")
            Spacer(Modifier.height(7.dp))

            KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                Column {
                    KfSettingRow(
                        title = "记忆",
                        subtitle = "长期画像 / 近期摘要",
                        value = "$studentCount 个学生"
                    )
                    KfDivider()
                    KfSettingRow(
                        title = "备份与恢复",
                        subtitle = "导出 zip，含数据库和图片",
                        value = "打开",
                        showChevron = true,
                        onClick = onOpenBackup
                    )
                    KfDivider()
                    KfSettingRow(
                        title = "回收站",
                        subtitle = "删除的反馈可恢复",
                        value = "$trashRetentionDays 天自动清理",
                        showChevron = true,
                        onClick = onOpenTrash
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            KfBanner(
                text = "所有数据只存在这台手机上",
                type = com.kefan.app.core.design.KfBannerType.OK
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = "课返 v${BuildConfig.VERSION_NAME} · 数据流向说明",
                fontSize = 10.5.sp,
                color = KfMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDataFlow = true }
                    .padding(vertical = 6.dp)
            )
        }
    }
}
