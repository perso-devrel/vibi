package com.example.dubcast.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Input : Screen("input")

    data object Timeline : Screen("timeline/{projectId}") {
        fun createRoute(projectId: String) = "timeline/$projectId"
    }

    data object Export : Screen("export/{projectId}") {
        fun createRoute(projectId: String) = "export/$projectId"
    }

    data object Share : Screen("share/{outputPath}") {
        fun createRoute(outputPath: String) = "share/${Uri.encode(outputPath)}"
    }

}
