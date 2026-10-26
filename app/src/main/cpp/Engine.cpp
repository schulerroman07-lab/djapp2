#include "Engine.h"

#include <android/log.h>
#include <cmath>
#include <cstring>
#include <thread>

#define LOG_TAG "Fadelux"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace dj {

Engine::Engine() {
    a_.assign(kChunk * 2, 0);
    b_.assign(kChunk * 2, 0);
    ca_.assign(kChunk * 2, 0);
    cb_.assign(kChunk * 2, 0);
    master_.assign(kChunk * 2, 0);
    hp_.assign(kChunk * 2, 0);
    cueTmp_.assign(8192 * 2, 0);
}

Engine::~Engine() { stop(); }

bool Engine::start() {
    std::lock_guard<std::mutex> lk(streamMutex_);
    return openStreams();
}

void Engine::stop() {
    recorder.stop();
    std::lock_guard<std::mutex> lk(streamMutex_);
    closeStreams();
}

void Engine::reopen() {
    std::lock_guard<std::mutex> lk(streamMutex_);
    closeStreams();
    openStreams();
}

void Engine::setOutput(int mode, int masterDevice, int cueDevice) {
    {
        std::lock_guard<std::mutex> lk(streamMutex_);
        mode_.store(mode);
        masterDevice_ = masterDevice;
        cueDevice_ = cueDevice;
    }
    reopen();
}

bool Engine::openStreams() {
    int mode = mode_.load();
    auto build = [&](int channels, int deviceId, std::shared_ptr<oboe::AudioStream>& out) {
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(channels)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setDataCallback(this)
            ->setErrorCallback(this);
        if (rate_.load() > 0) b.setSampleRate(rate_.load());
        if (deviceId > 0) b.setDeviceId(deviceId);
        return b.openStream(out);
    };

    oboe::Result r = build(mode == kModeFourCh ? 4 : 2, masterDevice_, main_);
    if (r != oboe::Result::OK && mode == kModeFourCh) {
        LOGE("4-Kanal-Ausgabe nicht möglich, nehme Stereo");
        r = build(2, masterDevice_, main_);
    }
    if (r != oboe::Result::OK) {
        LOGE("Audio-Stream konnte nicht geöffnet werden: %s", oboe::convertToText(r));
        main_.reset();
        return false;
    }
    if (rate_.load() == 0) rate_.store(main_->getSampleRate());
    main_->setBufferSizeInFrames(main_->getFramesPerBurst() * bufferBursts_.load());

    if (mode == kModeTwoDev && cueDevice_ > 0) {
        cueRing_.clear();
        if (build(2, cueDevice_, cue_) == oboe::Result::OK) {
            cue_->setBufferSizeInFrames(cue_->getFramesPerBurst() * 2);
            cueRaw_.store(cue_.get());
            cue_->requestStart();
        } else {
            LOGE("Kopfhörer-Gerät konnte nicht geöffnet werden");
            cue_.reset();
        }
    }
    main_->requestStart();
    LOGI("Audio läuft: %d Hz, %d Kanäle", main_->getSampleRate(), main_->getChannelCount());
    return true;
}

void Engine::closeStreams() {
    cueRaw_.store(nullptr);
    if (cue_) {
        cue_->stop();
        cue_->close();
        cue_.reset();
    }
    if (main_) {
        main_->stop();
        main_->close();
        main_.reset();
    }
}

double Engine::latencyMs() {
    std::lock_guard<std::mutex> lk(streamMutex_);
    if (!main_) return 0;
    auto l = main_->calculateLatencyMillis();
    return l ? l.value() : 0;
}

int Engine::channelCount() {
    std::lock_guard<std::mutex> lk(streamMutex_);
    return main_ ? main_->getChannelCount() : 0;
}

void Engine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    // z.B. Bluetooth-Box getrennt/verbunden: Stream neu öffnen
    LOGI("Audio-Stream Fehler (%s) – öffne neu", oboe::convertToText(error));
    std::thread([this]() { reopen(); }).detach();
}

