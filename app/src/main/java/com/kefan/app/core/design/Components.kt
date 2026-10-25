package com.kefan.app.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ------------------------------------------------------------------ */
/* 顶栏 / 底栏                                                          */
/* ------------------------------------------------------------------ */

@Composable
fun KfPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = KfInk,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = KfInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = KfMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

data class KfBottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun KfBottomBar(
    tabs: List<KfBottomTab>,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KfSurface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = KfLine, thickness = 1.dp)
        Row(modifier = Modifier.height(58.dp)) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) KfPrimary else KfMuted,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        color = if (selected) KfPrimary else KfMuted,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* 容器                                                                */
/* ------------------------------------------------------------------ */

@Composable
fun KfCard(
    modifier: Modifier = Modifier,
    flat: Boolean = false,
    padding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(14.dp),
        color = if (flat) KfFlat else KfSurface,
        border = BorderStroke(1.dp, if (flat) KfFlatLine else KfLine)
    ) {
        Column(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
fun KfSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = KfMuted,
            letterSpacing = 0.4.sp
        )
        trailing?.invoke()
    }
}

@Composable
fun KfDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = KfLine, thickness = 1.dp)
}

/* ------------------------------------------------------------------ */
/* 标签 / 胶囊                                                          */
/* ------------------------------------------------------------------ */

enum class KfTagType { NEUTRAL, BLUE, GREEN, AMBER }

@Composable
fun KfTag(text: String, type: KfTagType = KfTagType.NEUTRAL, modifier: Modifier = Modifier) {
    val bg = when (type) {
        KfTagType.NEUTRAL -> KfFill
        KfTagType.BLUE -> KfPrimarySoft
        KfTagType.GREEN -> KfOkSoft
        KfTagType.AMBER -> KfAmberSoft
    }
    val fg = when (type) {
        KfTagType.NEUTRAL -> KfFillText
        KfTagType.BLUE -> KfPrimaryDeep
        KfTagType.GREEN -> KfOkInk
        KfTagType.AMBER -> KfAmberInk
    }
    Box(
        modifier = modifier
            .height(19.dp)
            .background(bg, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 10.sp, color = fg, maxLines = 1)
    }
}

@Composable
fun KfChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    outlined: Boolean = false,
    height: Dp = 26.dp,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        selected -> KfPrimary
        outlined -> KfSurface
        else -> KfFill
    }
    val fg = when {
        selected -> Color.White
        else -> KfInk2
    }
    Row(
        modifier = modifier
            .height(height)
            .background(bg, shape)
            .then(
                if (outlined && !selected) {
                    Modifier.border(1.dp, KfGhostLine, shape)
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            maxLines = 1
        )
    }
}

/* ------------------------------------------------------------------ */
/* 提示条                                                              */
/* ------------------------------------------------------------------ */

enum class KfBannerType { INFO, WARNING, OK, ERROR }

@Composable
fun KfBanner(
    text: String,
    modifier: Modifier = Modifier,
    type: KfBannerType = KfBannerType.INFO,
    trailing: (@Composable () -> Unit)? = null
) {
    val bg = when (type) {
        KfBannerType.INFO -> KfPrimarySoft
        KfBannerType.WARNING -> KfWarnBg
        KfBannerType.OK -> KfOkSoft
        KfBannerType.ERROR -> KfDangerSoft
    }
    val line = when (type) {
        KfBannerType.INFO -> Color(0xFFDBE6FF)
        KfBannerType.WARNING -> KfWarnLine
        KfBannerType.OK -> Color(0xFFCCEEDE)
        KfBannerType.ERROR -> Color(0xFFF5C6C6)
    }
    val fg = when (type) {
        KfBannerType.INFO -> KfPrimaryDeep
        KfBannerType.WARNING -> KfWarnInk
        KfBannerType.OK -> KfOkInk
        KfBannerType.ERROR -> KfDanger
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, line, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        when (type) {
            KfBannerType.WARNING -> Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            KfBannerType.OK -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            KfBannerType.ERROR -> Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            KfBannerType.INFO -> Unit
        }
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = fg,
            modifier = Modifier.weight(1f),
            lineHeight = 17.sp
        )
        trailing?.invoke()
    }
}

