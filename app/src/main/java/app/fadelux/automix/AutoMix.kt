package app.fadelux.automix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fadelux.DeckModel
import app.fadelux.MixerModel
import app.fadelux.R
import app.fadelux.data.AppSettings
import app.fadelux.data.Track
import app.fadelux.ui.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong

/**
 * Auto-Mix: spielt die Warteschlange automatisch ab und überblendet im Takt.
 * Ablauf pro Übergang:
 *  1. Nächsten Titel ins freie Deck laden, Tempo angleichen, Bass wegdrehen
 *  2. Am Ende des aktuellen Titels (Outro, auf einen Takt gerundet) starten und Beats synchronisieren
 *  3. Crossfader über die gewählte Länge schieben, in der Mitte den Bass tauschen
 *  4. Alten Titel stoppen
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

    private var job: Job? = null
    private var mixNow = false

    init {
        // DJ greift ein → Auto-Mix beenden
        mixer.onManualCrossfader = {
            if (running && transitioning) {
                stop()
                status = UiText(R.string.automix_manual)
            }
        }
    }

    fun add(t: Track) { if (queue.none { it.id == t.id }) queue += t }
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

    /** Übergang sofort starten (nicht bis zum Outro warten). */
    fun mixNow() { mixNow = true }

    fun start() {
        if (running) return
        if (queue.isEmpty() && decks.none { it.playing }) {
            status = UiText(R.string.automix_empty)
            return
        }
        running = true
        job = scope.launch {
            try { loop() } finally {
                running = false
                transitioning = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        running = false
        transitioning = false
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
        while (scope.isActive) {
            val a = decks[cur]
            val nxt = 1 - cur
            val b = decks[nxt]
            if (queue.isEmpty()) {
                status = UiText(R.string.automix_last)
                while (a.playing) delay(200)
                status = UiText(R.string.automix_done)
                return
            }
            // 1) Nächsten Titel vorbereiten
            val next = queue.removeAt(0)
            status = UiText(R.string.automix_next, next.title)
            load(nxt, next)
            if (!waitLoaded(b)) { status = UiText(R.string.automix_load_error); continue }
            val curBpm = a.effectiveBpm
            TrackAnalysis.tempoRatio(curBpm, b.bpm)?.let { b.setTempoRatio(it) } ?: b.setTempoRatio(1f)
            b.changeVolume(a.volume)
            b.setEq(2, -1f)   // Bass des neuen Titels erst einmal weg
            // 2) Übergangslänge und Startpunkt berechnen (in "Titel-Sekunden" des aktuellen Decks)
            val bars = settings.transitionBars
            val beatSec = if (a.bpm > 0) 60.0 / a.bpm else 0.5
            val transTrackSec = if (bars > 0) bars * 4 * beatSec else -bars * a.tempo.toDouble()

            // Intro des neuen Titels: so starten, dass sein Hauptteil genau am Ende der Überblendung einsetzt
            val firstBeatB = if (b.firstBeat < 30f) b.firstBeat.toDouble() else 0.0
            var startAt = firstBeatB
            val (bIntro, _) = TrackAnalysis.introOutro(b.peaks)
            val transB = transTrackSec / a.tempo * b.tempo   // Übergang in "Titel-Sekunden" des neuen Decks
            if (b.bpm > 0 && bIntro - transB > firstBeatB + 1.0) {
                val barB = 4 * 60.0 / b.bpm
                startAt = firstBeatB + kotlin.math.floor((bIntro - transB - firstBeatB) / barB) * barB
            }
            b.seek(startAt)
            val (_, aOutro) = TrackAnalysis.introOutro(a.peaks)
            var mixOut = minOf(aOutro, a.duration - transTrackSec - 1.0)
            if (a.bpm > 0) {
                val bar = 4 * beatSec
                mixOut = a.firstBeat + ((mixOut - a.firstBeat) / bar).roundToLong() * bar
            }
            mixOut = mixOut.coerceAtLeast(a.position + 2.0)
            status = UiText(R.string.automix_waiting, next.title)
            mixNow = false
            while (a.position < mixOut && !mixNow) {
                if (!a.playing) break
                delay(10)
            }

            // 3) Überblenden
            transitioning = true
            status = UiText(R.string.automix_mixing, next.title)
            b.play(true)
            delay(250)
            if (a.bpm > 0 && b.bpm > 0) b.sync()
            val realSec = (transTrackSec / a.tempo).coerceIn(2.0, 60.0)
            val from = side(cur)
            val to = side(nxt)
            val startNs = System.nanoTime()
            var swapped = false
            while (true) {
                val p = ((System.nanoTime() - startNs) / 1e9 / realSec).toFloat().coerceIn(0f, 1f)
                delay(16)   // ca. 60 Schritte pro Sekunde
                if (!transitioning) return  // abgebrochen
                mixer.moveCrossfader(from + (to - from) * p)
                if (!swapped && p >= 0.5f) {
                    // Bass-Tausch in der Mitte: sauberer Übergang ohne "Bass-Matsch"
                    b.setEq(2, 0f)
                    a.setEq(2, -1f)
                    swapped = true
                }
                if (p >= 1f) break
            }
            // 4) Aufräumen
            a.play(false)
            a.setEq(2, 0f)
            transitioning = false
            cur = nxt
        }
    }
}