oboe::DataCallbackResult Engine::onAudioReady(oboe::AudioStream* stream, void* data,
                                              int32_t numFrames) {
    float* out = static_cast<float*>(data);
    if (stream == cueRaw_.load()) {
        // Zweites Gerät (Kopfhörer): Daten aus dem Ringpuffer holen
        size_t avail = cueRing_.available();
        size_t maxBacklog = (size_t)numFrames * 3 + 2048;
        if (avail > maxBacklog) {  // zu viel Rückstand: überspringen (Verzögerung klein halten)
            size_t skip = avail - maxBacklog;
            while (skip > 0) {
                size_t n = cueRing_.read(cueTmp_.data(), std::min<size_t>(skip, 8192));
                if (n == 0) break;
                skip -= n;
            }
        }
        size_t got = cueRing_.read(out, numFrames);
        if (got < (size_t)numFrames) std::memset(out + got * 2, 0, (numFrames - got) * 2 * sizeof(float));
        return oboe::DataCallbackResult::Continue;
    }

    int channels = stream->getChannelCount();
    int done = 0;
    while (done < numFrames) {
        // Mit geplantem Start in kleinen Stücken rechnen → Start auf ~1 ms genau
        int chunk = schedTarget_.load(std::memory_order_relaxed) >= 0 ? 48 : kChunk;
        checkSchedule();
        int n = std::min(chunk, numFrames - done);
        renderMain(out + (size_t)done * channels, channels, n);
        done += n;
    }
    return oboe::DataCallbackResult::Continue;
}

void Engine::renderMain(float* out, int channels, int frames) {
    int rate = rate_.load();
    deck[0].render(a_.data(), ca_.data(), frames, rate);
    deck[1].render(b_.data(), cb_.data(), frames, rate);

    // Crossfader: in der Mitte beide voll, aussen wird eine Seite ausgeblendet
    float x = xfader_.load();
    float xa, xb;
    if (xCurve_.load() == 1) {
        // Equal-Power: Summe der Leistung bleibt gleich (kein Loch, kein Buckel in der Mitte)
        float p = (x + 1.f) * 0.25f * kPi;   // 0 .. pi/2
        xa = std::cos(p);
        xb = std::sin(p);
    } else {
        xa = x <= 0 ? 1.f : std::cos(x * kPi / 2.f);
        xb = x >= 0 ? 1.f : std::cos(-x * kPi / 2.f);
    }
    float mv = masterVol_.load();
    float hv = hpVol_.load();
    float mix = cueMix_.load();

    // Kopfhörer-Verzögerung (Ausgleich für Bluetooth-Latenz am Master)
    size_t delayLen = (size_t)rate;  // max. 1 Sekunde
    if (delayBuf_.size() != delayLen * 2) {
        delayBuf_.assign(delayLen * 2, 0.f);
        delayPos_ = 0;
    }
    size_t delay = std::min(delayLen - 1, (size_t)(cueDelayMs_.load() * rate / 1000.f));

    float peak = 0;
    for (int i = 0; i < frames; i++) {
        float k = (float)(i + 1) / frames;
        float ga = curXa_ + (xa - curXa_) * k;
        float gb = curXb_ + (xb - curXb_) * k;
        // Begrenzer: sehr schnell leiser bei Spitzen über 0.9, langsam (ca. 0.3 s) wieder lauter
        float l = (a_[i * 2] * ga + b_[i * 2] * gb) * mv * 0.8f;
        float r = (a_[i * 2 + 1] * ga + b_[i * 2 + 1] * gb) * mv * 0.8f;
        float pk = std::max(std::fabs(l), std::fabs(r)) * limGain_;
        if (pk > 0.9f) limGain_ *= 0.9f / pk;
        else limGain_ += (1.f - limGain_) * (1.f / (0.3f * rate));
        float lr[2] = {l * limGain_, r * limGain_};
        for (int ch = 0; ch < 2; ch++) {
            // 0.8 = etwas Reserve, damit zwei laute Titel nicht verzerren
            float m = softClip(lr[ch]);
            master_[i * 2 + ch] = m;
            peak = std::max(peak, std::fabs(m));
            float c = ca_[i * 2 + ch] + cb_[i * 2 + ch];
            float h = softClip((c * (1.f - mix) + m * mix) * hv);
            // Verzögerung
            delayBuf_[delayPos_ * 2 + ch] = h;
            size_t rd = (delayPos_ + delayLen - delay) % delayLen;
            hp_[i * 2 + ch] = delayBuf_[rd * 2 + ch];
        }
        delayPos_ = (delayPos_ + 1) % delayLen;
    }
    curXa_ = xa;
    curXb_ = xb;
    masterLevel_.store(std::max(peak, masterLevel_.load() * 0.85f));

    recorder.push(master_.data(), frames);

    // Mono-Kopie für die Spektrum-Anzeige (nur kopieren – sehr billig)
    int sp = scopePos_.load(std::memory_order_relaxed);
    for (int i = 0; i < frames; i++) {
        scope_[sp] = (master_[i * 2] + master_[i * 2 + 1]) * 0.5f;
        sp = (sp + 1) & (kScope - 1);
    }
    scopePos_.store(sp, std::memory_order_relaxed);

    int mode = mode_.load();
    if (mode == kModeSplit && channels >= 2) {
        for (int i = 0; i < frames; i++) {
            out[i * channels] = (master_[i * 2] + master_[i * 2 + 1]) * 0.5f;
            out[i * channels + 1] = (hp_[i * 2] + hp_[i * 2 + 1]) * 0.5f;
            for (int ch = 2; ch < channels; ch++) out[i * channels + ch] = 0;
        }
        return;
    }
    if (mode == kModeFourCh && channels >= 4) {
        for (int i = 0; i < frames; i++) {
            out[i * channels] = master_[i * 2];
            out[i * channels + 1] = master_[i * 2 + 1];
            out[i * channels + 2] = hp_[i * 2];
            out[i * channels + 3] = hp_[i * 2 + 1];
            for (int ch = 4; ch < channels; ch++) out[i * channels + ch] = 0;
        }
        return;
    }
    if (mode == kModeTwoDev && cueRaw_.load()) cueRing_.write(hp_.data(), frames);
    for (int i = 0; i < frames; i++) {
        out[i * channels] = master_[i * 2];
        if (channels > 1) out[i * channels + 1] = master_[i * 2 + 1];
        for (int ch = 2; ch < channels; ch++) out[i * channels + ch] = 0;
    }
}

}  // namespace dj

