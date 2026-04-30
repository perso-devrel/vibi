package com.dubcast.cmp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dubcast.cmp.ui.input.InputScreen
import com.dubcast.cmp.ui.timeline.TimelineScreen

/**
 * 단순 sealed-class 기반 navigation. JetBrains multiplatform navigation-compose 의 안정 버전이
 * maven central 에 미공개라 자체 구현. 저장 흐름을 timeline 의 헤더 "저장" 버튼으로 통합한 뒤
 * 별도 Export/Share 화면이 사라져 2 화면 (Input → Timeline) 만 남는다.
 */
sealed interface Screen {
    data object Input : Screen
    data class Timeline(val projectId: String) : Screen
}

@Composable
fun DubCastNavHost() {
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Input)) }
    val pop: () -> Unit = {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    val popToInput: () -> Unit = {
        stack = listOf(Screen.Input)
    }

    when (val current = stack.last()) {
        Screen.Input -> InputScreen(
            onNavigateToTimeline = { projectId ->
                stack = stack + Screen.Timeline(projectId)
            }
        )

        is Screen.Timeline -> TimelineScreen(
            projectId = current.projectId,
            onBack = pop,
            onSaved = popToInput,
        )
    }
}
