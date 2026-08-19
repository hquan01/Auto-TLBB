package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFBCEBFF),
    secondary = EmeraldGreen,
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF005234),
    onSecondaryContainer = Color(0xFF86F8B6),
    tertiary = WarningAmber,
    onTertiary = Color(0xFF452B00),
    error = ErrorRed,
    onError = Color.White,
    background = DarkNavyBg,
    onBackground = TextPrimary,
    surface = DarkNavyCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHighlight,
    onSurfaceVariant = TextSecondary,
    outline = DarkNavyCardBorder
)

private val LightColorScheme = darkColorScheme(
    // Default to dark cyber aesthetic for auto clicker game utility
    primary = DeepCyan,
    onPrimary = Color.White,
    secondary = EmeraldGreen,
    background = DarkNavyBg,
    surface = DarkNavyCard
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
