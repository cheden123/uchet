package com.uchet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B3D62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E4F5),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFF4A6A84),
    tertiary = Color(0xFF2E7D32),
    surface = Color(0xFFFAFCFF),
    background = Color(0xFFF4F7FA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCBF5),
    onPrimary = Color(0xFF003352),
    primaryContainer = Color(0xFF074A6B),
    onPrimaryContainer = Color(0xFFD2E4F5),
    secondary = Color(0xFFB5CBE0),
    tertiary = Color(0xFFA5D6A7)
)

@Composable
fun PipeTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
