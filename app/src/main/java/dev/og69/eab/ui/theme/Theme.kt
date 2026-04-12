package dev.og69.eab.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = SurfaceTint,
    secondary = CoralPrimaryDark,
    surface = Color(0xFFFFFBF8),
    surfaceContainerHighest = Color(0xFFF2E8E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59A),
    onPrimary = Color(0xFF4A1F00),
    primaryContainer = Color(0xFF6B3010),
    secondary = Color(0xFFE8A088),
)

@Composable
fun TogetherEabTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content
    )
}
