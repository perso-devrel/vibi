package com.vibi.cmp.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.shared.domain.model.AuthUser

@Composable
fun UserAvatarButton(
    user: AuthUser?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UserAvatar(
        user = user,
        size = 36.dp,
        initialFontSize = 15.sp,
        modifier = modifier.clickable(onClick = onClick),
        contentDescription = "내 정보",
    )
}

/**
 * 원형 유저 아바타. picture 가 있으면 Coil 로 로드, 없으면 이름/이메일 첫 글자 (대문자) 이니셜.
 * 클릭 동작이 필요하면 [UserAvatarButton] 또는 호출자가 modifier 에 clickable 부여.
 */
@Composable
fun UserAvatar(
    user: AuthUser?,
    size: Dp,
    initialFontSize: TextUnit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val tokens = LocalVibiColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tokens.chipBg)
            .border(width = 1.dp, color = tokens.hairline, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val picture = user?.picture?.takeIf { it.isNotBlank() }
        if (picture != null) {
            AsyncImage(
                model = picture,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            val initial = user?.name?.trim()?.firstOrNull()?.uppercase()
                ?: user?.email?.trim()?.firstOrNull()?.uppercase()
                ?: "?"
            Text(
                text = initial,
                style = TextStyle(
                    fontSize = initialFontSize,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            )
        }
    }
}
