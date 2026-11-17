#include "Analyzer.h"

#include <algorithm>
#include <cmath>
#include <numeric>

#include "VocalModel.h"
#include "VocalNet.h"

namespace dj {

namespace {

const double kPi = 3.14159265358979323846;

// Radix-2-FFT (in place) mit vorberechneten Tabellen – getrennte Real-/Imaginärteile (schnell auf dem Handy)
struct Fft {
    int n = 0;
    std::vector<int> rev;
    std::vector<float> cs, sn;
    void init(int size) {
        n = size;
        rev.resize(n);
        for (int i = 0, j = 0; i < n; i++) {
            rev[i] = j;
            int bit = n >> 1;
            for (; j & bit; bit >>= 1) j ^= bit;
            j ^= bit;
        }
        cs.resize(n / 2);
        sn.resize(n / 2);
        for (int k = 0; k < n / 2; k++) {
            cs[k] = (float)std::cos(-2 * kPi * k / n);
            sn[k] = (float)std::sin(-2 * kPi * k / n);
        }
    }
    // re/im: Eingabe in natürlicher Reihenfolge, Ausgabe = Spektrum
    void run(float* re, float* im) const {
        for (int i = 0; i < n; i++) {
            const int j = rev[i];
            if (i < j) { std::swap(re[i], re[j]); std::swap(im[i], im[j]); }
        }
        for (int len = 2; len <= n; len <<= 1) {
            const int half = len >> 1, step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < half; k++) {
                    const float wr = cs[k * step], wi = sn[k * step];
                    const float xr = re[i + k + half] * wr - im[i + k + half] * wi;
                    const float xi = re[i + k + half] * wi + im[i + k + half] * wr;
                    re[i + k + half] = re[i + k] - xr;
                    im[i + k + half] = im[i + k] - xi;
                    re[i + k] += xr;
                    im[i + k] += xi;
                }
            }
        }
    }
};

float quantile(std::vector<float> v, double q) {
    if (v.empty()) return 0;
    size_t k = (size_t)std::min<double>(v.size() - 1, std::max(0.0, q * (v.size() - 1)));
    std::nth_element(v.begin(), v.begin() + k, v.end());
    return v[k];
}

// Wert einer Kurve an gebrochener Stelle (linear interpoliert)
inline float at(const std::vector<float>& v, double x) {
    if (x <= 0) return v.empty() ? 0 : v[0];
    size_t i = (size_t)x;
    if (i + 1 >= v.size()) return v.empty() ? 0 : v.back();
    float f = (float)(x - i);
    return v[i] * (1 - f) + v[i + 1] * f;
}

}  // namespace

AnalyzerTuning& tuning() {
    static AnalyzerTuning t;
    return t;
}

void TrackAnalyzer::setup(int sampleRate) {
    rate_ = sampleRate;
    fps_ = (double)sampleRate / kHop;
    mid_.assign(kN, 0.f);
    side_.assign(kN, 0.f);
    ringPos_ = filled_ = sinceHop_ = 0;
    window_.resize(kN);
    for (int i = 0; i < kN; i++) window_[i] = (float)(0.5 - 0.5 * std::cos(2 * kPi * i / kN));
    // Bänder: 20 Stück, logarithmisch von 40 Hz bis 16 kHz
    bandOf_.assign(kN / 2 + 1, -1);
    const double f0 = 40.0, f1 = 16000.0;
    lowBands_ = 0;
    for (int k = 1; k <= kN / 2; k++) {
        double f = (double)k * sampleRate / kN;
        if (f < f0 || f >= f1) continue;
        int b = (int)(std::log(f / f0) / std::log(f1 / f0) * kBands);
        bandOf_[k] = std::min(kBands - 1, std::max(0, b));
    }
    for (int b = 0; b < kBands; b++) {
        double hi = f0 * std::pow(f1 / f0, (double)(b + 1) / kBands);
        if (hi <= 160.0) lowBands_ = b + 1;
    }
    // Mel-Filter (dreieckig, kMel Stück zwischen 50 Hz und 10 kHz): jedes Bin liegt zwischen zwei Mittelpunkten
    melIdx_.assign(kN / 2 + 1, -2);
    melW_.assign(kN / 2 + 1, 0.f);
    {
        auto mel = [](double f) { return 2595.0 * std::log10(1.0 + f / 700.0); };
        const double m0 = mel(50.0), m1 = mel(10000.0), d = (m1 - m0) / (kMel + 1);
        for (int k = 1; k <= kN / 2; k++) {
            const double u = (mel((double)k * sampleRate / kN) - m0) / d;
            if (u < 0 || u >= kMel + 1) continue;
            const int i = (int)u;
            melIdx_[k] = i - 1;                 // Filter i−1 (fallende Flanke) und i (steigende Flanke)
            melW_[k] = (float)(u - i);
        }
    }
    melAccM_.assign(kMel, 0.0);
    melAccS_.assign(kMel, 0.0);
    melFrames_ = 0;
    prevLog_.assign(kBands, 0.f);
    prev2Log_.assign(kBands, 0.f);
    accMid_.assign(kBands, 0.0);
    accSide_.assign(kBands, 0.0);
    accFrames_ = 0;
}

