package com.dubcast.cmp.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dubcast.shared.data.repository.AuthRepository
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val SPLASH_DURATION_MS = 1500L

/**
 * 앱 진입 직후 1.5초간 "vibi" 워드마크. 이 시간 동안 백그라운드에서 토큰 유효성을
 * 확인하고, [onDone] 콜백으로 다음 화면 라우팅 결정을 부모(NavHost)에 위임.
 */
@Composable
fun SplashScreen(
    onDone: (signedIn: Boolean) -> Unit,
    authRepository: AuthRepository = koinInject(),
) {
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        onDone(authRepository.hasValidSession())
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "vibi",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
