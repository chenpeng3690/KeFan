package com.kefan.app.feature.student

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kefan.app.core.design.KfFill
import com.kefan.app.core.design.KfInk
import com.kefan.app.core.design.KfMuted
import com.kefan.app.core.design.KfPrimaryButton
import com.kefan.app.core.design.KfTextButton
import java.util.Locale

private val WheelHeight = 180.dp
private val WheelItemHeight = 36.dp

/**
 * 手机闹钟式时间选择：上下滑动小时 / 分钟两列，可选任意开始时间。
 */
@Composable
fun ClassTimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parts = initialTime.split(":")
    val initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
    val initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }

    val hours = remember { (0..23).map { String.format(Locale.CHINA, "%02d", it) } }
    val minutes = remember { (0..59).map { String.format(Locale.CHINA, "%02d", it) } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择上课时间",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk
                )
                Spacer(Modifier.weight(1f))
                Text("24 小时制", fontSize = 10.sp, color = KfMuted)
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "时",
                    fontSize = 10.5.sp,
                    color = KfMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(34.dp))
                Text(
                    text = "分",
                    fontSize = 10.5.sp,
                    color = KfMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelColumn(
                    items = hours,
                    initialIndex = initialHour,
                    modifier = Modifier.weight(1f),
                    onSelected = { hour = it }
                )
                Text(
                    text = ":",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KfInk,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                WheelColumn(
                    items = minutes,
                    initialIndex = initialMinute,
                    modifier = Modifier.weight(1f),
                    onSelected = { minute = it }
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KfTextButton(
                    text = "清除",
                    onClick = { onConfirm("") },
                    color = KfMuted
                )
                Spacer(Modifier.weight(1f))
                KfTextButton(text = "取消", onClick = onDismiss, color = KfMuted)
                Spacer(Modifier.width(10.dp))
                KfPrimaryButton(
                    text = "确定",
                    onClick = {
                        onConfirm(
                            String.format(Locale.CHINA, "%02d:%02d", hour, minute)
                        )
                    },
                    height = 36.dp,
                    fontSize = 13
                )
            }
        }
    }
}

@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    )
    val itemHeightPx = with(LocalDensity.current) { WheelItemHeight.roundToPx() }
    val selectedIndex by remember {
        derivedStateOf {
            val index = state.firstVisibleItemIndex
            if (state.firstVisibleItemScrollOffset > itemHeightPx / 2) index + 1 else index
        }
    }
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))

    LaunchedEffect(safeSelected) { onSelected(safeSelected) }

    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val target = safeSelected.coerceIn(0, items.lastIndex.coerceAtLeast(0))
            if (state.firstVisibleItemIndex != target ||
                state.firstVisibleItemScrollOffset != 0
            ) {
                state.animateScrollToItem(target)
            }
        }
    }

    Box(
        modifier = modifier
            .height(WheelHeight)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(WheelItemHeight)
                .background(KfFill, RoundedCornerShape(8.dp))
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = (WheelHeight - WheelItemHeight) / 2)
        ) {
            itemsIndexed(items) { index, text ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        fontSize = if (index == safeSelected) 17.sp else 14.sp,
                        fontWeight = if (index == safeSelected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (index == safeSelected) KfInk else KfMuted
                    )
                }
            }
        }
    }
}
