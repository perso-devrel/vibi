package com.example.dubcast.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.dubcast.ui.export.ExportScreen
import com.example.dubcast.ui.input.InputScreen
import com.example.dubcast.ui.share.ShareScreen
import com.example.dubcast.ui.timeline.TimelineScreen

@Composable
fun DubCastNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Input.route,
        modifier = modifier
    ) {
        composable(Screen.Input.route) {
            InputScreen(
                onNavigateToTimeline = { projectId ->
                    navController.navigate(Screen.Timeline.createRoute(projectId))
                }
            )
        }

        composable(
            route = Screen.Timeline.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) {
            TimelineScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExport = { projectId ->
                    navController.navigate(Screen.Export.createRoute(projectId))
                }
            )
        }

        composable(
            route = Screen.Export.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) {
            ExportScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToShare = { outputPath ->
                    navController.navigate(Screen.Share.createRoute(outputPath))
                }
            )
        }

        composable(
            route = Screen.Share.route,
            arguments = listOf(navArgument("outputPath") { type = NavType.StringType })
        ) {
            ShareScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Screen.Input.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
