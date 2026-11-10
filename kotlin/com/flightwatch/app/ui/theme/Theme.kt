package com.flightwatch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF0B3D66)
private val NavyLight = Color(0xFF1C5D93)
private val Sand = Color(0xFFF6F8FB)
private val Gold = Color(0xFFE8A33D)
private val Teal = Color(0xFF00897B)
private val Danger = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F3),
    onPrimaryContainer = Navy,
    secondary = Teal,
    onSecondary = Color.White,
    tertiary = Gold,
    onTertiary = Color.Black,
    background = Sand,
    onBackground = Color(0xFF16202B),
    surface = Color.White,
    onSurface = Color(0xFF16202B),
    surfaceVariant = Color(0xFFE7EDF3),
    onSurfaceVariant = Color(0xFF44505C),
    error = Danger,
    outline = Color(0xFFB4C0CC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC3EA),
    onPrimary = Color(0xFF00344F),
    primaryContainer = NavyLight,
    onPrimaryContainer = Color(0xFFD3E4F3),
    secondary = Color(0xFF6FD6C6),
    onSecondary = Color(0xFF00382F),
    tertiary = Gold,
    onTertiary = Color.Black,
    background = Color(0xFF10161D),
    onBackground = Color(0xFFE3E9EF),
    surface = Color(0xFF1A222B),
    onSurface = Color(0xFFE3E9EF),
    surfaceVariant = Color(0xFF2B3540),
    onSurfaceVariant = Color(0xFFC3CDD8),
    error = Color(0xFFFF8A80),
)

@Composable
fun FlightWatchTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
