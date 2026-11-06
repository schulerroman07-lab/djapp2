package app.fadelux.automix

import app.fadelux.data.Track
import kotlin.math.abs
import kotlin.math.min
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
        // Tonart: harmonisch passende Songs bevorzugen (unbekannt = neutral)
        c += (1f - Keys.compatibility(a.musicalKey, b.musicalKey)) * 0.6f
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

    // ------------------------------------------------------------------
    // Reihenfolge nach Stimmung: Energie-Kurve und/oder Genre-Blöcke
    // ------------------------------------------------------------------

    /**
     * Ziel-Energie (0..1, als Rang innerhalb der Songs) an der Stelle x (0 = erster, 1 = letzter Song).
     * RISING: ruhig beginnen, stetig steigern bis zum Höhepunkt am Schluss.
     * ARC ("Party-Bogen"): Warm-up → Höhepunkt bei ca. 70 % → Ausklang.
     */
    fun target(curve: FlowCurve, x: Float): Float {
        val p = x.coerceIn(0f, 1f)
        return when (curve) {
            FlowCurve.ARC -> when {
                p < 0.2f -> 0.1f + p / 0.2f * 0.45f                    // Warm-up 0.10 → 0.55
                p < 0.7f -> 0.55f + (p - 0.2f) / 0.5f * 0.45f           // Aufbau bis Peak 1.0
                else -> 1f - (p - 0.7f) / 0.3f * 0.6f                   // Ausklang 1.0 → 0.4
            }
            else -> 0.05f + 0.95f * p                                   // RISING (und Original)
        }
    }

    /**
     * Ordnet die Warteschlange nach Stimmung. Angeheftete Songs bleiben an ihrer Position.
     * - Energie-Kurve: Songs werden den Positionen nach ihrem Energie-Rang zugeteilt; bei
     *   ähnlicher Energie gewinnt der Song, der vom Tempo/Tonart/Genre her besser anschliesst.
     * - Genre-Blöcke: Songs derselben Genre-Familie bleiben zusammen; Blöcke von ruhig nach
     *   energiegeladen (z.B. Rock → Party/Schlager → Techno), innerhalb jedes Blocks gilt die Kurve.
     * - Original (ohne Blöcke): Reihenfolge bleibt unverändert.
     * Es gibt nie ein "passt nicht" – es wird immer der bestmögliche Ablauf gebaut.
     */
    fun arrangeByMood(queue: List<Track>, prev: Track?, pinned: Set<Long>, flow: FlowSettings): List<Track> {
        if (queue.size < 2) return queue
        if (flow.curve == FlowCurve.ORIGINAL && !flow.genreBlocks) return queue
        val fixed = queue.withIndex().filter { it.value.id in pinned }.associate { it.index to it.value }
        val free = queue.filter { it.id !in pinned }
        if (free.isEmpty()) return queue
        val m = free.size

        // Energie-Rang 0..1 (bei Gleichstand nach Tempo)
        val ranked = free.sortedWith(compareBy<Track>({ it.energy ?: 0.5f }, { it.bpm ?: 0f }))
        val rank = HashMap<Long, Float>()
        ranked.forEachIndexed { i, t -> rank[t.id] = if (m == 1) 0.5f else i / (m - 1f) }

        // Blöcke bilden
        val blocks: List<List<Track>> = if (flow.genreBlocks) {
            val groups = LinkedHashMap<GenreFamily, MutableList<Track>>()
            for (t in free) groups.getOrPut(Genres.family(t)) { mutableListOf() } += t
            val list = groups.values.toList()
            if (flow.curve == FlowCurve.ORIGINAL) list                                    // Reihenfolge des ersten Auftretens
            else list.sortedBy { b -> b.map { rank[it.id] ?: 0.5f }.average() }           // ruhig → energiegeladen
        } else listOf(free)

        val order = ArrayList<Track>(m)
        var last = prev
        var slot = 0
        for (block in blocks) {
            val rest = block.toMutableList()
            if (flow.curve == FlowCurve.ORIGINAL) { order += rest; last = rest.last(); slot += rest.size; continue }
            // Rang innerhalb des Blocks (damit jeder Block die Kurve "abfährt")
            val inBlock = block.sortedWith(compareBy<Track>({ it.energy ?: 0.5f }, { it.bpm ?: 0f }))
            val bRank = HashMap<Long, Float>()
            inBlock.forEachIndexed { i, t -> bRank[t.id] = if (inBlock.size == 1) 0.5f else i / (inBlock.size - 1f) }
            // Ziel-Werte dieses Abschnitts der Kurve auf 0..1 strecken
            val targets = (0 until block.size).map { k -> target(flow.curve, if (m == 1) 0f else (slot + k) / (m - 1f)) }
            val tMin = targets.min()
            val tMax = targets.max()
            for (k in block.indices) {
                val tn = if (tMax - tMin < 1e-3f) (if (block.size == 1) 0.5f else k / (block.size - 1f))
                else (targets[k] - tMin) / (tMax - tMin)
                val ref = last
                // Übergangs-Kosten begrenzt (max. 0,5 ≈ 2 Plätze Abweichung): sonst bleibt ein Song, der
                // schlecht zu allen anderen passt (z.B. ruhiger Chill-Song), bis zum Schluss übrig
                // und landet beim "Steigernd"-Ablauf ausgerechnet am Höhepunkt.
                val next = rest.minBy { t ->
                    abs((bRank[t.id] ?: 0.5f) - tn) * 4f + (ref?.let { min(cost(it, t), 2f) * 0.25f } ?: 0f)
                }
                rest.remove(next)
                order += next
                last = next
            }
            slot += block.size
        }

        // Angeheftete Songs wieder an ihre Plätze
        val out = ArrayList<Track>(queue.size)
        var fi = 0
        for (i in queue.indices) out += fixed[i] ?: order[fi++]
        return out
    }
}

/** Ablauf-Kurve der Party. */
enum class FlowCurve { RISING, ARC, ORIGINAL }

/** Einstellungen für die Reihenfolge. */
data class FlowSettings(val curve: FlowCurve = FlowCurve.ARC, val genreBlocks: Boolean = false)
