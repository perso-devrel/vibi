package com.example.dubcast.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun rememberParsedColor(hex: String, fallback: Color = Color.Black): Color =
    remember(hex) {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }
            .getOrDefault(fallback)
    }
