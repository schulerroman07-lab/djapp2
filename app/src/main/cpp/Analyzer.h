// Song-Analyse für den Auto-DJ (Version 2): Beats, Downbeats, Tempo mit Vertrauenswert,
// Merkmale pro Beat (Energie, Bass, Mitten, Höhen, Gesang) – entwickelt und gemessen an echter Musik
// (tools/realmusic). Arbeitet im Strom: feed() bekommt Stereo-Samples, result() rechnet am Schluss.
#pragma once
#include <cstdint>
#include <vector>

namespace dj {

struct Analysis {
    float bpm = 0;              // Tempo (Schläge pro Minute), 0 = nicht erkannt
    float firstBeat = 0;        // Zeit des ersten Beats (s)
    float confidence = 0;       // 0..1: Vertrauen in das Beat-Raster (≥ 0,6 = zum Beatmatching geeignet)
    bool steady = false;        // Tempo über den ganzen Song fest (Raster passt)
    std::vector<float> beats;   // alle Beat-Zeiten (s)
    int downbeat = 0;           // Index (0..3) des ersten Downbeats ("die 1") in beats
    float downbeatConf = 0;     // 0..1
    float energy = 0;           // 0..1 Lautheit/Intensität
    std::vector<uint8_t> features;   // pro Beat kFeat Bytes (siehe kFeat)
    std::vector<uint8_t> vocal;      // Gesangs-Wahrscheinlichkeit 0..255 pro 0,25 s
    // Zwischenwerte des Vertrauens (zum Eichen an echter Musik, tools/realmusic)
    float inlierFrac = 0, salience = 0, clarity = 0, netAct = 0;
};

/** Stellgrössen der Analyse (an echter Musik abgestimmt, siehe tools/realmusic). */
struct AnalyzerTuning {
    float lowWeight = 1.0f;       // Gewicht der Bass-Einsätze in der Onset-Kurve
    float priorBpm = 120.f;       // bevorzugtes Tempo (bei halb/doppelt-Entscheidung)
    float priorOct = 0.9f;        // Breite der Vorliebe in Oktaven
    float tightness = 100.f;      // wie streng der Beat-Abstand eingehalten wird
    float steadyFrac = 0.75f;     // Anteil der (deutlichen) Beats im festen Raster, ab dem das Tempo als fest gilt
    float latencySec = 0.f;       // gemessene Verzögerung der Onset-Kurve gegenüber dem hörbaren Einsatz
    float confBias = -6.f, confInlier = 5.f, confSalience = 0.8f, confClarity = 2.f;
    float dbLow = 1.f, dbNov = 1.f, dbLow3 = 0.3f;
    // Vertrauen mit Beat-Netz: aus der Netz-Kurve an den Beats, Raster-Treue und Tempo-Eindeutigkeit
    float netBias = -6.f, netAct = 8.f, netInlier = 3.f, netClarity = 1.f;
    float netEvident = 0.5f;      // Netz-Kurve, ab der ein Beat für die Raster-Treue zählt (Breaks zählen nicht)
    float netNov = 0.1f;          // Klangwechsel zusätzlich zur Downbeat-Kurve des Netzes (hilft bei Techno/House, wo jeder Beat gleich klingt)
};
AnalyzerTuning& tuning();

class TrackAnalyzer {
public:
    // Bytes pro Beat: Energie, Bass, Mitten, Höhen, Gesang (0..255)
    static constexpr int kFeat = 5;

    void feed(const int16_t* stereo, size_t frames, int sampleRate);
    Analysis result() const;
    /** Sind Beat- und Gesangs-Netz trainiert? Erst dann nutzt die App diese Analyse (sonst die bisherige). */
    static bool netsReady();

    // --- für Entwicklungs-Werkzeuge (tools/realmusic) ---
    double frameRate() const { return fps_; }
    const std::vector<float>& onset() const { return ose_; }
    /** Spektralfluss pro Rahmen in kGroups Frequenzgruppen (Bass, Tiefmitten, Mitten, Präsenz, Höhen). */
    const std::vector<float>& groupFlux() const { return fluxG_; }
    static constexpr int kGroups = 5;
    /** Eingang des Gesangs-Netzes: pro Mel-Rahmen (melHop() s) 2·kMel Werte in dB:
     *  Mitte relativ zum Song (kMel), dann Seite gegen Mitte (kMel). */
    std::vector<float> melInput() const;
    double melHop() const { return fps_ > 0 ? kMelBlock / fps_ : 0; }
    /** Beat- und Downbeat-Kurve (0..1) des Beat-Netzes pro Mel-Rahmen; leer, solange kein Netz trainiert ist. */
    void beatNet(std::vector<float>& beat, std::vector<float>& down) const;
    /** Gesangs-Wahrscheinlichkeit pro Ausgabe-Schritt des Netzes (8 Mel-Rahmen ≈ 0,19 s) + Zeiten (s). */
    std::vector<float> vocalNet(std::vector<float>* times) const;
    static constexpr int kBands = 20;       // Frequenzbänder (Mitte) – dieselben für die Seite
    static constexpr int kBlock = 8;        // Rahmen pro Block (ca. 46 ms)
    static constexpr int kMel = 40;         // Mel-Bänder (50 Hz – 10 kHz) für das Gesangs-Netz
    static constexpr int kMelBlock = 4;     // Rahmen pro Mel-Rahmen (ca. 23 ms)

private:
    static constexpr int kN = 1024;         // FFT-Länge
    static constexpr int kHop = 256;        // Schritt (ca. 5,8 ms bei 44,1 kHz)

    void setup(int sampleRate);
    void frame();
    std::vector<float> vocalProb() const;

    int rate_ = 0;
    double fps_ = 0;
    std::vector<float> mid_, side_;         // Ringpuffer der letzten kN Samples
    size_t ringPos_ = 0, filled_ = 0, sinceHop_ = 0;
    std::vector<float> window_;
    std::vector<int> bandOf_;               // FFT-Bin → Band (oder -1)
    std::vector<float> prevLog_, prev2Log_; // für den Spektralfluss
    int lowBands_ = 0;
    // pro Rahmen
    std::vector<float> ose_, oseLow_;
    std::vector<float> fluxG_;              // kGroups Werte pro Rahmen
    std::vector<float> pAll_, pLow_, pMid_, pHigh_;
    // pro Block: Bänder der Mitte (log), für Downbeats
    std::vector<float> blkMid_;
    std::vector<double> accMid_;
    int accFrames_ = 0;
    // Mel-Spektrum (Mitte/Seite, dB) für das Gesangs-Netz: pro FFT-Bin zwei Filter mit Gewicht
    std::vector<int> melIdx_;               // unteres Filter je Bin (−1 = keins), das obere ist +1
    std::vector<float> melW_;               // Gewicht für das obere Filter (das untere bekommt 1 − w)
    std::vector<double> melAccM_, melAccS_;
    int melFrames_ = 0;
    std::vector<float> melMid_, melSide_;
    double sumAll_ = 0, sumLow_ = 0;
    size_t sumCount_ = 0;
};

}  // namespace dj
