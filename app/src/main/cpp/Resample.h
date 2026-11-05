// Abtastrate umrechnen (z.B. 44,1 kHz → 48 kHz) mit kubischer Interpolation.
#pragma once
#include <cstdint>
#include <vector>

namespace dj {

/** Rechnet die ersten srcFrames Frames von src (Stereo, 16 Bit) auf targetRate um. */
inline void resampleTo(const std::vector<int16_t>& src, size_t srcFrames, int srcRate, int targetRate, std::vector<int16_t>& out) {
    if (srcRate == targetRate || srcRate <= 0) {
        out.assign(src.begin(), src.begin() + srcFrames * 2);
        return;
    }
    // Abtastrate umrechnen (kubische Interpolation, gute Qualität)
    double ratio = (double)srcRate / targetRate;
    size_t outFrames = (size_t)((double)srcFrames / ratio);
    out.assign(outFrames * 2, 0);
    auto at = [&](long i, int ch) -> float {
        if (i < 0) i = 0;
        if (i >= (long)srcFrames) i = (long)srcFrames - 1;
        return src[i * 2 + ch];
    };
    for (size_t o = 0; o < outFrames; o++) {
        double p = o * ratio;
        long i = (long)p;
        float t = (float)(p - i);
        for (int ch = 0; ch < 2; ch++) {
            float y0 = at(i - 1, ch), y1 = at(i, ch), y2 = at(i + 1, ch), y3 = at(i + 2, ch);
            // Hermite-Interpolation
            float c1 = 0.5f * (y2 - y0);
            float c2 = y0 - 2.5f * y1 + 2.f * y2 - 0.5f * y3;
            float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
            float v = ((c3 * t + c2) * t + c1) * t + y1;
            if (v > 32767.f) v = 32767.f;
            if (v < -32768.f) v = -32768.f;
            out[o * 2 + ch] = (int16_t)v;
        }
    }
}


}  // namespace dj
