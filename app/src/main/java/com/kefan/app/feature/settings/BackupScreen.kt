package com.kefan.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.kefan.app.core.design.KfChevron
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPageHeader
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfTag
import com.kefan.app.core.design.KfTextButton
import com.kefan.app.core.util.formatDate

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importBackup)
    }
    val mergeBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importBackupMerge)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KfPageHeader(
            title = "备份与恢复",
            subtitle = "数据只在这台手机上，建议定期备份",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            KfBanner(
                text = "备份包包含数据库和全部图片。API Key 不会写入备份，恢复后需要重新填写。",
                type = KfBannerType.INFO
            )
            Spacer(Modifier.height(10.dp))

            if (ui.running) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = KfPrimary, strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "正在处理，请暂时不要退出…",
                    fontSize = 11.sp,
                    color = KfMuted,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                KfCard(
                    flat = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        createBackup.launch("kefan_backup_${formatDate(System.currentTimeMillis())}.zip")
                    }
                ) {
                    Text(
                        text = "导出备份",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KfInk
                    )
                    Text(
                        text = "生成一个 zip，含数据库和图片，可存到网盘或电脑",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    KfTag("建议每周一次")
                }

                Spacer(Modifier.height(10.dp))
                KfCard(
                    flat = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openBackup.launch(arrayOf("application/zip")) }
                ) {
                    Text(
                        text = "从备份恢复",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KfInk
                    )
                    Text(
                        text = "恢复会覆盖当前数据库和图片，完成后 App 自动重启",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    KfTag("覆盖当前数据", com.kefan.app.core.design.KfTagType.AMBER)
                }

                Spacer(Modifier.height(10.dp))
                KfCard(
                    flat = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { mergeBackup.launch(arrayOf("application/zip")) }
                ) {
                    Text(
                        text = "合并恢复",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KfInk
                    )
                    Text(
                        text = "保留当前数据，只补充备份里没有的学生、反馈、成绩和错题",
                        fontSize = 10.5.sp,
                        color = KfMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    KfTag("不覆盖现有数据", com.kefan.app.core.design.KfTagType.BLUE)
                }
            }

            val message = ui.message
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = message,
                    type = KfBannerType.OK,
                    trailing = {
                        KfTextButton("知道了", onClick = viewModel::dismiss, color = KfMuted)
                    }
                )
            }
            val error = ui.error
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                KfBanner(
                    text = error,
                    type = KfBannerType.ERROR,
                    trailing = {
                        KfTextButton("关闭", onClick = viewModel::dismiss, color = KfMuted)
                    }
                )
            }

            Spacer(Modifier.height(14.dp))
            KfCard(flat = true, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "恢复注意事项",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Text(
                    text = "1. 只能恢复课返自己的备份包；\n2. 覆盖恢复会删除当前数据；\n3. 合并恢复保留当前数据，只补充缺少的内容；\n4. 模型 Key 不在备份里，需要在设置里重填。",
                    fontSize = 10.5.sp,
                    lineHeight = 17.sp,
                    color = KfMuted
                )
            }
        }
    }
}
