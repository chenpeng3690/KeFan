package com.kefan.app.feature.model

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
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
import com.kefan.app.core.design.KfTagType
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.domain.model.AiProviderPresets

@Composable
fun ModelEditScreen(
    onBack: () -> Unit,
    viewModel: ModelEditViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var providerMenu by remember { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ModelEditEvent.Saved -> {
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                }
                ModelEditEvent.Deleted -> {
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    onBack()
                }
                is ModelEditEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (ui.showModels) {
        AlertDialog(
            onDismissRequest = viewModel::dismissModels,
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "选择模型（${ui.models.size} 个）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(ui.models) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.chooseModel(model) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = model,
                                fontSize = 12.sp,
                                color = KfInk,
                                modifier = Modifier.weight(1f)
                            )
                            if (model == form.modelName) {
                                KfTag("当前", KfTagType.BLUE)
                            }
                        }
                        KfDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissModels) {
                    Text("关闭", color = KfMuted)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "删除这张配置？",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
            },
            text = {
                Text(
                    text = "密钥会一并从本机加密存储中删除，历史的反馈内容不受影响。",
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF3F4753)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                    }
                ) {
                    Text("删除", color = KfDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = KfMuted)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = if (ui.isEdit) "编辑模型" else "新建模型",
            onBack = onBack,
            actions = {
                KfTextButton(text = "保存", onClick = viewModel::save)
            }
        )

        if (ui.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                KfSectionLabel(text = "连接")
                Spacer(Modifier.height(7.dp))

                KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        KfSettingRow(
                            title = "服务商预设",
                            subtitle = "选预设自动填地址和模型，仍可手改",
                            value = form.provider,
                            showChevron = true,
                            onClick = { providerMenu = true }
                        )
                        KfDropdownMenu(
                            expanded = providerMenu,
                            onDismissRequest = { providerMenu = false }
                        ) {
                            KfDropdownHeader(text = "服务商预设")
                            AiProviderPresets.all.forEach { preset ->
                                KfDropdownItem(
                                    text = preset.name,
                                    supporting = preset.defaultModel.takeUnless { it.isBlank() },
                                    selected = preset.name == form.provider,
                                    onClick = {
                                        viewModel.applyPreset(preset.name)
                                        providerMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                KfTextField(
                    value = form.name,
                    onValueChange = { value -> viewModel.updateForm { it.copy(name = value) } },
                    label = "配置名称",
                    placeholder = "如：DeepSeek 主力"
                )

                Spacer(Modifier.height(10.dp))
                KfTextField(
                    value = form.baseUrl,
                    onValueChange = { value -> viewModel.updateForm { it.copy(baseUrl = value) } },
                    label = "Base URL",
                    placeholder = "https://api.deepseek.com/v1"
                )

                Spacer(Modifier.height(10.dp))
                KfTextField(
                    value = form.apiKey,
                    onValueChange = { value -> viewModel.updateForm { it.copy(apiKey = value) } },
                    label = "API Key",
                    placeholder = if (ui.isEdit && ui.maskedKey != "未填写") {
                        "已保存（${ui.maskedKey}），留空不修改"
                    } else {
                        "sk-..."
                    },
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (showKey) "隐藏密钥" else "显示密钥",
                                tint = KfMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    KfTextField(
                        value = form.modelName,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(modelName = value) }
                        },
                        label = "模型名",
                        placeholder = "deepseek-flash",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    KfGhostButton(
                        text = if (ui.fetchingModels) "拉取中…" else "拉取模型",
                        onClick = viewModel::fetchModels,
                        enabled = !ui.fetchingModels,
                        height = 52.dp
                    )
                }

                Spacer(Modifier.height(10.dp))
                KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                    KfSettingRow(
                        title = "支持识图",
                        subtitle = "决定直接读教案照片，还是走 OCR 文本",
                        trailing = {
                            KfSwitch(
                                checked = form.supportsVision,
                                onCheckedChange = { value ->
                                    viewModel.updateForm { it.copy(supportsVision = value) }
                                }
                            )
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "高级参数")
                Spacer(Modifier.height(7.dp))

                KfCard {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KfTextField(
                                value = form.contextWindow,
                                onValueChange = { value ->
                                    viewModel.updateForm { it.copy(contextWindow = value) }
                                },
                                label = "上下文窗口",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            KfTextField(
                                value = form.timeoutSeconds,
                                onValueChange = { value ->
                                    viewModel.updateForm { it.copy(timeoutSeconds = value) }
                                },
                                label = "超时（秒）",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KfTextField(
                                value = form.temperature,
                                onValueChange = { value ->
                                    viewModel.updateForm { it.copy(temperature = value) }
                                },
                                label = "temperature",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            KfTextField(
                                value = form.maxTokens,
                                onValueChange = { value ->
                                    viewModel.updateForm { it.copy(maxTokens = value) }
                                },
                                label = "max_tokens",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        KfTextField(
                            value = form.customHeadersJson,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(customHeadersJson = value) }
                            },
                            label = "自定义 Header（JSON）",
                            placeholder = "{\"X-Api-Version\":\"2024-01-01\"}",
                            singleLine = false,
                            minLines = 3,
                            maxLines = 5
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                KfGhostButton(
                    text = if (ui.testing) "测试中…" else "测试连接",
                    onClick = viewModel::testConnection,
                    enabled = !ui.testing,
                    modifier = Modifier.fillMaxWidth(),
                    height = 40.dp
                )

                val testMessage = ui.testMessage
                if (testMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    KfBanner(
                        text = testMessage,
                        type = if (ui.testOk == true) KfBannerType.OK else KfBannerType.ERROR
                    )
                }

                Spacer(Modifier.height(14.dp))
                KfPrimaryButton(
                    text = "保存配置",
                    onClick = viewModel::save,
                    enabled = !ui.saving,
                    loading = ui.saving,
                    modifier = Modifier.fillMaxWidth()
                )

                if (ui.isEdit) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "删除这张配置",
                        fontSize = 12.sp,
                        color = KfDanger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteConfirm = true }
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
