package com.kefan.app.feature.feedback

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefan.app.core.design.KfDanger
import com.kefan.app.core.design.KfFact
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimaryDeep
import com.kefan.app.core.design.KfPrimarySoft
import com.kefan.app.core.design.KfTone
import com.kefan.app.data.quality.ReviewIssue
import com.kefan.app.data.quality.ReviewIssueType
import com.kefan.app.data.quality.WeChatFormatter

/** 把审阅项画成编辑器里的底色：待核对黄底、表述偏重橙底 */
class ReviewHighlightTransformation(
    private val issues: List<ReviewIssue>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (issues.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(text)
        issues.forEach { issue ->
            val start = issue.start.coerceIn(0, text.length)
            val end = issue.end.coerceIn(start, text.length)
            if (end > start) {
                builder.addStyle(
                    SpanStyle(
                        background = if (issue.type == ReviewIssueType.FACT) KfFact else KfTone
                    ),
                    start,
                    end
                )
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun ReviewStatusBar(
    issues: List<ReviewIssue>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (issues.isEmpty()) {
        com.kefan.app.core.design.KfBanner(
            text = "已核对，没有发现待确认项。",
            modifier = modifier,
            type = com.kefan.app.core.design.KfBannerType.OK
        )
        return
    }
    val factCount = issues.count { it.type == ReviewIssueType.FACT }
    val toneCount = issues.count { it.type == ReviewIssueType.TONE }
    val parts = buildList {
        if (factCount > 0) add("$factCount 处待核对")
        if (toneCount > 0) add("$toneCount 处表述偏重")
    }
    com.kefan.app.core.design.KfBanner(
        text = parts.joinToString(" · "),
        modifier = modifier,
        type = com.kefan.app.core.design.KfBannerType.WARNING,
        trailing = {
            com.kefan.app.core.design.KfTextButton(
                text = "逐条处理",
                onClick = onOpen,
                fontSize = 11
            )
        }
    )
}

@Composable
fun ReviewIssueDialog(
    issues: List<ReviewIssue>,
    onDismiss: () -> Unit,
    onDeleteSentence: (ReviewIssue) -> Unit,
    onApplySuggestion: (ReviewIssue, String) -> Unit,
    onKeep: (ReviewIssue) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "审阅",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${issues.size} 处",
                    fontSize = 11.sp,
                    color = KfMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (issues.isEmpty()) {
                    Text(
                        text = "全部处理完了，可以放心复制。",
                        fontSize = 12.sp,
                        color = KfMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    issues.forEachIndexed { index, issue ->
                        if (index > 0) {
                            com.kefan.app.core.design.KfDivider(
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        IssueBlock(
                            issue = issue,
                            onDeleteSentence = { onDeleteSentence(issue) },
                            onApplySuggestion = { onApplySuggestion(issue, it) },
                            onKeep = { onKeep(issue) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = KfPrimary)
            }
        }
    )
}

@Composable
private fun IssueBlock(
    issue: ReviewIssue,
    onDeleteSentence: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onKeep: () -> Unit
) {
    val isFact = issue.type == ReviewIssueType.FACT
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.kefan.app.core.design.KfTag(
                text = if (isFact) "待核对" else "表述偏重",
                type = com.kefan.app.core.design.KfTagType.AMBER
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = issue.reason,
                fontSize = 10.5.sp,
                color = KfMuted,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .background(
                    if (isFact) KfFact else KfTone,
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(text = issue.text, fontSize = 11.5.sp, color = KfInk)
        }

        if (!isFact && issue.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                issue.suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KfPrimarySoft, RoundedCornerShape(8.dp))
                            .clickable { onApplySuggestion(suggestion) }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "改为：$suggestion",
                            fontSize = 11.5.sp,
                            color = KfPrimaryDeep
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isFact) {
                com.kefan.app.core.design.KfTextButton(
                    text = "删掉这句",
                    onClick = onDeleteSentence,
                    color = KfDanger,
                    fontSize = 11
                )
            }
            com.kefan.app.core.design.KfTextButton(
                text = if (isFact) "保留，我已确认" else "保留原文",
                onClick = onKeep,
                color = KfMuted,
                fontSize = 11
            )
        }
    }
}

@Composable
fun CopyPreviewDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "复制预览",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "复制后就是这个样子",
                    fontSize = 10.5.sp,
                    color = KfMuted
                )
            }
        },
        text = {
            Column {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        lineHeight = 21.sp,
                        color = Color(0xFF1F2530)
                    )
                }
                if (WeChatFormatter.isLong(text)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "偏长：${text.length} 字，微信里可能被折叠。",
                        fontSize = 10.5.sp,
                        color = Color(0xFFC2410C)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("复制", color = KfPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = KfMuted)
            }
        }
    )
}

fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("课返反馈", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享反馈"))
}
