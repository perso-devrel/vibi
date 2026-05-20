package com.vibi.cmp.ui.splash

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
import com.vibi.shared.data.repository.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** 워드마크 노출 최소 시간 — restoreSession() 보다 짧게 끝나면 잔여 시간만 wait. */
private const val SPLASH_MIN_VISIBLE_MS = 600L

/**
 * 앱 진입 직후 "vibi" 워드마크. restoreSession() (백그라운드 JWT 검증) 과 splash 최소 노출 시간을
 * race — 둘 다 끝나야 onDone. 이전엔 1500ms 고정 delay 후 sequential restoreSession 호출이라
 * worst-case 1.5s + restoreSession 시간이 모두 사용자 wait 으로 누적됐다.
 */
@Composable
fun SplashScreen(
    onDone: (signedIn: Boolean) -> Unit,
    authRepository: AuthRepository = koinInject(),
) {
    LaunchedEffect(Unit) {
        coroutineScope {
            val restore = async { authRepository.restoreSession() }
            val timer = launch { delay(SPLASH_MIN_VISIBLE_MS) }
            restore.await()
            timer.join()
        }
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
