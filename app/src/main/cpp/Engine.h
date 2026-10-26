// Die Audio-Engine: mischt Deck A und B und gibt den Ton über Oboe aus.
#pragma once
#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <vector>

#include "Deck.h"
#include "Dsp.h"
#include "Recorder.h"

namespace dj {

// Ausgabe-Modus
enum OutputMode {
    kModeStereo = 0,   // nur Master (Stereo)
    kModeSplit = 1,    // Splitter-Kabel: links Master, rechts Kopfhörer (je Mono)
    kModeFourCh = 2,   // USB-Interface mit 4 Kanälen: 1/2 Master, 3/4 Kopfhörer
    kModeTwoDev = 3,   // zwei Geräte: Master auf Gerät 1, Kopfhörer auf Gerät 2
};

class Engine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    Engine();
    ~Engine();

    bool start();
    void stop();
    void reopen();

    Deck deck[2];

    void setCrossfader(float x) { xfader_.store(x); }
    void setMasterVolume(float v) { masterVol_.store(v); }
    void setHeadphoneVolume(float v) { hpVol_.store(v); }
    void setCueMix(float v) { cueMix_.store(v); }
    void setCueDelayMs(float ms) { cueDelayMs_.store(ms); }
    void setOutput(int mode, int masterDevice, int cueDevice);

    void setBufferBursts(int n);

    /**
     * Geplanter Start (für Auto-Mix): Deck "target" startet genau dann, wenn Deck "trigger"
     * die Position triggerSec erreicht (Genauigkeit ~1 ms). stopTrigger = gleichzeitig das
     * auslösende Deck anhalten (harter Schnitt auf dem Downbeat).
     */
    void scheduleStart(int target, int trigger, double triggerSec, bool stopTrigger);
    void cancelSchedule() { schedTarget_.store(-1); }
    bool scheduled() const { return schedTarget_.load() >= 0; }
    // Spektrum für die Anzeige (wird im UI-Thread berechnet, NICHT im Audio-Thread)
    void spectrum(float* bands, int numBands);

    int sampleRate() const { return rate_.load(); }
    float masterLevel() const { return masterLevel_.load(); }
    double latencyMs();
    int channelCount();

    Recorder recorder;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* data,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openStreams();
    void closeStreams();
    void renderMain(float* out, int channels, int frames);

    std::mutex streamMutex_;
    std::shared_ptr<oboe::AudioStream> main_, cue_;
    std::atomic<oboe::AudioStream*> cueRaw_{nullptr};
    std::atomic<int> rate_{0};
    std::atomic<int> mode_{kModeStereo};
    int masterDevice_ = 0, cueDevice_ = 0;

    std::atomic<float> xfader_{0}, masterVol_{0.9f}, hpVol_{0.8f}, cueMix_{0.f};
    std::atomic<float> cueDelayMs_{0};
    std::atomic<float> masterLevel_{0};
    float curXa_ = 1, curXb_ = 1;

    static constexpr int kChunk = 1024;
    std::vector<float> a_, b_, ca_, cb_, master_, hp_;
    std::vector<float> delayBuf_;
    size_t delayPos_ = 0;
    StereoRing cueRing_{1 << 15};
    std::vector<float> cueTmp_;
    std::atomic<int> bufferBursts_{2};
    std::atomic<int> schedTarget_{-1}, schedTrigger_{0};
    std::atomic<double> schedFrame_{0};
    std::atomic<bool> schedStop_{false};
    void checkSchedule();
    static constexpr int kScope = 2048;          // Mono-Kopie des Masters für das Spektrum
    float scope_[kScope] = {0};
    std::atomic<int> scopePos_{0};
};

}  // namespace dj
