package com.dubcast.cmp.ui.cupertino

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 플랫폼 분기 UI 위젯 (expect/actual).
 *
 * iosMain: iOS HIG 직접 구현 — Large Title, grouped list, system blue, etc.
 * androidMain: Material3 wrappers (현행 유지).
 *
 * 화면 코드는 commonMain 에 두고 본 위젯들로만 조립한다.
 */

@Composable
expect fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    content: @Composable () -> Unit
)

/** 스텝 라벨 + Large Title hero 블럭 — PageScaffold 안 쓰는 화면에서 헤더만 일관되게 쓸 때.
 *  [compact] true 면 작은 타이틀 변종 (Timeline / Export 처럼 영상 위에 헤더 올릴 때). */
@Composable
expect fun StepHero(
    step: Int,
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
)

@Composable
expect fun Section(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)

/** Section 내부의 한 행. content 는 행 본문 (좌측 라벨 + 우측 액션 자유 구성). */
@Composable
expect fun SectionRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
)

@Composable
expect fun BodyText(text: String, modifier: Modifier = Modifier)

@Composable
expect fun SecondaryText(text: String, modifier: Modifier = Modifier)

@Composable
expect fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
)

@Composable
expect fun TextButton(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    modifier: Modifier = Modifier
)

@Composable
expect fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
)

@Composable
expect fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)

@Composable
expect fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
)
