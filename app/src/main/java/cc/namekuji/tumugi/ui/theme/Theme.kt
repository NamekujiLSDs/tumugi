package cc.namekuji.tumugi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cc.namekuji.tumugi.data.ThemeMode

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val SharpShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

fun getCustomColorScheme(accentColorHex: String, isDark: Boolean, isBlack: Boolean): ColorScheme {
    val resolvedAccent = if (accentColorHex.lowercase() == "#6650a4") {
        if (isDark) "#ffffff" else "#000000"
    } else accentColorHex

    var primaryColor = try {
        Color(android.graphics.Color.parseColor(resolvedAccent))
    } catch (e: Exception) {
        if (isDark) Color.White else Color.Black
    }

    if (isDark && (resolvedAccent == "#000000" || resolvedAccent.lowercase() == "#111111")) {
        primaryColor = Color.White
    }
    if (!isDark && resolvedAccent.lowercase() == "#ffffff") {
        primaryColor = Color.Black
    }

    return if (isBlack) {
        darkColorScheme(
            primary = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            tertiary = primaryColor.copy(alpha = 0.6f),
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFCCCCCC),
            outline = Color(0xFF424242)
        )
    } else if (isDark) {
        darkColorScheme(
            primary = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            tertiary = primaryColor.copy(alpha = 0.6f),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            surfaceVariant = Color(0xFF2C2C2C),
            onBackground = Color(0xFFECEFF1),
            onSurface = Color(0xFFECEFF1),
            onSurfaceVariant = Color(0xFFB0BEC5),
            outline = Color(0xFF37474F)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            tertiary = primaryColor.copy(alpha = 0.6f),
            background = Color.White,
            surface = Color(0xFFF5F5F5),
            surfaceVariant = Color(0xFFEEEEEE),
            onBackground = Color(0xFF212121),
            onSurface = Color(0xFF212121),
            onSurfaceVariant = Color(0xFF757575),
            outline = Color(0xFFBDBDBD)
        )
    }
}

@Composable
fun TumugiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColorHex: String = "#000000",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val (isDark, isBlack) = when (themeMode) {
        ThemeMode.LIGHT -> false to false
        ThemeMode.DARK -> true to false
        ThemeMode.BLACK -> true to true
        ThemeMode.SYSTEM -> isSystemDark to false
    }

    val colorScheme = getCustomColorScheme(accentColorHex, isDark, isBlack)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SharpShapes,
        content = content
    )
}
