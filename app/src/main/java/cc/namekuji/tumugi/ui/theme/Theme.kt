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

fun isColorLight(color: Color): Boolean {
    val r = color.red
    val g = color.green
    val b = color.blue
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return luminance > 0.5f
}

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

    val onPrimaryColor = if (isColorLight(primaryColor)) Color.Black else Color.White

    return if (isBlack) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            onSecondary = onPrimaryColor,
            secondaryContainer = primaryColor.copy(alpha = 0.10f),
            onSecondaryContainer = primaryColor,
            tertiary = primaryColor.copy(alpha = 0.6f),
            onTertiary = onPrimaryColor,
            tertiaryContainer = primaryColor.copy(alpha = 0.05f),
            onTertiaryContainer = primaryColor,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF121212),
            onSurfaceVariant = Color(0xFFCCCCCC),
            outline = Color(0xFF424242),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    } else if (isDark) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            onSecondary = onPrimaryColor,
            secondaryContainer = primaryColor.copy(alpha = 0.10f),
            onSecondaryContainer = primaryColor,
            tertiary = primaryColor.copy(alpha = 0.6f),
            onTertiary = onPrimaryColor,
            tertiaryContainer = primaryColor.copy(alpha = 0.05f),
            onTertiaryContainer = primaryColor,
            background = Color(0xFF121212),
            onBackground = Color(0xFFECEFF1),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFECEFF1),
            surfaceVariant = Color(0xFF2C2C2C),
            onSurfaceVariant = Color(0xFFB0BEC5),
            outline = Color(0xFF37474F),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryColor.copy(alpha = 0.12f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            onSecondary = onPrimaryColor,
            secondaryContainer = primaryColor.copy(alpha = 0.08f),
            onSecondaryContainer = primaryColor,
            tertiary = primaryColor.copy(alpha = 0.6f),
            onTertiary = onPrimaryColor,
            tertiaryContainer = primaryColor.copy(alpha = 0.04f),
            onTertiaryContainer = primaryColor,
            background = Color.White,
            onBackground = Color(0xFF212121),
            surface = Color(0xFFF5F5F5),
            onSurface = Color(0xFF212121),
            surfaceVariant = Color(0xFFEEEEEE),
            onSurfaceVariant = Color(0xFF757575),
            outline = Color(0xFFBDBDBD),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002)
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
