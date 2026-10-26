// Ein Deck (Plattenspieler): spielt einen Titel ab, mit Tempo, EQ, Filter und Echo.
#pragma once
#include <atomic>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include "BpmAnalyzer.h"
#include "Dsp.h"

namespace dj {

// Ein geladener Titel im Speicher (Stereo, 16 Bit, Abtastrate der Engine).
struct TrackData {
    std::vector<int16_t> pcm;
    size_t frames = 0;
    int rate = 48000;
    float bpm = 0;
    float firstBeat = 0;  // Sekunden
    std::vector<float> peaks;  // Wellenform (Spitzenwerte, 0..1), ca. 100 pro Sekunde
};

enum class LoadState { Empty = 0, Loading = 1, Ready = 2, Error = 3 };

class Deck {
public:
    Deck();
    ~Deck();

    // --- Steuerung (aus dem UI-Thread) ---
    void load(int fd, int64_t length, int engineRate);
    void setPlaying(bool p);
    void cuePress();
    void seekSeconds(double s);
    void setTempo(float ratio) { tempo_.store(ratio); }
    void setKeylock(bool k) { keylock_.store(k); }
    void setVolume(float v) { volume_.store(v); }
    void setEq(int band, float v) { eq_[band].store(v); }
    void setKill(int band, bool k) { kill_[band].store(k); }
    void setFilter(float v) { filter_.store(v); }
    void setEcho(bool on, float amount) { echoOn_.store(on); echoAmount_.store(amount); }
    void setPfl(bool on) { pfl_.store(on); }

    // --- Abfragen ---
    LoadState loadState() const { return state_.load(); }
    bool playing() const { return playing_.load(); }
    double positionSeconds() const;
    double durationSeconds() const;
    float bpm() const { return bpm_.load(); }
    float firstBeat() const { return firstBeat_.load(); }
    float tempo() const { return tempo_.load(); }
    double cueSeconds() const;
    bool pfl() const { return pfl_.load(); }
    float level() const { return level_.load(); }
    std::vector<float> waveform(int points);

    // --- Audio-Thread ---
    // Rendert numFrames Stereo-Frames. 'main' = nach Lautstärkeregler, 'cue' = vor Regler.
    void render(float* main, float* cue, int numFrames, int engineRate);

private:
    void renderSource(TrackData* t, float* out, int numFrames, double& pos);
    void wsolaGrain(TrackData* t);
    float sample(TrackData* t, long i, int ch) const;

    std::mutex trackMutex_;
    std::unique_ptr<TrackData> track_;
    std::mutex peaksMutex_;
    std::vector<float> peaks_;  // Kopie der Wellenform für das UI
    std::thread loader_;
    std::atomic<bool> cancelLoad_{false};

    std::atomic<LoadState> state_{LoadState::Empty};
    std::atomic<bool> playing_{false};
    std::atomic<double> pos_{0};          // aktuelle Position (Frames) – nur Audio-Thread schreibt
    std::atomic<double> seekRequest_{-1};  // gewünschte neue Position (Frames)
    std::atomic<double> cue_{0};
    std::atomic<size_t> frames_{0};
    std::atomic<int> rate_{48000};
    std::atomic<float> bpm_{0}, firstBeat_{0};

    std::atomic<float> tempo_{1.f};
    std::atomic<bool> keylock_{true};
    std::atomic<float> volume_{1.f};
    std::atomic<float> eq_[3];
    std::atomic<bool> kill_[3];
    std::atomic<float> filter_{0};
    std::atomic<bool> echoOn_{false};
    std::atomic<float> echoAmount_{0.5f};
    std::atomic<bool> pfl_{false};
    std::atomic<float> level_{0};

    // DSP-Zustand (nur Audio-Thread)
    Biquad eqLow_, eqHigh_, filterLp_, filterHp_;
    float curGain_[3] = {1, 1, 1};
    float curVol_ = 1;
    std::vector<float> echoBuf_;
    size_t echoPos_ = 0;
    std::vector<float> tmp_;

    // WSOLA (Tempo ändern ohne Tonhöhe)
    static constexpr int kGrain = 2048;
    static constexpr int kHop = kGrain / 2;
    static constexpr int kTol = 400;
    std::vector<float> window_;
    std::vector<float> ola_;      // Überlappungspuffer (Stereo, kGrain)
    std::vector<float> outFifo_;  // fertige Samples (Stereo, kHop)
    int fifoRead_ = kHop;
    double wsolaIn_ = 0;     // Leseposition im Titel
    long prevGrain_ = -1;    // Start des vorherigen Korns
    bool wsolaActive_ = false;
    bool lastKeylockMode_ = false;
};

}  // namespace dj