void TrackAnalyzer::feed(const int16_t* stereo, size_t frames, int sampleRate) {
    if (rate_ != sampleRate) setup(sampleRate);
    for (size_t i = 0; i < frames; i++) {
        const float l = stereo[i * 2] * (1.f / 32768.f), r = stereo[i * 2 + 1] * (1.f / 32768.f);
        const float m = 0.5f * (l + r);
        mid_[ringPos_] = m;
        side_[ringPos_] = 0.5f * (l - r);
        ringPos_ = (ringPos_ + 1) % kN;
        sumAll_ += m * m;
        sumCount_++;
        if (filled_ < (size_t)kN) filled_++;
        if (++sinceHop_ >= (size_t)kHop && filled_ >= (size_t)kN) {
            sinceHop_ = 0;
            frame();
        }
    }
}

void TrackAnalyzer::frame() {
    // Mitte und Seite in EINER FFT: z = Mitte + i·Seite  →  M[k] = (Z[k] + conj(Z[N−k]))/2, S[k] = (Z[k] − conj(Z[N−k]))/(2i)
    static const Fft fftTab = [] { Fft f; f.init(kN); return f; }();
    static thread_local std::vector<float> re, im;
    re.resize(kN);
    im.resize(kN);
    for (int i = 0; i < kN; i++) {
        const size_t p = (ringPos_ + i) % kN;
        re[i] = mid_[p] * window_[i];
        im[i] = side_[p] * window_[i];
    }
    fftTab.run(re.data(), im.data());
    const float norm = 1.f / (kN / 4.f);        // Sinus mit voller Amplitude → 1
    double bm[kBands] = {0}, bs[kBands] = {0};
    double all = 0, low = 0, midp = 0, high = 0;
    for (int k = 1; k <= kN / 2; k++) {
        const float ar = re[k], ai = im[k], br = re[kN - k], bi = im[kN - k];
        const float mr = 0.5f * (ar + br), mi = 0.5f * (ai - bi);
        const float sr = 0.5f * (ai + bi), si = 0.5f * (br - ar);
        const float pm = (mr * mr + mi * mi) * norm * norm;
        const float ps = (sr * sr + si * si) * norm * norm;
        const double f = (double)k * rate_ / kN;
        all += pm;
        if (f < 150) low += pm;
        else if (f >= 300 && f < 3000) midp += pm;
        else if (f >= 3000) high += pm;
        const int b = bandOf_[k];
        if (b >= 0) { bm[b] += pm; bs[b] += ps; }
        const int j = melIdx_[k];
        if (j >= -1) {
            const float w = melW_[k];
            if (j >= 0) { melAccM_[j] += (1 - w) * pm; melAccS_[j] += (1 - w) * ps; }
            if (j + 1 < kMel) { melAccM_[j + 1] += w * pm; melAccS_[j + 1] += w * ps; }
        }
    }
    if (++melFrames_ >= kMelBlock) {
        for (int m = 0; m < kMel; m++) {
            melMid_.push_back(10.f * std::log10((float)(melAccM_[m] / kMelBlock) + 1e-10f));
            melSide_.push_back(10.f * std::log10((float)(melAccS_[m] / kMelBlock) + 1e-10f));
            melAccM_[m] = melAccS_[m] = 0;
        }
        melFrames_ = 0;
    }
    sumLow_ += low;
    // Spektralfluss (nur Anstiege), in logarithmischer Lautstärke – gegen den Rahmen 2 Schritte zuvor
    float flux = 0, fluxLow = 0;
    for (int b = 0; b < kBands; b++) {
        const float lg = std::log(1.f + 200.f * (float)std::sqrt(bm[b]));
        const float d = lg - prev2Log_[b];
        if (d > 0) {
            flux += d;
            if (b < lowBands_) fluxLow += d;
        }
        prev2Log_[b] = prevLog_[b];
        prevLog_[b] = lg;
        accMid_[b] += bm[b];
        accSide_[b] += bs[b];
    }
    ose_.push_back(flux);
    oseLow_.push_back(fluxLow);
    pAll_.push_back((float)all);
    pLow_.push_back((float)low);
    pMid_.push_back((float)midp);
    pHigh_.push_back((float)high);
    if (++accFrames_ >= kBlock) {
        for (int b = 0; b < kBands; b++) {
            blkMid_.push_back((float)(10.0 * std::log10(accMid_[b] / kBlock + 1e-10)));
            blkSide_.push_back((float)(10.0 * std::log10(accSide_[b] / kBlock + 1e-10)));
            accMid_[b] = accSide_[b] = 0;
        }
        accFrames_ = 0;
    }
}

