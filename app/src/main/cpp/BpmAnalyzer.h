// Einfache Beat-Erkennung: findet BPM (Schläge pro Minute) und den ersten Beat.
#pragma once
#include <cstdint>
#include <vector>

namespace dj {

struct BpmResult {
    float bpm = 0;         // 0 = nicht erkannt
    float firstBeat = 0;   // Zeit des ersten Beats in Sekunden
};

// Sammelt Lautstärke-Hüllkurve aus Stereo-Samples und berechnet daraus das Tempo.
class BpmAnalyzer {
public:
    void feed(const int16_t* stereo, size_t frames, int sampleRate);
    BpmResult result() const;

private:
    void ensureRate(int sampleRate);

    int rate_ = 0;
    int hop_ = 256;          // Samples pro Hüllkurvenpunkt
    double envRate_ = 0;     // Hüllkurvenpunkte pro Sekunde
    float lp_ = 0;           // Tiefpass-Zustand (Bassdrum betonen)
    float lpCoef_ = 0;
    double accLow_ = 0, accAll_ = 0;
    int accCount_ = 0;
    std::vector<float> low_, all_;
};

}  // namespace dj
