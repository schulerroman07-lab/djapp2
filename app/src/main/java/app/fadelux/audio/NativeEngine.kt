package app.fadelux.audio

/**
 * Verbindung zur C++-Audio-Engine (Oboe). Alle Funktionen sind "external",
 * d.h. sie sind in C++ geschrieben (jni_bridge.cpp).
 */
object NativeEngine {
    init {
        System.loadLibrary("fadelux")
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

    external fun getMasterLevel(): Float

    /** Füllt out mit Spektrum-Bändern 0..1 (Berechnung im aufrufenden Thread, nicht im Audio-Thread) */
    external fun getSpectrum(out: FloatArray)

    /** Puffergrösse in "Bursts": klein = tiefe Latenz, gross = stabiler */
    external fun setBufferBursts(n: Int)

    /** Volle Wellenform: 100 Spitzenwerte pro Sekunde */
    external fun getPeaks(deck: Int): FloatArray

    /** Füllt out (4 Werte): Abtastrate, Latenz ms, Kanäle, Master-Pegel */
    external fun getEngineInfo(out: DoubleArray)

    // --- Für Auto-Mix-Übergänge ---
    external fun setEchoFeedback(deck: Int, feedback: Float)
    /** Hall: Anteil, der in den Hall geschickt wird (0 = aus). Klingt nach dem Stoppen aus. */
    external fun setReverb(deck: Int, send: Float)
    /** Loop von startSec über lengthSec Sekunden (on=false beendet ihn). */
    external fun setLoop(deck: Int, startSec: Double, lengthSec: Double, on: Boolean)
    /** Deck "target" startet genau, wenn Deck "trigger" triggerSec erreicht (optional trigger stoppen). */
    external fun scheduleStart(target: Int, trigger: Int, triggerSec: Double, stopTrigger: Boolean)
    external fun cancelSchedule()
    external fun isScheduled(): Boolean
    /** Merkmale pro Beat des geladenen Titels: je 4 Bytes (Energie, Bass, Mitten, Höhen). */
    external fun getDeckFeatures(deck: Int): ByteArray

    /** Analyse einer Datei (blockiert!). Ergebnis: [BPM, erster Beat s, Energie 0..1, Anzahl Beats n, dann 4·n Merkmale 0..255] */
    external fun analyzeFile(fd: Int, length: Long): FloatArray
}
