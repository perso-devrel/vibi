package com.vibi.cmp.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Google / Apple 로그인 버튼 — 하나의 공유 컨테이너([SignInButton])로 그려 "완전히 같은 디자인"을 보장한다.
 *
 * 컨테이너 규격(두 버튼 100% 동일): 흰색 배경 + 1dp 회색 테두리 + 28dp 라운드(앱 PrimaryButton 과 동일한
 * pill) + 최소 높이 52dp. 로고와 라벨 텍스트만 다르다.
 *
 * 왜 둘 다 흰색인가: Google 은 흰색/#131314, Apple 은 검정/흰색만 허용한다. 두 브랜드가 공통으로 허용하는
 * 배경은 흰색뿐이라, 흰색으로 통일해야 규정 준수 + 동일 디자인을 동시에 만족한다. Apple 흰색 변형은
 * 테두리를 덧대야 하는데(HIG), 그 테두리가 Google 흰색 버튼 규격과도 일치한다.
 *
 * 로고는 외부 drawable 없이 [ImageVector] 로 인코딩 → Android/iOS 공통 렌더링.
 */

private val ContainerColor = Color(0xFFFFFFFF)
private val LabelColor = Color(0xFF1F1F1F)
private val StrokeColor = Color(0xFFDADCE0) // 밝은 캔버스 위에서도 은은한 hairline 테두리.
private val ButtonShape = RoundedCornerShape(28.dp) // 앱 PrimaryButton 과 동일한 pill.

/**
 * Google/Apple 공용 로그인 버튼 컨테이너. 흰 pill + hairline 테두리 안에 [logo] + [label] 을 가로 배치.
 *
 * @param logo 로고 슬롯 — Google 은 4색 [Image](틴트 없음), Apple 은 단색 [Icon](라벨색 틴트).
 */
@Composable
private fun SignInButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    logo: @Composable () -> Unit,
) {
    // 비활성 시 콘텐츠/테두리만 흐리게(배경 흰색은 유지).
    val contentAlpha = if (enabled) 1f else 0.38f

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(ButtonShape)
            .background(ContainerColor)
            .border(BorderStroke(1.dp, StrokeColor.copy(alpha = contentAlpha)), ButtonShape)
            .clickable(
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = LabelColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(contentAlpha),
            ) {
                logo()
                Spacer(Modifier.width(10.dp))
                Text(
                    text = label,
                    color = LabelColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * "Sign in with Google" — Google Identity 브랜딩 가이드 준수(흰 배경 + 테두리 + 4색 G + 라벨).
 * 컨테이너는 [SignInButton] 공유(=Apple 버튼과 동일 디자인).
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    SignInButton(
        label = "Sign in with Google",
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        modifier = modifier,
    ) {
        Image(
            imageVector = GoogleGLogo,
            contentDescription = null, // 라벨이 접근성 이름을 제공.
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * "Sign in with Apple" — Apple HIG 준수. 로고+텍스트 버튼의 코너 반경은 "앱의 다른 버튼과 맞춤" 규정에 따라
 * 앱 PrimaryButton 과 같은 pill. 흰색 변형 + 테두리 + 검정 로고/텍스트. 컨테이너는 [SignInButton] 공유.
 */
@Composable
fun AppleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    SignInButton(
        label = "Sign in with Apple",
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        modifier = modifier,
    ) {
        Icon(
            imageVector = AppleLogo,
            contentDescription = null, // 라벨이 접근성 이름을 제공.
            tint = LabelColor, // 단색 로고를 라벨색(검정 계열)으로 통일.
            // 원본 뷰포트 384x512(비율 0.75) 유지 → 왜곡 없음. 대략 텍스트 캡 하이트.
            modifier = Modifier.size(width = 13.5.dp, height = 18.dp),
        )
    }
}

/**
 * 공식 멀티컬러 Google "G" 로고(2015 마크)를 [ImageVector] 로 인코딩.
 * 4-path 48x48 SVG 의 정식 "d" 데이터 + 브랜드 4색: blue #4285F4, green #34A853, yellow #FBBC05, red #EA4335.
 * 파일 로드 시 1회만 생성해 재사용.
 */
private val GoogleGLogo: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "GoogleG",
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 48f,
        viewportHeight = 48f,
    ).apply {
        // Blue — 오른팔 + 가로 바.
        addPath(
            pathData = PathParser().parsePathString(
                "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 " +
                    "5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
            ).toNodes(),
            fill = SolidColor(Color(0xFF4285F4)),
        )
        // Green — 아래 획.
        addPath(
            pathData = PathParser().parsePathString(
                "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 " +
                    "2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
            ).toNodes(),
            fill = SolidColor(Color(0xFF34A853)),
        )
        // Yellow — 왼쪽 획.
        addPath(
            pathData = PathParser().parsePathString(
                "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 " +
                    "16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
            ).toNodes(),
            fill = SolidColor(Color(0xFFFBBC05)),
        )
        // Red — 위쪽 획.
        addPath(
            pathData = PathParser().parsePathString(
                "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 " +
                    "0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
            ).toNodes(),
            fill = SolidColor(Color(0xFFEA4335)),
        )
    }.build()
}

/**
 * Apple 로고 마크(사과 + 잎). 표준 Apple 아이콘 SVG(viewBox 0 0 384 512)를 [PathParser] 로 파싱해
 * [ImageVector] 노드로 변환. 파일 로드 시 1회만 생성. 단색 벡터라 [Icon] tint 로 색을 입힌다.
 */
private val AppleLogo: ImageVector = run {
    val pathData =
        "M318.7 268.7c-.2-36.7 16.4-64.4 50-84.8-18.8-26.9-47.2-41.7-84.7-44.6-35.5-2.8-74.3 " +
            "20.7-88.5 20.7-15 0-49.4-19.7-76.4-19.7C63.3 141.2 4 184.8 4 273.5q0 39.3 14.4 " +
            "81.2c12.8 36.7 59 126.7 107.2 125.2 25.2-.6 43-17.9 75.8-17.9 31.8 0 48.3 17.9 76.4 " +
            "17.9 48.6-.7 90.4-82.5 102.6-119.3-65.2-30.7-61.7-90-61.7-91.9zm-56.6-164.2c27.3-32.4 " +
            "24.8-61.9 24-72.5-24.1 1.4-52 16.4-67.9 34.9-17.5 19.8-27.8 44.3-25.6 71.9 26.1 2 " +
            "49.9-11.4 69.5-34.3z"
    ImageVector.Builder(
        name = "AppleLogo",
        defaultWidth = 13.5.dp,
        defaultHeight = 18.dp,
        viewportWidth = 384f,
        viewportHeight = 512f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        // 실제 색은 Icon tint 가 오버라이드하지만, 렌더되려면 non-null fill 이 필요.
        fill = SolidColor(Color.Black),
    ).build()
}
