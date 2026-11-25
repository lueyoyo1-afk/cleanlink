package me.ipromptnest.cleanlink.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 站点设计系统搬过来的那套：冷灰纸面 / 墨色硬边 / 荧光橘只做强调。
 * 不另起一套，作品集里所有东西看起来得是同一双手做的。
 */
@Immutable
data class CLColors(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val scrim: Color,
)

private val LightColors = CLColors(
    paper = Color(0xFFEFF1F5),
    card = Color(0xFFFCFDFE),
    ink = Color(0xFF0E1116),
    muted = Color(0xFF6B7280),
    line = Color(0xFFD9DEE7),
    accent = Color(0xFFFF5C2E),
    accentSoft = Color(0x14FF5C2E),
    scrim = Color(0x8A0E1116),
)

private val DarkColors = CLColors(
    paper = Color(0xFF0B0D11),
    card = Color(0xFF161A20),
    ink = Color(0xFFEDEFF3),
    muted = Color(0xFF8B94A2),
    line = Color(0xFF2B323B),
    accent = Color(0xFFFF6B3D),
    accentSoft = Color(0x33FF6B3D),
    scrim = Color(0xB3000000),
)

val LocalCLColors = staticCompositionLocalOf { LightColors }

object CLType {
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 23.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.6).sp,
    )
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val monoStrong = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
}

@Composable
fun CleanLinkTheme(
    dark: Boolean = isSystemInDarkTheme(),
    activity: Activity? = null,
    drawSystemBarIconsDark: Boolean = !dark,
    content: @Composable () -> Unit,
) {
    val cl = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (activity != null && !view.isInEditMode) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = drawSystemBarIconsDark
                isAppearanceLightNavigationBars = drawSystemBarIconsDark
            }
        }
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = cl.accent,
            background = cl.paper,
            surface = cl.card,
            onPrimary = Color(0xFF0E1116),
            onBackground = cl.ink,
            onSurface = cl.ink,
        )
    } else {
        lightColorScheme(
            primary = cl.accent,
            background = cl.paper,
            surface = cl.card,
            onPrimary = Color(0xFF0E1116),
            onBackground = cl.ink,
            onSurface = cl.ink,
        )
    }
    CompositionLocalProvider(LocalCLColors provides cl) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = CLType.body, titleLarge = CLType.title),
            content = content,
        )
    }
}
