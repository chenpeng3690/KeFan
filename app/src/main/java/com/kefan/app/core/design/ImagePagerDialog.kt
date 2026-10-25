package com.kefan.app.core.design

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全屏图片查看：多张图左右滑动切换。
 */
@Composable
fun KfImagePagerDialog(
    uris: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (uris.isEmpty()) return
    val safeIndex = initialIndex.coerceIn(0, uris.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { uris.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                KfThumbnail(
                    uri = Uri.parse(uris[page]),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    maxPx = 2048
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第 ${pagerState.currentPage + 1} / ${uris.size} 张",
                    fontSize = 12.sp,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                KfTextButton(text = "关闭", onClick = onDismiss, color = Color.White)
            }

            Text(
                text = "左右滑动切换图片",
                fontSize = 10.5.sp,
                color = Color(0xFF9AA2AE),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            )
        }
    }
}
