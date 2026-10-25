package com.kefan.app.feature.score

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfLine
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPrimary
import com.kefan.app.core.design.KfPrimarySoft
import com.kefan.app.data.local.entity.ScoreRecord
import com.kefan.app.core.util.formatDateShort
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ScoreMetric(val label: String) {
    SCORE("分数"),
    RATE("得分率"),
    RANK("排名")
}

@Composable
fun ScoreTrendChart(
    records: List<ScoreRecord>,
    metric: ScoreMetric,
    modifier: Modifier = Modifier
) {
    val filtered = when (metric) {
        ScoreMetric.SCORE -> records
        ScoreMetric.RATE -> records.filter { it.fullScore > 0.0 }
        ScoreMetric.RANK -> records.filter { it.rank != null }
    }.sortedBy { it.date }

    if (filtered.size < 1) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (metric) {
                    ScoreMetric.RANK -> "还没有录入班级排名"
                    else -> "还没有可画的成绩"
                },
                fontSize = 10.5.sp,
                color = KfMuted
            )
        }
        return
    }

    val values = filtered.map { record ->
        when (metric) {
            ScoreMetric.SCORE -> record.score.toFloat()
            ScoreMetric.RATE -> (record.score / record.fullScore * 100.0).toFloat()
            ScoreMetric.RANK -> (record.rank ?: 0).toFloat()
        }
    }
    val labels = filtered.map { formatDateShort(it.date) }

    val density = LocalDensity.current
    val labelPx = with(density) { 9.5.sp.toPx() }
    val lineColor = KfPrimary
    val gridColor = KfLine
    val fillColor = KfPrimary.copy(alpha = 0.14f)
    val dotColor = KfPrimary
    val latestColor = Color(0xFF1D4ED8)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(158.dp)
    ) {
        val left = 34.dp.toPx()
        val right = 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 22.dp.toPx()
        val chartWidth = size.width - left - right
        val chartHeight = size.height - top - bottom

        val rawMin = values.minOrNull() ?: 0f
        val rawMax = values.maxOrNull() ?: 1f
        val (minValue, maxValue) = when (metric) {
            ScoreMetric.SCORE -> (rawMin - 5f).coerceAtLeast(0f) to (rawMax + 5f)
            ScoreMetric.RATE -> (rawMin - 5f).coerceAtLeast(0f) to (rawMax + 5f).coerceAtMost(100f)
            ScoreMetric.RANK -> 1f to (rawMax + 1f)
        }
        val span = (maxValue - minValue).coerceAtLeast(1f)

        fun yOf(value: Float): Float {
            val ratio = ((value - minValue) / span).coerceIn(0f, 1f)
            return if (metric == ScoreMetric.RANK) {
                top + ratio * chartHeight
            } else {
                top + (1f - ratio) * chartHeight
            }
        }

        // 横向网格 + 左侧刻度
        val gridCount = 3
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridColor.toArgb()
            textSize = labelPx
        }
        repeat(gridCount + 1) { index ->
            val ratio = index.toFloat() / gridCount
            val y = top + ratio * chartHeight
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(size.width - right, y),
                strokeWidth = 1f
            )
            val value = if (metric == ScoreMetric.RANK) {
                minValue + ratio * span
            } else {
                maxValue - ratio * span
            }
            val label = when (metric) {
                ScoreMetric.SCORE -> value.toInt().toString()
                ScoreMetric.RATE -> "${value.toInt()}%"
                ScoreMetric.RANK -> "第${value.toInt()}名"
            }
            drawIntoCanvas { canvas ->
                gridPaint.textAlign = Paint.Align.RIGHT
                canvas.nativeCanvas.drawText(
                    label,
                    left - 4.dp.toPx(),
                    y + labelPx * 0.35f,
                    gridPaint
                )
            }
        }

        val stepX = if (values.size > 1) chartWidth / (values.size - 1) else 0f
        val points = values.mapIndexed { index, value ->
            val x = if (values.size > 1) left + index * stepX else left + chartWidth / 2f
            Offset(x, yOf(value))
        }

        if (points.size > 1) {
            val fill = Path().apply {
                moveTo(points.first().x, top + chartHeight)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, top + chartHeight)
                close()
            }
            drawPath(fill, fillColor)

            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx()))
        }

        points.forEachIndexed { index, point ->
            val latest = index == points.lastIndex
            drawCircle(
                color = if (latest) latestColor else dotColor,
                radius = if (latest) 3.6.dp.toPx() else 2.6.dp.toPx(),
                center = point
            )
        }

        // X 轴日期（最多 4 个）
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A929E.toInt()
            textSize = labelPx
            textAlign = Paint.Align.CENTER
        }
        val maxLabels = 4
        val labelStep = if (labels.size <= maxLabels) 1 else {
            ((labels.size - 1).toFloat() / (maxLabels - 1)).toInt().coerceAtLeast(1)
        }
        labels.forEachIndexed { index, label ->
            val show = index % labelStep == 0 || index == labels.lastIndex
            if (show) {
                val x = if (values.size > 1) left + index * stepX else left + chartWidth / 2f
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        label,
                        x,
                        size.height - 4.dp.toPx(),
                        textPaint
                    )
                }
            }
        }
    }
}

