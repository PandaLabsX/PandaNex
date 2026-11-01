package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppThemeMode {
    DARK_AQUA,
    LIGHT_AQUA,
    SYSTEM_DEFAULT
}

@Immutable
data class AquaExtendedColors(
    val isDark: Boolean,
    val statusNormal: Color,
    val statusWarning: Color,
    val statusCritical: Color,
    val statusInfo: Color,
    val cardBorder: Color,
    val cardSurfaceElevated: Color,
    val glowAqua: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

val LocalAquaColors = staticCompositionLocalOf {
    AquaExtendedColors(
        isDark = true,
        statusNormal = StatusNormalGreen,
        statusWarning = StatusWarningAmber,
        statusCritical = StatusCriticalRed,
        statusInfo = StatusInfoCyan,
        cardBorder = AquaDarkOutline,
        cardSurfaceElevated = AquaDarkSurfaceElevated,
        glowAqua = Color(0x3300E5FF),
        gradientStart = Color(0xFF081933),
        gradientEnd = Color(0xFF050B16)
    )
}

private val DarkAquaColorScheme = darkColorScheme(
    primary = AquaCyanPrimary,
    onPrimary = Color(0xFF002730),
    primaryContainer = Color(0xFF004E5F),
    onPrimaryContainer = Color(0xFFB8F4FF),
    secondary = AquaIceBlue,
    onSecondary = Color(0xFF00344A),
    secondaryContainer = AquaDarkSurfaceElevated,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = AquaTealAccent,
    onTertiary = Color(0xFF00382B),
    tertiaryContainer = Color(0xFF00513F),
    onTertiaryContainer = Color(0xFFA6F7DC),
    background = AquaDarkBackground,
    onBackground = TextPrimaryDark,
    surface = AquaDarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = AquaDarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = AquaDarkOutline,
    outlineVariant = Color(0xFF152A4A),
    error = StatusCriticalRed,
    onError = Color(0xFF3B0004)
)

private val LightAquaColorScheme = lightColorScheme(
    primary = AquaLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F1FF),
    onPrimaryContainer = Color(0xFF002029),
    secondary = AquaLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = AquaLightSurfaceElevated,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = AquaLightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC2F5EC),
    onTertiaryContainer = Color(0xFF00201D),
    background = AquaLightBackground,
    onBackground = TextPrimaryLight,
    surface = AquaLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = AquaLightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = AquaLightOutline,
    outlineVariant = Color(0xFFD2E4F3),
    error = StatusCriticalRedLight,
    onError = Color.White
)

val AquaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun PandaNexTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_AQUA,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.DARK_AQUA -> true
        AppThemeMode.LIGHT_AQUA -> false
        AppThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                // Dark Aqua -> crisp light status bar icons (false); Light Aqua -> crisp dark status bar icons (true)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    val colorScheme = if (useDarkTheme) DarkAquaColorScheme else LightAquaColorScheme

    val extendedColors = if (useDarkTheme) {
        AquaExtendedColors(
            isDark = true,
            statusNormal = StatusNormalGreen,
            statusWarning = StatusWarningAmber,
            statusCritical = StatusCriticalRed,
            statusInfo = StatusInfoCyan,
            cardBorder = Color(0xFF1D3861),
            cardSurfaceElevated = AquaDarkSurfaceElevated,
            glowAqua = Color(0x2E00E5FF),
            gradientStart = Color(0xFF0B1E3B),
            gradientEnd = Color(0xFF050B16)
        )
    } else {
        AquaExtendedColors(
            isDark = false,
            statusNormal = StatusNormalGreenLight,
            statusWarning = StatusWarningAmberLight,
            statusCritical = StatusCriticalRedLight,
            statusInfo = StatusInfoCyanLight,
            cardBorder = Color(0xFFC4DDF2),
            cardSurfaceElevated = Color(0xFFEAF4FC),
            glowAqua = Color(0x1F007B99),
            gradientStart = Color(0xFFE3F3FD),
            gradientEnd = Color(0xFFF3F8FC)
        )
    }

    CompositionLocalProvider(
        LocalAquaColors provides extendedColors,
        LocalAquaAnimationsEnabled provides animationsEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AquaShapes,
            content = content
        )
    }
}

val LocalAquaAnimationsEnabled = staticCompositionLocalOf { true }
