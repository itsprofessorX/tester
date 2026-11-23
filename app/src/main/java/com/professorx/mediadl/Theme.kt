package com.professorx.mediadl

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class MediaDownPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean
)

val LocalMediaColors = staticCompositionLocalOf {
    MediaDownPalette(
        background = Color(0xFF000000),
        surface = Color(0xFF141414),
        surfaceElevated = Color(0xFF1C1C1C),
        border = Color(0xFF252525),
        textPrimary = Color(0xFFFFFFFF),
        textMuted = Color(0xFF8A8A8A),
        accent = Color(0xFFFF3B4A),
        onAccent = Color(0xFFFFFFFF),
        good = Color(0xFF4ADE80),
        warning = Color(0xFFFBBF24),
        error = Color(0xFFEF4444),
        isDark = true
    )
}

@Composable
fun MediaDownTheme(
    prefs: UserPreferences,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = when (prefs.themeMode) {
        AppThemeMode.AMOLED, AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    val useAmoled = prefs.themeMode == AppThemeMode.AMOLED || (prefs.themeMode == AppThemeMode.SYSTEM && systemDark)

    val dynamicScheme = if (prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else null

    val accent = dynamicScheme?.primary ?: prefs.accentPreset.color

    val palette = if (useDark) {
        MediaDownPalette(
            background = if (useAmoled) Color(0xFF000000) else Color(0xFF0D0D10),
            surface = if (useAmoled) Color(0xFF141414) else Color(0xFF1A1A1F),
            surfaceElevated = if (useAmoled) Color(0xFF1E1E1E) else Color(0xFF24242B),
            border = Color(0xFF252525),
            textPrimary = Color(0xFFFFFFFF),
            textMuted = Color(0xFF8E8E93),
            accent = accent,
            onAccent = Color.White,
            good = Color(0xFF4ADE80),
            warning = Color(0xFFFBBF24),
            error = Color(0xFFEF4444),
            isDark = true
        )
    } else {
        MediaDownPalette(
            background = Color(0xFFF6F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFEEEEF2),
            border = Color(0xFFE2E2E8),
            textPrimary = Color(0xFF111114),
            textMuted = Color(0xFF6B6B73),
            accent = accent,
            onAccent = Color.White,
            good = Color(0xFF16A34A),
            warning = Color(0xFFD97706),
            error = Color(0xFFDC2626),
            isDark = false
        )
    }

    val scale = if (prefs.largeTextScale) 1.15f else 1.0f
    val typography = Typography(
        headlineMedium = TextStyle(fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontSize = (20 * scale).sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = (16 * scale).sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = (15 * scale).sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = (13 * scale).sp, fontWeight = FontWeight.Normal),
        labelMedium = TextStyle(fontSize = (12 * scale).sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = (11 * scale).sp, fontWeight = FontWeight.Medium)
    )

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            outline = palette.border
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            outline = palette.border
        )
    }

    CompositionLocalProvider(LocalMediaColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
