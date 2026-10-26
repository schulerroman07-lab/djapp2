#include "Deck.h"

#include <unistd.h>
#include <algorithm>
#include <cmath>
#include <cstring>

#include "Decoder.h"

namespace dj {

Deck::Deck() {
    for (int i = 0; i < 3; i++) {
        eq_[i].store(0.f);
        kill_[i].store(false);
    }
    window_.resize(kGrain);
    for (int k = 0; k < kGrain; k++) window_[k] = 0.5f - 0.5f * std::cos(2.f * kPi * k / kGrain);
    ola_.assign(kGrain * 2, 0.f);
    outFifo_.assign(kHop * 2, 0.f);
}

Deck::~Deck() {
    cancelLoad_.store(true);
    if (loader_.joinable()) loader_.join();
}

void Deck::load(int fd, int64_t length, int engineRate) {
    // Laufendes Laden abbrechen
    cancelLoad_.store(true);
    if (loader_.joinable()) loader_.join();
    cancelLoad_.store(false);

    playing_.store(false);
    state_.store(LoadState::Loading);

    loader_ = std::thread([this, fd, length, engineRate]() {
        auto t = std::make_unique<TrackData>();
        t->rate = engineRate;
        bool ok = decodeToRate(fd, length, engineRate, t->pcm, &cancelLoad_);
        close(fd);
        if (!ok || cancelLoad_.load()) {
            if (!cancelLoad_.load()) state_.store(LoadState::Error);
            return;
        }
        t->frames = t->pcm.size() / 2;

        // BPM und ersten Beat erkennen
        BpmAnalyzer an;
        const size_t chunk = 4096;
        for (size_t f = 0; f < t->frames; f += chunk) {
            size_t n = std::min(chunk, t->frames - f);
            an.feed(&t->pcm[f * 2], n, engineRate);
        }
        BpmResult r = an.result();
        t->bpm = r.bpm;
        t->firstBeat = r.firstBeat;
        t->features = an.beatFeatures(r.bpm, r.firstBeat);

        // Wellenform: 100 Spitzenwerte pro Sekunde
        size_t step = std::max(1, engineRate / 100);
        t->peaks.reserve(t->frames / step + 1);
        for (size_t f = 0; f < t->frames; f += step) {
            int peak = 0;
            size_t end = std::min(t->frames, f + step);
            for (size_t i = f; i < end; i++) {
                peak = std::max(peak, std::abs((int)t->pcm[i * 2]));
                peak = std::max(peak, std::abs((int)t->pcm[i * 2 + 1]));
            }
            t->peaks.push_back(peak / 32768.f);
        }

        {
            std::lock_guard<std::mutex> lk(peaksMutex_);
            peaks_ = t->peaks;
            featuresCopy_ = t->features;
        }
        loopOn_.store(false);
        frames_.store(t->frames);
        rate_.store(engineRate);
        bpm_.store(t->bpm);
        firstBeat_.store(t->firstBeat);
        cue_.store(0);

        std::unique_ptr<TrackData> old;
        {
            std::lock_guard<std::mutex> lk(trackMutex_);
            old = std::move(track_);
            track_ = std::move(t);
            seekRequest_.store(0);
            pos_.store(0);
        }
        state_.store(LoadState::Ready);
        // 'old' wird hier (ausserhalb des Audio-Threads) freigegeben
    });
}

void Deck::setLoop(double startSec, double lengthSec, bool on) {
    int rate = rate_.load();
    loopStart_.store(startSec * rate);
    loopLen_.store(lengthSec * rate);
    loopOn_.store(on && lengthSec > 0.05);
}

std::vector<uint8_t> Deck::features() {
    std::lock_guard<std::mutex> lk(peaksMutex_);
    return featuresCopy_;
}

void Reverb::prepare(int rate) {
    if (rate == rate_) return;
    rate_ = rate;
    const int combs[4] = {1116, 1188, 1277, 1356};
    const int aps[2] = {556, 441};
    float scale = rate / 44100.f;
    for (int i = 0; i < 4; i++) {
        combL_[i].assign((size_t)(combs[i] * scale), 0.f);
        combR_[i].assign((size_t)((combs[i] + 23) * scale), 0.f);
        combPosL_[i] = combPosR_[i] = 0;
        combStoreL_[i] = combStoreR_[i] = 0;
    }
    for (int i = 0; i < 2; i++) {
        apL_[i].assign((size_t)(aps[i] * scale), 0.f);
        apR_[i].assign((size_t)((aps[i] + 23) * scale), 0.f);
        apPosL_[i] = apPosR_[i] = 0;
    }
}

void Deck::setPlaying(bool p) {
    if (p && state_.load() != LoadState::Ready) return;
    playing_.store(p);
}

void Deck::cuePress() {
    if (state_.load() != LoadState::Ready) return;
    if (playing_.load()) {
        // Spielt: zurück zum Cue-Punkt und anhalten
        playing_.store(false);
        seekRequest_.store(cue_.load());
    } else {
        // Steht: aktuelle Position als Cue-Punkt merken
        double s = seekRequest_.load();
        cue_.store(s >= 0 ? s : pos_.load());
    }
}

void Deck::seekSeconds(double s) {
    if (s < 0) s = 0;
    seekRequest_.store(s * rate_.load());
}

double Deck::positionSeconds() const {
    double s = seekRequest_.load();
    return (s >= 0 ? s : pos_.load()) / rate_.load();
}

double Deck::durationSeconds() const { return (double)frames_.load() / rate_.load(); }

double Deck::cueSeconds() const { return cue_.load() / rate_.load(); }

std::vector<float> Deck::waveform(int points) {
    std::lock_guard<std::mutex> lk(peaksMutex_);
    std::vector<float> out(points, 0.f);
    if (peaks_.empty() || points <= 0) return out;
    double per = (double)peaks_.size() / points;
    for (int p = 0; p < points; p++) {
        size_t a = (size_t)(p * per), b = std::max(a + 1, (size_t)((p + 1) * per));
        float m = 0;
        for (size_t i = a; i < b && i < peaks_.size(); i++) m = std::max(m, peaks_[i]);
        out[p] = m;
    }
    return out;
}

float Deck::sample(TrackData* t, long i, int ch) const {
    if (i < 0 || (size_t)i >= t->frames) return 0.f;
    return t->pcm[(size_t)i * 2 + ch] * (1.f / 32768.f);
}

void Deck::wsolaGrain(TrackData* t) {
    long target = (long)wsolaIn_;
    long start = target;
    if (prevGrain_ >= 0) {
        // Bestes Korn suchen, das nahtlos an das vorherige anschliesst
        long nat = prevGrain_ + kHop;
        auto mono = [&](long i) { return sample(t, i, 0) + sample(t, i, 1); };
        auto corr = [&](long s, int step) {
            double dot = 0, en = 1e-9;
            for (int k = 0; k < kHop; k += step) {
                float a = mono(s + k), b = mono(nat + k);
                dot += a * b;
                en += a * a;
            }
            return dot / std::sqrt(en);
        };
        double best = -1e30;
        long bestS = target;
        for (long d = -kTol; d <= kTol; d += 8) {
            double c = corr(target + d, 8);
            if (c > best) {
                best = c;
                bestS = target + d;
            }
        }
        long coarse = bestS;
        best = -1e30;
        for (long d = -7; d <= 7; d++) {
            double c = corr(coarse + d, 2);
            if (c > best) {
                best = c;
                bestS = coarse + d;
            }
        }
        start = bestS;
    }
    for (int k = 0; k < kGrain; k++) {
        float w = window_[k];
        ola_[k * 2] += w * sample(t, start + k, 0);
        ola_[k * 2 + 1] += w * sample(t, start + k, 1);
    }
    std::memcpy(outFifo_.data(), ola_.data(), sizeof(float) * kHop * 2);
    std::memmove(ola_.data(), ola_.data() + kHop * 2, sizeof(float) * kHop * 2);
    std::fill(ola_.begin() + kHop * 2, ola_.end(), 0.f);
    prevGrain_ = start;
    wsolaIn_ += kHop * tempo_.load();
    if (loopOn_.load(std::memory_order_relaxed)) {
        double ls = loopStart_.load(), ll = loopLen_.load();
        if (wsolaIn_ >= ls + ll) {
            // zurückspringen – auch das vorherige Korn, damit der Übergang nahtlos bleibt
            wsolaIn_ -= ll;
            prevGrain_ -= (long)ll;
        }
    }
}

void Deck::renderSource(TrackData* t, float* out, int numFrames, double& pos) {
    float tempo = tempo_.load();
    bool keylock = keylock_.load() && std::fabs(tempo - 1.f) > 0.002f;
    if (keylock != lastKeylockMode_) {
        wsolaActive_ = false;
        lastKeylockMode_ = keylock;
    }
    double end = (double)t->frames - 2;

    if (!keylock) {
        // "Vinyl"-Modus: schneller = höher (einfaches Umrechnen)
        for (int i = 0; i < numFrames; i++) {
            if (pos >= end) {
                playing_.store(false);
                break;
            }
            long i0 = (long)pos;
            float fr = (float)(pos - i0);
            out[i * 2] = sample(t, i0, 0) * (1 - fr) + sample(t, i0 + 1, 0) * fr;
            out[i * 2 + 1] = sample(t, i0, 1) * (1 - fr) + sample(t, i0 + 1, 1) * fr;
            pos += tempo;
            if (loopOn_.load(std::memory_order_relaxed)) {
                double ls = loopStart_.load(), ll = loopLen_.load();
                if (pos >= ls + ll) pos -= ll;
            }
        }
        return;
    }

    // Keylock: Tempo ändern, Tonhöhe bleibt (WSOLA)
    if (!wsolaActive_) {
        wsolaIn_ = pos;
        prevGrain_ = -1;
        std::fill(ola_.begin(), ola_.end(), 0.f);
        fifoRead_ = kHop;
        wsolaActive_ = true;
    }
    for (int i = 0; i < numFrames; i++) {
        if (wsolaIn_ >= end) {
            playing_.store(false);
            break;
        }
        if (fifoRead_ >= kHop) {
            wsolaGrain(t);
            fifoRead_ = 0;
        }
        out[i * 2] = outFifo_[fifoRead_ * 2];
        out[i * 2 + 1] = outFifo_[fifoRead_ * 2 + 1];
        fifoRead_++;
    }
    // Hörbare Position = Start des aktuellen Korns + bereits ausgegebene Samples
    // (wsolaIn_ liegt ein Korn voraus – wichtig für genaues Beat-Sync)
    pos = prevGrain_ >= 0 ? prevGrain_ + fifoRead_ * (double)tempo_.load() : wsolaIn_;
}

void Deck::render(float* main, float* cue, int numFrames, int engineRate) {
    std::memset(main, 0, sizeof(float) * numFrames * 2);
    std::memset(cue, 0, sizeof(float) * numFrames * 2);

    std::unique_lock<std::mutex> lk(trackMutex_, std::try_to_lock);
    if (!lk.owns_lock() || !track_) {
        level_.store(0);
        return;
    }
    TrackData* t = track_.get();
    double pos = pos_.load();
    double seek = seekRequest_.exchange(-1);
    if (seek >= 0) {
        pos = std::min(seek, (double)t->frames);
        wsolaActive_ = false;
    }

    if (playing_.load()) {
        renderSource(t, main, numFrames, pos);
    } else {
        wsolaActive_ = false;
    }
    pos_.store(pos);
    lk.unlock();

    // --- Filter-Koeffizienten (einmal pro Block) ---
    eqLow_.setLowpass(250.f, 0.707f, (float)engineRate);
    eqHigh_.setHighpass(3500.f, 0.707f, (float)engineRate);
    float f = filter_.load();
    bool useLp = f < -0.02f, useHp = f > 0.02f;
    if (useLp) filterLp_.setLowpass(20000.f * std::pow(0.004f, -f), 1.2f, (float)engineRate);
    else filterLp_.reset();
    if (useHp) filterHp_.setHighpass(20.f * std::pow(400.f, f), 1.2f, (float)engineRate);
    else filterHp_.reset();

    // --- EQ-Ziele (Kill = ganz weg) ---
    float target[3];
    for (int b = 0; b < 3; b++) {
        float v = eq_[b].load();
        target[b] = kill_[b].load() ? 0.f : (v < 0 ? dbToGain(v * 26.f) : dbToGain(v * 6.f));
    }
    float vol = volume_.load();

    // --- Echo ---
    size_t echoLen = (size_t)engineRate * 2;
    if (echoBuf_.size() != echoLen * 2) {
        echoBuf_.assign(echoLen * 2, 0.f);
        echoPos_ = 0;
    }
    float bpm = bpm_.load();
    float tempo = tempo_.load();
    double delaySec = bpm > 0 ? 60.0 / (bpm * tempo) * 0.5 : 0.25;  // halber Beat
    size_t delay = std::min(echoLen - 1, (size_t)(delaySec * engineRate));
    bool echoOn = echoOn_.load();
    float wet = echoAmount_.load();
    bool pfl = pfl_.load();
    float echoFb = std::min(0.9f, std::max(0.f, echoFeedback_.load()));
    float send = reverbSend_.load();
    if (send > 0.001f) reverbTail_ = engineRate * 4;   // Hall klingt bis 4 s nach
    bool useReverb = reverbTail_ > 0;
    if (useReverb) {
        reverb_.prepare(engineRate);
        reverbTail_ -= numFrames;
    }

    float peak = 0;
    for (int i = 0; i < numFrames; i++) {
        float k = (float)(i + 1) / numFrames;  // sanfte Übergänge (kein Knacksen)
        float g0 = curGain_[0] + (target[0] - curGain_[0]) * k;
        float g1 = curGain_[1] + (target[1] - curGain_[1]) * k;
        float g2 = curGain_[2] + (target[2] - curGain_[2]) * k;
        float gv = curVol_ + (vol - curVol_) * k;
        for (int ch = 0; ch < 2; ch++) {
            float x = main[i * 2 + ch];
            float lo = eqLow_.process(x, ch);
            float hi = eqHigh_.process(x, ch);
            float mid = x - lo - hi;
            float y = lo * g0 + mid * g1 + hi * g2;
            if (useLp) y = filterLp_.process(y, ch);
            if (useHp) y = filterHp_.process(y, ch);

            size_t rd = (echoPos_ + echoLen - delay) % echoLen;
            float d = echoBuf_[rd * 2 + ch];
            echoBuf_[echoPos_ * 2 + ch] = (echoOn ? y : 0.f) + d * echoFb;
            y += d * wet;
            main[i * 2 + ch] = y;   // Zwischenspeichern (Hall wird unten pro Stereo-Paar berechnet)
        }
        if (useReverb) {
            float rl, rr;
            reverb_.process(main[i * 2] * send, main[i * 2 + 1] * send, rl, rr);
            main[i * 2] += rl;
            main[i * 2 + 1] += rr;
        }
        for (int ch = 0; ch < 2; ch++) {
            float y = main[i * 2 + ch];

            if (pfl) cue[i * 2 + ch] = y;
            float m = y * gv;
            main[i * 2 + ch] = m;
            peak = std::max(peak, std::fabs(m));
        }
        echoPos_ = (echoPos_ + 1) % echoLen;
    }
    for (int b = 0; b < 3; b++) curGain_[b] = target[b];
    curVol_ = vol;
    level_.store(std::max(peak, level_.load() * 0.85f));
}

}  // namespace dj
