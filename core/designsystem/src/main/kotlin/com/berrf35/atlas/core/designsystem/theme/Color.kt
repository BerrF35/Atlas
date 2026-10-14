package com.berrf35.atlas.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AtlasColors(
    val surface: Color,
    val surfaceRaised: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val quiet: Color,
    val danger: Color,
    val isDark: Boolean
)

// Tokens strictly from UI_UX_SPEC.md §1.1
val AtlasLightColors = AtlasColors(
    surface = Color(0xFFF7F7F5),
    surfaceRaised = Color(0xFFFFFFFF),
    ink = Color(0xFF151515),
    muted = Color(0xFF686866),
    rule = Color(0xFFD1D1CD),
    quiet = Color(0xFFE8E8E4),
    danger = Color(0xFFA22D2D),
    isDark = false
)

val AtlasDarkColors = AtlasColors(
    surface = Color(0xFF111111),
    surfaceRaised = Color(0xFF191919),
    ink = Color(0xFFF2F2F0),
    muted = Color(0xFFAAA9A5),
    rule = Color(0xFF383836),
    quiet = Color(0xFF272725),
    danger = Color(0xFFFF9B91),
    isDark = true
)

val LocalAtlasColors = staticCompositionLocalOf { AtlasLightColors }