// ---------------------------------------------------------------------------------------------
// Gesang
// ---------------------------------------------------------------------------------------------

std::vector<float> TrackAnalyzer::vocalFeatures() const {
    const size_t nb = blkMid_.size() / kBands;
    std::vector<float> out(nb * kVocalDim, 0.f);
    if (nb == 0) return out;
    // Lautstärke-Bezug: lauteste Stellen des Songs
    std::vector<float> tot(nb);
    for (size_t i = 0; i < nb; i++) {
        double p = 0;
        for (int b = 0; b < kBands; b++) p += std::pow(10.0, blkMid_[i * kBands + b] / 10.0);
        tot[i] = (float)(10.0 * std::log10(p + 1e-10));
    }
    const float ref = quantile(tot, 0.95);
    // Bänder des Sprachbereichs (ca. 300–3400 Hz)
    int v0 = 0, v1 = kBands;
    for (int b = 0; b < kBands; b++) {
        const double lo = 40.0 * std::pow(400.0, (double)b / kBands), hi = 40.0 * std::pow(400.0, (double)(b + 1) / kBands);
        if (hi <= 300.0) v0 = b + 1;
        if (lo < 3400.0) v1 = b + 1;
    }
    std::vector<float> vb(nb), vs(nb);
    for (size_t i = 0; i < nb; i++) {
        double pm = 0, ps = 0;
        for (int b = v0; b < v1; b++) {
            pm += std::pow(10.0, blkMid_[i * kBands + b] / 10.0);
            ps += std::pow(10.0, blkSide_[i * kBands + b] / 10.0);
        }
        vb[i] = (float)(10.0 * std::log10(pm + 1e-10));
        vs[i] = (float)(10.0 * std::log10(ps + 1e-10));
    }
    const int ctx = std::max(1, (int)std::lround(0.5 * fps_ / kBlock));   // ±0,5 s
    for (size_t i = 0; i < nb; i++) {
        float* o = &out[i * kVocalDim];
        double mean = 0;
        for (int b = 0; b < kBands; b++) mean += blkMid_[i * kBands + b];
        mean /= kBands;
        for (int b = 0; b < kBands; b++) {
            const float m = blkMid_[i * kBands + b], s = blkSide_[i * kBands + b];
            o[b] = (float)(m - mean) / 10.f;                                   // Klangfarbe (Form des Spektrums)
            o[kBands + b] = std::max(-4.f, std::min(2.f, (s - m) / 10.f));     // Stereo-Breite pro Band
        }
        o[40] = std::max(-6.f, (tot[i] - ref) / 10.f);                         // Lautstärke relativ zum Song
        o[41] = (vb[i] - tot[i]) / 10.f;                                       // Anteil des Sprachbereichs
        o[42] = std::max(-4.f, std::min(2.f, (vs[i] - vb[i]) / 10.f));         // Sprachbereich: Seite gegen Mitte
        // zeitliche Bewegung im Sprachbereich (Gesang schwankt, Flächen/Synths stehen)
        const size_t a = i > (size_t)ctx ? i - ctx : 0, e = std::min(nb, i + ctx + 1);
        double m1 = 0, m2 = 0, fl = 0;
        for (size_t k = a; k < e; k++) {
            m1 += vb[k];
            m2 += (double)vb[k] * vb[k];
            if (k > a) fl += std::fabs(vb[k] - vb[k - 1]);
        }
        const double cnt = (double)(e - a);
        o[43] = (float)std::sqrt(std::max(0.0, m2 / cnt - (m1 / cnt) * (m1 / cnt))) / 10.f;
        o[44] = (float)(fl / std::max(1.0, cnt - 1)) / 10.f;
        double lowp = 0;
        for (int b = 0; b < lowBands_; b++) lowp += std::pow(10.0, blkMid_[i * kBands + b] / 10.0);
        o[45] = (float)(10.0 * std::log10(lowp + 1e-10) - tot[i]) / 10.f;      // Bass-Anteil
    }
    return out;
}

std::vector<float> TrackAnalyzer::vocalInputs() const {
    // Klassifikator-Eingang: Mittelwert und Streuung der Block-Merkmale über ±0,75 s, alle 0,25 s
    const std::vector<float> f = vocalFeatures();
    const size_t nb = f.size() / kVocalDim;
    std::vector<float> out;
    if (nb == 0) return out;
    const double hopS = vocalHop();
    const int half = std::max(1, (int)std::lround(0.75 / hopS));
    const double step = 0.25 / hopS;
    for (double c = 0; c < (double)nb; c += step) {
        const size_t ci = (size_t)c;
        const size_t a = ci > (size_t)half ? ci - half : 0, e = std::min(nb, ci + half + 1);
        const size_t base = out.size();
        out.resize(base + 2 * kVocalDim);
        for (int d = 0; d < kVocalDim; d++) {
            double m1 = 0, m2 = 0;
            for (size_t k = a; k < e; k++) {
                const float v = f[k * kVocalDim + d];
                m1 += v;
                m2 += (double)v * v;
            }
            const double cnt = (double)(e - a);
            out[base + d] = (float)(m1 / cnt);
            out[base + kVocalDim + d] = (float)std::sqrt(std::max(0.0, m2 / cnt - (m1 / cnt) * (m1 / cnt)));
        }
    }
    return out;
}

