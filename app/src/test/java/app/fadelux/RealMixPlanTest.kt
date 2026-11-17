package app.fadelux

import app.fadelux.automix.AnalysisData
import app.fadelux.automix.FlowCurve
import app.fadelux.automix.FlowSettings
import app.fadelux.automix.GenreFamily
import app.fadelux.automix.Genres
import app.fadelux.automix.MixStyle
import app.fadelux.automix.PlannerSettings
import app.fadelux.automix.QueueFlow
import app.fadelux.automix.StructureAnalyzer
import app.fadelux.automix.TrackStructure
import app.fadelux.automix.TransitionPlan
import app.fadelux.automix.TransitionPlanner
import app.fadelux.automix.TransitionType
import app.fadelux.automix.transitions.MixBus
import app.fadelux.automix.transitions.MixDeck
import app.fadelux.automix.transitions.TransitionContext
import app.fadelux.automix.transitions.Transitions
import app.fadelux.data.Track
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Messung mit echter Musik, Teil 1 (Planen): Der echte Planer der App plant Partys aus den Test-Songs
 * (tools/realmusic/songs.json) – mit der echten Analyse (analyze_cli) – und zeichnet für jeden Übergang
 * die Regler-Verläufe der echten Übergangs-Klassen auf. Teil 2 (tools/realmusic/mixrender) spielt sie
 * mit der echten Audio-Engine ab, Teil 3 (eval_mix.py) misst.
 *
 * Läuft nur mit Umgebungsvariablen (sonst übersprungen):
 *   REALMIX_SONGS   = Pfad zu songs.json
 *   REALMIX_ANALYSIS = Ordner mit <id>.json (Ausgabe von analyze_cli)
 *   REALMIX_PCM     = Ordner mit <id>.pcm (für die Pfade in den Abspiel-Dateien)
 *   REALMIX_OUT     = Ausgabe-Ordner für die Abspiel-Dateien (<n>.mix)
 */
class RealMixPlanTest {

