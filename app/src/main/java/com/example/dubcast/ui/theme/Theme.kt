package com.example.dubcast.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightStudioColorScheme = lightColorScheme(
    primary = CharcoalPrimary,
    onPrimary = TextOnCharcoal,
    primaryContainer = CharcoalContainer,
    onPrimaryContainer = OnCharcoalContainer,
    secondary = SlateSecondary,
    onSecondary = TextOnCharcoal,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = OrangeTertiary,
    onTertiary = TextOnCharcoal,
    tertiaryContainer = OrangeContainer,
    onTertiaryContainer = TextPrimary,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceBright = StudioSurfaceBright,
    error = ErrorRed,
    onError = TextOnCharcoal,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorRed,
    outline = BorderLine,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = StudioBackground,
)

@Composable
fun DubCastTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightStudioColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = StudioBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
