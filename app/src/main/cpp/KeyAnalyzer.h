// Tonart-Erkennung (grob): Chroma (Energie pro Halbton-Klasse C, C#, … H) über den ganzen Song,
// verglichen mit den Tonart-Profilen von Krumhansl. Ergebnis: 0..11 = C..H Dur, 12..23 = c..h Moll.
#pragma once
#include <cmath>
#include <complex>
#include <cstdint>
#include <vector>

namespace dj {

class KeyAnalyzer {
public:
    void feed(const int16_t* stereo, size_t frames, int sampleRate) {
        if (rate_ != sampleRate) {
            rate_ = sampleRate;
            decim_ = std::max(1, sampleRate / 11025);  // auf ca. 11 kHz reduzieren (Töne bis 2 kHz reichen)
            frameRate_ = (double)sampleRate / decim_;
            buf_.clear();
            acc_ = 0;
            accN_ = 0;
        }
        for (size_t i = 0; i < frames; i++) {
            acc_ += (stereo[i * 2] + stereo[i * 2 + 1]) * (0.5f / 32768.f);
            if (++accN_ == decim_) {
                buf_.push_back(acc_ / decim_);   // Mittelwert = einfacher Tiefpass
                acc_ = 0;
                accN_ = 0;
                if (buf_.size() == kN) {
                    analyzeFrame();
                    buf_.clear();
                }
            }
        }
    }

    /** Tonart (0..23) oder -1, wenn zu wenig tonales Material. confidence 0..1. */
    int result(float* confidence = nullptr) const {
        double total = 0;
        for (double c : chroma_) total += c;
        if (total <= 1e-9 || frames_ < 8) {
            if (confidence) *confidence = 0;
            return -1;
        }
        static const double major[12] = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
        static const double minor[12] = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};
        double best = -2, second = -2;
        int bestKey = -1;
        for (int k = 0; k < 24; k++) {
            const double* prof = k < 12 ? major : minor;
            int root = k % 12;
            double r = corr(prof, root);
            if (r > best) { second = best; best = r; bestKey = k; }
            else if (r > second) second = r;
        }
        if (confidence) *confidence = (float)std::max(0.0, std::min(1.0, (best - second) * 5 + std::max(0.0, best) * 0.5));
        return best > 0.2 ? bestKey : -1;
    }

    const double* chroma() const { return chroma_; }

private:
    static constexpr size_t kN = 4096;

    double corr(const double* prof, int root) const {
        double mx = 0, my = 0;
        for (int i = 0; i < 12; i++) { mx += chroma_[i]; my += prof[i]; }
        mx /= 12; my /= 12;
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < 12; i++) {
            double x = chroma_[(i + root) % 12] - mx;
            double y = prof[i] - my;
            sxy += x * y; sxx += x * x; syy += y * y;
        }
        return sxy / std::sqrt(sxx * syy + 1e-12);
    }

    void analyzeFrame() {
        std::vector<std::complex<double>> x(kN);
        for (size_t i = 0; i < kN; i++) {
            double w = 0.5 - 0.5 * std::cos(2 * M_PI * i / kN);
            x[i] = buf_[i] * w;
        }
        fft(x);
        double frame[12] = {0};
        double energy = 0;
        for (size_t k = 1; k < kN / 2; k++) {
            double f = k * frameRate_ / kN;
            if (f < 80 || f > 2000) continue;
            double mag = std::abs(x[k]);
            double midi = 69 + 12 * std::log2(f / 440.0);
            int pc = ((int)std::lround(midi) % 12 + 12) % 12;
            // Abstand zum nächsten Halbton: Töne genau auf dem Halbton zählen mehr
            double off = std::fabs(midi - std::round(midi));
            double w = mag * (1.0 - off);
            frame[pc] += w;
            energy += w;
        }
        if (energy < 1e-6) return;           // Stille
        // Jeden Frame normieren (laute Stellen dominieren nicht), log-komprimiert
        for (int i = 0; i < 12; i++) chroma_[i] += std::log1p(10.0 * frame[i] / energy);
        frames_++;
    }

    static void fft(std::vector<std::complex<double>>& a) {
        size_t n = a.size();
        for (size_t i = 1, j = 0; i < n; i++) {
            size_t bit = n >> 1;
            for (; j & bit; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) std::swap(a[i], a[j]);
        }
        for (size_t len = 2; len <= n; len <<= 1) {
            double ang = -2 * M_PI / len;
            std::complex<double> wl(std::cos(ang), std::sin(ang));
            for (size_t i = 0; i < n; i += len) {
                std::complex<double> w(1);
                for (size_t j = 0; j < len / 2; j++) {
                    auto u = a[i + j], v = a[i + j + len / 2] * w;
                    a[i + j] = u + v;
                    a[i + j + len / 2] = u - v;
                    w *= wl;
                }
            }
        }
    }

    int rate_ = 0, decim_ = 4;
    double frameRate_ = 11025;
    std::vector<float> buf_;
    float acc_ = 0;
    int accN_ = 0;
    double chroma_[12] = {0};
    int frames_ = 0;
};

}  // namespace dj
