package app.fadelux.automix

import app.fadelux.data.Track
import kotlin.math.abs

/**
 * Tonarten im "Camelot-Rad" (DJ-Standard für harmonisches Mischen):
 * Gleiche Zahl = passt, ±1 = passt, A↔B (Dur/Moll-Parallele) = passt.
 * Interne Tonart: 0..11 = C..H Dur, 12..23 = c..h Moll, -1/null = unbekannt.
 */
object Keys {
    /** Camelot-Code, z.B. 8A (a-Moll) oder 8B (C-Dur); null = unbekannt. */
    fun camelot(key: Int?): Pair<Int, Char>? {
        if (key == null || key !in 0..23) return null
        val pc = key % 12
        return if (key < 12) (((7 * pc) % 12 + 7) % 12 + 1) to 'B'
        else (((7 * pc) % 12 + 4) % 12 + 1) to 'A'
    }

    fun label(key: Int?): String = camelot(key)?.let { "${it.first}${it.second}" } ?: "–"

    /** Wie gut passen zwei Tonarten? 1 = gleich, 0.85–0.9 = harmonisch, 0.5 = unbekannt, darunter = reibt. */
    fun compatibility(a: Int?, b: Int?): Float {
        val ca = camelot(a) ?: return 0.5f
        val cb = camelot(b) ?: return 0.5f
        val d = minOf(abs(ca.first - cb.first), 12 - abs(ca.first - cb.first))
        return when {
            d == 0 && ca.second == cb.second -> 1f
            d == 0 -> 0.9f                       // Dur ↔ parallele Moll
            d == 1 && ca.second == cb.second -> 0.85f
            d == 2 && ca.second == cb.second -> 0.5f   // "Energie-Sprung", geht manchmal
            d == 1 -> 0.4f
            else -> 0.15f
        }
    }
}

/** Genre-Familien für "Genre-Blöcke" (grob, international). */
enum class GenreFamily {
    CHILL, ROCK, OLDIES, POP, HIPHOP, LATIN, PARTY, HOUSE, TECHNO, OTHER
}

object Genres {
    private val keywords: List<Pair<GenreFamily, List<String>>> = listOf(
        GenreFamily.PARTY to listOf("schlager", "volksmusik", "party", "apres", "après", "ballermann", "stimmung", "fox", "oktoberfest", "mallorca", "karneval", "fasnacht", "guggen"),
        GenreFamily.TECHNO to listOf("techno", "trance", "hardstyle", "hardcore", "drum", "dnb", "dubstep", "psy", "uptempo", "hard dance", "rave"),
        GenreFamily.HOUSE to listOf("house", "dance", "electronic", "electro", "edm", "club", "deep"),
        GenreFamily.LATIN to listOf("latin", "reggaeton", "salsa", "bachata", "cumbia", "merengue", "dembow", "samba", "kizomba"),
        GenreFamily.HIPHOP to listOf("hip hop", "hip-hop", "hiphop", "rap", "r&b", "rnb", "trap", "urban", "grime", "drill"),
        GenreFamily.ROCK to listOf("rock", "metal", "punk", "alternative", "indie", "grunge", "hard rock"),
        GenreFamily.OLDIES to listOf("oldies", "disco", "funk", "soul", "motown", "60s", "70s", "80s", "90s", "rock'n'roll", "rock & roll", "schlager oldies"),
        GenreFamily.CHILL to listOf("ambient", "chill", "lounge", "jazz", "classical", "klassik", "acoustic", "ballad", "folk", "singer", "blues", "country", "easy listening"),
        GenreFamily.POP to listOf("pop", "charts", "top 40", "hits"),
    )

    /** Familie aus dem ID3-Genre; sonst grob aus Tempo und Energie geschätzt. */
    fun family(t: Track): GenreFamily {
        val g = t.genre.lowercase()
        if (g.isNotBlank()) {
            // "rock'n'roll"/"disco" vor "rock"/"house" prüfen: spezifische Begriffe zuerst
            if (listOf("rock'n'roll", "rock & roll", "disco").any { g.contains(it) }) return GenreFamily.OLDIES
            for ((fam, words) in keywords) if (words.any { g.contains(it) }) return fam
        }
        return estimate(t.bpm ?: 0f, t.energy ?: -1f)
    }

    /** Schätzung ohne Genre-Tag (nur aus der Audio-Analyse). */
    fun estimate(bpm: Float, energy: Float): GenreFamily = when {
        energy < 0f || bpm <= 0f -> GenreFamily.OTHER
        energy < 0.38f -> GenreFamily.CHILL
        bpm >= 134f && energy >= 0.6f -> GenreFamily.TECHNO
        bpm in 118f..134f && energy >= 0.6f -> GenreFamily.HOUSE
        else -> GenreFamily.POP
    }
}
