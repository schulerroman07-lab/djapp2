// Einfache Beat-Erkennung: findet BPM (Schläge pro Minute) und den ersten Beat.
#pragma once
#include <cstdint>
#include <vector>

namespace dj {

struct BpmResult {
    float bpm = 0;         // 0 = nicht erkannt
    float firstBeat = 0;   // Zeit des ersten Beats in Sekunden
    float energy = 0;      // 0..1: Lautheit/Intensität (für Song-Vorschläge)
};

// Sammelt Lautstärke-Hüllkurve aus Stereo-Samples und berechnet daraus das Tempo.
class BpmAnalyzer {
public:
    void feed(const int16_t* stereo, size_t frames, int sampleRate);
    BpmResult result() const;

    // Merkmale pro Beat (für Auto-Mix): je 4 Bytes = Energie, Bass, Mitten (Gesang), Höhen (0..255)
    std::vector<uint8_t> beatFeatures(float bpm, float firstBeat) const;

private:
    void ensureRate(int sampleRate);

    int rate_ = 0;
    int hop_ = 256;          // Samples pro Hüllkurvenpunkt
    double envRate_ = 0;     // Hüllkurvenpunkte pro Sekunde
    float lp_ = 0;           // Tiefpass-Zustand (Bassdrum betonen)
    float lpCoef_ = 0;
    double accLow_ = 0, accAll_ = 0;
    double sumLow_ = 0, sumAll_ = 0;  // Summen für die Energie-Berechnung
    size_t sumCount_ = 0;
    int accCount_ = 0;
    std::vector<float> low_, all_;
    // Bänder in linearer Leistung pro Abschnitt
    float lp300_ = 0, lp3k_ = 0, c300_ = 0, c3k_ = 0;
    double accMid_ = 0, accHigh_ = 0, accLowLin_ = 0;
    std::vector<float> bandAll_, bandLow_, bandMid_, bandHigh_;
};

}  // namespace dj