@Composable
fun KnowledgeRadarCard(
    points: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(110.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "还没有知识点数据\n录成绩时填知识点，或等错题入库",
                fontSize = 10.5.sp,
                color = KfMuted
            )
        }
        return
    }

    if (points.size < 3) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            points.forEach { (tag, count) ->
                val ratio = count.toFloat() / (points.maxOf { it.second }.coerceAtLeast(1))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        color = KfInk,
                        modifier = Modifier.width(82.dp),
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(KfPrimarySoft)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(KfPrimary)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(text = "$count", fontSize = 10.5.sp, color = KfMuted)
                }
            }
        }
        return
    }

    val density = LocalDensity.current
    val labelPx = with(density) { 9.5.sp.toPx() }
    val maxCount = points.maxOf { it.second }.coerceAtLeast(1)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f + 2.dp.toPx())
        val radius = min(size.width, size.height) * 0.30f
        val count = points.size

        fun vertex(index: Int, scale: Float): Offset {
            val angle = -PI / 2 + 2 * PI * index / count
            return Offset(
                center.x + (radius * scale * cos(angle)).toFloat(),
                center.y + (radius * scale * sin(angle)).toFloat()
            )
        }

        repeat(3) { level ->
            val scale = (level + 1) / 3f
            val grid = Path()
            repeat(count) { index ->
                val p = vertex(index, scale)
                if (index == 0) grid.moveTo(p.x, p.y) else grid.lineTo(p.x, p.y)
            }
            grid.close()
            drawPath(grid, Color(0xFFDDE3EA), style = Stroke(width = 1f))
        }

        repeat(count) { index ->
            drawLine(
                color = Color(0xFFE5E9EF),
                start = center,
                end = vertex(index, 1f),
                strokeWidth = 1f
            )
        }

        val path = Path()
        points.forEachIndexed { index, (_, countValue) ->
            val p = vertex(index, countValue.toFloat() / maxCount)
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, KfPrimary.copy(alpha = 0.18f))
        drawPath(path, KfPrimary, style = Stroke(width = 1.6.dp.toPx()))

        points.forEachIndexed { index, (_, countValue) ->
            drawCircle(
                color = KfPrimary,
                radius = 2.6.dp.toPx(),
                center = vertex(index, countValue.toFloat() / maxCount)
            )
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4B5563.toInt()
            textSize = labelPx
            textAlign = Paint.Align.CENTER
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9AA1AC.toInt()
            textSize = labelPx * 0.9f
            textAlign = Paint.Align.CENTER
        }
        points.forEachIndexed { index, (tag, countValue) ->
            val labelPos = vertex(index, 1f)
            val angle = -PI / 2 + 2 * PI * index / count
            val x = labelPos.x + (cos(angle) * 18.dp.toPx()).toFloat()
            val y = labelPos.y + (sin(angle) * 14.dp.toPx()).toFloat()
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    if (tag.length > 6) tag.take(5) + "…" else tag,
                    x,
                    y,
                    labelPaint
                )
                canvas.nativeCanvas.drawText(
                    countValue.toString(),
                    x,
                    y + labelPx * 1.05f,
                    countPaint
                )
            }
        }
    }
}