namespace dj {

void Engine::setBufferBursts(int n) {
    bufferBursts_.store(std::max(1, std::min(8, n)));
    std::lock_guard<std::mutex> lk(streamMutex_);
    if (main_) main_->setBufferSizeInFrames(main_->getFramesPerBurst() * bufferBursts_.load());
}

// Einfache FFT (Radix 2, in-place)
static void fft(std::vector<float>& re, std::vector<float>& im) {
    size_t n = re.size();
    for (size_t i = 1, j = 0; i < n; i++) {
        size_t bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) {
            std::swap(re[i], re[j]);
            std::swap(im[i], im[j]);
        }
    }
    for (size_t len = 2; len <= n; len <<= 1) {
        float ang = -2.f * kPi / len;
        float wr = std::cos(ang), wi = std::sin(ang);
        for (size_t i = 0; i < n; i += len) {
            float cr = 1, ci = 0;
            for (size_t k = 0; k < len / 2; k++) {
                size_t a = i + k, b = i + k + len / 2;
                float tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
                re[b] = re[a] - tr;
                im[b] = im[a] - ti;
                re[a] += tr;
                im[a] += ti;
                float ncr = cr * wr - ci * wi;
                ci = cr * wi + ci * wr;
                cr = ncr;
            }
        }
    }
}

void Engine::spectrum(float* bands, int numBands) {
    const int n = 1024;
    std::vector<float> re(n), im(n, 0.f);
    int end = scopePos_.load(std::memory_order_relaxed);
    for (int i = 0; i < n; i++) {
        float w = 0.5f - 0.5f * std::cos(2.f * kPi * i / (n - 1));
        re[i] = scope_[(end - n + i + kScope) & (kScope - 1)] * w;
    }
    fft(re, im);
    int rate = std::max(8000, rate_.load());
    // Logarithmische Bänder von 40 Hz bis 16 kHz
    for (int b = 0; b < numBands; b++) {
        float f0 = 40.f * std::pow(400.f, (float)b / numBands);
        float f1 = 40.f * std::pow(400.f, (float)(b + 1) / numBands);
        int k0 = std::max(1, (int)(f0 * n / rate));
        int k1 = std::max(k0 + 1, (int)(f1 * n / rate));
        float m = 0;
        for (int k = k0; k < k1 && k < n / 2; k++) m = std::max(m, re[k] * re[k] + im[k] * im[k]);
        float db = 10.f * std::log10(m + 1e-12f);
        bands[b] = std::max(0.f, std::min(1.f, (db + 20.f) / 60.f));
    }
}

}  // namespace dj

namespace dj {

void Engine::scheduleStart(int target, int trigger, double triggerSec, bool stopTrigger) {
    schedTrigger_.store(trigger & 1);
    schedFrame_.store(triggerSec * std::max(1, rate_.load()));
    schedStop_.store(stopTrigger);
    schedTarget_.store(target & 1);   // zuletzt setzen = "scharf schalten"
}

void Engine::checkSchedule() {
    int target = schedTarget_.load(std::memory_order_acquire);
    if (target < 0) return;
    Deck& trig = deck[schedTrigger_.load()];
    // Auslösen, wenn die Position erreicht ist – oder der Titel zu Ende gelaufen ist.
    // (Ist das Deck nur pausiert, z.B. wegen eines Anrufs, wird gewartet.)
    bool ended = !trig.playing() && trig.positionFrames() >= trig.durationFrames() - 4096;
    if ((trig.playing() && trig.positionFrames() >= schedFrame_.load()) || ended) {
        deck[target].setPlaying(true);
        if (schedStop_.load()) trig.setPlaying(false);
        schedTarget_.store(-1);
    }
}

}  // namespace dj
