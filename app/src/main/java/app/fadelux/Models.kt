package app.fadelux

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fadelux.audio.NativeEngine
import app.fadelux.data.Track
import kotlin.math.abs

/** Zustand und Steuerung eines Decks (A oder B). */
class DeckModel(val index: Int) {
    val name = if (index == 0) "A" else "B"

    // Anzeige (wird laufend aus der Engine aktualisiert)
    var track by mutableStateOf<Track?>(null)
    var loadState by mutableIntStateOf(0)   // 0 leer, 1 lädt, 2 bereit, 3 Fehler
    var playing by mutableStateOf(false)
    var position by mutableStateOf(0.0)
    var duration by mutableStateOf(0.0)
    var bpm by mutableFloatStateOf(0f)
    var cue by mutableStateOf(0.0)
    var level by mutableFloatStateOf(0f)
    var waveform by mutableStateOf(FloatArray(0))
    var peaks by mutableStateOf(FloatArray(0))     // volle Auflösung (100 Werte/s)
    var firstBeat by mutableFloatStateOf(0f)

    // Bedienelemente
    var tempoRange by mutableIntStateOf(8)      // ±8 % oder ±16 %
    var tempoSlider by mutableFloatStateOf(0f)  // -1..1
    var keylock by mutableStateOf(true)
    var volume by mutableFloatStateOf(0.85f)
    val eq = mutableStateListOf(0f, 0f, 0f)     // Hoch, Mitte, Tief (-1..1)
    val kill = mutableStateListOf(false, false, false)
    var filter by mutableFloatStateOf(0f)
    var echoOn by mutableStateOf(false)
    var echoAmount by mutableFloatStateOf(0.4f)
    var pfl by mutableStateOf(false)
    /** Lautheits-Angleichung durch Auto-Mix (1 = unverändert). */
    var gain by mutableFloatStateOf(1f)

    val tempo: Float get() = 1f + tempoSlider * tempoRange / 100f
    val effectiveBpm: Float get() = bpm * tempo

    // Hinweis: Band-Index in der Engine: 0 = Tief, 1 = Mitte, 2 = Hoch
    fun applyAll() {
        NativeEngine.setTempo(index, tempo)
        NativeEngine.setKeylock(index, keylock)
        NativeEngine.setVolume(index, volume)
        for (i in 0..2) {
            NativeEngine.setEq(index, 2 - i, eq[i])
            NativeEngine.setKill(index, 2 - i, kill[i])
        }
        NativeEngine.setFilter(index, filter)
        NativeEngine.setEcho(index, echoOn, echoAmount)
        NativeEngine.setPfl(index, pfl)
    }

    fun changeTempo(slider: Float) {
        tempoSlider = slider.coerceIn(-1f, 1f)
        NativeEngine.setTempo(index, tempo)
    }

    /** Tempo direkt als Verhältnis setzen (z.B. 1.03 = +3 %). Bereich wird bei Bedarf erweitert. */
    fun setTempoRatio(ratio: Float) {
        val needed = (ratio - 1f) * 100f
        if (abs(needed) > tempoRange) tempoRange = 16
        changeTempo(needed / tempoRange)
    }

    fun changeRange() {
        val t = tempo
        tempoRange = if (tempoRange == 8) 16 else 8
        changeTempo((t - 1f) * 100f / tempoRange)
    }

    fun resetTempo() = changeTempo(0f)

    /** Kurz schneller/langsamer (zum Angleichen der Beats von Hand). */
    fun nudge(direction: Int) {
        NativeEngine.setTempo(index, tempo * (1f + 0.04f * direction))
    }

    fun sync() {
        val ratio = NativeEngine.sync(index)
        setTempoRatio(ratio)
    }

    fun togglePlay() {
        if (loadState != 2) return
        NativeEngine.setPlaying(index, !playing)
    }

    fun play(on: Boolean) {
        if (loadState == 2) NativeEngine.setPlaying(index, on)
    }

    fun cuePress() = NativeEngine.cuePress(index)
    fun seek(seconds: Double) = NativeEngine.seek(index, seconds.coerceAtLeast(0.0))
    fun seekFraction(f: Float) {
        if (duration > 0) NativeEngine.seek(index, duration * f.coerceIn(0f, 1f))
    }

    fun setEq(i: Int, v: Float) { eq[i] = v; NativeEngine.setEq(index, 2 - i, v) }
    fun toggleKill(i: Int) { kill[i] = !kill[i]; NativeEngine.setKill(index, 2 - i, kill[i]) }
    fun changeVolume(v: Float) { volume = v.coerceIn(0f, 1f); NativeEngine.setVolume(index, volume) }
    fun changeFilter(v: Float) { filter = v; NativeEngine.setFilter(index, v) }
    fun toggleEcho() { echoOn = !echoOn; NativeEngine.setEcho(index, echoOn, echoAmount) }
    fun changeEchoAmount(v: Float) { echoAmount = v; NativeEngine.setEcho(index, echoOn, v) }
    fun togglePfl() { pfl = !pfl; NativeEngine.setPfl(index, pfl) }

