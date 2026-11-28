package com.adhdcompanion.focus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val AmoledDarkColors = darkColorScheme(
    primary = FocusEmerald,
    onPrimary = AmoledBlack,
    primaryContainer = FocusEmeraldDim,
    onPrimaryContainer = FocusEmerald,
    secondary = FocusCyan,
    onSecondary = AmoledBlack,
    secondaryContainer = AmoledSurfaceHigh,
    onSecondaryContainer = OnAmoled,
    tertiary = FocusAmber,
    onTertiary = AmoledBlack,
    tertiaryContainer = Color(0xFF332609),
    onTertiaryContainer = FocusAmber,
    error = FocusCoral,
    onError = AmoledBlack,
    errorContainer = FocusCoralContainer,
    onErrorContainer = FocusCoral,
    background = AmoledBlack,
    onBackground = OnAmoled,
    surface = AmoledSurface,
    onSurface = OnAmoled,
    surfaceVariant = AmoledSurfaceElevated,
    onSurfaceVariant = OnAmoledMuted,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineSubtle,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurface,
    surfaceContainer = AmoledSurfaceElevated,
    surfaceContainerHigh = AmoledSurfaceHigh
)

private val AmoledShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ADHDFocusCompanionTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AmoledDarkColors,
        typography = FocusTypography,
        shapes = AmoledShapes,
        content = content
    )
}
