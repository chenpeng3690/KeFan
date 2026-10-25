package com.kefan.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfDivider
import com.kefan.app.core.design.KfGhostButton
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.design.KfTextField

@Composable
fun SensitiveWordsScreen(
    onBack: () -> Unit,
    viewModel: SensitiveWordsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = "敏感词库",
            subtitle = "命中后标橙底提醒，不自动替换",
            onBack = onBack
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
                Spacer(Modifier.height(10.dp))

                KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)) {
                    KfTextField(
                        value = ui.input,
                        onValueChange = viewModel::updateInput,
                        label = "新增敏感词",
                        placeholder = "如：笨、不用心、态度有问题"
                    )
                    Spacer(Modifier.height(9.dp))
                    KfPrimaryButton(
                        text = "添加词条",
                        onClick = viewModel::addWord,
                        enabled = ui.input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        height = 36.dp,
                        fontSize = 13
                    )
                }

                val message = ui.message
                if (message != null) {
                    Spacer(Modifier.height(10.dp))
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

                Spacer(Modifier.height(14.dp))
                KfSectionLabel(
                    text = "当前词条",
                    trailing = {
                        Text(
                            text = "${ui.words.size} 个词",
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
                    }
                )
                Spacer(Modifier.height(7.dp))

                KfCard(padding = PaddingValues(horizontal = 13.dp, vertical = 2.dp)) {
                    Column {
                        if (ui.words.isEmpty()) {
                            Text(
                                text = "词库是空的，生成内容不会做敏感表述检查。",
                                fontSize = 11.5.sp,
                                color = KfMuted,
                                modifier = Modifier.padding(vertical = 14.dp)
                            )
                        } else {
                            ui.words.forEachIndexed { index, word ->
                                if (index > 0) KfDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = word,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = KfInk,
                                        modifier = Modifier.weight(1f)
                                    )
                                    KfTextButton(
                                        text = "删除",
                                        onClick = { viewModel.removeWord(word) },
                                        color = KfDanger,
                                        fontSize = 11
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                KfGhostButton(
                    text = "恢复内置词库",
                    onClick = viewModel::resetToDefault,
                    modifier = Modifier.fillMaxWidth(),
                    height = 36.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "自定义后只使用这里的词条；恢复内置词库会覆盖当前列表。",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
