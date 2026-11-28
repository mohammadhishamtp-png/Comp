package com.adhdcompanion.focus.ui.theme

import androidx.compose.ui.graphics.Color

// True OLED Pitch-Black Minimalist Palette
val AmoledBlack = Color(0xFF000000)
val AmoledSurface = Color(0xFF080A0F)
val AmoledSurfaceElevated = Color(0xFF10131A)
val AmoledSurfaceHigh = Color(0xFF171C26)
val AmoledOutline = Color(0xFF1E2432)
val AmoledOutlineSubtle = Color(0xFF131722)

// High-Contrast Accents (YPT x Regain x Minimalist)
val FocusEmerald = Color(0xFF2EE6A6)
val FocusEmeraldDim = Color(0xFF0D2E24)
val FocusOrange = Color(0xFFFF9F43)
val FocusCyan = Color(0xFF38BDF8)
val FocusIndigo = Color(0xFF818CF8)
val FocusAmber = Color(0xFFFBBF24)
val FocusCoral = Color(0xFFFB7185)
val FocusCoralContainer = Color(0xFF36101B)

// Text Colors on True Black
val OnAmoled = Color(0xFFF8FAFC)
val OnAmoledMuted = Color(0xFF8E9BAE)
val OnAmoledSubtle = Color(0xFF5A6578)

// Backward-compatible aliases
val FocusTeal = FocusEmerald
val FocusTealDark = Color(0xFF0F766E)
val BgDark = AmoledBlack
val SurfaceDark = AmoledSurface
val SurfaceDarkAlt = AmoledSurfaceElevated
val OnDark = OnAmoled
val OnDarkMuted = OnAmoledMuted

val BgLight = Color(0xFFFAFAF9)
val SurfaceLight = Color(0xFFFFFFFF)
val OnLight = Color(0xFF1C1E27)
val OnLightMuted = Color(0xFF6B7280)

val SubjectColorPalette: List<Long> = listOf(
    0xFF38BDF8L, // Sky Blue
    0xFFA855F7L, // Vivid Violet
    0xFF2EE6A6L, // Emerald
    0xFFFF9F43L, // Warm Orange (YPT signature)
    0xFFFB7185L, // Coral
    0xFF818CF8L, // Indigo
    0xFF4ADE80L, // Mint
    0xFFF472B6L  // Rose
)
