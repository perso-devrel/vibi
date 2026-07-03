package com.vibi.cmp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vibi.cmp.ui.auth.LoginScreen
import com.vibi.cmp.ui.input.InputScreen
import com.vibi.cmp.ui.splash.SplashScreen
import com.vibi.cmp.ui.timeline.TimelineScreen
import com.vibi.shared.data.local.AuthEventBus
import org.koin.compose.koinInject

/**
 * 단순 sealed-class 기반 navigation. JetBrains multiplatform navigation-compose 의 안정 버전이
 * maven central 에 미공개라 자체 구현.
 *
 * 흐름: Splash → (signedIn) Input ↔ Timeline / (signedOut) Login → Input
 */
sealed interface Screen {
    data object Splash : Screen
    data object Login : Screen
    data object Input : Screen
    data class Timeline(val projectId: String) : Screen
}

@Composable
fun VibiNavHost() {
    val authEventBus = koinInject<AuthEventBus>()
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Splash)) }

    // 세션 만료(401 → 재인증 필요)를 어느 화면에서 받든 로그인으로 리셋. 토큰은 HTTP 레이어가 이미
    // 폐기했으므로 재로그인만 남는다. Splash/Login 에서 발생해도 stack 재설정이라 멱등.
    LaunchedEffect(authEventBus) {
        authEventBus.sessionExpired.collect {
            stack = listOf(Screen.Login)
        }
    }

    val pop: () -> Unit = {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    val popToInput: () -> Unit = {
        stack = listOf(Screen.Input)
    }

    when (val current = stack.last()) {
        Screen.Splash -> SplashScreen(
            onDone = { signedIn ->
                stack = listOf(if (signedIn) Screen.Input else Screen.Login)
            }
        )

        Screen.Login -> LoginScreen(
            onSignedIn = { stack = listOf(Screen.Input) }
        )

        Screen.Input -> InputScreen(
            onNavigateToTimeline = { projectId ->
                stack = stack + Screen.Timeline(projectId)
            },
            onSignedOut = { stack = listOf(Screen.Login) },
        )

        is Screen.Timeline -> TimelineScreen(
            projectId = current.projectId,
            onBack = pop,
            onSaved = popToInput,
        )
    }
}
