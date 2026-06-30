package com.example.multibarcode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF00B863)
val BrandGreenDark = Color(0xFF00874A)
val ScanAccent = Color(0xFF00E676)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F3CF),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4D6357),
    background = Color(0xFFF6FBF6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EFE8),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = ScanAccent,
    onPrimary = Color(0xFF00391E),
    primaryContainer = BrandGreenDark,
    onPrimaryContainer = Color(0xFFB7F3CF),
    secondary = Color(0xFFB3CCBC),
    background = Color(0xFF101814),
    surface = Color(0xFF17211B),
    surfaceVariant = Color(0xFF2A332C),
    error = Color(0xFFFF6B6B),
)

@Composable
fun MultiBarcodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
