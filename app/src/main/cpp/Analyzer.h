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
};

/** Stellgrössen der Analyse (an echter Musik abgestimmt, siehe tools/realmusic). */
struct AnalyzerTuning {
    float lowWeight = 1.0f;       // Gewicht der Bass-Einsätze in der Onset-Kurve
    float priorBpm = 120.f;       // bevorzugtes Tempo (bei halb/doppelt-Entscheidung)
    float priorOct = 0.9f;        // Breite der Vorliebe in Oktaven
    float tightness = 100.f;      // wie streng der Beat-Abstand eingehalten wird
    float steadyFrac = 0.85f;     // Anteil der Beats im festen Raster, ab dem das Tempo als fest gilt
    float latencySec = 0.f;       // gemessene Verzögerung der Onset-Kurve gegenüber dem hörbaren Einsatz
    float confBias = -6.f, confInlier = 5.f, confSalience = 0.8f, confClarity = 2.f;
    float dbLow = 1.f, dbNov = 1.f, dbLow3 = 0.3f;
};
AnalyzerTuning& tuning();

class TrackAnalyzer {
public:
    // Bytes pro Beat: Energie, Bass, Mitten, Höhen, Gesang (0..255)
    static constexpr int kFeat = 5;

    void feed(const int16_t* stereo, size_t frames, int sampleRate);
    Analysis result() const;

    // --- für Entwicklungs-Werkzeuge (tools/realmusic) ---
    double frameRate() const { return fps_; }
    const std::vector<float>& onset() const { return ose_; }
    /** Merkmale für die Gesangserkennung pro Block (kVocalDim Werte je Block), Blocklänge vocalHop() s. */
    std::vector<float> vocalFeatures() const;
    /** Eingang des Gesangs-Klassifikators: alle 0,25 s je 2·kVocalDim Werte (Mittel + Streuung über ±0,75 s). */
    std::vector<float> vocalInputs() const;
    double vocalHop() const { return fps_ > 0 ? kBlock / fps_ : 0; }
    static constexpr int kBands = 20;       // Frequenzbänder (Mitte) – dieselben für die Seite
    static constexpr int kBlock = 8;        // Rahmen pro Block (ca. 46 ms)
    static constexpr int kVocalDim = 46;    // Merkmale pro Block für den Gesangs-Klassifikator

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
    std::vector<float> pAll_, pLow_, pMid_, pHigh_;
    // pro Block: Bänder Mitte/Seite (log), für Gesang und Downbeats
    std::vector<float> blkMid_, blkSide_;
    std::vector<double> accMid_, accSide_;
    int accFrames_ = 0;
    double sumAll_ = 0, sumLow_ = 0;
    size_t sumCount_ = 0;
};

}  // namespace dj
