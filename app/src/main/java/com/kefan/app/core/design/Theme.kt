package com.kefan.app.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = KfPrimary,
    onPrimary = Color.White,
    primaryContainer = KfPrimarySoft,
    onPrimaryContainer = KfPrimaryDeep,
    secondary = KfOk,
    onSecondary = Color.White,
    secondaryContainer = KfOkSoft,
    onSecondaryContainer = KfOkInk,
    background = KfBg,
    onBackground = KfInk,
    surface = KfSurface,
    onSurface = KfInk,
    surfaceVariant = KfFill,
    onSurfaceVariant = KfInk2,
    outline = KfGhostLine,
    outlineVariant = KfLine,
    error = KfDanger,
    onError = Color.White
)

/**
 * 设计稿是浅色主题，这里锁定浅色，不跟随系统深色模式。
 */
@Composable
fun KeFanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = KfTypography,
        content = content
    )
}
