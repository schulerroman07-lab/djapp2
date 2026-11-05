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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/** Frage "Passt perfekt – jetzt mischen?" (Früh-Übergang) mit Ablaufzeit. */
data class EarlyOffer(val nextTitle: String, val deadlineMs: Long, val bar: Int)

/**
 * Der Auto-DJ: spielt die Warteschlange selbstständig ab und mischt wie ein DJ.
 * Für jedes Song-Paar: Struktur analysieren → passenden Übergang planen → neuen Song
 * sample-genau auf dem Downbeat starten → Übergang ausführen → Tempo sanft zurückführen.
 *
 * Intern laufen zwei "Decks" der Audio-Engine im Wechsel; die Oberfläche sieht nur
 * "aktueller Song" und "Als Nächstes".
 */
class AutoMix(
    private val scope: CoroutineScope,
    private val decks: List<DeckModel>,
    private val mixer: MixerModel,
    private val settings: AppSettings,
    /** Titel in ein Deck laden. false = Datei nicht lesbar. */
    private val load: suspend (Int, Track) -> Boolean,
    /** Neueste Daten eines Titels (z.B. nach der Analyse: BPM, Tonart, Energie). */
    private val refresh: (Track) -> Track = { it },
) {
    val queue = mutableStateListOf<Track>()
    var running by mutableStateOf(false)
        private set
    var transitioning by mutableStateOf(false)
        private set
    var status by mutableStateOf<UiText?>(null)
        private set

    /** Der Song, der gerade läuft ("on air"). */
    var current by mutableStateOf<Track?>(null)
        private set
    /** Der nächste geplante Übergang (für die Anzeige). */
    var nextPlan by mutableStateOf<TransitionPlan?>(null)
        private set
    var nextTrack by mutableStateOf<Track?>(null)
        private set
    /** Index des Decks, das gerade läuft (Quelle für Position/Countdown). */
    var currentDeck by mutableStateOf(0)
        private set
    /** Vom Benutzer gewählter Typ für den nächsten Übergang (null = automatisch). */
    var forcedType by mutableStateOf<TransitionType?>(null)
        private set
    /** Offene Frage "Jetzt mischen?" (Früh-Übergang) oder null. */
    var earlyOffer by mutableStateOf<EarlyOffer?>(null)
        private set

    /** Protokoll zum Testen: welcher Übergang wann und warum (neueste zuerst). */
    val logLines = mutableStateListOf<String>()

    /** Bereits gespielte Songs (für "Zurück"), neuester zuletzt. */
    private val played = mutableListOf<Track>()

    private var job: Job? = null
    private var tempoJob: Job? = null
    private var rampDeck = -1           // Deck, dessen Tempo gerade zurückgeführt wird
    private var mixNow = false
    private var quickRequest = false    // "Weiter" gedrückt
    private var prevRequest = false     // "Zurück" gedrückt
    private var prevTransition = false  // aktueller Übergang ist ein "Zurück"
    private var replan = false
    private var skip = false
    private var earlyAnswer: Boolean? = null
    private val typeHistory = mutableListOf<TransitionType>()
    private val structureCache = HashMap<Long, TrackStructure?>()

    // ---------- Warteschlange ----------
    /** Songs, die der Nutzer selbst platziert hat (verschoben, "Als Nächstes", Wünsche) – bleiben, wo sie sind. */
    val pinned = mutableStateListOf<Long>()

    /** Song, nach dem die Warteschlange beginnt (schon vorbereiteter nächster Song oder der laufende). */
    private fun flowRef(): Track? = nextTrack ?: current

    fun flowSettings() = FlowSettings(
        FlowCurve.entries.getOrElse(settings.flowCurve) { FlowCurve.ARC },
        settings.genreBlocks,
    )

    fun add(t: Track) = addAll(listOf(t))

    /** Songs hinzufügen: dort einreihen, wo sie am besten passen (Genre, BPM, Energie, Tonart). */
    fun addAll(tracks: List<Track>, sorted: Boolean = settings.queueAutoSort) {
        val fresh = tracks.map(refresh).distinctBy { it.id }.filter { t -> queue.none { it.id == t.id } }
        if (!sorted) { queue.addAll(fresh); return }
        for (t in fresh) {
            val headPinned = queue.firstOrNull()?.id?.let { it in pinned } == true
            queue.add(QueueFlow.bestGap(queue, flowRef(), t, headPinned), t)
        }
    }

    /** In genau dieser Reihenfolge ans Ende. */
    fun addInOrder(tracks: List<Track>) = addAll(tracks, sorted = false)

    /**
     * Playlist starten: Reihenfolge nach Stimmung (Einstellung) und sofort losspielen.
     * Läuft schon Musik, geht es nach dem aktuellen Song mit der neuen Playlist weiter.
     */
    fun startPlaylist(tracks: List<Track>, shuffle: Boolean = false) {
        val fresh = tracks.map(refresh).distinctBy { it.id }.filter { it.id != current?.id }
        if (fresh.isEmpty()) { status = UiText(R.string.automix_empty); return }
        queue.clear()
        pinned.clear()
        queue.addAll(if (shuffle) fresh.shuffled() else QueueFlow.arrangeByMood(fresh, current, emptySet(), flowSettings()))
        log("Playlist: ${fresh.size} Songs, Ablauf ${if (shuffle) "zufällig" else flowSettings().toString()}")
        if (running) skip = true          // vorbereiteten Song verwerfen → erster Song der neuen Playlist
        else start()
    }

    /**
     * Warteschlange neu nach Stimmung ordnen (z.B. wenn die Analyse neue Werte geliefert hat).
     * Angeheftete Songs bleiben an ihrem Platz.
     */
    fun arrangeQueue() {
        if (queue.size < 2) return
        val fresh = queue.map(refresh)
        val order = QueueFlow.arrangeByMood(fresh, flowRef(), pinned.toSet(), flowSettings())
        if (order != queue.toList()) {
            queue.clear()
            queue.addAll(order)
        }
    }

    /**
     * Gästewunsch einreihen: an der passendsten Stelle unter den nächsten Songs (nicht einfach ans
     * Ende, nicht abrupt dazwischen). false = schon gespielt/eingereiht.
     */
    fun insertWish(t: Track): Boolean {
        val tr = refresh(t)
        if (tr.id == current?.id || tr.id == nextTrack?.id || queue.any { it.id == tr.id }) return false
        if (played.takeLast(10).any { it.id == tr.id }) return false
        val window = queue.take(5)
        val headPinned = queue.firstOrNull()?.id?.let { it in pinned } == true
        val gap = QueueFlow.bestGap(window, flowRef(), tr, headPinned)
        queue.add(gap, tr)
        pinned += tr.id
        log("Wunsch eingereiht: ${tr.title} (Platz ${gap + 1})")
        return true
    }

    fun remove(index: Int) {
        if (index in queue.indices) pinned.remove(queue.removeAt(index).id)
    }

    /** Vom Nutzer verschoben → angeheftet. */
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

    /** "Ans Ende" */
    fun moveToEnd(index: Int) {
        if (index !in queue.indices) return
        if (index == queue.lastIndex) { if (queue[index].id !in pinned) pinned += queue[index].id; return }
        move(index, queue.lastIndex)
    }

    /** Song (aus der Bibliothek) als Nächstes spielen – ist er schon in der Warteschlange, wird er nach vorne geholt. */
    fun addNext(t: Track) {
        val i = queue.indexOfFirst { it.id == t.id }
        if (i >= 0) playNext(i) else { queue.add(0, refresh(t)); pinned += t.id }
    }

    /** Song (aus der Bibliothek) ans Ende der Warteschlange. */
    fun addLast(t: Track) {
        val i = queue.indexOfFirst { it.id == t.id }
        if (i >= 0) moveToEnd(i) else { queue.add(refresh(t)); pinned += t.id }
    }

    fun clear() { queue.clear(); pinned.clear() }

    /** Ganze Warteschlange für den besten Flow ordnen (angeheftete Songs bleiben). */
    fun optimize() = rearrange(QueueFlow.arrange(queue.map(refresh), flowRef(), pinned.toSet()))

    /** Smart Shuffle: zufällig, aber mit gutem Flow (BPM/Energie/Genre). */
    fun smartShuffle() = rearrange(QueueFlow.arrange(queue.map(refresh), flowRef(), pinned.toSet(), kotlin.random.Random(System.nanoTime())))

    private fun rearrange(order: List<Track>) {
        if (order.map { it.id } == queue.map { it.id }) return
        queue.clear()
        queue.addAll(order)
    }

    /** Ersten Song aus der Warteschlange nehmen (wird jetzt gespielt). */
    private fun takeNext(): Track {
        val t = queue.removeAt(0)
        pinned.remove(t.id)
        return refresh(t)
    }

    // ---------- Bedienung ----------
    /** "Weiter": kurzer, sauberer Übergang im Takt zum nächsten Song. */
    fun next() {
        if (!running) { if (queue.isNotEmpty()) start(); return }
        if (transitioning) return
        if (queue.isEmpty() && nextTrack == null) { status = UiText(R.string.msg_queue_empty_next); return }
        quickRequest = true
        replan = true
    }

    /** "Zurück": kurzer Übergang zum vorherigen Song (ohne Verlauf: aktueller Song von vorne). */
    fun previous() {
        if (!running || transitioning) return
        prevRequest = true
    }

    /** Im laufenden Song spulen (nicht während eines Übergangs). */
    fun seek(seconds: Double) {
        if (transitioning) return
        val d = decks[currentDeck]
        d.seek(seconds.coerceIn(0.0, (d.duration - 1.0).coerceAtLeast(0.0)))
        earlyOffer = null
        replan = true
    }

    /** Antwort auf "Jetzt mischen?". */
    fun answerEarly(yes: Boolean) { earlyAnswer = yes }

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
                earlyOffer = null
            }
        }
    }

    /** Auto-DJ ganz beenden (Musik stoppt). */
    fun stop() {
        job?.cancel()
        tempoJob?.cancel()
        NativeEngine.cancelSchedule()
        // Automatische Effekte, die "hängen bleiben" könnten, zurücknehmen
        decks.forEach { it.setLoop(0.0, 0.0, false); it.setReverb(0f) }
        if (abs(mixer.crossfader) >= 0.98f) mixer.setCurve(false)
        if (running) log("Auto-DJ gestoppt")
        running = false
        transitioning = false
        nextPlan = null
        nextTrack = null
        earlyOffer = null
    }

    private fun side(deck: Int) = if (deck == 0) -1f else 1f

    private suspend fun waitLoaded(d: DeckModel): Boolean =
        withTimeoutOrNull(30_000) {
            while (d.loadState != 2 || d.peaks.isEmpty()) {
                if (d.loadState == 3) return@withTimeoutOrNull false
                delay(30)
            }
            true
        } ?: false

    /** Titel laden und warten, bis er bereit ist. */
    private suspend fun loadInto(deck: Int, t: Track): Boolean {
        if (!load(deck, t)) return false
        return waitLoaded(decks[deck])
    }

    /** Struktur des geladenen Titels (aus den Beat-Merkmalen der Engine). */
    private fun structureOf(d: DeckModel): TrackStructure? {
        val t = d.track ?: return null
        structureCache[t.id]?.let { return it }
        val fresh = refresh(t)
        val s = StructureAnalyzer.analyze(d.bpm, d.firstBeat, d.features(), d.duration, StructureAnalyzer.soundEnd(d.peaks, d.duration))
            ?.copy(trackEnergy = fresh.energy ?: -1f, musicalKey = fresh.musicalKey ?: -1)
        if (structureCache.size > 50) structureCache.clear()
        structureCache[t.id] = s
        return s
    }

    /** Plan ohne erkennbares Tempo: kurzer Echo- oder Hall-Ausklang am Ende. */
    private fun fallbackPlan(a: DeckModel, now: Boolean): TransitionPlan {
        val s = plannerSettings()
        val type = listOf(TransitionType.ECHO_OUT, TransitionType.REVERB_WASH, TransitionType.CUT).firstOrNull { it in s.enabled }
            ?: TransitionType.CUT
        // Ende = letzter hörbarer Moment (Stille am Schluss zählt nicht)
        val end = StructureAnalyzer.soundEnd(a.peaks, a.duration)
        val at = if (now) a.livePosition() + 2.0 else maxOf(a.livePosition() + 4.0, end - 6.0)
        return TransitionPlan(type, -1, at, 0.0, 2, 1f, false, "kein Tempo erkannt → ${type.name} am Ende")
    }

    // ---------- Ablauf ----------
    private suspend fun loop() {
        // Start: läuft schon ein Deck? Sonst ersten Titel auf Deck A sofort starten.
        var cur = decks.indexOfFirst { it.playing }
        if (cur < 0) {
            while (true) {
                if (queue.isEmpty()) { status = UiText(R.string.automix_empty); return }
                val first = takeNext()
                if (loadInto(0, first)) {
                    mixer.setCurve(false)
                    mixer.moveCrossfader(side(0))
                    decks[0].applyGain(1f)
                    decks[0].seek(0.0)
                    decks[0].play(true)
                    current = first
                    log("Start: ${first.title}")
                    break
                }
                log("Nicht lesbar, übersprungen: ${first.title}")
            }
            cur = 0
        } else current = decks[cur].track
        while (scope.isActive) {
            cur = try {
                step(cur) ?: return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nie stehen bleiben: Fehler protokollieren, aufräumen, mit dem nächsten Song weitermachen
                android.util.Log.e("AutoMix", "Fehler im Ablauf", e)
                log("Fehler: ${e.javaClass.simpleName} – weiter mit dem nächsten Song")
                recover(cur)
            }
        }
    }

    /** Nach einem Fehler: was gerade läuft weiterspielen lassen, Plan verwerfen. */
    private fun recover(cur: Int): Int {
        NativeEngine.cancelSchedule()
        transitioning = false
        nextPlan = null
        earlyOffer = null
        nextTrack?.let { if (queue.none { q -> q.id == it.id }) queue.add(0, it) }
        nextTrack = null
        val playing = decks.indexOfFirst { it.livePlaying() }
        val c = if (playing >= 0) playing else cur
        decks[1 - c].play(false)
        mixer.moveCrossfader(side(c))
        mixer.setCurve(false)
        currentDeck = c
        current = decks[c].track
        return c
    }

    /** Ein Song-Wechsel. Gibt das neue laufende Deck zurück, oder null = fertig. */
    private suspend fun step(cur: Int): Int? {
        currentDeck = cur
        val a = decks[cur]
        val nxt = 1 - cur
        val b = decks[nxt]
        current = a.track
        if (queue.isEmpty()) {
            nextPlan = null
            nextTrack = null
            status = UiText(R.string.automix_last)
            while (a.livePlaying() || a.livePosition() < a.duration - 1.0) {
                if (queue.isNotEmpty()) return cur      // neuer Titel kam dazu
                if (prevRequest) { prevRequest = false; queueBackToPrevious(a, null); return cur }
                delay(200)
            }
            status = UiText(R.string.automix_done)
            log("Ende der Warteschlange")
            return null
        }

        // ---- 1) Nächsten Titel laden (die Tempo-Rückführung von A läuft dabei weiter) ----
        val next = takeNext()
        nextTrack = next
        status = UiText(R.string.automix_next, next.title)
        if (!loadInto(nxt, next)) {
            log("Nicht lesbar, übersprungen: ${next.title}")
            nextTrack = null
            prevTransition = false
            return cur
        }
        b.resetEffects()
        b.changeVolume(a.volume)
        matchLoudness(a, b)
        val aStruct = structureOf(a)
        val bStruct = structureOf(b)

        // ---- 2) Planen (und bei Änderungswünschen neu planen) ----
        var plan: TransitionPlan = fallbackPlan(a, false)
        var transition: Transition = Transitions.create(plan.type)
        var ctx = TransitionContext(a, b, mixer, plan, aStruct, bStruct)
        mixNow = false
        replan = true
        skip = false
        earlyAnswer = null
        earlyOffer = null
        var earlyBar: Int? = null          // möglicher Früh-Übergang (noch nicht gefragt)
        var earlyAccepted: Int? = null     // bestätigter Früh-Übergang
        var quick = false
        var centered = false
        while (true) {
            if (prevRequest) {
                prevRequest = false
                cancelPrepared(b, centered, cur)
                queueBackToPrevious(a, next)
                return cur
            }
            if (skip) {
                cancelPrepared(b, centered, cur)
                skip = false
                forcedType = null
                log("Übersprungen: ${next.title}")
                return cur
            }
            if (replan) {
                replan = false
                if (quickRequest) { quick = true; quickRequest = false; earlyBar = null; earlyOffer = null }
                NativeEngine.cancelSchedule()
                b.play(false)
                b.resetEffects()
                if (centered) mixer.moveCrossfader(side(cur))   // Crossfader zurück zu A (B ist noch stumm)
                // Läuft A noch in der Tempo-Rückführung? Dann gilt für die Planung das Zieltempo (Original),
                // ausser bei sofortigem Übergang: dann Rückführung anhalten und mit dem aktuellen Tempo planen.
                if ((mixNow || quick) && rampRunning(a)) stopRamp()
                val aTempo = if (rampRunning(a)) 1f else a.tempo
                plan = if (aStruct != null && bStruct != null) {
                    TransitionPlanner.plan(
                        aStruct, a.livePosition(), aTempo, bStruct, plannerSettings(), typeHistory, mixNow, forcedType,
                        quick = quick, atBar = earlyAccepted,
                    )
                } else fallbackPlan(a, mixNow || quick)
                // Tempo des neuen Songs angleichen (nur bei kleinem Unterschied) – Tonhöhe bleibt (Keylock)
                if (TempoRamp.needed(plan.tempoRatio)) b.ensureKeylock()
                b.setTempoRatio(plan.tempoRatio)
                b.seek(plan.bStartSec)
                transition = Transitions.create(plan.type)
                ctx = TransitionContext(a, b, mixer, plan, aStruct, bStruct)
                // Kurve nur umschalten, solange der Crossfader ganz bei A steht (kein Lautstärkesprung)
                mixer.setCurve(transition.equalPower && abs(mixer.crossfader) >= 0.98f)
                transition.prepare(ctx)
                // Sample-genauen Start in der Engine "scharf schalten"
                NativeEngine.scheduleStart(nxt, cur, plan.mixSec, transition.stopsAAtStart)
                nextPlan = plan
                centered = false
                status = UiText(R.string.automix_waiting, next.title)
                log("Plan ${a.track?.title ?: "?"} → ${next.title}: ${plan.type.name} · ${plan.lengthBars} Takte · " +
                    "Einsatz bei ${mmss(plan.mixSec)}" + (if (plan.reason.isNotEmpty()) " · ${plan.reason}" else ""))
                // Früh-Übergang möglich? (nur beim normalen Plan)
                if (!quick && !mixNow && earlyAccepted == null && settings.earlyMode != EARLY_NEVER && aStruct != null && bStruct != null && earlyBar == null) {
                    earlyBar = TransitionPlanner.earlyBar(aStruct, a.livePosition(), aTempo, bStruct, plan.mixBar)
                    earlyBar?.let { log("Früh-Übergang möglich bei Takt $it (${mmss(aStruct.barTime(it))})") }
                }
            }
            val aPos = a.livePosition()

            // ---- Früh-Übergang: fragen bzw. automatisch ----
            val eb = earlyBar
            if (eb != null && aStruct != null) {
                val ebSec = aStruct.barTime(eb)
                val askAt = ebSec - (10.0 + 2 * aStruct.barSec) * a.tempo
                if (aPos > ebSec - aStruct.barSec) {           // verpasst
                    earlyBar = null
                    earlyOffer = null
                } else if (aPos >= askAt && earlyOffer == null && earlyAnswer == null) {
                    if (settings.earlyMode == EARLY_ALWAYS) {
                        earlyAnswer = true
                    } else {
                        earlyOffer = EarlyOffer(next.title, System.currentTimeMillis() + 10_000, eb)
                        log("Frage: Passt perfekt – jetzt mischen?")
                    }
                }
                val offer = earlyOffer
                val answer = earlyAnswer
                if (answer == true) {
                    earlyAccepted = eb
                    earlyBar = null
                    earlyOffer = null
                    earlyAnswer = null
                    replan = true
                    log("Früh-Übergang: ja")
                    continue
                } else if (answer == false || (offer != null && System.currentTimeMillis() > offer.deadlineMs)) {
                    log(if (answer == false) "Früh-Übergang: nein" else "Früh-Übergang: keine Antwort → normal bis zum Ende")
                    earlyBar = null
                    earlyOffer = null
                    earlyAnswer = null
                }
            }

            // Sicherheit (sehr kurze Songs): Rückführung kurz vor dem Einsatz abschliessen
            if (rampRunning(a) && aPos >= plan.mixSec - 2 * ctx.aBarSec) { stopRamp(); a.setTempoRatio(1f) }
            // Vor-Phase (z.B. Hall aufbauen, Echo einschalten)
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
        earlyOffer = null

        // ---- 3) Überblenden ----
        transitioning = true
        status = UiText(R.string.automix_mixing, next.title)
        log("Übergang startet: ${plan.type.name} bei ${mmss(a.livePosition())} (geplant ${mmss(plan.mixSec)})")
        val bBarSec = bStruct?.barSec ?: 2.0
        val lengthTrackSec = plan.lengthBars * bBarSec
        while (true) {
            if (!transitioning) return null      // abgebrochen
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
        if (!prevTransition) a.track?.let { played += it; if (played.size > 200) played.removeAt(0) }
        prevTransition = false
        typeHistory += plan.type
        if (typeHistory.size > 10) typeHistory.removeAt(0)
        forcedType = null
        transitioning = false
        nextPlan = null
        nextTrack = null
        current = next
        currentDeck = nxt
        log("Übergang fertig: ${next.title} läuft (Tempo ${"%+.1f".format((b.tempo - 1f) * 100)} %)")
        // Tempo des neuen Songs fliessend (über 8–16 Takte) zurück zum Original
        if (TempoRamp.needed(b.tempo)) {
            val bars = bStruct?.let { TempoRamp.bars(it.endBar - 32 - it.barAt(b.livePosition())) } ?: 16
            rampTempoHome(b, bars, bBarSec)
        }
        return nxt
    }

    /** Vorbereiteten nächsten Song verwerfen (B stumm und gestoppt, Crossfader zurück zu A). */
    private fun cancelPrepared(b: DeckModel, centered: Boolean, cur: Int) {
        NativeEngine.cancelSchedule()
        b.play(false)
        b.resetEffects()
        if (centered) mixer.moveCrossfader(side(cur))
        mixer.setCurve(false)
        nextPlan = null
        earlyOffer = null
    }

    /**
     * "Zurück": vorheriger Song kommt als Nächstes (danach wieder der aktuelle und der schon
     * vorbereitete), der Übergang wird kurz ("Weiter"-Art). Ohne Verlauf: aktueller Song von vorne.
     */
    private fun queueBackToPrevious(a: DeckModel, prepared: Track?) {
        val now = a.track ?: return
        val prev = played.removeLastOrNull() ?: now
        prepared?.let { if (queue.none { q -> q.id == it.id }) { queue.add(0, it); pinned += it.id } }
        if (prev.id != now.id) { queue.add(0, now); pinned += now.id }
        queue.add(0, prev)
        pinned += prev.id
        prevTransition = true
        quickRequest = true
        nextTrack = null
        log("Zurück zu: ${prev.title}")
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
        if (logLines.size > 80) logLines.removeAt(logLines.lastIndex)
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
                val pos = d.livePosition()
                // Gespult (z.B. zurück an den Anfang)? Dann sofort ans Ziel, statt rückwärts zu laufen
                val p = if (pos < startPos - 0.5) 1f else ((pos - startPos) / length).toFloat()
                d.setTempoRatio(TempoRamp.value(start, p))
                if (p >= 1f) break
                delay(50)
            }
            log("Tempo zurück auf 100 % (${"%.1f".format((start - 1f) * 100)} % über $bars Takte)")
        }
    }

    companion object {
        /** Früh-Übergänge: 0 = fragen (Standard), 1 = immer, 2 = nie */
        const val EARLY_ASK = 0
        const val EARLY_ALWAYS = 1
        const val EARLY_NEVER = 2
    }
}
