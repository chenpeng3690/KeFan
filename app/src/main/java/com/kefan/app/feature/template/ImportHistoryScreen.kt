package com.kefan.app.feature.template

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefan.app.core.design.KfBanner
import com.kefan.app.core.design.KfBannerType
import com.kefan.app.core.design.KfCard
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfSectionLabel
import com.kefan.app.core.design.KfTextField
import com.kefan.app.core.design.KfTextButton

@Composable
fun ImportHistoryScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenModels: () -> Unit,
    viewModel: ImportHistoryViewModel = hiltViewModel()
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Done -> {
                    Toast.makeText(context, "已提取 ${event.sectionCount} 段模板", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(title = "导入历史反馈", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            KfBanner(
                text = "把以前发给家长的反馈原文粘贴进来。多条之间用 --- 或空行分隔；没有分隔时，会按开头的日期或学生姓名自动识别。",
                type = KfBannerType.INFO
            )

            Spacer(Modifier.height(10.dp))
            KfTextField(
                value = text,
                onValueChange = viewModel::onTextChange,
                label = "历史反馈原文",
                placeholder = "张三家长您好，数学课情况如下\n\n【上课时间】2026年10月17日(2课时)\n【上课主要内容】比例\n【课堂反馈】\n1. …\n2. …\n【课后任务】复习错题",
                singleLine = false,
                minLines = 8,
                maxLines = 16
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已识别 ${samples.size} 条样例",
                    fontSize = 11.5.sp,
                    color = if (samples.isEmpty()) KfMuted else KfInk
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "自动识别分隔",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
            }

            if (samples.size == 1) {
                Spacer(Modifier.height(8.dp))
                KfBanner(
                    text = "样本太少，结构可能不准，建议再贴一条。",
                    type = KfBannerType.WARNING
                )
            }

            if (samples.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                KfSectionLabel(text = "样例预览")
                Spacer(Modifier.height(7.dp))
                Column {
                    samples.take(5).forEachIndexed { index, sample ->
                        KfCard(flat = true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "样例 ${index + 1}",
                                    fontSize = 11.5.sp,
                                    color = KfInk
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${sample.length} 字",
                                    fontSize = 10.sp,
                                    color = KfMuted
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = sample.replace("\n", " ").take(80),
                                fontSize = 10.5.sp,
                                color = KfMuted,
                                lineHeight = 17.sp,
                                maxLines = 2
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (samples.size > 5) {
                        Text(
                            text = "还有 ${samples.size - 5} 条，提取时会一起分析",
                            fontSize = 10.5.sp,
                            color = KfMuted
                        )
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

            if (ui.analyzing) {
                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = "AI 正在读你的写法，通常需要 10~30 秒。",
                    type = KfBannerType.INFO
                )
            }

            Spacer(Modifier.height(16.dp))
            KfPrimaryButton(
                text = "分析风格",
                onClick = viewModel::extract,
                enabled = samples.isNotEmpty() && !ui.analyzing,
                loading = ui.analyzing,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = "分析完成后会生成这个学生的专属模板，原文同时进入样例库。",
                fontSize = 10.5.sp,
                color = Color(0xFF8B939F),
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
