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
import kotlin.math.log10
import kotlin.math.pow

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

    /** Protokoll zum Testen: welcher Übergang wann und warum (neueste zuerst). */
    val logLines = mutableStateListOf<String>()

    private var job: Job? = null
    private var tempoJob: Job? = null
    private var rampDeck = -1           // Deck, dessen Tempo gerade zurückgeführt wird
    private var mixNow = false
    private var replan = false
    private var skip = false
    private val history = mutableListOf<TransitionType>()
    private val structureCache = HashMap<Long, TrackStructure?>()

    init {
        // Während Auto-Mix ist der Crossfader gesperrt (versehentliche Wischgesten stoppen nichts mehr).
        // Übernehmen nur bewusst über "Manuell übernehmen" → takeOver().
        mixer.locked = { running }
    }

    // ---------- Warteschlange ----------
    /** Songs, die der DJ selbst platziert hat (verschoben, "Als Nächstes", "Ans Ende") – bleiben, wo sie sind. */
    val pinned = mutableStateListOf<Long>()

    /** Song, nach dem die Warteschlange beginnt (schon vorbereiteter nächster Song oder der laufende). */
    private fun flowRef(): Track? =
        nextTrack ?: decks.firstOrNull { it.playing }?.track ?: decks.getOrNull(currentDeck)?.track

    fun add(t: Track) = addAll(listOf(t))

    /**
     * Songs hinzufügen. Mit automatischer Sortierung (Einstellung) wird jeder Song dort
     * eingereiht, wo er am besten passt (Genre, BPM, Energie); sonst ans Ende.
     */
    fun addAll(tracks: List<Track>, sorted: Boolean = settings.queueAutoSort) {
        val fresh = tracks.distinctBy { it.id }.filter { t -> queue.none { it.id == t.id } }
        if (!sorted) { queue.addAll(fresh); return }
        for (t in fresh) {
            val headPinned = queue.firstOrNull()?.id?.let { it in pinned } == true
            queue.add(QueueFlow.bestGap(queue, flowRef(), t, headPinned), t)
        }
    }

    /** In genau dieser Reihenfolge ans Ende (z.B. Playlist zufällig gemischt). */
    fun addInOrder(tracks: List<Track>) = addAll(tracks, sorted = false)

    fun remove(index: Int) {
        if (index in queue.indices) pinned.remove(queue.removeAt(index).id)
    }

    /** Vom DJ verschoben → angeheftet. */
    fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val t = queue.removeAt(from)
        queue.add(to, t)
        if (t.id !in pinned) pinned += t.id
    }

    /** "Als Nächstes spielen" */
    fun playNext(index: Int) {
        if (index !in queue.indices) return
        if (index == 0) { if (queue[0].id !in pinned) pinned += queue[0].id; return }
        move(index, 0)
    }

    /** Song (aus der Bibliothek) als Nächstes spielen – ist er schon in der Warteschlange, wird er nach vorne geholt. */
    fun addNext(t: Track) {
        val i = queue.indexOfFirst { it.id == t.id }
        if (i >= 0) playNext(i) else { queue.add(0, t); pinned += t.id }
    }

    /** Song (aus der Bibliothek) ans Ende der Warteschlange. */
    fun addLast(t: Track) {
        val i = queue.indexOfFirst { it.id == t.id }
        if (i >= 0) moveToEnd(i) else { queue.add(t); pinned += t.id }
    }

    /** "Ans Ende" */
    fun moveToEnd(index: Int) {
        if (index !in queue.indices) return
        if (index == queue.lastIndex) { if (queue[index].id !in pinned) pinned += queue[index].id; return }
        move(index, queue.lastIndex)
    }

    fun clear() { queue.clear(); pinned.clear() }

    /** Ganze Warteschlange für den besten Flow ordnen (angeheftete Songs bleiben). */
    fun optimize() = rearrange(QueueFlow.arrange(queue.toList(), flowRef(), pinned.toSet()))

    /** Smart Shuffle: zufällig, aber mit gutem Flow (BPM/Energie/Genre). */
    fun smartShuffle() = rearrange(QueueFlow.arrange(queue.toList(), flowRef(), pinned.toSet(), kotlin.random.Random(System.nanoTime())))

    private fun rearrange(order: List<Track>) {
        if (order.map { it.id } == queue.map { it.id }) return
        queue.clear()
        queue.addAll(order)
    }

    /** Ersten Song aus der Warteschlange nehmen (wird jetzt gespielt). */
    private fun takeNext(): Track {
        val t = queue.removeAt(0)
        pinned.remove(t.id)
        return t
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

    /** DJ übernimmt bewusst: Auto-Mix endet, alles bleibt so, wie es gerade klingt. */
    fun takeOver() {
        if (!running) return
        log("DJ übernimmt manuell")
        stop()
        status = UiText(R.string.automix_manual)
    }

    fun stop() {
        job?.cancel()
        tempoJob?.cancel()
        NativeEngine.cancelSchedule()
        // Automatische Effekte, die "hängen bleiben" könnten, zurücknehmen
        decks.forEach { it.setLoop(0.0, 0.0, false); it.setReverb(0f) }
        // Steht der Crossfader am Ende, sofort zurück zur DJ-Kurve (sonst erst, wenn der DJ ihn ans Ende zieht)
        if (kotlin.math.abs(mixer.crossfader) >= 0.98f) mixer.setCurve(false)
        if (running) log("Auto-Mix gestoppt")
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
        val s = StructureAnalyzer.analyze(d.bpm, d.firstBeat, d.features(), d.duration, StructureAnalyzer.soundEnd(d.peaks, d.duration))
            ?.copy(trackEnergy = d.track?.energy ?: -1f)
        if (structureCache.size > 50) structureCache.clear()
        structureCache[id] = s
        return s
    }

    /** Plan ohne erkennbares Tempo: kurzer Echo- oder Hall-Ausklang am Ende. */
    private fun fallbackPlan(a: DeckModel, now: Boolean): TransitionPlan {
        val s = plannerSettings()
        val type = listOf(TransitionType.ECHO_OUT, TransitionType.REVERB_WASH, TransitionType.CUT).firstOrNull { it in s.enabled }
            ?: TransitionType.CUT
        // Ende = letzter hörbarer Moment (Stille am Schluss zählt nicht)
        val end = StructureAnalyzer.soundEnd(a.peaks, a.duration)
        val at = if (now) a.position + 2.0 else maxOf(a.position + 4.0, end - 6.0)
        return TransitionPlan(type, -1, at, 0.0, 2, 1f, false)
    }

    private suspend fun loop() {
        // Start: läuft schon ein Deck? Sonst ersten Titel auf Deck A starten.
        var cur = decks.indexOfFirst { it.playing }
        if (cur < 0) {
            val first = takeNext()
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
            // (Die Tempo-Rückführung von A läuft dabei weiter – sie wird NICHT abgebrochen.)
            val next = takeNext()
            nextTrack = next
            status = UiText(R.string.automix_next, next.title)
            load(nxt, next)
            if (!waitLoaded(b)) { status = UiText(R.string.automix_load_error); continue }
            b.resetEffects()
            b.changeVolume(a.volume)
            matchLoudness(a, b)
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
                    if (centered) mixer.moveCrossfader(side(cur))
                    mixer.setCurve(false)
                    log("Übersprungen: ${next.title}")
                    skip = false
                    forcedType = null
                    continue@outer
                }
                if (replan) {
                    replan = false
                    NativeEngine.cancelSchedule()
                    b.play(false)
                    b.resetEffects()
                    if (centered) mixer.moveCrossfader(side(cur))   // Crossfader zurück zu A (B ist noch stumm)
                    // Läuft A noch in der Tempo-Rückführung? Dann gilt für die Planung das Zieltempo (Original),
                    // ausser "Jetzt mixen": dann Rückführung anhalten und mit dem aktuellen Tempo planen.
                    if (mixNow && rampRunning(a)) stopRamp()
                    val aTempo = if (rampRunning(a)) 1f else a.tempo
                    plan = if (aStruct != null && bStruct != null) {
                        TransitionPlanner.plan(aStruct, a.livePosition(), aTempo, bStruct, plannerSettings(), history, mixNow, forcedType)
                    } else fallbackPlan(a, mixNow)
                    // Tempo des neuen Songs angleichen (nur bei kleinem Unterschied) – Tonhöhe bleibt (Keylock)
                    if (TempoRamp.needed(plan.tempoRatio)) b.ensureKeylock()
                    b.setTempoRatio(plan.tempoRatio)
                    b.seek(plan.bStartSec)
                    transition = Transitions.create(plan.type)
                    ctx = TransitionContext(a, b, mixer, plan, aStruct, bStruct)
                    // Kurve nur umschalten, solange der Crossfader ganz bei A steht (kein Lautstärkesprung)
                    mixer.setCurve(transition.equalPower)
                    transition.prepare(ctx)
                    log("Plan ${a.track?.title ?: "?"} → ${next.title}: ${plan.type.name} · ${plan.lengthBars} Takte · " +
                        "Einsatz bei ${mmss(plan.mixSec)}" + (if (plan.reason.isNotEmpty()) " · ${plan.reason}" else ""))
                    // Sample-genauen Start in der Engine "scharf schalten"
                    NativeEngine.scheduleStart(nxt, cur, plan.mixSec, transition.stopsAAtStart)
                    nextPlan = plan
                    centered = false
                    status = UiText(R.string.automix_waiting, next.title)
                }
                // Vor-Phase (z.B. Hall aufbauen, Echo einschalten)
                val aPos = a.livePosition()
                // Sicherheit (sehr kurze Songs): Rückführung kurz vor dem Einsatz abschliessen
                if (rampRunning(a) && aPos >= plan.mixSec - 2 * ctx.aBarSec) { stopRamp(); a.setTempoRatio(1f) }
                val preStart = plan.mixSec - transition.preBars * ctx.aBarSec
                if (transition.preBars > 0 && aPos >= preStart) {
                    transition.pre(ctx, ((aPos - preStart) / (transition.preBars * ctx.aBarSec)).toFloat().coerceIn(0f, 1f))
                }
                if (transition.centerBeforeStart && !centered && aPos >= plan.mixSec - ctx.aBarSec) {
                    mixer.moveCrossfader(0f)   // Mitte: alter Song bleibt voll, neuer ist noch stumm
                    centered = true
                }
                if (b.livePlaying()) break     // Engine hat den neuen Song gestartet
                delay(10)
            }

            // ---- 3) Überblenden ----
            transitioning = true
            status = UiText(R.string.automix_mixing, next.title)
            log("Übergang startet: ${plan.type.name} bei ${mmss(a.livePosition())} (geplant ${mmss(plan.mixSec)})")
            val bBarSec = bStruct?.barSec ?: 2.0
            val lengthTrackSec = plan.lengthBars * bBarSec
            while (true) {
                if (!transitioning) return     // abgebrochen
                val p = ((b.livePosition() - plan.bStartSec) / lengthTrackSec).toFloat().coerceIn(0f, 1f)
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
            mixer.setCurve(false)                     // Crossfader steht ganz bei B → Kurve zurück
            log("Übergang fertig: ${next.title} läuft (Tempo ${"%+.1f".format((b.tempo - 1f) * 100)} %)")
            history += plan.type
            if (history.size > 10) history.removeAt(0)
            forcedType = null
            transitioning = false
            nextPlan = null
            nextTrack = null
            cur = nxt
            // Tempo des neuen Songs fliessend (über 8–16 Takte) zurück zum Original
            if (TempoRamp.needed(b.tempo)) {
                val bars = bStruct?.let { TempoRamp.bars(it.endBar - 32 - it.barAt(b.livePosition())) } ?: 16
                rampTempoHome(b, bars, bBarSec)
            }
        }
    }

    /**
     * Lautheit angleichen: B so lauter/leiser stellen, dass er so laut klingt wie A gerade
     * (inkl. dessen Angleichung). Begrenzt auf -9…+4 dB, damit nichts übersteuert.
     */
    private fun matchLoudness(a: DeckModel, b: DeckModel) {
        val la = a.loudness() + 20f * log10(a.gain.coerceAtLeast(0.01f))
        val lb = b.loudness()
        if (la < -60f || lb < -60f) { b.applyGain(1f); return }
        val db = (la - lb).coerceIn(-9f, 4f)
        b.applyGain(10f.pow(db / 20f))
        log("Lautheit: A ${"%.1f".format(la)} dB, B ${"%.1f".format(lb)} dB → B ${"%+.1f".format(db)} dB")
    }

    private fun mmss(sec: Double): String {
        val s = sec.coerceAtLeast(0.0).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** Eintrag ins Protokoll (auch in Logcat unter "AutoMix"). */
    internal fun log(text: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())
        android.util.Log.i("AutoMix", text)
        logLines.add(0, "$time  $text")
        if (logLines.size > 60) logLines.removeAt(logLines.lastIndex)
    }

    private fun rampRunning(d: DeckModel) = rampDeck == d.index && tempoJob?.isActive == true

    private fun stopRamp() {
        tempoJob?.cancel()
        rampDeck = -1
    }

    /**
     * Tempo unhörbar auf das Originaltempo zurückführen (Keylock: Tonhöhe bleibt).
     * Der Fortschritt richtet sich nach der Song-Position (nicht nach der Uhr):
     * so stimmt die Länge in Takten, und bei Pause wartet die Rückführung einfach.
     */
    private fun rampTempoHome(d: DeckModel, bars: Int, barSec: Double) {
        stopRamp()
        val start = d.tempo
        val startPos = d.livePosition()
        val length = bars * barSec          // in Song-Sekunden
        rampDeck = d.index
        tempoJob = scope.launch {
            while (true) {
                val p = ((d.livePosition() - startPos) / length).toFloat()
                d.setTempoRatio(TempoRamp.value(start, p))
                if (p >= 1f) break
                delay(50)
            }
            log("Tempo zurück auf 100 % (${"%.1f".format((start - 1f) * 100)} % über $bars Takte)")
        }
    }
}