    // ---------- kleiner JSON-Leser (Test-Werkzeug, ohne Bibliothek) ----------
    private class Json(private val s: String) {
        private var i = 0
        fun parse(): Any? { ws(); return value() }
        private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun value(): Any? {
            ws()
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> num()
            }
        }
        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); i++ /* : */; m[k] = value(); ws()
                if (s[i] == ',') { i++; continue }
                i++; return m
            }
        }
        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                if (s[i] == ',') { i++; continue }
                i++; return l
            }
        }
        private fun str(): String {
            val sb = StringBuilder(); i++
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (s[i]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> sb.append(s[i])
                    }
                } else sb.append(s[i])
                i++
            }
            i++; return sb.toString()
        }
        private fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDouble()
        }
    }

    private fun hex(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** Aufzeichner: merkt sich die Regler-Stellungen eines Decks (wie DeckModel, nur ohne Engine). */
    private class RecDeck : MixDeck {
        val eq = FloatArray(3)
        var filter = 0f
        var echoOn = false; var echoAmt = 0.4f; var echoFb = 0.55f
        var rev = 0f
        var loopOn = false; var loopStart = 0.0; var loopLen = 0.0
        override fun setEq(i: Int, v: Float) { eq[i] = v }
        override fun changeFilter(v: Float) { filter = v }
        override fun setEcho(on: Boolean, amount: Float, feedback: Float) { echoOn = on; echoAmt = amount; echoFb = feedback }
        override fun setReverb(send: Float) { rev = send }
        override fun setLoop(startSec: Double, lengthSec: Double, on: Boolean) { loopOn = on; loopStart = startSec; loopLen = lengthSec }
    }

    private class RecBus : MixBus {
        var x = -1f
        override fun moveCrossfader(x: Float) { this.x = x.coerceIn(-1f, 1f) }
    }

    private fun frame(p: Double, a: RecDeck, b: RecDeck, bus: RecBus): String = String.format(
        Locale.ROOT, "%.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %d %.4f %.4f %.4f %.4f %d %.4f %.4f",
        p, a.eq[0], a.eq[1], a.eq[2], b.eq[0], b.eq[1], b.eq[2], a.filter, b.filter,
        if (a.echoOn) 1 else 0, a.echoAmt, a.echoFb, a.rev, bus.x, if (a.loopOn) 1 else 0, a.loopStart, a.loopLen,
    )

    private class Song(val track: Track, val s: TrackStructure, val loud: Double)

    @Test
    fun planPartiesFromRealSongs() {
        val songsPath = System.getenv("REALMIX_SONGS")
        val anaDir = System.getenv("REALMIX_ANALYSIS")
        val pcmDir = System.getenv("REALMIX_PCM")
        val out = System.getenv("REALMIX_OUT")
        assumeTrue("nur mit echten Songs (REALMIX_*)", songsPath != null && anaDir != null && pcmDir != null && out != null)
        File(out!!).mkdirs()
        @Suppress("UNCHECKED_CAST")
        val list = Json(File(songsPath!!).readText()).parse() as List<Map<String, Any?>>
        val songs = LinkedHashMap<String, Song>()
        for ((n, m) in list.withIndex()) {
            val id = m["id"] as String
            val f = File(anaDir, "$id.json")
            if (!f.exists()) continue
            @Suppress("UNCHECKED_CAST")
            val j = Json(f.readText()).parse() as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val beats = (j["beats"] as List<Double>)
            val vocal = (j["vocal"] as List<*>).map { (it as Double).toInt().toByte() }.toByteArray()
            val genre = m["genre"] as String
            val bpm = (j["bpm"] as Double).toFloat()
            val key = (j["key"] as Double).toInt()
            val track = Track(n + 1L, "", m["title"] as String, m["artist"] as String, ((j["duration"] as Double) * 1000).toLong(),
                bpm, (j["energy"] as Double).toFloat(), genre = genre, musicalKey = key, loudness = (j["loudness"] as Double).toFloat())
            val fam = if (genre == "Schlager") GenreFamily.PARTY else Genres.family(track)
            val d = AnalysisData(bpm, beats.firstOrNull() ?: 0.0, (j["downbeat"] as Double).toInt(), (j["confidence"] as Double).toFloat(),
                j["steady"] as Boolean, hex(j["features"] as String), vocal, j["duration"] as Double)
            val s = StructureAnalyzer.analyze(d, j["soundEnd"] as Double, fam) ?: continue
            songs[id] = Song(track, s.copy(musicalKey = key, trackEnergy = track.energy ?: -1f), j["loudness"] as Double)
        }
        val byTrack = songs.entries.associate { it.value.track.id to it.key }
        val orders = listOf(
            "steigernd" to QueueFlow.arrangeByMood(songs.values.map { it.track }, null, emptySet(), FlowSettings(FlowCurve.RISING)),
            "bogen-genre" to QueueFlow.arrangeByMood(songs.values.map { it.track }, null, emptySet(), FlowSettings(FlowCurve.ARC, genreBlocks = true)),
        )
        var n = 0
        val summary = StringBuilder()
        for ((name, order) in orders) {
            val recent = ArrayList<TransitionType>()
            for (k in 0 until order.size - 1) {
                val aId = byTrack[order[k].id]!!
                val bId = byTrack[order[k + 1].id]!!
                val a = songs[aId]!!; val b = songs[bId]!!
                val aPos = min(30.0, a.s.duration * 0.2)
                val plan = TransitionPlanner.plan(a.s, aPos, 1f, b.s, PlannerSettings(style = MixStyle.BALANCED), recent)
                val early = TransitionPlanner.earlyBar(a.s, aPos, 1f, b.s, plan.mixBar)
                recent += plan.type
                n++
                File(out, "%03d.mix".format(n)).writeText(script(n, name, aId, bId, a, b, plan, early, pcmDir!!))
                if (early != null) {
                    // Früh-Übergang (wenn angeboten und angenommen) als eigene Variante messen: Nummer 500 + n
                    val ep = TransitionPlanner.plan(a.s, aPos, 1f, b.s, PlannerSettings(style = MixStyle.BALANCED), recent.dropLast(1), atBar = early)
                    File(out, "%03d.mix".format(500 + n)).writeText(script(500 + n, "$name-früh", aId, bId, a, b, ep, early, pcmDir))
                    summary.append(String.format(Locale.ROOT, "%03d %-12s %s→%s %-10s FRÜH start %.1f von %.1f%n", 500 + n, name, aId, bId, ep.type, ep.mixSec, a.s.soundEnd))
                }
                summary.append(String.format(Locale.ROOT, "%03d %-12s %s→%s %-10s start %.1f von %.1f (letzter Gesang %.1f) Vertrauen %.2f/%.2f früh=%s%n",
                    n, name, aId, bId, plan.type, plan.mixSec, a.s.soundEnd, a.s.lastVocalEnd, a.s.confidence, b.s.confidence, early?.toString() ?: "-"))
            }
        }
        File(out, "plans.txt").writeText(summary.toString())
        println(summary)
        println("Übergänge geplant: $n")
    }

    /** Abspiel-Datei für mixrender: Plan + Regler-Tabellen (vor dem Einsatz über q, im Übergang über p). */
    private fun script(n: Int, order: String, aId: String, bId: String, a: Song, b: Song, plan: TransitionPlan, early: Int?, pcm: String): String {
        val t = Transitions.create(plan.type)
        val da = RecDeck(); val db = RecDeck(); val bus = RecBus()
        val ctx = TransitionContext(da, db, bus, plan, a.s, b.s, aIsLeft = true)
        val sb = StringBuilder()
        fun kv(k: String, v: Any) = sb.append(k).append(' ').append(v).append('\n')
        kv("n", n); kv("order", order); kv("a", aId); kv("b", bId)
        kv("apcm", "$pcm/$aId.pcm"); kv("bpcm", "$pcm/$bId.pcm")
        kv("type", plan.type.name); kv("mixSec", plan.mixSec); kv("bStart", plan.bStartSec); kv("ratio", plan.tempoRatio)
        val lenSec = if (plan.lengthSec > 0) plan.lengthSec else plan.lengthBars * (b.s.barSec)
        kv("lenSec", lenSec)
        kv("preSec", t.preBars * ctx.aBarSec)
        kv("centerSec", if (t.centerBeforeStart) plan.mixSec - ctx.aBarSec else -1.0)
        kv("stopsA", if (t.stopsAAtStart) 1 else 0)
        kv("equalPower", if (t.equalPower) 1 else 0)
        // Lautheit angleichen wie AutoMix.matchLoudness (B auf A, -9…+4 dB)
        val gainDb = (a.loud - b.loud).coerceIn(-9.0, 4.0)
        kv("gainB", 10.0.pow(gainDb / 20.0))
        kv("matchTempo", if (plan.matchTempo) 1 else 0)
        kv("early", early ?: -1)
        kv("aConf", a.s.confidence); kv("bConf", b.s.confidence)
        kv("aLastVocal", a.s.lastVocalEnd); kv("aSoundEnd", a.s.soundEnd)
        kv("aGenre", a.track.genre); kv("bGenre", b.track.genre)
        kv("reason", plan.reason.replace('\n', ' '))
        t.prepare(ctx)
        val steps = 200
        if (t.preBars > 0) {
            kv("pre", steps + 1)
            for (k in 0..steps) { val q = k / steps.toDouble(); t.pre(ctx, q.toFloat()); sb.append(frame(q, da, db, bus)).append('\n') }
        } else kv("pre", 0)
        kv("step", steps + 1)
        for (k in 0..steps) { val p = k / steps.toDouble(); t.step(ctx, p.toFloat()); sb.append(frame(p, da, db, bus)).append('\n') }
        t.finish(ctx)
        sb.append("end\n")
        return sb.toString()
    }

    @Suppress("unused")
    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
}
