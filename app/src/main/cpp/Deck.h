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
    std::vector<uint8_t> features;  // pro Beat: Energie, Bass, Mitten, Höhen (für Auto-Mix)
};

/** Einfacher Hall (Freeverb-Prinzip): 4 Kammfilter + 2 Allpass pro Kanal. */
class Reverb {
public:
    void prepare(int rate);
    // Verarbeitet ein Stereo-Sample (Eingang = gesendeter Anteil), gibt Hall zurück
    inline void process(float inL, float inR, float& outL, float& outR) {
        float in = (inL + inR) * 0.5f;
        float l = 0, r = 0;
        for (int i = 0; i < 4; i++) {
            l += comb(combL_[i], combPosL_[i], combStoreL_[i], in);
            r += comb(combR_[i], combPosR_[i], combStoreR_[i], in);
        }
        for (int i = 0; i < 2; i++) {
            l = allpass(apL_[i], apPosL_[i], l);
            r = allpass(apR_[i], apPosR_[i], r);
        }
        outL = l * 0.25f;
        outR = r * 0.25f;
    }

private:
    inline float comb(std::vector<float>& buf, size_t& pos, float& store, float in) {
        float out = buf[pos];
        store = out * (1.f - damp_) + store * damp_;
        buf[pos] = in + store * feedback_;
        if (++pos >= buf.size()) pos = 0;
        return out;
    }
    inline float allpass(std::vector<float>& buf, size_t& pos, float in) {
        float b = buf[pos];
        float out = -in + b;
        buf[pos] = in + b * 0.5f;
        if (++pos >= buf.size()) pos = 0;
        return out;
    }
    std::vector<float> combL_[4], combR_[4], apL_[2], apR_[2];
    size_t combPosL_[4] = {0}, combPosR_[4] = {0}, apPosL_[2] = {0}, apPosR_[2] = {0};
    float combStoreL_[4] = {0}, combStoreR_[4] = {0};
    float feedback_ = 0.84f, damp_ = 0.3f;
    int rate_ = 0;
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
    // Lautheits-Angleichung (Auto-Mix): zusätzlicher Pegel, unabhängig vom Lautstärkeregler
    void setGain(float g) { gain_.store(g); }
    float gain() const { return gain_.load(); }
    float loudnessDb() const { return loudness_.load(); }
    void setEq(int band, float v) { eq_[band].store(v); }
    void setKill(int band, bool k) { kill_[band].store(k); }
    void setFilter(float v) { filter_.store(v); }
    void setEcho(bool on, float amount) { echoOn_.store(on); echoAmount_.store(amount); }
    void setPfl(bool on) { pfl_.store(on); }
    void setEchoFeedback(float fb) { echoFeedback_.store(fb); }
    void setReverb(float send) { reverbSend_.store(send); }
    // Loop: Abschnitt [start, start+length) wiederholen (Sekunden). on=false beendet den Loop.
    void setLoop(double startSec, double lengthSec, bool on);
    std::vector<uint8_t> features();
    double positionFrames() const { return pos_.load(); }
    double durationFrames() const { return (double)frames_.load(); }

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
    std::vector<float> peaks() {
        std::lock_guard<std::mutex> lk(peaksMutex_);
        return peaks_;
    }

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
    std::atomic<float> gain_{1.f};
    std::atomic<float> loudness_{-14.f};
    std::atomic<float> eq_[3];
    std::atomic<bool> kill_[3];
    std::atomic<float> filter_{0};
    std::atomic<bool> echoOn_{false};
    std::atomic<float> echoAmount_{0.5f};
    std::atomic<bool> pfl_{false};
    std::atomic<float> level_{0};
    std::atomic<float> echoFeedback_{0.55f};
    std::atomic<float> reverbSend_{0};
    std::atomic<bool> loopOn_{false};
    std::atomic<double> loopStart_{0}, loopLen_{0};   // in Frames
    Reverb reverb_;
    int reverbTail_ = 0;      // wie lange der Hall noch nachklingt (Frames)
    std::vector<uint8_t> featuresCopy_;

    // DSP-Zustand (nur Audio-Thread)
    Biquad eqLow_, eqHigh_, filterLp_, filterHp_;
    float curGain_[3] = {1, 1, 1};
    float curVol_ = 1;
    float curFilter_ = 0;      // geglätteter Filter-Wert (langsame, musikalische Bewegung)
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
