package ch.hirschexpress.dj.audio

/**
 * Verbindung zur C++-Audio-Engine (Oboe). Alle Funktionen sind "external",
 * d.h. sie sind in C++ geschrieben (jni_bridge.cpp).
 */
object NativeEngine {
    init {
        System.loadLibrary("hirschdj")
    }

    external fun create(): Boolean
    external fun reopen()

    /** Lädt eine Datei in ein Deck (0 = A, 1 = B). fd wird von C++ geschlossen. */
    external fun loadTrack(deck: Int, fd: Int, length: Long)

    /** Füllt out (9 Werte): Ladezustand, spielt, Position s, Dauer s, BPM, 1. Beat s, Tempo, Cue s, Pegel */
    external fun getDeckState(deck: Int, out: DoubleArray)
    external fun getWaveform(deck: Int, points: Int): FloatArray

    external fun setPlaying(deck: Int, playing: Boolean)
    external fun cuePress(deck: Int)
    external fun seek(deck: Int, seconds: Double)
    external fun setTempo(deck: Int, ratio: Float)
    external fun setKeylock(deck: Int, on: Boolean)
    external fun setVolume(deck: Int, v: Float)
    external fun setEq(deck: Int, band: Int, v: Float)
    external fun setKill(deck: Int, band: Int, on: Boolean)
    external fun setFilter(deck: Int, v: Float)
    external fun setEcho(deck: Int, on: Boolean, amount: Float)
    external fun setPfl(deck: Int, on: Boolean)
    external fun sync(deck: Int): Float

    external fun setCrossfader(x: Float)
    external fun setMasterVolume(v: Float)
    external fun setHeadphoneVolume(v: Float)
    external fun setCueMix(v: Float)
    external fun setCueDelayMs(ms: Float)
    external fun setOutput(mode: Int, masterDevice: Int, cueDevice: Int)

    external fun startRecording(fd: Int): Boolean
    external fun stopRecording()
    external fun recordingSeconds(): Double

    /** Füllt out (4 Werte): Abtastrate, Latenz ms, Kanäle, Master-Pegel */
    external fun getEngineInfo(out: DoubleArray)

    /** BPM-Analyse einer Datei (blockiert!). Ergebnis: [BPM, erster Beat s] */
    external fun analyzeFile(fd: Int, length: Long): FloatArray
}