/* ------------------------------------------------------------------ */
/* 头像 / 空状态                                                        */
/* ------------------------------------------------------------------ */

private data class AvatarColor(val bg: Color, val fg: Color)

private val AvatarColors = listOf(
    AvatarColor(KfPrimarySoft, KfPrimaryDeep),
    AvatarColor(Color(0xFFFDF2E9), Color(0xFFB45309)),
    AvatarColor(Color(0xFFEEF2FF), Color(0xFF4338CA)),
    AvatarColor(Color(0xFFF0FDF4), Color(0xFF15803D)),
    AvatarColor(Color(0xFFF3F4F6), Color(0xFF6B7280))
)

@Composable
fun KfAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val style = AvatarColors[(name.hashCode().let { if (it < 0) -it else it }) % AvatarColors.size]
    Box(
        modifier = modifier
            .size(size)
            .background(style.bg, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1),
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold,
            color = style.fg
        )
    }
}

@Composable
fun KfEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFB9C1CC),
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = KfInk2,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = hint,
            fontSize = 11.5.sp,
            color = KfMuted,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

/* ------------------------------------------------------------------ */
/* 按钮                                                                */
/* ------------------------------------------------------------------ */

@Composable
fun KfPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 42.dp,
    fontSize: Int = 14
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KfPrimary,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFB9CDEF),
            disabledContentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun KfGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 30.dp,
    fontSize: Int = 12,
    leading: (@Composable () -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, KfGhostLine),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = KfSurface,
            contentColor = KfInk2,
            disabledContentColor = KfMuted
        ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, fontSize = fontSize.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun KfTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = KfPrimary,
    fontSize: Int = 12
) {
    Text(
        text = text,
        fontSize = fontSize.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
    )
}

@Composable
fun KfAddIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(32.dp)) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = "添加",
            tint = KfPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun KfChevron(modifier: Modifier = Modifier, tint: Color = KfMuted) {
    Icon(
        Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(14.dp)
    )
}

/* ------------------------------------------------------------------ */
/* 设置行 / 开关 / 输入框                                                */
/* ------------------------------------------------------------------ */

@Composable
fun KfSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KfInk
            )
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 10.5.sp, color = KfMuted)
            }
        }
        if (value != null) {
            Text(
                text = value,
                fontSize = 10.5.sp,
                color = KfMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            KfChevron()
        }
    }
}

@Composable
fun KfSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = KfPrimary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFDFE4EC),
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun KfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = if (label != null) {
            { Text(label, fontSize = 11.5.sp) }
        } else {
            null
        },
        placeholder = if (placeholder != null) {
            { Text(placeholder, fontSize = 12.5.sp, color = Color(0xFFB6BDC7)) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle,
        shape = RoundedCornerShape(11.dp),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = KfInk,
            unfocusedTextColor = KfInk,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = KfPrimary,
            unfocusedBorderColor = KfLine,
            focusedLabelColor = KfPrimary,
            unfocusedLabelColor = KfMuted,
            cursorColor = KfPrimary
        )
    )
}

@Composable
fun KfSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = KfFlat,
        border = BorderStroke(1.dp, KfFlatLine)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = KfMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(text = placeholder, fontSize = 11.5.sp, color = KfMuted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = KfInk),
                    cursorBrush = SolidColor(KfPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* 下拉菜单（与卡片同一套视觉）                                          */
/* ------------------------------------------------------------------ */

@Composable
fun KfDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 176.dp, max = 286.dp),
        offset = offset,
        shape = RoundedCornerShape(14.dp),
        containerColor = KfSurface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, KfLine),
        content = content
    )
}

@Composable
fun KfDropdownHeader(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = KfMuted,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
fun KfDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    selected: Boolean = false,
    danger: Boolean = false
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (danger) KfDanger else KfInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            fontSize = 10.5.sp,
                            color = KfMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = KfPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier.heightIn(min = 42.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
fun KfFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .background(KfPrimary, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
