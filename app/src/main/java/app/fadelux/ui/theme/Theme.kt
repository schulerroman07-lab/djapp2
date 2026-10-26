package app.fadelux.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Aktuelle Akzentfarbe (in den Einstellungen wählbar). */
val LocalAccent = staticCompositionLocalOf { AppColors.Emerald }

@Composable
fun AppTheme(accent: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = accent,
                secondary = AppColors.Cyan,
                background = AppColors.Background,
                surface = Color(0xFF111418),
                onBackground = AppColors.Text,
                onSurface = AppColors.Text,
                onPrimary = Color.Black,
            ),
            content = content,
        )
    }
}
