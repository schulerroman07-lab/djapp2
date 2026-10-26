package app.fadelux.automix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fadelux.DeckModel
import app.fadelux.MixerModel
import app.fadelux.R
import app.fadelux.audio.NativeEngine
import app.fadelux.automix.transitions.Transition
import app.fadelux.automix.transitions.TransitionContext
import app.fadelux.automix.transitions.Transitions
import app.fadelux.data.AppSettings
import app.fadelux.data.Track
import app.fadelux.ui.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Auto-Mix: spielt die Warteschlange ab und mischt wie ein DJ.
 * Für jedes Song-Paar: Struktur analysieren → passenden Übergang planen → neuen Song
 * sample-genau auf dem Downbeat starten → Übergang ausführen → Tempo sanft zurückführen.
 * Dreht der DJ selbst am Crossfader, gibt Auto-Mix sofort ab.
 */
class AutoMix(
    private val scope: CoroutineScope,
    private val decks: List<DeckModel>,
    private val mixer: MixerModel,
    private val settings: AppSettings,
    private val load: (Int, Track) -> Unit,
) {
    val queue = mutableStateListOf<Track>()
    var running by mutableStateOf(false)
        private set
    var transitioning by mutableStateOf(false)
        private set
    var status by mutableStateOf<UiText?>(null)
        private set

    /** Der nächste geplante Übergang (für die Anzeige mit Countdown). */
    var nextPlan by mutableStateOf<TransitionPlan?>(null)
        private set
    var nextTrack by mutableStateOf<Track?>(null)
        private set
    /** Index des Decks, das gerade läuft (Quelle für den Countdown). */
    var currentDeck by mutableStateOf(0)
        private set
    /** Vom Benutzer gewählter Typ für den nächsten Übergang (null = automatisch). */
    var forcedType by mutableStateOf<TransitionType?>(null)
        private set

    private var job: Job? = null
    private var tempoJob: Job? = null
    private var mixNow = false
    private var replan = false
    private var skip = false
    private val history = mutableListOf<TransitionType>()
    private val structureCache = HashMap<Long, TrackStructure?>()

    init {
        // DJ greift während eines Übergangs ein → Auto-Mix beenden
        mixer.onManualCrossfader = {
            if (running && (transitioning || NativeEngine.isScheduled())) {
                stop()
                status = UiText(R.string.automix_manual)
            }
        }
    }

    // ---------- Warteschlange ----------
    fun add(t: Track) { if (queue.none { it.id == t.id }) queue += t }
    fun addAll(tracks: List<Track>) = tracks.forEach { add(it) }
    fun remove(index: Int) { if (index in queue.indices) queue.removeAt(index) }
    fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        queue.add(to, queue.removeAt(from))
    }
    fun clear() = queue.clear()
    fun optimize() {
        val sorted = TrackAnalysis.optimizeOrder(queue.toList())
        queue.clear()
        queue.addAll(sorted)
    }

    // ---------- Eingriffe ----------
    /** Übergang bald starten (nächste 4-Takt-Grenze) statt am Outro. */
    fun mixNow() { mixNow = true; replan = true }
    /** Typ des nächsten Übergangs ändern (null = wieder automatisch). */
    fun changeType(t: TransitionType?) { forcedType = t; replan = true }
    /** Den vorbereiteten nächsten Song überspringen. */
    fun skipNext() { skip = true }

    /** Countdown bis zum Einsatz des nächsten Songs (Sekunden, Echtzeit) oder null. */
    fun secondsUntilNext(): Double? {
        val p = nextPlan ?: return null
        val d = decks[currentDeck]
        return ((p.mixSec - d.position) / d.tempo.coerceAtLeast(0.5f)).coerceAtLeast(0.0)
    }

    fun plannerSettings(): PlannerSettings {
        val off = settings.disabledTransitions.split(',').mapNotNull { n -> TransitionType.entries.firstOrNull { it.name == n } }.toSet()
        return PlannerSettings(
            enabled = TransitionType.entries.toSet() - off,
            style = MixStyle.entries.getOrElse(settings.mixStyle) { MixStyle.BALANCED },
            lengthBars = settings.transitionBars.coerceAtLeast(0),
        )
    }

    fun start() {
        if (running) return
        if (queue.isEmpty() && decks.none { it.playing }) {
            status = UiText(R.string.automix_empty)
            return
        }
        running = true
        job = scope.launch {
            try { loop() } finally {
                NativeEngine.cancelSchedule()
                running = false
                transitioning = false
                nextPlan = null
                nextTrack = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        tempoJob?.cancel()
        NativeEngine.cancelSchedule()
        // Automatische Effekte, die "hängen bleiben" könnten, zurücknehmen
        decks.forEach { it.setLoop(0.0, 0.0, false); it.setReverb(0f) }
        running = false
        transitioning = false
        nextPlan = null
        nextTrack = null
    }

    private fun side(deck: Int) = if (deck == 0) -1f else 1f

    private suspend fun waitLoaded(d: DeckModel): Boolean =
        withTimeoutOrNull(30_000) {
            delay(150)
            while (d.loadState != 2 || d.peaks.isEmpty()) {
                if (d.loadState == 3) return@withTimeoutOrNull false
                delay(50)
            }
            true
        } ?: false

    /** Struktur des geladenen Titels (aus den Beat-Merkmalen der Engine). */
    private fun structureOf(d: DeckModel): TrackStructure? {
        val id = d.track?.id ?: return null
        structureCache[id]?.let { return it }
        val s = StructureAnalyzer.analyze(d.bpm, d.firstBeat, d.features(), d.duration)
        if (structureCache.size > 50) structureCache.clear()
        structureCache[id] = s
        return s
    }

    /** Plan ohne erkennbares Tempo: kurzer Echo- oder Hall-Ausklang am Ende. */
    private fun fallbackPlan(a: DeckModel, now: Boolean): TransitionPlan {
        val s = plannerSettings()
        val type = listOf(TransitionType.ECHO_OUT, TransitionType.REVERB_WASH, TransitionType.CUT).firstOrNull { it in s.enabled }
            ?: TransitionType.CUT
        val at = if (now) a.position + 2.0 else maxOf(a.position + 4.0, a.duration - 6.0)
        return TransitionPlan(type, -1, at, 0.0, 2, 1f, false)
    }

    private suspend fun loop() {
        // Start: läuft schon ein Deck? Sonst ersten Titel auf Deck A starten.
        var cur = decks.indexOfFirst { it.playing }
        if (cur < 0) {
            val first = queue.removeAt(0)
            load(0, first)
            if (!waitLoaded(decks[0])) { status = UiText(R.string.automix_load_error); return }
            mixer.moveCrossfader(side(0))
            decks[0].seek(0.0)
            decks[0].play(true)
            cur = 0
        }
        outer@ while (scope.isActive) {
            currentDeck = cur
            val a = decks[cur]
            val nxt = 1 - cur
            val b = decks[nxt]
            if (queue.isEmpty()) {
                nextPlan = null
                nextTrack = null
                status = UiText(R.string.automix_last)
                while (a.playing || a.position < a.duration - 1.0) {
                    if (queue.isNotEmpty()) continue@outer      // neuer Titel kam dazu
                    delay(200)
                }
                status = UiText(R.string.automix_done)
                return
            }

            // ---- 1) Nächsten Titel laden ----
            tempoJob?.cancel()
            val next = queue.removeAt(0)
            nextTrack = next
            status = UiText(R.string.automix_next, next.title)
            load(nxt, next)
            if (!waitLoaded(b)) { status = UiText(R.string.automix_load_error); continue }
            b.resetEffects()
            b.changeVolume(a.volume)
            val aStruct = structureOf(a)
            val bStruct = structureOf(b)

            // ---- 2) Planen (und bei Änderungswünschen neu planen) ----
            var plan: TransitionPlan
            var transition: Transition
            var ctx: TransitionContext
            mixNow = false
            replan = true
            skip = false
            plan = fallbackPlan(a, false)
            transition = Transitions.create(plan.type)
            ctx = TransitionContext(a, b, mixer, plan, aStruct, bStruct)
            var centered = false
            while (true) {
                if (skip) {
                    NativeEngine.cancelSchedule()
                    b.resetEffects()
                    skip = false
                    forcedType = null
                    continue@outer
                }
                if (replan) {
                    replan = false
                    NativeEngine.cancelSchedule()
                    b.play(false)
                    b.resetEffects()
                    plan = if (aStruct != null && bStruct != null) {
                        TransitionPlanner.plan(aStruct, a.position, a.tempo, bStruct, plannerSettings(), history, mixNow, forcedType)
                    } else fallbackPlan(a, mixNow)
                    // Tempo des neuen Songs angleichen (nur bei kleinem Unterschied)
                    b.setTempoRatio(plan.tempoRatio)
                    b.seek(plan.bStartSec)
                    transition = Transitions.create(plan.type)
                    ctx = TransitionContext(a, b, mixer, plan, aStruct, bStruct)
                    transition.prepare(ctx)
                    // Sample-genauen Start in der Engine "scharf schalten"
                    NativeEngine.scheduleStart(nxt, cur, plan.mixSec, transition.stopsAAtStart)
                    nextPlan = plan
                    centered = false
                    status = UiText(R.string.automix_waiting, next.title)
                }
                // Vor-Phase (z.B. Hall aufbauen, Echo einschalten)
                val preStart = plan.mixSec - transition.preBars * ctx.aBarSec
                if (transition.preBars > 0 && a.position >= preStart) {
                    transition.pre(ctx, ((a.position - preStart) / (transition.preBars * ctx.aBarSec)).toFloat().coerceIn(0f, 1f))
                }
                if (transition.centerBeforeStart && !centered && a.position >= plan.mixSec - ctx.aBarSec) {
                    mixer.moveCrossfader(0f)   // Mitte: alter Song bleibt voll, neuer ist noch stumm
                    centered = true
                }
                if (b.playing) break           // Engine hat den neuen Song gestartet
                delay(10)
            }

            // ---- 3) Überblenden ----
            transitioning = true
            status = UiText(R.string.automix_mixing, next.title)
            val bBarSec = bStruct?.barSec ?: 2.0
            val lengthTrackSec = plan.lengthBars * bBarSec
            while (true) {
                if (!transitioning) return     // abgebrochen
                val p = ((b.position - plan.bStartSec) / lengthTrackSec).toFloat().coerceIn(0f, 1f)
                transition.step(ctx, p)
                if (p >= 1f) break
                delay(16)
            }
            transition.finish(ctx)

            // ---- 4) Aufräumen ----
            a.play(false)
            a.resetEffects()
            b.resetEffects()
            mixer.moveCrossfader(side(nxt))
            history += plan.type
            if (history.size > 10) history.removeAt(0)
            forcedType = null
            transitioning = false
            nextPlan = null
            nextTrack = null
            cur = nxt
            // Tempo des neuen Songs langsam (über ~16 Takte) zurück zum Original
            if (plan.matchTempo && abs(plan.tempoRatio - 1f) > 0.005f) rampTempoHome(b, 16 * bBarSec)
        }
    }

    /** Tempo unhörbar langsam auf das Originaltempo zurückführen (Keylock: Tonhöhe bleibt). */
    private fun rampTempoHome(d: DeckModel, seconds: Double) {
        tempoJob?.cancel()
        tempoJob = scope.launch {
            val start = d.tempo
            val steps = (seconds * 10).toInt().coerceAtLeast(10)
            for (i in 1..steps) {
                delay(100)
                if (!d.playing) { delay(100); continue }
                d.setTempoRatio(start + (1f - start) * i / steps)
            }
        }
    }
}
