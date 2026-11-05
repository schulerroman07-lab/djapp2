// Dauertest: 2 Stunden Auto-DJ-Betrieb mit der echten Engine (Decks, Mixer, Begrenzer, geplanter Start).
// Prüft: keine NaN/Inf, kein Übersteuern, keine Aussetzer (stille 100-ms-Blöcke), Speicher stabil.
#include "Engine.h"
#include "Decoder.h"
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <thread>
#include <unistd.h>
using namespace dj;

static std::atomic<int> g_song{0};
static const float kBpms[] = {120, 124, 128, 126, 122, 98, 140, 125};
static const int kLen = 150;   // Sekunden pro Song (+3 s Stille am Schluss)

namespace dj {
bool decodeToRate(int, int64_t, int rate, std::vector<int16_t>& out, const std::atomic<bool>*) {
    int id = g_song.fetch_add(1);
    float bpm = kBpms[id % 8];
    double beat = 60.0 / bpm;
    size_t n = (size_t)rate * (kLen + 3);
    out.assign(n * 2, 0);
    unsigned seed = 1234 + id;
    for (size_t i = 0; i < (size_t)rate * kLen; i++) {
        double t = (double)i / rate;
        double bt = std::fmod(t, beat);
        double kick = 0.5 * std::exp(-bt * 14) * std::sin(2 * M_PI * 55 * bt);
        seed = seed * 1103515245 + 12345;
        double hat = (std::fmod(t + beat / 2, beat) < 0.02) ? 0.08 * (((seed >> 16) & 0x7fff) / 16384.0 - 1) : 0;
        double chord = 0.08 * std::sin(2 * M_PI * (220 + 20 * (id % 5)) * t) + 0.06 * std::sin(2 * M_PI * (277 + 20 * (id % 5)) * t);
        double v = (kick + hat + chord) * (id % 3 == 0 ? 0.6 : 1.0);   // verschieden laute Songs
        out[2 * i] = out[2 * i + 1] = (int16_t)std::max(-32767.0, std::min(32767.0, v * 32767));
    }
    return true;
}
bool decodeAudio(int, int64_t, const PcmChunkCallback&, const std::atomic<bool>*, double) { return false; }
}

static long rssKb() {
    FILE* f = fopen("/proc/self/status", "r"); char line[256]; long kb = 0;
    while (fgets(line, sizeof line, f)) if (!strncmp(line, "VmRSS:", 6)) kb = atol(line + 6);
    fclose(f); return kb;
}