std::vector<float> TrackAnalyzer::melInput() const {
    const size_t nf = melMid_.size() / kMel;
    std::vector<float> out(nf * 2 * kMel);
    if (nf == 0) return out;
    // Lautstärke-Bezug: die lauten Stellen des Songs (95 %-Quantil des mittleren Pegels)
    std::vector<float> lvl(nf);
    for (size_t t = 0; t < nf; t++) {
        double s = 0;
        for (int m = 0; m < kMel; m++) s += melMid_[t * kMel + m];
        lvl[t] = (float)(s / kMel);
    }
    const float ref = quantile(lvl, 0.95);
    for (size_t t = 0; t < nf; t++) {
        float* o = &out[t * 2 * kMel];
        for (int m = 0; m < kMel; m++) {
            const float md = melMid_[t * kMel + m], sd = melSide_[t * kMel + m];
            o[m] = std::max(-80.f, std::min(20.f, md - ref));                   // Mitte relativ zum Song
            o[kMel + m] = std::max(-30.f, std::min(10.f, sd - md));             // Stereo-Breite pro Band
        }
    }
    return out;
}

namespace {
// Faltung 3×3 (Rand mit Nullen) + ReLU + Max-Pooling 2×2: in [cin][F][T] → out [cout][F/2][T/2]
void convPool(const std::vector<float>& in, int cin, int F, int T, const float* W, const float* B, int cout,
              std::vector<float>& out) {
    const int F2 = F / 2, T2 = T / 2;
    out.assign((size_t)cout * F2 * T2, 0.f);
    std::vector<float> r0(T), r1(T);
    for (int co = 0; co < cout; co++) {
        for (int f = 0; f < F2 * 2; f++) {
            std::vector<float>& r = (f & 1) ? r1 : r0;
            std::fill(r.begin(), r.end(), B[co]);
            for (int ci = 0; ci < cin; ci++) {
                for (int df = 0; df < 3; df++) {
                    const int ff = f + df - 1;
                    if (ff < 0 || ff >= F) continue;
                    const float* src = &in[((size_t)ci * F + ff) * T];
                    const float* w = &W[(((size_t)co * cin + ci) * 3 + df) * 3];
                    // Zeit-Versatz −1, 0, +1
                    for (int t = 1; t < T; t++) r[t] += w[0] * src[t - 1];
                    for (int t = 0; t < T; t++) r[t] += w[1] * src[t];
                    for (int t = 0; t + 1 < T; t++) r[t] += w[2] * src[t + 1];
                }
            }
            if (f & 1) {
                float* o = &out[((size_t)co * F2 + f / 2) * T2];
                for (int t = 0; t < T2; t++)
                    o[t] = std::max(0.f, std::max(std::max(r0[2 * t], r0[2 * t + 1]), std::max(r1[2 * t], r1[2 * t + 1])));
            }
        }
    }
}
}  // namespace

std::vector<float> TrackAnalyzer::vocalNet(std::vector<float>* times) const {
    using namespace vn;
    const std::vector<float> mel = melInput();
    const int T = (int)(mel.size() / (2 * kMel));
    if (!kReady || T < 16) return {};
    // Eingang normieren und umordnen: [Kanal][Band][Zeit]
    std::vector<float> x((size_t)kIn * kF * T);
    for (int c = 0; c < kIn; c++)
        for (int m = 0; m < kF; m++) {
            const float mu = kMean[c * kF + m], is = 1.f / kStd[c * kF + m];
            float* o = &x[((size_t)c * kF + m) * T];
            for (int t = 0; t < T; t++) o[t] = (mel[(size_t)t * 2 * kMel + c * kMel + m] - mu) * is;
        }
    std::vector<float> a, b;
    convPool(x, kIn, kF, T, W1, B1, kC1, a);
    convPool(a, kC1, kF / 2, T / 2, W2, B2, kC2, b);
    convPool(b, kC2, kF / 4, T / 4, W3, B3, kC3, a);
    const int T3 = T / 8, F3 = kF / 8;
    // letzte Schicht: Faltung über alle restlichen Bänder × kK4 Zeitschritte, ReLU, dann gewichtete Summe → Sigmoid
    std::vector<float> out(T3);
    std::vector<float> h(T3);
    const int half = kK4 / 2;
    std::vector<double> z(T3, B5);
    for (int co = 0; co < kC4; co++) {
        std::fill(h.begin(), h.end(), B4[co]);
        for (int ci = 0; ci < kC3; ci++)
            for (int f = 0; f < F3 && f < kF4; f++) {
                const float* src = &a[((size_t)ci * F3 + f) * T3];
                const float* w = &W4[(((size_t)co * kC3 + ci) * kF4 + f) * kK4];
                for (int k = 0; k < kK4; k++) {
                    const int d = k - half;
                    const float wk = w[k];
                    const int t0 = std::max(0, -d), t1 = std::min(T3, T3 - d);
                    for (int t = t0; t < t1; t++) h[t] += wk * src[t + d];
                }
            }
        for (int t = 0; t < T3; t++) z[t] += W5[co] * std::max(0.f, h[t]);
    }
    for (int t = 0; t < T3; t++) out[t] = (float)(1.0 / (1.0 + std::exp(-z[t])));
    if (times) {
        // Mitte eines Ausgabe-Schritts: 8 Mel-Rahmen à kMelBlock FFT-Rahmen, FFT-Fenster-Mitte
        times->resize(T3);
        for (int t = 0; t < T3; t++)
            (*times)[t] = (float)(((8.0 * t + 3.5) * kMelBlock + 1.5) / fps_ + (kN / 2.0) / rate_);
    }
    return out;
}

