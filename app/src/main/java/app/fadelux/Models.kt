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
    fun toggleKeylock() { keylock = !keylock; NativeEngine.setKeylock(index, keylock) }
}

/** Zustand des Mischpults in der Mitte (Crossfader, Master, Kopfhörer). */
class MixerModel {
    var crossfader by mutableFloatStateOf(0f)
    var masterVolume by mutableFloatStateOf(0.9f)
    var headphoneVolume by mutableFloatStateOf(0.8f)
    var cueMix by mutableFloatStateOf(0f)
    var masterLevel by mutableFloatStateOf(0f)
    var spectrum by mutableStateOf(FloatArray(28))

    /** Wird gesetzt, wenn der DJ selbst am Crossfader dreht (Auto-Mix gibt dann ab). */
    var onManualCrossfader: (() -> Unit)? = null

    fun applyAll() {
        NativeEngine.setCrossfader(crossfader)
        NativeEngine.setMasterVolume(masterVolume)
        NativeEngine.setHeadphoneVolume(headphoneVolume)
        NativeEngine.setCueMix(cueMix)
    }

    /** Vom Menschen bedient (Finger). */
    fun changeCrossfader(x: Float) {
        onManualCrossfader?.invoke()
        moveCrossfader(x)
    }

    /** Von der App gesteuert (Auto-Mix). */
    fun moveCrossfader(x: Float) { crossfader = x.coerceIn(-1f, 1f); NativeEngine.setCrossfader(crossfader) }
    fun changeMasterVolume(v: Float) { masterVolume = v.coerceIn(0f, 1f); NativeEngine.setMasterVolume(masterVolume) }
    fun changeHeadphoneVolume(v: Float) { headphoneVolume = v.coerceIn(0f, 1f); NativeEngine.setHeadphoneVolume(headphoneVolume) }
    fun changeCueMix(v: Float) { cueMix = v.coerceIn(0f, 1f); NativeEngine.setCueMix(cueMix) }
}
