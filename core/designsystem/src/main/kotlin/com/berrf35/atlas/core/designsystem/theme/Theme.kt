package com.berrf35.atlas.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun AtlasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) AtlasDarkColors else AtlasLightColors
    val typography = DefaultAtlasTypography

    CompositionLocalProvider(
        LocalAtlasColors provides colors,
        LocalAtlasTypography provides typography,
        content = content
    )
}

object AtlasTheme {
    val colors: AtlasColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAtlasColors.current

    val typography: AtlasTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAtlasTypography.current
}
