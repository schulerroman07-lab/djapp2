package ch.hirschexpress.dj.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Farben: dunkel (für dunkle Umgebung), Deck A orange, Deck B blau
object DjColors {
    val Background = Color(0xFF101014)
    val Panel = Color(0xFF1B1B22)
    val PanelLight = Color(0xFF2A2A34)
    val Text = Color(0xFFEDEDF2)
    val TextDim = Color(0xFF9A9AA8)
    val DeckA = Color(0xFFFF8A00)
    val DeckB = Color(0xFF2FA8FF)
    val Play = Color(0xFF2ECC71)
    val Cue = Color(0xFFFFC300)
    val Red = Color(0xFFFF3B3B)

    fun deck(i: Int) = if (i == 0) DeckA else DeckB
}

@Composable
fun DjTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DjColors.DeckA,
            secondary = DjColors.DeckB,
            background = DjColors.Background,
            surface = DjColors.Panel,
            onBackground = DjColors.Text,
            onSurface = DjColors.Text,
        ),
        content = content,
    )
}
