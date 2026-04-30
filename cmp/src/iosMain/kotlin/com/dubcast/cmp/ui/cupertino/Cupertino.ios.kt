package com.dubcast.cmp.ui.cupertino

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Apple Podcasts / TV+ 스타일 팔레트 — 라이트/다크 양쪽 변종.
private data class CupertinoPalette(
    val bg: Color,
    val surfaceElevated: Color,
    val surfaceSubtle: Color,
    val separator: Color,
    val systemBlue: Color,
    val systemRed: Color,
    val systemGreen: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val fillTertiary: Color,
    val ctaBg: Color,             // PrimaryButton 배경 — 라이트는 검정, 다크는 흰
    val ctaFg: Color,             // PrimaryButton 글자 — 반전
    val chipSelectedBg: Color,    // 선택 chip 배경
    val chipSelectedFg: Color,
)

private val DarkCupertinoPalette = CupertinoPalette(
    bg = Color(0xFF000000),
    surfaceElevated = Color(0xFF1C1C1E),
    surfaceSubtle = Color(0xFF2C2C2E),
    separator = Color(0x33FFFFFF),
    systemBlue = Color(0xFF0A84FF),
    systemRed = Color(0xFFFF453A),
    systemGreen = Color(0xFF30D158),
    labelPrimary = Color(0xFFFFFFFF),
    labelSecondary = Color(0x99EBEBF5),
    labelTertiary = Color(0x4DEBEBF5),
    fillTertiary = Color(0x33767680),
    ctaBg = Color.White,
    ctaFg = Color.Black,
    chipSelectedBg = Color.White,
    chipSelectedFg = Color.Black,
)

private val LightCupertinoPalette = CupertinoPalette(
    bg = Color(0xFFF2F2F7),                 // systemGroupedBackground light
    surfaceElevated = Color(0xFFFFFFFF),    // 카드 흰색
    surfaceSubtle = Color(0xFFEFEFF4),
    separator = Color(0x1F000000),          // 12% 검정
    systemBlue = Color(0xFF007AFF),
    systemRed = Color(0xFFFF3B30),
    systemGreen = Color(0xFF34C759),
    labelPrimary = Color(0xFF000000),
    labelSecondary = Color(0x993C3C43),
    labelTertiary = Color(0x4D3C3C43),
    fillTertiary = Color(0x1F767680),
    ctaBg = Color.Black,
    ctaFg = Color.White,
    chipSelectedBg = Color.Black,
    chipSelectedFg = Color.White,
)

@Composable
private fun cupertinoPalette(): CupertinoPalette =
    if (isSystemInDarkTheme()) DarkCupertinoPalette else LightCupertinoPalette

private fun largeTitleStyle(p: CupertinoPalette) = TextStyle(
    fontSize = 34.sp, fontWeight = FontWeight.Bold,
    color = p.labelPrimary, letterSpacing = (-0.4).sp
)
private fun eyebrowStyle(p: CupertinoPalette) = TextStyle(
    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    color = p.labelSecondary, letterSpacing = 0.6.sp
)
private fun bodyStyle(p: CupertinoPalette) = TextStyle(fontSize = 17.sp, color = p.labelPrimary)
private fun footnoteStyle(p: CupertinoPalette) = TextStyle(fontSize = 13.sp, color = p.labelSecondary)
private fun sectionHeaderStyle(p: CupertinoPalette) = TextStyle(
    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    color = p.labelSecondary, letterSpacing = 0.5.sp
)
private fun buttonStyle(p: CupertinoPalette) = TextStyle(
    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.ctaFg
)

@Composable
actual fun PageScaffold(
    title: String,
    modifier: Modifier,
    step: Int,
    content: @Composable () -> Unit
) {
    val p = cupertinoPalette()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.bg)
    ) {
        StepHero(
            step = step,
            title = title,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            content()
        }
    }
}

@Composable
actual fun StepHero(step: Int, title: String, modifier: Modifier, compact: Boolean) {
    val p = cupertinoPalette()
    val titleStyle = if (compact) {
        TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = p.labelPrimary,
            letterSpacing = (-0.2).sp
        )
    } else {
        largeTitleStyle(p)
    }
    Column(modifier = modifier) {
        Text(text = "STEP $step", style = eyebrowStyle(p))
        Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
        Text(text = title, style = titleStyle)
    }
}

@Composable
actual fun Section(
    header: String?,
    footer: String?,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val p = cupertinoPalette()
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(if (header != null) 24.dp else 16.dp))
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = sectionHeaderStyle(p),
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(p.surfaceElevated)
        ) {
            content()
        }
        if (footer != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = footer,
                style = footnoteStyle(p),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
actual fun SectionRow(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val p = cupertinoPalette()
    val rowMod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Column(modifier = rowMod.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            CompositionLocalProvider(LocalTextStyle provides bodyStyle(p)) {
                content()
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(p.separator)
        )
    }
}

@Composable
actual fun BodyText(text: String, modifier: Modifier) {
    Text(text = text, style = bodyStyle(cupertinoPalette()), modifier = modifier)
}

@Composable
actual fun SecondaryText(text: String, modifier: Modifier) {
    Text(text = text, style = footnoteStyle(cupertinoPalette()), modifier = modifier)
}

@Composable
actual fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier
) {
    val p = cupertinoPalette()
    val bg = if (enabled) p.ctaBg else p.fillTertiary
    val fg = if (enabled) p.ctaFg else p.labelTertiary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = buttonStyle(p).copy(color = fg), textAlign = TextAlign.Center)
    }
}

@Composable
actual fun TextButton(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean,
    modifier: Modifier
) {
    val p = cupertinoPalette()
    val color = if (destructive) p.systemRed else p.systemBlue
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(text = text, style = bodyStyle(p).copy(color = color))
    }
}

@Composable
actual fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    val p = cupertinoPalette()
    val trackColor = if (checked) p.systemGreen else Color(0x52787880)
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 22.dp else 2.dp, y = 2.dp)
                .size(27.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

@Composable
actual fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val p = cupertinoPalette()
    val bg = if (selected) p.chipSelectedBg else p.fillTertiary
    val fg = if (selected) p.chipSelectedFg else p.labelPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 15.sp, color = fg, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
actual fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier
) {
    val p = cupertinoPalette()
    Box(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = bodyStyle(p),
            modifier = Modifier.fillMaxWidth(),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(p.systemBlue),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = bodyStyle(p).copy(color = p.labelTertiary))
                    }
                    inner()
                }
            }
        )
    }
}