    // --- Für automatische Übergänge (Auto-Mix) ---
    fun setEcho(on: Boolean, amount: Float, feedback: Float = 0.55f) {
        echoOn = on
        echoAmount = amount
        NativeEngine.setEcho(index, on, amount)
        NativeEngine.setEchoFeedback(index, feedback)
    }
    fun setReverb(send: Float) = NativeEngine.setReverb(index, send)
    fun setLoop(startSec: Double, lengthSec: Double, on: Boolean) = NativeEngine.setLoop(index, startSec, lengthSec, on)

    /** Alles zurücksetzen, was ein Übergang verändert hat (EQ, Filter, Echo, Hall, Loop). */
    fun resetEffects() {
        for (i in 0..2) { if (kill[i]) toggleKill(i); setEq(i, 0f) }
        changeFilter(0f)
        setEcho(false, 0.4f)
        setReverb(0f)
        setLoop(0.0, 0.0, false)
    }

    private val liveBuf = DoubleArray(9)

    /** Position direkt aus der Engine (genau, auch wenn die Anzeige im Hintergrund seltener aktualisiert). */
    fun livePosition(): Double {
        NativeEngine.getDeckState(index, liveBuf)
        return liveBuf[2]
    }

    /** Spielt das Deck gerade? (direkt aus der Engine) */
    fun livePlaying(): Boolean {
        NativeEngine.getDeckState(index, liveBuf)
        return liveBuf[1] > 0.5
    }

    /** Merkmale pro Beat des geladenen Titels (für die Struktur-Analyse). */
    fun features(): ByteArray = NativeEngine.getDeckFeatures(index)
    fun toggleKeylock() { keylock = !keylock; NativeEngine.setKeylock(index, keylock) }
    /** Keylock einschalten (Tempo ändern ohne Tonhöhe) – für Auto-Mix. */
    fun applyGain(g: Float) { gain = g; NativeEngine.setGain(index, g) }
    /** Lautheit des geladenen Titels in dB (gemessen beim Laden). */
    fun loudness(): Float = NativeEngine.getLoudness(index)
    fun ensureKeylock() { if (!keylock) toggleKeylock() }
}

/** Zustand des Mischpults in der Mitte (Crossfader, Master, Kopfhörer). */
class MixerModel {
    var crossfader by mutableFloatStateOf(0f)
    var masterVolume by mutableFloatStateOf(0.9f)
    var headphoneVolume by mutableFloatStateOf(0.8f)
    var cueMix by mutableFloatStateOf(0f)
    var masterLevel by mutableFloatStateOf(0f)
    var spectrum by mutableStateOf(FloatArray(28))

    /** Crossfader gesperrt? (während Auto-Mix: bewegt sich nur automatisch) */
    var locked: () -> Boolean = { false }

    /** Equal-Power-Kurve aktiv (nur während automatischer Überblendungen). */
    var equalPower = false
        private set

    /** Kurve umschalten. Nur an den Enden des Crossfaders umschalten, sonst springt die Lautstärke. */
    fun setCurve(ep: Boolean) {
        equalPower = ep
        NativeEngine.setCrossfaderCurve(if (ep) 1 else 0)
    }

    fun applyAll() {
        NativeEngine.setCrossfaderCurve(if (equalPower) 1 else 0)
        NativeEngine.setCrossfader(crossfader)
        NativeEngine.setMasterVolume(masterVolume)
        NativeEngine.setHeadphoneVolume(headphoneVolume)
        NativeEngine.setCueMix(cueMix)
    }

    /** Vom Menschen bedient (Finger). */
    fun changeCrossfader(x: Float) {
        if (locked()) return          // z.B. Wischgeste über den Fader während Auto-Mix → nichts passiert
        moveCrossfader(x)
        // Nach Auto-Mix: am Ende des Wegs wieder auf die normale DJ-Kurve wechseln
        if (equalPower && abs(crossfader) >= 0.98f) setCurve(false)
    }

    /** Von der App gesteuert (Auto-Mix). */
    fun moveCrossfader(x: Float) { crossfader = x.coerceIn(-1f, 1f); NativeEngine.setCrossfader(crossfader) }
    fun changeMasterVolume(v: Float) { masterVolume = v.coerceIn(0f, 1f); NativeEngine.setMasterVolume(masterVolume) }
    fun changeHeadphoneVolume(v: Float) { headphoneVolume = v.coerceIn(0f, 1f); NativeEngine.setHeadphoneVolume(headphoneVolume) }
    fun changeCueMix(v: Float) { cueMix = v.coerceIn(0f, 1f); NativeEngine.setCueMix(cueMix) }
}
