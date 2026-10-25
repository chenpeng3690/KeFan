package com.kefan.app.core.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import com.kefan.app.core.math.MathRun
import com.kefan.app.core.math.MathRunStyle
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 把公式文字画成 PNG，供 Word 内联插入。
 * 只负责展示，不改变存储的原始题干。
 */
object FormulaImageRenderer {

    fun render(
        runs: List<MathRun>,
        textSizePx: Float,
        targetFile: File
    ): Boolean = runCatching {
        targetFile.parentFile?.mkdirs()
        val spannable = toSpannable(runs)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            color = Color.BLACK
            typeface = Typeface.SERIF
        }
        val layoutWidth = 8000
        val layout = StaticLayout.Builder
            .obtain(spannable, 0, spannable.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        var lineWidth = 0f
        for (line in 0 until layout.lineCount) {
            lineWidth = max(lineWidth, layout.getLineWidth(line))
        }
        val padding = (textSizePx * 0.35f).toInt().coerceAtLeast(6)
        val width = min(layoutWidth, ceil(lineWidth).toInt() + padding * 2).coerceAtLeast(1)
        val height = (layout.height + padding * 2).coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)

        FileOutputStream(targetFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        true
    }.getOrDefault(false)

    private fun toSpannable(runs: List<MathRun>): SpannableString {
        val builder = StringBuilder()
        val positions = mutableListOf<Triple<Int, Int, MathRunStyle>>()
        runs.forEach { run ->
            val start = builder.length
            builder.append(run.text)
            val end = builder.length
            if (end > start) positions.add(Triple(start, end, run.style))
        }
        val spannable = SpannableString(builder.toString())
        positions.forEach { (start, end, style) ->
            when (style) {
                MathRunStyle.SUP -> {
                    spannable.setSpan(
                        SuperscriptSpan(),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        RelativeSizeSpan(0.68f),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                MathRunStyle.SUB -> {
                    spannable.setSpan(
                        SubscriptSpan(),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        RelativeSizeSpan(0.68f),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                MathRunStyle.NORMAL -> Unit
            }
        }
        return spannable
    }
}