std::vector<float> TrackAnalyzer::vocalProb() const {
    // Gesangs-Netz (wenn trainiert): auf das 0,25-s-Raster der App umrechnen (linear interpoliert)
    std::vector<float> tm;
    const std::vector<float> p = vocalNet(&tm);
    if (!p.empty()) {
        const double dur = (double)sumCount_ / std::max(1, rate_);
        const size_t n = (size_t)(dur / 0.25) + 1;
        std::vector<float> out(n);
        size_t j = 0;
        for (size_t i = 0; i < n; i++) {
            const double t = i * 0.25;
            while (j + 1 < tm.size() && tm[j + 1] <= t) j++;
            if (t <= tm[0]) out[i] = p[0];
            else if (j + 1 >= tm.size()) out[i] = p.back();
            else {
                const double f = (t - tm[j]) / (tm[j + 1] - tm[j]);
                out[i] = (float)(p[j] * (1 - f) + p[j + 1] * f);
            }
        }
        return out;
    }
    const std::vector<float> in = vocalInputs();
    const size_t n = in.size() / (2 * kVocalDim);
    std::vector<float> out(n);
    for (size_t i = 0; i < n; i++) out[i] = vocalModelPredict(&in[i * 2 * kVocalDim]);
    return out;
}

// ---------------------------------------------------------------------------------------------
// Beats
// ---------------------------------------------------------------------------------------------

