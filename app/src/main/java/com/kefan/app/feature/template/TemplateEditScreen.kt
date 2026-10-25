package com.kefan.app.feature.template

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfChip
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfSettingRow
import com.kefan.app.core.design.KfSwitch
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.domain.model.TemplateSection

@Composable
fun TemplateEditScreen(
    onBack: () -> Unit,
    isGlobal: Boolean = false,
    viewModel: TemplateEditViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TemplateEditEvent.Saved -> {
                    Toast.makeText(context, "已保存为 v${event.version}", Toast.LENGTH_SHORT).show()
                    onBack()
                }
                is TemplateEditEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = if (isGlobal) "全局模板" else "编辑模板",
            subtitle = if (isGlobal) "新生没导入历史反馈时自动继承" else null,
            onBack = onBack,
            actions = {
                KfTextButton(text = "保存", onClick = viewModel::save)
            }
        )

        if (ui.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                KfSectionLabel(text = "开头")
                Spacer(Modifier.height(7.dp))
                KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                    Column {
                        KfTextField(
                            value = form.opening,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(opening = value) }
                            },
                            label = "开头模板",
                            placeholder = "{{姓名}}家长您好，{{科目}}课情况如下",
                            modifier = Modifier.padding(top = 9.dp)
                        )
                        Text(
                            text = "可用 {{姓名}}、{{科目}} 占位",
                            fontSize = 10.5.sp,
                            color = KfMuted,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        KfDivider()
                        KfSettingRow(
                            title = "开头后空一行",
                            subtitle = "只影响复制出去的排版",
                            trailing = {
                                KfSwitch(
                                    checked = form.blankLineAfterOpening,
                                    onCheckedChange = { value ->
                                        viewModel.updateForm {
                                            it.copy(blankLineAfterOpening = value)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(
                    text = "段落",
                    trailing = {
                        Text(
                            text = "${form.sections.size} 段",
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
                    }
                )
                Spacer(Modifier.height(7.dp))

                form.sections.forEachIndexed { index, section ->
                    SectionEditorCard(
                        index = index,
                        section = section,
                        totalSections = form.sections.size,
                        onUpdate = { transform ->
                            viewModel.updateSection(index, transform)
                        },
                        onMoveUp = { viewModel.moveSection(index, -1) },
                        onMoveDown = { viewModel.moveSection(index, 1) },
                        onDelete = { viewModel.removeSection(index) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                KfGhostButton(
                    text = "＋ 添加段落",
                    onClick = viewModel::addSection,
                    modifier = Modifier.fillMaxWidth(),
                    height = 36.dp
                )

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "文风与限制")
                Spacer(Modifier.height(7.dp))
                KfCard {
                    Column {
                        KfTextField(
                            value = form.tone,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(tone = value) }
                            },
                            label = "语气",
                            placeholder = "平实、专业、不煽情，像真人手打"
                        )
                        Spacer(Modifier.height(9.dp))
                        KfTextField(
                            value = form.address,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(address = value) }
                            },
                            label = "对学生的称呼",
                            placeholder = "孩子"
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KfTextField(
                                value = form.lengthMin,
                                onValueChange = { value ->
                                    viewModel.updateForm { it.copy(lengthMin = value) }
                                },
                                label = "最短字数",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            KfTextField(
                                value = form.lengthMax,
                                onValueChange = { value ->
                                    viewModel.updateForm { it.copy(lengthMax = value) }
                                },
                                label = "最长字数",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        KfTextField(
                            value = form.bannedWordsText,
                            onValueChange = { value ->
                                viewModel.updateForm { it.copy(bannedWordsText = value) }
                            },
                            label = "禁用词（用顿号或逗号分隔）",
                            placeholder = "表现良好、继续努力、该生、学生",
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4
                        )
                        Spacer(Modifier.height(6.dp))
                        KfSettingRow(
                            title = "允许 emoji",
                            subtitle = "默认关闭，复制时也不保留",
                            trailing = {
                                KfSwitch(
                                    checked = form.useEmoji,
                                    onCheckedChange = { value ->
                                        viewModel.updateForm { it.copy(useEmoji = value) }
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                KfPrimaryButton(
                    text = "保存为新版本",
                    onClick = viewModel::save,
                    enabled = !ui.saving,
                    loading = ui.saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isGlobal) {
                        "保存后作为所有未导入历史反馈的学生的默认模板。"
                    } else {
                        "旧版本会保留，随时可以在写作风格页回滚。"
                    },
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionEditorCard(
    index: Int,
    section: TemplateSectionForm,
    totalSections: Int,
    onUpdate: ((TemplateSectionForm) -> TemplateSectionForm) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    KfCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFFF2F4F7), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", fontSize = 10.sp, color = Color(0xFF6B7280))
                }
                Spacer(Modifier.width(8.dp))
                KfTextField(
                    value = section.title,
                    onValueChange = { value ->
                        onUpdate { it.copy(title = value) }
                    },
                    label = "段落标题（含【】）",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("来源", fontSize = 10.5.sp, color = KfMuted, modifier = Modifier.width(34.dp))
                KfChip(
                    text = "AI 写",
                    selected = section.source == TemplateSection.SOURCE_AI,
                    onClick = {
                        onUpdate { it.copy(source = TemplateSection.SOURCE_AI) }
                    }
                )
                Spacer(Modifier.width(6.dp))
                KfChip(
                    text = "程序填",
                    selected = section.source == TemplateSection.SOURCE_APP,
                    onClick = {
                        onUpdate {
                            it.copy(source = TemplateSection.SOURCE_APP, inline = true)
                        }
                    }
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("排版", fontSize = 10.5.sp, color = KfMuted, modifier = Modifier.width(34.dp))
                KfChip(
                    text = "单行",
                    selected = section.inline,
                    onClick = { onUpdate { it.copy(inline = true) } }
                )
                Spacer(Modifier.width(6.dp))
                KfChip(
                    text = "分段",
                    selected = !section.inline,
                    onClick = { onUpdate { it.copy(inline = false) } }
                )
            }

            if (section.source == TemplateSection.SOURCE_AI) {
                if (!section.inline) {
                    Spacer(Modifier.height(9.dp))
                    KfTextField(
                        value = section.items,
                        onValueChange = { value ->
                            onUpdate { it.copy(items = value) }
                        },
                        label = "条数",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(9.dp))
                KfTextField(
                    value = section.hint,
                    onValueChange = { value ->
                        onUpdate { it.copy(hint = value) }
                    },
                    label = "给 AI 的写作提示",
                    placeholder = "这段该写什么、怎么写",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3
                )
            } else {
                Spacer(Modifier.height(9.dp))
                KfTextField(
                    value = section.defaultValue,
                    onValueChange = { value ->
                        onUpdate { it.copy(defaultValue = value) }
                    },
                    label = "程序填写值",
                    placeholder = "如：复习错题"
                )
            }

            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index > 0) {
                    KfTextButton(text = "上移", onClick = onMoveUp, color = KfMuted, fontSize = 11)
                }
                if (index < totalSections - 1) {
                    Spacer(Modifier.width(8.dp))
                    KfTextButton(text = "下移", onClick = onMoveDown, color = KfMuted, fontSize = 11)
                }
                if (totalSections > 1) {
                    Spacer(Modifier.width(8.dp))
                    KfTextButton(text = "删除", onClick = onDelete, color = KfDanger, fontSize = 11)
                }
            }
        }
    }
}
