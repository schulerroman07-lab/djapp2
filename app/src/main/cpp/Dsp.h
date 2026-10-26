// Kleine Audio-Bausteine: Filter (Biquad), Ringpuffer, Hilfsfunktionen.
#pragma once
#include <algorithm>
#include <atomic>
#include <cmath>
#include <vector>
#include <cstdint>

namespace dj {

constexpr float kPi = 3.14159265358979f;

// Klassischer Biquad-Filter (Formeln nach "RBJ Audio EQ Cookbook").
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1[2] = {0, 0}, z2[2] = {0, 0};  // Zustand pro Kanal (L/R)

    void reset() { z1[0] = z1[1] = z2[0] = z2[1] = 0; }

    void setLowpass(float freq, float q, float rate) { set(freq, q, rate, false); }
    void setHighpass(float freq, float q, float rate) { set(freq, q, rate, true); }

    inline float process(float x, int ch) {
        // "Transposed Direct Form II" – numerisch stabil
        float y = b0 * x + z1[ch];
        z1[ch] = b1 * x - a1 * y + z2[ch];
        z2[ch] = b2 * x - a2 * y;
        return y;
    }

private:
    void set(float freq, float q, float rate, bool high) {
        if (freq > rate * 0.45f) freq = rate * 0.45f;
        if (freq < 10.f) freq = 10.f;
        float w0 = 2.f * kPi * freq / rate;
        float cs = std::cos(w0), sn = std::sin(w0);
        float alpha = sn / (2.f * q);
        float a0 = 1.f + alpha;
        if (high) {
            b0 = (1.f + cs) / 2.f / a0;
            b1 = -(1.f + cs) / a0;
            b2 = b0;
        } else {
            b0 = (1.f - cs) / 2.f / a0;
            b1 = (1.f - cs) / a0;
            b2 = b0;
        }
        a1 = -2.f * cs / a0;
        a2 = (1.f - alpha) / a0;
    }
};

// Ringpuffer für Stereo-Float-Samples: ein Thread schreibt, ein anderer liest
// (ohne Sperren, damit der Audio-Thread nie warten muss).
class StereoRing {
public:
    explicit StereoRing(size_t frames = 1 << 17) : buf_(frames * 2), cap_(frames) {}

    size_t available() const {
        return w_.load(std::memory_order_acquire) - r_.load(std::memory_order_acquire);
    }
    size_t space() const { return cap_ - available(); }

    // Schreibt Frames; was keinen Platz hat, wird verworfen.
    void write(const float* lr, size_t frames) {
        size_t w = w_.load(std::memory_order_relaxed);
        size_t n = std::min(frames, space());
        for (size_t i = 0; i < n; i++) {
            size_t idx = ((w + i) % cap_) * 2;
            buf_[idx] = lr[i * 2];
            buf_[idx + 1] = lr[i * 2 + 1];
        }
        w_.store(w + n, std::memory_order_release);
    }

    // Liest bis zu 'frames' Frames, gibt die Anzahl gelesener Frames zurück.
    size_t read(float* lr, size_t frames) {
        size_t r = r_.load(std::memory_order_relaxed);
        size_t n = std::min(frames, available());
        for (size_t i = 0; i < n; i++) {
            size_t idx = ((r + i) % cap_) * 2;
            lr[i * 2] = buf_[idx];
            lr[i * 2 + 1] = buf_[idx + 1];
        }
        r_.store(r + n, std::memory_order_release);
        return n;
    }

    void clear() { r_.store(w_.load()); }

private:
    std::vector<float> buf_;
    size_t cap_;
    std::atomic<size_t> w_{0}, r_{0};
};

/**
 * Lautheit eines Titels in dB (vereinfacht nach EBU R128): 400-ms-Blöcke, Bass unter 100 Hz
 * abgeschwächt, leise Stellen (Stille, Intro-Pausen) werden ausgeblendet ("Gate").
 * Damit klingen zwei Songs mit gleichem Wert etwa gleich laut.
 */
inline float gatedLoudnessDb(const int16_t* pcm, size_t frames, int rate) {
    if (frames == 0 || rate <= 0) return -70.f;
    Biquad hp;
    hp.setHighpass(100.f, 0.707f, (float)rate);
    size_t block = (size_t)rate * 4 / 10;
    std::vector<double> ms;
    double acc = 0;
    size_t n = 0;
    for (size_t i = 0; i < frames; i++) {
        float x = (pcm[i * 2] + pcm[i * 2 + 1]) * (0.5f / 32768.f);
        float y = hp.process(x, 0);
        acc += (double)y * y;
        if (++n == block) {
            ms.push_back(acc / n);
            acc = 0;
            n = 0;
        }
    }
    auto gatedMean = [&](double thr) {
        double s = 0;
        size_t c = 0;
        for (double v : ms)
            if (v > thr) { s += v; c++; }
        return c ? s / c : 0.0;
    };
    double abs = gatedMean(1e-6);               // -60 dB: echte Stille weg
    if (abs <= 0) return -70.f;
    double rel = gatedMean(abs * 0.1);          // 10 dB unter dem Mittel: leise Passagen weg
    return (float)(10.0 * std::log10(std::max(rel, 1e-10)));
}

// Weiche Begrenzung, damit es bei Übersteuerung nicht hart knackt.
inline float softClip(float x) {
    if (x > 1.5f) return 1.f;
    if (x < -1.5f) return -1.f;
    return x - (x * x * x) / 6.75f;
}

inline float dbToGain(float db) { return std::pow(10.f, db / 20.f); }

}  // namespace dj
