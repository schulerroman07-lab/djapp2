#include "BpmAnalyzer.h"

#include <algorithm>
#include <cmath>

namespace dj {

void BpmAnalyzer::ensureRate(int sampleRate) {
    if (rate_ == sampleRate) return;
    rate_ = sampleRate;
    hop_ = std::max(64, sampleRate / 200);  // ca. 200 Punkte pro Sekunde
    envRate_ = (double)sampleRate / hop_;
    lpCoef_ = 1.f - std::exp(-2.f * 3.14159265f * 150.f / sampleRate);
    c300_ = 1.f - std::exp(-2.f * 3.14159265f * 300.f / sampleRate);
    c3k_ = 1.f - std::exp(-2.f * 3.14159265f * 3000.f / sampleRate);
}

void BpmAnalyzer::feed(const int16_t* stereo, size_t frames, int sampleRate) {
    ensureRate(sampleRate);
    for (size_t i = 0; i < frames; i++) {
        float m = (stereo[i * 2] + stereo[i * 2 + 1]) * (0.5f / 32768.f);
        lp_ += lpCoef_ * (m - lp_);
        accLow_ += lp_ * lp_;
        accAll_ += m * m;
        sumLow_ += lp_ * lp_;
        sumAll_ += m * m;
        sumCount_++;
        lp300_ += c300_ * (m - lp300_);
        lp3k_ += c3k_ * (m - lp3k_);
        float mid = lp3k_ - lp300_;
        float high = m - lp3k_;
        accMid_ += mid * mid;
        accHigh_ += high * high;
        accLowLin_ += lp_ * lp_;
        if (++accCount_ >= hop_) {
            low_.push_back((float)std::log(1e-6 + accLow_ / hop_));
            all_.push_back((float)std::log(1e-6 + accAll_ / hop_));
            bandAll_.push_back((float)(accAll_ / hop_));
            bandLow_.push_back((float)(accLowLin_ / hop_));
            bandMid_.push_back((float)(accMid_ / hop_));
            bandHigh_.push_back((float)(accHigh_ / hop_));
            accLow_ = accAll_ = 0;
            accMid_ = accHigh_ = accLowLin_ = 0;
            accCount_ = 0;
        }
    }
}

BpmResult BpmAnalyzer::result() const {
    BpmResult res;
    size_t n = low_.size();
    if (n < (size_t)(envRate_ * 8)) return res;  // weniger als 8 Sekunden: zu kurz

    // 1) Onset-Kurve: nur Anstiege der Lautstärke zählen
    std::vector<float> onset(n, 0.f);
    for (size_t i = 1; i < n; i++) {
        float d1 = std::max(0.f, low_[i] - low_[i - 1]);
        float d2 = std::max(0.f, all_[i] - all_[i - 1]);
        onset[i] = d1 + 0.5f * d2;
    }
    // Mittelwert abziehen
    double mean = 0;
    for (float v : onset) mean += v;
    mean /= n;
    for (float& v : onset) v = std::max(0.f, (float)(v - mean));

    // 2) Bewertung eines Tempos: Kammfilter über alle Beats (mit bester Phase)
    auto score = [&](double bpm, double* bestPhase) -> double {
        double period = envRate_ * 60.0 / bpm;  // Punkte pro Beat
        int steps = 48;
        double best = -1;
        for (int p = 0; p < steps; p++) {
            double phase = period * p / steps;
            double s = 0;
            int cnt = 0;
            for (double t = phase; t < n - 1; t += period) {
                size_t i = (size_t)t;
                // kleine Toleranz: Nachbarpunkte mitnehmen
                float v = onset[i];
                if (i + 1 < n) v = std::max(v, onset[i + 1]);
                if (i > 0) v = std::max(v, onset[i - 1]);
                s += v;
                cnt++;
            }
            if (cnt > 0) s /= cnt;
            if (s > best) {
                best = s;
                if (bestPhase) *bestPhase = phase;
            }
        }
        return best;
    };

    // 3) Grobe Suche 70–180 BPM, dann Feinsuche
    double bestBpm = 0, bestScore = -1;
    for (double bpm = 70; bpm <= 180; bpm += 0.5) {
        double s = score(bpm, nullptr);
        if (s > bestScore) {
            bestScore = s;
            bestBpm = bpm;
        }
    }
    for (double bpm = bestBpm - 0.5; bpm <= bestBpm + 0.5; bpm += 0.02) {
        double s = score(bpm, nullptr);
        if (s > bestScore) {
            bestScore = s;
            bestBpm = bpm;
        }
    }

    // 4) Halbes/doppeltes Tempo: Bereich 78–160 bevorzugen (typisch für Partymusik)
    if (bestBpm < 78) bestBpm *= 2;
    if (bestBpm > 160) bestBpm /= 2;

    double phase = 0;
    score(bestBpm, &phase);
    res.bpm = (float)(std::round(bestBpm * 10.0) / 10.0);
    res.firstBeat = (float)(phase / envRate_);

    // Energie: 70 % Lautheit (RMS in dB) + 30 % Bass-Anteil
    if (sumCount_ > 0) {
        double rmsDb = 10.0 * std::log10(1e-12 + sumAll_ / sumCount_);
        double loud = std::min(1.0, std::max(0.0, (rmsDb + 28.0) / 20.0));
        double bass = std::min(1.0, std::max(0.0, (sumLow_ / (sumAll_ + 1e-12)) * 2.0));
        res.energy = (float)(0.7 * loud + 0.3 * bass);
    }
    return res;
}

}  // namespace dj

namespace dj {

std::vector<uint8_t> BpmAnalyzer::beatFeatures(float bpm, float firstBeat) const {
    std::vector<uint8_t> out;
    size_t n = bandAll_.size();
    if (bpm <= 0 || n == 0 || envRate_ <= 0) return out;
    double beatHops = envRate_ * 60.0 / bpm;
    double start = firstBeat * envRate_;
    size_t beats = (size_t)std::max(0.0, (n - start) / beatHops);
    if (beats == 0) return out;
    // 1) Mittelwert pro Beat und Band
    std::vector<float> v[4];
    const std::vector<float>* src[4] = {&bandAll_, &bandLow_, &bandMid_, &bandHigh_};
    for (int b = 0; b < 4; b++) v[b].resize(beats);
    for (size_t k = 0; k < beats; k++) {
        size_t a = (size_t)(start + k * beatHops);
        size_t e = std::min(n, (size_t)(start + (k + 1) * beatHops));
        for (int b = 0; b < 4; b++) {
            double s = 0;
            for (size_t i = a; i < e; i++) s += (*src[b])[i];
            v[b][k] = (float)(e > a ? s / (e - a) : 0.0);
        }
    }
    // 2) Pro Band auf das 95-%-Quantil normieren (laute Stellen ≈ 1), Wurzel = Amplitude
    out.resize(beats * 4);
    for (int b = 0; b < 4; b++) {
        std::vector<float> sorted = v[b];
        std::sort(sorted.begin(), sorted.end());
        float ref = sorted[(size_t)(sorted.size() * 0.95)] + 1e-12f;
        for (size_t k = 0; k < beats; k++) {
            float x = std::sqrt(v[b][k] / ref);
            out[k * 4 + b] = (uint8_t)std::min(255.f, std::max(0.f, x * 255.f));
        }
    }
    return out;
}

}  // namespace dj