Analysis TrackAnalyzer::result() const {
    Analysis res;
    const size_t n = ose_.size();
    if (sumCount_ > 0) {
        const double rmsDb = 10.0 * std::log10(1e-12 + sumAll_ / sumCount_);
        const double loud = std::min(1.0, std::max(0.0, (rmsDb + 28.0) / 20.0));
        double lowShare = 0;
        if (!pAll_.empty()) {
            double a = 0, l = 0;
            for (size_t i = 0; i < pAll_.size(); i++) { a += pAll_[i]; l += pLow_[i]; }
            lowShare = l / (a + 1e-12);
        }
        res.energy = (float)(0.7 * loud + 0.3 * std::min(1.0, lowShare * 2.0));
    }
    {
        const std::vector<float> vp = vocalProb();
        res.vocal.resize(vp.size());
        for (size_t i = 0; i < vp.size(); i++) res.vocal[i] = (uint8_t)std::min(255.f, std::max(0.f, vp[i] * 255.f));
    }
    if (n < (size_t)(fps_ * 8)) return res;          // weniger als 8 s: zu kurz

    // 1) Onset-Kurve: Fluss gesamt + Bass betont, lokalen Mittelwert abziehen, normieren
    std::vector<float> o(n);
    for (size_t i = 0; i < n; i++) o[i] = ose_[i] + tuning().lowWeight * oseLow_[i];
    {
        const int w = (int)(fps_ * 0.5);
        std::vector<double> cum(n + 1, 0.0);
        for (size_t i = 0; i < n; i++) cum[i + 1] = cum[i] + o[i];
        std::vector<float> t(n);
        for (size_t i = 0; i < n; i++) {
            const size_t a = i > (size_t)w ? i - w : 0, e = std::min(n, i + w + 1);
            t[i] = std::max(0.f, o[i] - (float)((cum[e] - cum[a]) / (e - a)));
        }
        double s2 = 0;
        for (float v : t) s2 += (double)v * v;
        const float sd = (float)std::sqrt(s2 / n) + 1e-9f;
        for (size_t i = 0; i < n; i++) o[i] = t[i] / sd;
    }

    // 2) Tempo: Autokorrelation + Kamm über Vielfache, mit Vorliebe für 70–170 BPM
    const int minLag = (int)(fps_ * 60.0 / 215.0), maxLag = (int)(fps_ * 60.0 / 50.0);
    const int accLag = maxLag * 4 + 2;
    std::vector<float> acf(accLag + 1, 0.f);
    for (int lag = 1; lag <= accLag && (size_t)lag < n; lag++) {
        double s = 0;
        for (size_t i = lag; i < n; i++) s += (double)o[i] * o[i - lag];
        acf[lag] = (float)(s / (n - lag));
    }
    auto comb = [&](double lag) {
        // Summe über 1×, 2×, 3×, 4× die Periode (ein Beat-Tempo erklärt auch die Vielfachen)
        double s = 0;
        for (int k = 1; k <= 4; k++) s += at(acf, lag * k) * (k == 1 ? 1.0 : 0.6);
        return s;
    };
    double bestLag = 0, bestScore = -1e9;
    std::vector<std::pair<double, double>> cands;      // (Lag, Wert) lokaler Maxima
    for (double lag = minLag; lag <= maxLag; lag += 0.25) {
        const double bpm = 60.0 * fps_ / lag;
        const double pri = std::exp(-0.5 * std::pow(std::log2(bpm / tuning().priorBpm) / tuning().priorOct, 2.0));
        const double s = comb(lag) * pri;
        if (s > bestScore) { bestScore = s; bestLag = lag; }
    }
    if (bestLag <= 0 || bestScore <= 0) return res;
    // Feinsuche um das Maximum (ohne Vorliebe)
    {
        double b = bestLag, bs = -1e9;
        for (double lag = bestLag - 1.0; lag <= bestLag + 1.0; lag += 0.02) {
            const double s = comb(lag);
            if (s > bs) { bs = s; b = lag; }
        }
        bestLag = b;
    }
    const double period = bestLag;                      // Rahmen pro Beat

    // 3) Beats per dynamischer Programmierung (Ellis): hohe Onset-Werte, Abstand nahe der Periode
    std::vector<float> ls(n);
    {
        const double sig = std::max(1.0, period / 32.0);
        const int r = (int)std::ceil(sig * 3);
        std::vector<float> g(2 * r + 1);
        double gs = 0;
        for (int k = -r; k <= r; k++) { g[k + r] = (float)std::exp(-0.5 * k * k / (sig * sig)); gs += g[k + r]; }
        for (size_t i = 0; i < n; i++) {
            double s = 0;
            for (int k = -r; k <= r; k++) {
                const long j = (long)i + k;
                if (j >= 0 && j < (long)n) s += o[j] * g[k + r];
            }
            ls[i] = (float)(s / gs);
        }
    }
    std::vector<float> cum(n);
    std::vector<int> back(n, -1);
    {
        const int lo = (int)std::lround(period * 0.5), hi = (int)std::lround(period * 2.0);
        std::vector<float> pen(hi - lo + 1);
        for (int d = lo; d <= hi; d++) pen[d - lo] = (float)(-tuning().tightness * std::pow(std::log((double)d / period), 2.0));
        for (size_t i = 0; i < n; i++) {
            float best = -1e30f;
            int bi = -1;
            for (int d = lo; d <= hi; d++) {
                const long j = (long)i - d;
                if (j < 0) break;
                const float v = cum[j] + pen[d - lo];
                if (v > best) { best = v; bi = (int)j; }
            }
            if (bi < 0) { cum[i] = ls[i]; back[i] = -1; }
            else { cum[i] = ls[i] + best; back[i] = bi; }
        }
    }
    // letzter Beat: bestes Ende im letzten Stück
    size_t last = n - 1;
    {
        float best = -1e30f;
        const size_t from = n > (size_t)(period * 1.5) ? n - (size_t)(period * 1.5) : 0;
        for (size_t i = from; i < n; i++) if (cum[i] > best) { best = cum[i]; last = i; }
    }
    std::vector<double> bt;                              // Beat-Zeiten in Rahmen (gebrochen)
    for (long i = (long)last; i >= 0; i = back[i]) {
        // genaue Lage: Parabel durch die drei Werte um das Maximum
        double x = (double)i;
        if (i > 0 && i + 1 < (long)n) {
            const double a = ls[i - 1], b = ls[i], c = ls[i + 1];
            const double den = a - 2 * b + c;
            if (den < -1e-9) x += std::max(-0.5, std::min(0.5, 0.5 * (a - c) / den));
        }
        bt.push_back(x);
        if (back[i] < 0) break;
    }
    std::reverse(bt.begin(), bt.end());
    if (bt.size() < 8) return res;

    // 4) Festes Raster anpassen: b_i ≈ t0 + T·k  (robust, Ausreisser werden ignoriert)
    const double tolFrames = 0.035 * fps_;
    double T = period, t0 = bt[0];
    {
        std::vector<float> d;
        for (size_t i = 1; i < bt.size(); i++) d.push_back((float)(bt[i] - bt[i - 1]));
        T = quantile(d, 0.5);
    }
    std::vector<long> idx(bt.size());
    double inlierFrac = 0;
    for (int iter = 0; iter < 6; iter++) {
        // Index jedes Beats im Raster
        const size_t mid = bt.size() / 2;
        for (size_t i = 0; i < bt.size(); i++) idx[i] = std::lround((bt[i] - bt[mid]) / T);
        if (iter == 0) t0 = bt[mid];
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        size_t cnt = 0;
        for (size_t i = 0; i < bt.size(); i++) {
            const double r = bt[i] - (t0 + T * idx[i]);
            if (iter > 0 && std::fabs(r) > tolFrames * (iter < 3 ? 3.0 : 1.0)) continue;
            sx += idx[i]; sy += bt[i]; sxx += (double)idx[i] * idx[i]; sxy += (double)idx[i] * bt[i];
            cnt++;
        }
        if (cnt >= 8) {
            const double den = cnt * sxx - sx * sx;
            if (std::fabs(den) > 1e-9) {
                T = (cnt * sxy - sx * sy) / den;
                t0 = (sy - T * sx) / cnt;
            }
        }
        size_t in = 0;
        for (size_t i = 0; i < bt.size(); i++) if (std::fabs(bt[i] - (t0 + T * idx[i])) <= tolFrames) in++;
        inlierFrac = (double)in / bt.size();
    }
    // Onset-Stärke an den Beats gegenüber dem Durchschnitt ("wie deutlich ist der Beat?")
    double atBeats = 0, overall = 0;
    for (double b : bt) atBeats += at(ls, b);
    atBeats /= bt.size();
    for (float v : ls) overall += v;
    overall /= n;
    const double salience = atBeats / (overall + 1e-9);
    // Tempo-Eindeutigkeit: Wert beim gefundenen Tempo gegenüber dem besten Wert ausserhalb ±8 %
    double other = 0;
    for (double lag = minLag; lag <= maxLag; lag += 0.5) {
        if (std::fabs(lag / period - 1.0) < 0.08) continue;
        bool multiple = false;
        for (double m : {0.5, 2.0, 1.5, 2.0 / 3.0, 3.0, 1.0 / 3.0}) if (std::fabs(lag / (period * m) - 1.0) < 0.05) multiple = true;
        if (multiple) continue;
        other = std::max(other, comb(lag));
    }
    const double clarity = comb(period) / (std::max(other, 1e-9) + comb(period));

    res.steady = inlierFrac >= tuning().steadyFrac;
    const double hopS = 1.0 / fps_;
    const double shift = (kN / 2.0) / rate_ - tuning().latencySec;     // Rahmen-Mitte, abzüglich gemessener Verzögerung
    const double durS = (double)sumCount_ / rate_;
    if (res.steady) {
        // Phase fein nachziehen: Raster so schieben, dass die Onset-Kurve an den Beats maximal ist
        double bestSh = 0, bs = -1e30;
        for (double sh = -4.0; sh <= 4.0; sh += 0.25) {
            double s = 0;
            int c = 0;
            for (double x = t0 + sh + T * std::ceil((0 - t0) / T); x < n - 1; x += T) { s += at(ls, x); c++; }
            if (c > 0 && s / c > bs) { bs = s / c; bestSh = sh; }
        }
        t0 += bestSh;
        const double Ts = T * hopS;
        double first = t0 * hopS + shift;
        first -= Ts * std::floor(first / Ts);            // erster Beat ≥ 0
        for (double t = first; t < durS; t += Ts) res.beats.push_back((float)t);
        res.bpm = (float)(60.0 / Ts);
    } else {
        for (double b : bt) {
            const double t = b * hopS + shift;
            if (t >= 0 && t < durS) res.beats.push_back((float)t);
        }
        std::vector<float> d;
        for (size_t i = 1; i < res.beats.size(); i++) d.push_back(res.beats[i] - res.beats[i - 1]);
        res.bpm = d.empty() ? 0 : 60.f / quantile(d, 0.5);
    }
    if (res.beats.size() < 8) { res.beats.clear(); res.bpm = 0; return res; }
    res.firstBeat = res.beats[0];
    // Vertrauen: aus Raster-Treue, Beat-Deutlichkeit und Tempo-Eindeutigkeit (an echter Musik geeicht)
    {
        const double z = tuning().confBias + tuning().confInlier * inlierFrac + tuning().confSalience * std::min(6.0, salience) + tuning().confClarity * clarity;
        res.confidence = (float)(1.0 / (1.0 + std::exp(-z)));
    }

    // 5) Downbeat ("die 1"): Bass-Einsätze und Klangwechsel sprechen für den Taktanfang
    const size_t nbk = blkMid_.size() / kBands;
    auto blockAt = [&](double sec) { return (long)((sec - shift) * fps_ / kBlock); };
    std::vector<double> lowAt(res.beats.size(), 0.0), novAt(res.beats.size(), 0.0), fullAt(res.beats.size(), 0.0);
    for (size_t i = 0; i < res.beats.size(); i++) {
        const double fr = (res.beats[i] - shift) * fps_;
        double l = 0, f = 0;
        for (int k = -2; k <= 3; k++) {
            const long j = (long)std::lround(fr) + k;
            if (j >= 0 && j < (long)n) { l = std::max(l, (double)oseLow_[j]); f = std::max(f, (double)ose_[j]); }
        }
        lowAt[i] = l;
        fullAt[i] = f;
        // Klangwechsel: Spektrum (80 Hz–2 kHz) des Beats davor gegen den Beat danach
        if (i > 0 && i + 1 < res.beats.size() && nbk > 0) {
            const long a0 = blockAt(res.beats[i - 1]), a1 = blockAt(res.beats[i]), b1 = blockAt(res.beats[i + 1]);
            if (a0 >= 0 && b1 <= (long)nbk && a1 > a0 && b1 > a1) {
                double dot = 0, na = 0, nb2 = 0;
                for (int b = 2; b < 14; b++) {
                    double va = 0, vb2 = 0;
                    for (long k = a0; k < a1; k++) va += std::pow(10.0, blkMid_[k * kBands + b] / 20.0);
                    for (long k = a1; k < b1; k++) vb2 += std::pow(10.0, blkMid_[k * kBands + b] / 20.0);
                    va /= (a1 - a0);
                    vb2 /= (b1 - a1);
                    dot += va * vb2; na += va * va; nb2 += vb2 * vb2;
                }
                novAt[i] = 1.0 - dot / (std::sqrt(na * nb2) + 1e-12);
            }
        }
    }
    {
        double sc[4] = {0, 0, 0, 0};
        double mLow = 0, mNov = 0;
        for (size_t i = 0; i < res.beats.size(); i++) { mLow += lowAt[i]; mNov += novAt[i]; }
        mLow = mLow / res.beats.size() + 1e-9;
        mNov = mNov / res.beats.size() + 1e-9;
        int cntp[4] = {0, 0, 0, 0};
        for (size_t i = 0; i < res.beats.size(); i++) {
            sc[i % 4] += tuning().dbLow * lowAt[i] / mLow + tuning().dbNov * novAt[i] / mNov;
            // Bass auch auf der "3" (halber Takt)
            sc[(i + 2) % 4] += tuning().dbLow3 * lowAt[i] / mLow;
            cntp[i % 4]++;
        }
        int best = 0;
        double b1 = -1e30, b2 = -1e30;
        for (int p = 0; p < 4; p++) {
            const double v = sc[p] / std::max(1, cntp[p]);
            if (v > b1) { b2 = b1; b1 = v; best = p; }
            else if (v > b2) b2 = v;
        }
        res.downbeat = best;
        res.downbeatConf = (float)std::min(1.0, std::max(0.0, (b1 - b2) / (std::fabs(b1) + 1e-9) * 4.0));
    }

    // 6) Merkmale pro Beat: Energie, Bass, Mitten, Höhen (auf laute Stellen normiert) + Gesang
    {
        const size_t nb = res.beats.size();
        std::vector<float> v[4];
        const std::vector<float>* src[4] = {&pAll_, &pLow_, &pMid_, &pHigh_};
        for (int b = 0; b < 4; b++) v[b].assign(nb, 0.f);
        for (size_t k = 0; k < nb; k++) {
            const double t0s = res.beats[k], t1s = k + 1 < nb ? res.beats[k + 1] : res.beats[k] + 60.0 / std::max(1.f, res.bpm);
            const long a = std::max(0L, (long)((t0s - shift) * fps_)), e = std::min((long)n, (long)((t1s - shift) * fps_));
            for (int b = 0; b < 4; b++) {
                double s = 0;
                for (long i = a; i < e; i++) s += (*src[b])[i];
                v[b][k] = (float)(e > a ? s / (e - a) : 0.0);
            }
        }
        res.features.assign(nb * kFeat, 0);
        for (int b = 0; b < 4; b++) {
            const float ref = quantile(v[b], 0.95) + 1e-12f;
            for (size_t k = 0; k < nb; k++) {
                const float x = std::sqrt(v[b][k] / ref);
                res.features[k * kFeat + b] = (uint8_t)std::min(255.f, std::max(0.f, x * 255.f));
            }
        }
        for (size_t k = 0; k < nb; k++) {
            const double t0s = res.beats[k], t1s = k + 1 < nb ? res.beats[k + 1] : res.beats[k] + 0.5;
            const long a = std::max(0L, (long)(t0s / 0.25)), e = std::min((long)res.vocal.size(), (long)std::ceil(t1s / 0.25));
            int m = 0;
            for (long i = a; i < e; i++) m = std::max(m, (int)res.vocal[i]);
            res.features[k * kFeat + 4] = (uint8_t)m;
        }
    }
    return res;
}

}  // namespace dj
