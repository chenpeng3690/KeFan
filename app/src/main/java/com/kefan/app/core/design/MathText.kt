package com.kefan.app.core.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kefan.app.core.math.MathRunStyle
import com.kefan.app.core.math.MathSyntax

/**
 * 错题文本渲染：常见公式显示上下标，复杂公式按原文显示。
 * 编辑框仍显示原始 $...$，这里只负责展示态。
 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    color: Color = KfInk,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null
) {
    val annotated = remember(text, fontSize, color) {
        buildMathAnnotated(text, fontSize, color)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize,
            color = color,
            fontWeight = fontWeight,
            lineHeight = lineHeight
        ),
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign
    )
}

private fun buildMathAnnotated(
    text: String,
    fontSize: TextUnit,
    color: Color
): AnnotatedString = buildAnnotatedString {
    MathSyntax.splitSegments(text).forEach { segment ->
        val normalized = if (segment.isFormula) {
            MathSyntax.normalizeMath(segment.text)
        } else {
            MathSyntax.normalizePlain(segment.text)
        }
        if (segment.isFormula && !normalized.supported) {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = color.copy(alpha = 0.62f)
                )
            ) {
                append(segment.text)
            }
        } else {
            normalized.runs.forEach { run ->
                when (run.style) {
                    MathRunStyle.NORMAL -> append(run.text)
                    MathRunStyle.SUP -> withStyle(
                        SpanStyle(
                            fontSize = fontSize * 0.68f,
                            baselineShift = BaselineShift.Superscript
                        )
                    ) {
                        append(run.text)
                    }

                    MathRunStyle.SUB -> withStyle(
                        SpanStyle(
                            fontSize = fontSize * 0.68f,
                            baselineShift = BaselineShift.Subscript
                        )
                    ) {
                        append(run.text)
                    }
                }
            }
        }
    }
}
