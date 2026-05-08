package com.dubcast.cmp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dubcast.cmp.ui.auth.LoginScreen
import com.dubcast.cmp.ui.input.InputScreen
import com.dubcast.cmp.ui.splash.SplashScreen
import com.dubcast.cmp.ui.timeline.TimelineScreen

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
fun DubCastNavHost() {
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Splash)) }
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