int main(int argc, char** argv) {
    double hours = argc > 1 ? atof(argv[1]) : 2.0;
    Engine e;
    if (!e.start()) { printf("start fehlgeschlagen\n"); return 1; }
    oboe::AudioStream stream;  // Ersatz-Stream (2 Kanäle, 48 kHz)
    const int block = 192 * 4;
    std::vector<float> buf(block * 2);
    auto load = [&](int d) { e.deck[d].load(open("/dev/null", O_RDONLY), 0, 48000); };
    auto ready = [&](int d) { return e.deck[d].loadState() == LoadState::Ready; };
    load(0);
    while (!ready(0)) std::this_thread::sleep_for(std::chrono::milliseconds(2));
    e.setCrossfader(-1); e.deck[0].setPlaying(true);

    int cur = 0; enum { WAIT_LOAD, LOADING, SCHEDULED, MIXING, RAMP } phase = WAIT_LOAD;
    double mixStartSec = 0, rampStart = 0, rampFrom = 1;
    int transitions = 0, silentBlocks = 0, nanCount = 0, schedFails = 0, kind = 0;
    float maxAbs = 0;
    long rss10 = 0;
    long total = (long)(hours * 3600 * 48000), done = 0;
    double silentAcc = 0; int silentN = 0;
    auto t0 = std::chrono::steady_clock::now();
    while (done < total) {
        e.onAudioReady(&stream, buf.data(), block);
        for (int i = 0; i < block * 2; i++) {
            float v = buf[i];
            if (!std::isfinite(v)) nanCount++;
            maxAbs = std::max(maxAbs, std::fabs(v));
            silentAcc += v * v;
        }
        silentN += block;
        if (silentN >= 4800) {   // 100 ms
            if (std::sqrt(silentAcc / (silentN * 2)) < 1e-3) {
                silentBlocks++;
                if (silentBlocks < 15) printf("  still bei %.1f s (Phase %d, Deck %d pos %.1f/%.1f spielt %d, anderes spielt %d, xf=%.2f)\n", done / 48000.0, (int)phase, cur,
                    e.deck[cur].positionSeconds(), e.deck[cur].durationSeconds(), e.deck[cur].playing(), e.deck[1-cur].playing(), 0.0);
            }
            silentAcc = 0; silentN = 0;
        }
        done += block;
        if (done == block * 75000) rss10 = rssKb();   // nach ca. 20 min (beide Decks gefüllt)

        // ---- "Auto-DJ"-Steuerung alle ~16 ms (wie im Kotlin-Teil) ----
        Deck& a = e.deck[cur];
        Deck& b = e.deck[1 - cur];
        double aPos = a.positionSeconds(), aDur = a.durationSeconds();
        switch (phase) {
            case WAIT_LOAD:
                if (aPos > 5) { load(1 - cur); phase = LOADING; }
                break;
            case LOADING:
                // Laden dauert in Echtzeit 1–3 s (Song hat dann noch ~2 min) – der Test läuft aber
                // ~60× schneller als Echtzeit, darum hier auf den Lade-Thread warten.
                while (!ready(1 - cur) && e.deck[1 - cur].loadState() != LoadState::Error)
                    std::this_thread::sleep_for(std::chrono::milliseconds(1));
                if (ready(1 - cur)) {
                    float ratio = a.bpm() > 0 && b.bpm() > 0 ? a.bpm() * a.tempo() / b.bpm() : 1.f;
                    if (std::fabs(ratio - 1) > 0.08f) ratio = 1;   // grosser Abstand: kein Angleichen
                    b.setKeylock(true); b.setTempo(ratio); b.seekSeconds(0);
                    b.setGain(1.0f);
                    mixStartSec = aDur - 3 - 20;            // 20 s vor dem hörbaren Ende
                    e.setCrossfaderCurve(1);
                    e.scheduleStart(1 - cur, cur, mixStartSec, false);
                    phase = SCHEDULED;
                    kind = transitions % 5;
                    if (kind == 2) b.setEq(0, -1);   // Bass von B weg (Bass-Swap)
                }
                break;
            case SCHEDULED:
                if (b.playing()) { phase = MIXING; }
                else if (aPos > aDur - 2.5) { schedFails++; }
                break;
            case MIXING: {
                double p = std::min(1.0, (b.positionSeconds()) / 16.0);
                float x = (float)(-1 + 2 * p) * (cur == 0 ? 1 : -1);
                e.setCrossfader(x);
                if (kind == 1) { a.setFilter(-0.8f * (float)p); }
                if (kind == 2 && p > 0.5) { a.setEq(0, -1); b.setEq(0, 0); }
                if (kind == 3) { a.setEchoFeedback(0.6f); a.setEcho(p < 0.2, 0.5f); }
                if (kind == 4) { a.setReverb(p < 0.5 ? (float)p : 0.f); if (p > 0.1 && p < 0.12) a.setLoop(aPos - 2, 2, true); }
                if (p >= 1) {
                    a.setPlaying(false); a.setLoop(0, 0, false); a.setFilter(0); a.setEq(0, 0); a.setEcho(false, 0.4f); a.setReverb(0);
                    e.setCrossfader(cur == 0 ? 1 : -1); e.setCrossfaderCurve(0);
                    cur = 1 - cur; transitions++;
                    rampFrom = e.deck[cur].tempo(); rampStart = e.deck[cur].positionSeconds();
                    phase = RAMP;
                }
                break;
            }
            case RAMP: {
                Deck& now = e.deck[cur];
                double p = std::min(1.0, (now.positionSeconds() - rampStart) / 30.0);
                double s = p * p * (3 - 2 * p);
                now.setTempo((float)(rampFrom + (1 - rampFrom) * s));
                if (p >= 1) { now.setTempo(1); phase = WAIT_LOAD; }
                break;
            }
        }
    }
    double secs = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    printf("Simuliert: %.2f h, Rechenzeit %.0f s (%.0fx schneller als Echtzeit)\n", hours, secs, hours * 3600 / secs);
    printf("Übergänge: %d, verpasste Starts: %d\n", transitions, schedFails);
    printf("NaN/Inf: %d, max. Pegel: %.3f, stille 100-ms-Blöcke: %d\n", nanCount, maxAbs, silentBlocks);
    printf("Speicher (RSS): nach 20 min %ld MB, am Ende %ld MB\n", rss10 / 1024, rssKb() / 1024);
    bool ok = nanCount == 0 && maxAbs <= 1.0f && silentBlocks == 0 && schedFails == 0 && (rss10 == 0 || rssKb() < rss10 + 60 * 1024);
    printf(ok ? "ERGEBNIS: OK\n" : "ERGEBNIS: FEHLER\n");
    return ok ? 0 : 1;
}
