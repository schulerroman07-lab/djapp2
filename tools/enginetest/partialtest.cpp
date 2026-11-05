// Schrittweises Laden: Titel startet, sobald er spielbar ist, und spielt ohne Abbruch weiter,
// während der Rest noch dekodiert wird (langsamer Decoder simuliert).
#include "Deck.h"
#include "Decoder.h"
#include <chrono>
#include <cmath>
#include <cstdio>
#include <fcntl.h>
#include <thread>
#include <unistd.h>
using namespace dj;
namespace dj {
bool decodeToRate(int, int64_t, int, std::vector<int16_t>&, const std::atomic<bool>*) { return false; }
// 60 s Musik, langsam dekodiert: 1 s Audio pro 40 ms (25× Echtzeit) – Playback wird im Test in Echtzeit-Blöcken simuliert
bool decodeAudio(int, int64_t, const PcmChunkCallback& cb, const std::atomic<bool>* cancel, double) {
    const int rate = 44100; std::vector<int16_t> buf(1152 * 2);
    for (size_t f = 0; f < (size_t)rate * 60; f += 1152) {
        if (cancel && cancel->load()) return false;
        for (int i = 0; i < 1152; i++) { double t = (double)(f + i) / rate; int16_t v = (int16_t)(8000 * std::sin(2 * M_PI * 440 * t)); buf[2*i] = buf[2*i+1] = v; }
        cb(buf.data(), 1152, rate);
        if ((f / 1152) % 38 == 0) std::this_thread::sleep_for(std::chrono::milliseconds(40));
    }
    return true;
}
}
int main() {
    Deck d; d.load(open("/dev/null", O_RDONLY), 0, 48000);
    auto t0 = std::chrono::steady_clock::now();
    while (d.loadState() != LoadState::Playable && d.loadState() != LoadState::Ready) std::this_thread::sleep_for(std::chrono::milliseconds(1));
    double playableMs = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
    d.setPlaying(true);
    bool startedPlaying = d.playing();
    std::vector<float> m(480 * 2), c(480 * 2);
    int silent = 0, stops = 0; double maxJump = 0; float prev = 0;
    // 40 s in Echtzeit-Blöcken (10 ms) abspielen, parallel lädt der Rest
    for (int b = 0; b < 4000; b++) {
        auto bt = std::chrono::steady_clock::now();
        d.render(m.data(), c.data(), 480, 48000);
        double sq = 0; for (int i = 0; i < 480; i++) { sq += m[2*i] * m[2*i]; maxJump = std::max(maxJump, (double)std::fabs(m[2*i] - prev)); prev = m[2*i]; }
        if (std::sqrt(sq / 480) < 1e-3) silent++;
        if (!d.playing()) stops++;
        std::this_thread::sleep_until(bt + std::chrono::microseconds(10000));
    }
    printf("spielbar nach %.0f ms, Play angenommen=%d, Zustand am Ende=%d (2=fertig), Pos %.2f s, stille Blöcke=%d, Stopps=%d, max. Sprung %.4f\n",
           playableMs, startedPlaying, (int)d.loadState(), d.positionSeconds(), silent, stops, maxJump);
    bool ok = startedPlaying && d.loadState() == LoadState::Ready && silent == 0 && stops == 0 && maxJump < 0.02 && std::fabs(d.positionSeconds() - 40) < 0.5;
    printf(ok ? "ERGEBNIS: OK\n" : "ERGEBNIS: FEHLER\n");
    return ok ? 0 : 1;
}
