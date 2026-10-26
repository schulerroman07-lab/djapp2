#include "Recorder.h"

#include <unistd.h>
#include <chrono>
#include <cstring>
#include <vector>

namespace dj {

bool Recorder::start(int fd, int sampleRate) {
    stop();
    fd_ = fd;
    rate_ = sampleRate;
    written_.store(0);
    ring_.clear();
    writeHeader(0);  // Platzhalter, wird am Ende korrigiert
    lseek(fd_, 44, SEEK_SET);
    active_.store(true);
    thread_ = std::thread(&Recorder::run, this);
    return true;
}

void Recorder::stop() {
    if (!active_.load()) return;
    active_.store(false);
    if (thread_.joinable()) thread_.join();
    writeHeader((uint32_t)(written_.load() * 4));
    close(fd_);
    fd_ = -1;
}

void Recorder::run() {
    std::vector<float> f(4096 * 2);
    std::vector<int16_t> s(4096 * 2);
    auto drain = [&]() {
        size_t n;
        while ((n = ring_.read(f.data(), 4096)) > 0) {
            for (size_t i = 0; i < n * 2; i++) {
                float v = f[i];
                if (v > 1.f) v = 1.f;
                if (v < -1.f) v = -1.f;
                s[i] = (int16_t)(v * 32767.f);
            }
            if (write(fd_, s.data(), n * 4) < 0) return;
            written_.fetch_add(n);
        }
    };
    while (active_.load()) {
        drain();
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
    drain();
}

void Recorder::writeHeader(uint32_t dataBytes) {
    // Standard-WAV-Kopf: 16 Bit, Stereo
    uint8_t h[44];
    auto put32 = [&](int o, uint32_t v) { memcpy(h + o, &v, 4); };
    auto put16 = [&](int o, uint16_t v) { memcpy(h + o, &v, 2); };
    memcpy(h, "RIFF", 4);
    put32(4, 36 + dataBytes);
    memcpy(h + 8, "WAVEfmt ", 8);
    put32(16, 16);
    put16(20, 1);
    put16(22, 2);
    put32(24, (uint32_t)rate_);
    put32(28, (uint32_t)rate_ * 4);
    put16(32, 4);
    put16(34, 16);
    memcpy(h + 36, "data", 4);
    put32(40, dataBytes);
    pwrite(fd_, h, 44, 0);
}

}  // namespace dj
