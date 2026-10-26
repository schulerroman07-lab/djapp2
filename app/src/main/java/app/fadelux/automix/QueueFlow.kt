package app.fadelux.automix

import app.fadelux.data.Track
import kotlin.math.abs
import kotlin.random.Random

/**
 * Warteschlange mit gutem "Flow": Songs werden so eingereiht, dass Genre, Tempo (BPM)
 * und Energie möglichst sanft wechseln. Passt ein Song nirgends gut, landet er dort,
 * wo der Sprung am kleinsten ist (z.B. zwischen zwei Songs, die ihn "überbrücken").
 * Es gibt NIE eine Fehlermeldung – es wird immer der bestmögliche Ablauf gebaut.
 *
 * Vom DJ verschobene Songs ("angeheftet") behalten ihren Platz.
 */
object QueueFlow {

    /** Hauptgenre vereinfacht (Kleinschreibung, nur der erste Eintrag bei "House/Dance"). */
    fun genreKey(g: String): String =
        g.lowercase().split('/', ';', ',', '|').firstOrNull()?.trim().orEmpty()

    /**
     * "Kosten" eines Übergangs von a nach b: 0 = perfekt, je grösser desto holpriger.
     * Tempo: 1 Punkt pro 6 % Abstand (halbes/doppeltes Tempo passt). Genre: anderes Genre 1 Punkt.
     * Energie: grosse Sprünge kosten, ein kleiner Anstieg ist sogar erwünscht (Party baut sich auf).
     */
    fun cost(a: Track, b: Track): Float {
        var c = if ((a.bpm ?: 0f) > 0f && (b.bpm ?: 0f) > 0f) TrackAnalysis.bpmDistance(a.bpm!!, b.bpm!!) else 1f
        val ga = genreKey(a.genre)
        val gb = genreKey(b.genre)
        c += when {
            ga.isEmpty() || gb.isEmpty() -> 0.3f
            ga == gb -> 0f
            ga.contains(gb) || gb.contains(ga) -> 0.3f     // z.B. "house" / "deep house"
            else -> 1f
        }
        val ea = a.energy ?: 0.5f
        val eb = b.energy ?: 0.5f
        val d = eb - (ea + 0.03f)                          // leichter Anstieg = ideal
        c += abs(d) * 3f + (if (d < -0.15f) (-d - 0.15f) * 2f else 0f)
        return c
    }

    /**
     * Neue Songs einreihen (günstigste Stelle). Bestehende Songs ändern ihre Reihenfolge nicht.
     * @param prev  Song, der vor der Warteschlange läuft (oder null)
     * @param headPinned true = der erste Song wurde vom DJ gesetzt ("Als Nächstes") → nichts davor
     */
    fun insertAll(queue: List<Track>, prev: Track?, newTracks: List<Track>, headPinned: Boolean = false): List<Track> {
        val out = queue.toMutableList()
        for (t in newTracks) {
            if (out.any { it.id == t.id }) continue
            out.add(bestGap(out, prev, t, headPinned), t)
        }
        return out
    }

    /** Beste Lücke (0..size) für t. */
    fun bestGap(queue: List<Track>, prev: Track?, t: Track, headPinned: Boolean): Int {
        if (queue.isEmpty()) return 0
        var best = queue.size
        var bestCost = Float.MAX_VALUE
        for (gap in (if (headPinned) 1 else 0)..queue.size) {
            val before = if (gap == 0) prev else queue[gap - 1]
            val after = queue.getOrNull(gap)
            val added = (before?.let { cost(it, t) } ?: 0f) + (after?.let { cost(t, it) } ?: 0f)
            val removed = if (before != null && after != null) cost(before, after) else 0f
            val delta = added - removed
            // bei Gleichstand lieber weiter hinten (nicht vordrängeln)
            if (delta < bestCost - 1e-4f || (abs(delta - bestCost) <= 1e-4f && gap > best)) {
                bestCost = delta
                best = gap
            }
        }
        return best
    }

    /**
     * Ganze Warteschlange neu ordnen. Angeheftete Songs bleiben an ihrer Position.
     * @param random null = immer der beste nächste Song; sonst "Smart Shuffle": zufällig
     *               unter den gut passenden (bleibt trotzdem ein guter Flow).
     */
    fun arrange(queue: List<Track>, prev: Track?, pinned: Set<Long>, random: Random? = null): List<Track> {
        if (queue.size < 2) return queue
        val fixed = queue.withIndex().filter { it.value.id in pinned }.associate { it.index to it.value }
        val rest = queue.filter { it.id !in pinned }.toMutableList()
        if (random != null) rest.shuffle(random)
        val out = ArrayList<Track>(queue.size)
        var last = prev
        for (i in queue.indices) {
            val f = fixed[i]
            if (f != null) { out += f; last = f; continue }
            val ref = last
            val next = when {
                ref == null && random == null -> rest.minBy { it.energy ?: 0.5f }   // ruhig beginnen
                ref == null -> rest.first()
                random == null -> rest.minBy { cost(ref, it) }
                else -> {
                    // Smart Shuffle: zufällig aus den 3 besten (aber nie ein grober Ausreisser)
                    val sorted = rest.sortedBy { cost(ref, it) }
                    val bestCost = cost(ref, sorted.first())
                    val good = sorted.take(3).filter { cost(ref, it) <= bestCost + 1.5f }
                    good[random.nextInt(good.size)]
                }
            }
            rest.remove(next)
            out += next
            last = next
        }
        return out
    }

    /** Summe aller Übergangs-Kosten (kleiner = besserer Flow). */
    fun totalCost(queue: List<Track>, prev: Track? = null): Float {
        var c = 0f
        var last = prev
        for (t in queue) { last?.let { c += cost(it, t) }; last = t }
        return c
    }
}
