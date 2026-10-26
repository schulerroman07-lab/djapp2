package app.fadelux.ui.theme

import androidx.compose.ui.graphics.Color

/** Alle Farben der App an einem Ort (Design-System). */
object AppColors {
    // Hintergrund und Flächen
    val Background = Color(0xFF050608)
    val GlassFill = Color(0x14FFFFFF)        // halbtransparentes "Glas"
    val GlassFillStrong = Color(0x22FFFFFF)
    val GlassBorder = Color(0x33FFFFFF)
    val Track = Color(0xFF0B0D10)             // Hintergrund von Reglern/Wellenform

    // Text
    val Text = Color(0xFFF2F5F7)
    val TextDim = Color(0xFF8D96A0)
    val TextFaint = Color(0xFF555D66)

    // Funktionsfarben
    val Emerald = Color(0xFF10B981)          // Standard-Akzent
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFFA78BFA)
    val Pink = Color(0xFFF472B6)
    val Amber = Color(0xFFFBBF24)
    val Red = Color(0xFFF43F5E)
    val Cue = Color(0xFFFBBF24)

    /** Wählbare Akzentfarben (Einstellungen → Design). */
    val accents = listOf(Emerald, Cyan, Violet, Pink, Amber)

    /** Deck A nutzt den Akzent, Deck B eine passende Zweitfarbe. */
    fun deck(index: Int, accent: Color) = if (index == 0) accent else if (accent == Cyan) Violet else Cyan
}
