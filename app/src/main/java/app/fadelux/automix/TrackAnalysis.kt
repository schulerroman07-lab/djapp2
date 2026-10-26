package app.fadelux.automix

import app.fadelux.data.Track
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

/** Hilfsfunktionen für Auto-Mix und Vorschläge. */
object TrackAnalysis {

    /**
     * Intro-Ende und Outro-Anfang (Sekunden) aus der Wellenform (100 Werte/s) erkennen:
     * Wo wird es "richtig laut" und wo wird es wieder leiser?
     */
    fun introOutro(peaks: FloatArray): Pair<Double, Double> {
        if (peaks.size < 1000) return 0.0 to peaks.size / 100.0
        val win = 200  // 2 Sekunden glätten
        val smooth = FloatArray(peaks.size)
        var sum = 0.0
        for (i in peaks.indices) {
            sum += peaks[i]
            if (i >= win) sum -= peaks[i - win]
            smooth[i] = (sum / min(i + 1, win)).toFloat()
        }
        val sorted = smooth.copyOf().apply { sort() }
        val thr = sorted[sorted.size / 2] * 0.75f
        val intro = smooth.indexOfFirst { it > thr }.coerceAtLeast(0)
        val outro = smooth.indexOfLast { it > thr }.coerceAtLeast(intro)
        return intro / 100.0 to outro / 100.0
    }

    /** Abstand zweier Tempi in "6-%-Schritten" (halbes/doppeltes Tempo zählt als passend). */
    fun bpmDistance(a: Float, b: Float): Float {
        if (a <= 0f || b <= 0f) return 3f
        val d = listOf(b, b * 2, b / 2).minOf { abs(ln(it / a)) }
        return (d * 100.0 / 6.0).toFloat()
    }

    /** Tempo-Verhältnis, damit b gleich schnell wie a läuft (halb/doppelt berücksichtigt). */
    fun tempoRatio(targetBpm: Float, trackBpm: Float): Float? {
        if (targetBpm <= 0f || trackBpm <= 0f) return null
        val r = listOf(targetBpm / trackBpm, targetBpm / (trackBpm * 2), targetBpm * 2 / trackBpm)
            .minBy { abs(ln(it)) }
        return if (abs(r - 1f) <= 0.16f) r else null
    }

    /** Reihenfolge für einen guten Flow: ähnliche BPM nebeneinander, Energie steigt langsam an. */
    fun optimizeOrder(tracks: List<Track>): List<Track> {
        if (tracks.size < 3) return tracks
        val rest = tracks.toMutableList()
        val result = mutableListOf(rest.minBy { it.energy ?: 0.5f }.also { rest.remove(it) })
        while (rest.isNotEmpty()) {
            val cur = result.last()
            val ce = cur.energy ?: 0.5f
            val next = rest.minBy { t ->
                bpmDistance(cur.bpm ?: 0f, t.bpm ?: 0f) + abs((t.energy ?: 0.5f) - (ce + 0.03f)) * 4f
            }
            rest.remove(next)
            result += next
        }
        return result
    }
}

/** Ein Vorschlag "Passt als nächstes". */
data class Suggestion(val track: Track, val isWish: Boolean)

object Suggestions {
    /** Passt ein Gästewunsch (Song aus der Liste oder Freitext) zu einem Titel der Bibliothek? */
    fun matchWish(text: String, all: List<Track>): Track? {
        val q = text.lowercase().trim()
        if (q.length < 3) return null
        return all.firstOrNull { "${it.artist} – ${it.title}".lowercase() == q }
            ?: all.firstOrNull { it.title.length >= 3 && q.contains(it.title.lowercase()) }
    }

    /**
     * Vorschläge: zuerst Gästewünsche, die in der Bibliothek sind,
     * dann Titel mit ähnlichem Tempo (±6 %) und ähnlicher Energie.
     */
    fun suggest(ref: Track?, refBpm: Float, all: List<Track>, wishTexts: List<String>, exclude: Set<Long>): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        val used = HashSet<Long>(exclude)
        // 1) Gästewünsche, die zu einem Titel der Bibliothek passen
        for (w in wishTexts) {
            val hit = matchWish(w, all.filter { it.id !in used })
            if (hit != null) { out += Suggestion(hit, true); used += hit.id }
        }
        // 2) Ähnliches Tempo und Energie
        if (ref != null && refBpm > 0f) {
            val e = ref.energy ?: 0.5f
            all.asSequence()
                .filter { it.id !in used && it.id != ref.id && (it.bpm ?: 0f) > 0f }
                .map { it to TrackAnalysis.bpmDistance(refBpm, it.bpm!!) }
                .filter { it.second <= 1f }
                .sortedBy { (t, d) -> d + abs((t.energy ?: 0.5f) - e) * 3f }
                .take(30)
                .forEach { out += Suggestion(it.first, false) }
        }
        return out
    }
}
