package com.yueji.finance.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yueji.finance.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFFFFB800), onPrimary = Color(0xFF2B2200), primaryContainer = Color(0xFFFFE29A), onPrimaryContainer = Color(0xFF241A00),
    secondary = Color(0xFF36342F), onSecondary = Color.White, secondaryContainer = Color(0xFFECE9E2), onSecondaryContainer = Color(0xFF22211E),
    tertiary = Color(0xFF17835B), onTertiary = Color.White, tertiaryContainer = Color(0xFFC3F2DB), onTertiaryContainer = Color(0xFF002116),
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF1B1C1E), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B1C1E),
    surfaceVariant = Color(0xFFF0F1F3), onSurfaceVariant = Color(0xFF67686D),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), outline = Color(0xFFD0D1D5), outlineVariant = Color(0xFFE6E7EA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC83D), onPrimary = Color(0xFF392F00), primaryContainer = Color(0xFF5A4700), onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFD0CDC5), onSecondary = Color(0xFF35332E), secondaryContainer = Color(0xFF47453F), onSecondaryContainer = Color(0xFFEDEAE2),
    tertiary = Color(0xFF6EDBAA), onTertiary = Color(0xFF003824), tertiaryContainer = Color(0xFF005236), onTertiaryContainer = Color(0xFF8CF8C5),
    background = Color(0xFF121315), onBackground = Color(0xFFE4E2E5), surface = Color(0xFF1B1C1F), onSurface = Color(0xFFE4E2E5),
    surfaceVariant = Color(0xFF27282C), onSurfaceVariant = Color(0xFFC5C6CB), error = Color(0xFFFFB4AB), outline = Color(0xFF8E9097), outlineVariant = Color(0xFF424349),
)

@Composable
fun YueJiTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val typography = Typography(
        displaySmall = Typography().displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        headlineSmall = Typography().headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        titleLarge = Typography().titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        titleMedium = Typography().titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = colors, typography = typography, shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    ), content = content)
}
