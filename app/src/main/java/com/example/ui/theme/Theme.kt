package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

val MinovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val MinovaDarkColorScheme = darkColorScheme(
    primary = MinovaCyanPrimary,
    onPrimary = Color(0xFF04131E),
    primaryContainer = Color(0xFF0E2942),
    onPrimaryContainer = Color(0xFFD6F4FF),
    secondary = MinovaVioletAccent,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF231F4A),
    onSecondaryContainer = Color(0xFFE4DFFF),
    tertiary = MinovaAzureLight,
    onTertiary = Color(0xFF032539),
    background = MinovaDarkBackground,
    onBackground = MinovaDarkTextPrimary,
    surface = MinovaDarkSurface,
    onSurface = MinovaDarkTextPrimary,
    surfaceVariant = MinovaDarkSurfaceElevated,
    onSurfaceVariant = MinovaDarkTextSecondary,
    outline = MinovaDarkSurfaceBorder,
    outlineVariant = Color(0xFF2E3F63),
    error = MinovaError,
    onError = Color.White,
    errorContainer = Color(0xFF3B1219),
    onErrorContainer = Color(0xFFFECDD3)
)

private val MinovaLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF5B3DF5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF3B21B6),
    tertiary = Color(0xFF0891B2),
    onTertiary = Color.White,
    background = MinovaLightBackground,
    onBackground = MinovaLightTextPrimary,
    surface = MinovaLightSurface,
    onSurface = MinovaLightTextPrimary,
    surfaceVariant = MinovaLightSurfaceElevated,
    onSurfaceVariant = MinovaLightTextSecondary,
    outline = MinovaLightSurfaceBorder,
    outlineVariant = Color(0xFFCBD5E1),
    error = MinovaError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) MinovaDarkColorScheme else MinovaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MinovaShapes,
        content = content
    )
}
