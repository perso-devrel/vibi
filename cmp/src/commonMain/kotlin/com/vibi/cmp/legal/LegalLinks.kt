package com.vibi.cmp.legal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * 개인정보처리방침 / 이용약관 공개 URL. 단일 소스 — 호스팅 확정 시 여기 두 줄만 교체하면
 * 로그인·결제 화면 등 모든 링크가 따라 바뀐다. 문서 원본은 repo `legal/` 하위.
 */
object LegalUrls {
    // vibi-landing(Next.js)의 /terms · /privacy 라우트로 게시. 문서 원본은 vibi-landing repo
    // legal/*.md. 마케팅/법무 도메인은 www.vibi.fm (api.vibi.fm 은 BFF 서브도메인이라 별개).
    const val TERMS = "https://www.vibi.fm/terms"
    const val PRIVACY = "https://www.vibi.fm/privacy"
}

/**
 * 클릭 시 [url] 을 외부 브라우저로 여는 밑줄 링크 span 을 추가한다.
 * [LinkAnnotation.Url] 은 `Text(AnnotatedString)` 렌더 시 `LocalUriHandler` 와 자동 연동되어
 * Android 는 브라우저, iOS 는 Safari 로 연다 — 별도 클릭 핸들러 배선 불필요.
 */
fun AnnotatedString.Builder.appendLegalLink(label: String, url: String, color: Color) {
    withLink(
        LinkAnnotation.Url(
            url = url,
            styles = TextLinkStyles(
                style = SpanStyle(color = color, textDecoration = TextDecoration.Underline),
            ),
        ),
    ) {
        append(label)
    }
}
