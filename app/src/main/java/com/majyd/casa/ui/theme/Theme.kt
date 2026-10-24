package com.majyd.casa.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.majyd.casa.data.model.ColorTheme

private data class Palette(
    val primary: androidx.compose.ui.graphics.Color,
    val primaryDark: androidx.compose.ui.graphics.Color,
    val secondary: androidx.compose.ui.graphics.Color,
    val secondaryDark: androidx.compose.ui.graphics.Color,
    val tertiary: androidx.compose.ui.graphics.Color,
    val tertiaryDark: androidx.compose.ui.graphics.Color,
)

private fun palette(theme: ColorTheme): Palette = when (theme) {
    ColorTheme.GREEN -> Palette(
        primary = SageGreen,
        primaryDark = SageGreenDark,
        secondary = androidx.compose.ui.graphics.Color(0xFF76558F),
        secondaryDark = androidx.compose.ui.graphics.Color(0xFFD5B9EC),
        tertiary = Butter,
        tertiaryDark = ButterDark,
    )
    ColorTheme.PURPLE -> Palette(
        primary = androidx.compose.ui.graphics.Color(0xFF76558F),
        primaryDark = androidx.compose.ui.graphics.Color(0xFFD5B9EC),
        secondary = androidx.compose.ui.graphics.Color(0xFF99607E),
        secondaryDark = androidx.compose.ui.graphics.Color(0xFFE8B8D0),
        tertiary = androidx.compose.ui.graphics.Color(0xFFB78A48),
        tertiaryDark = androidx.compose.ui.graphics.Color(0xFFF1CF8B),
    )
    ColorTheme.ULTRAMAR -> Palette(
        primary = androidx.compose.ui.graphics.Color(0xFF315D8C),
        primaryDark = androidx.compose.ui.graphics.Color(0xFFBBD3F0),
        secondary = androidx.compose.ui.graphics.Color(0xFF477B91),
        secondaryDark = androidx.compose.ui.graphics.Color(0xFFB8DCE7),
        tertiary = androidx.compose.ui.graphics.Color(0xFFC08B45),
        tertiaryDark = androidx.compose.ui.graphics.Color(0xFFF1CA83),
    )
}

private fun lightColors(theme: ColorTheme) = palette(theme).let { colors ->
    lightColorScheme(
        primary = colors.primary,
        secondary = colors.secondary,
        tertiary = colors.tertiary,
        background = Cream,
        surface = androidx.compose.ui.graphics.Color.White,
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFECE4D8),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        onSecondary = androidx.compose.ui.graphics.Color.White,
        onTertiary = Ink,
    )
}

private fun darkColors(theme: ColorTheme) = palette(theme).let { colors ->
    darkColorScheme(
        primary = colors.primaryDark,
        secondary = colors.secondaryDark,
        tertiary = colors.tertiaryDark,
        background = androidx.compose.ui.graphics.Color(0xFF131513),
        surface = androidx.compose.ui.graphics.Color(0xFF1B1E1B),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C302D),
        onPrimary = Ink,
        onSecondary = Ink,
        onTertiary = Ink,
    )
}

@Composable
fun CasaTheme(
    darkTheme: Boolean = false,
    colorTheme: ColorTheme = ColorTheme.GREEN,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColors(colorTheme) else lightColors(colorTheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
