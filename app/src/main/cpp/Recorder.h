// Nimmt den Master-Mix als WAV-Datei auf (in einem eigenen Thread).
#pragma once
#include <atomic>
#include <thread>

#include "Dsp.h"

namespace dj {

class Recorder {
public:
    ~Recorder() { stop(); }
    bool start(int fd, int sampleRate);
    void stop();
    bool active() const { return active_.load(); }
    double seconds() const { return rate_ > 0 ? (double)written_.load() / rate_ : 0; }

    // Aus dem Audio-Thread: Master-Samples anhängen
    void push(const float* lr, int frames) {
        if (active_.load(std::memory_order_relaxed)) ring_.write(lr, frames);
    }

private:
    void run();
    void writeHeader(uint32_t dataBytes);

    StereoRing ring_{1 << 18};
    std::thread thread_;
    std::atomic<bool> active_{false};
    std::atomic<size_t> written_{0};
    int fd_ = -1;
    int rate_ = 48000;
};

}  // namespace dj
