#include "Engine.h"

#include <android/log.h>
#include <cmath>
#include <cstring>
#include <thread>

#define LOG_TAG "HirschDJ"
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
    main_->setBufferSizeInFrames(main_->getFramesPerBurst() * 2);

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
        int n = std::min(kChunk, numFrames - done);
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
    float xa = x <= 0 ? 1.f : std::cos(x * kPi / 2.f);
    float xb = x >= 0 ? 1.f : std::cos(-x * kPi / 2.f);
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
        for (int ch = 0; ch < 2; ch++) {
            // 0.8 = etwas Reserve, damit zwei laute Titel nicht verzerren
            float m = softClip((a_[i * 2 + ch] * ga + b_[i * 2 + ch] * gb) * mv * 0.8f);
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
