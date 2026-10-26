package app.fadelux.automix

import kotlin.math.abs

/**
 * Tempo nach einem Übergang unhörbar zurück zum Originaltempo führen.
 * Weiche S-Kurve (langsam los, langsam an): keine hörbare "Kante" am Anfang oder Ende.
 */
object TempoRamp {
    /** Länge der Rückführung in Takten (8–16), kürzer nur, wenn der Song sonst schon zu Ende geht. */
    fun bars(barsUntilNextMix: Int): Int = when {
        barsUntilNextMix >= 20 -> 16
        barsUntilNextMix >= 12 -> 8
        else -> 4
    }

    /** Tempo an der Stelle progress (0..1) zwischen start und 1.0 (Original). */
    fun value(start: Float, progress: Float): Float {
        val x = progress.coerceIn(0f, 1f)
        val s = x * x * (3 - 2 * x)
        return if (x >= 1f) 1f else start + (1f - start) * s
    }

    /** Braucht es überhaupt eine Rückführung? (Abweichung > 0,1 %) */
    fun needed(tempo: Float): Boolean = abs(tempo - 1f) > 0.001f
}
