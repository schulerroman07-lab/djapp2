// PC-Werkzeug für die objektive Prüfung der Übergänge (Punkt 9): spielt einen geplanten Übergang
// (Abspiel-Datei *.mix aus RealMixPlanTest) mit der ECHTEN Engine der App ab – Decks, Tempo/Keylock (WSOLA),
// EQ, Filter, Echo, Hall, Loop, Crossfader-Kurve, Begrenzer, geplanter Start – genau wie AutoMix:
//   - B wird vorbereitet (Tempo, Lautheit, Startpunkt) und von der Engine sample-genau gestartet, wenn A
//     die Einsatzstelle erreicht (scheduleStart),
//   - die Regler folgen der Tabelle des Übergangs (vor dem Einsatz über q, danach über p = B-Fortschritt),
//   - am Schluss: A stoppt, Effekte zurück, Crossfader ganz zu B.
// Drei Durchgänge mit identischer Steuerung: Mix, nur A (B stumm), nur B (A stumm).
// Ausgabe: <out>.mix.f32 / .a.f32 / .b.f32 (Stereo float, 48 kHz) und <out>.log (Positionen alle 10 ms).
// Aufruf: mixrender <datei.mix> <ausgabe-präfix>
#include "Engine.h"
#include "Decoder.h"
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <map>
#include <string>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>
#include <vector>
using namespace dj;

namespace dj {
// Ersatz für den MP3-Dekoder: die Testdateien sind schon rohes PCM (s16le, Stereo, 44,1 kHz)
bool decodeAudio(int fd, int64_t, const PcmChunkCallback& cb, const std::atomic<bool>* cancel, double maxSeconds) {
    std::vector<int16_t> buf(1152 * 2);
    size_t total = 0;
    lseek(fd, 0, SEEK_SET);
    while (true) {
        if (cancel && cancel->load()) return false;
        ssize_t got = read(fd, buf.data(), buf.size() * 2);
        if (got <= 0) break;
        size_t frames = (size_t)got / 4;
        cb(buf.data(), frames, 44100);
        total += frames;
        if (maxSeconds > 0 && total >= maxSeconds * 44100) break;
    }
    return true;
}
bool decodeToRate(int, int64_t, int, std::vector<int16_t>&, const std::atomic<bool>*) { return false; }
}  // namespace dj

struct Row { double v[17]; };

struct Script {
    std::map<std::string, std::string> kv;
    std::vector<Row> pre, step;
    double num(const char* k, double def = 0) const {
        auto it = kv.find(k);
        return it == kv.end() ? def : atof(it->second.c_str());
    }
};

static bool readScript(const char* path, Script& s) {
    FILE* f = fopen(path, "r");
    if (!f) return false;
    char line[4096];
    std::vector<Row>* target = nullptr;
    int left = 0;
    while (fgets(line, sizeof line, f)) {
        line[strcspn(line, "\n")] = 0;
        if (left > 0) {
            Row r{};
            char* p = line;
            for (int i = 0; i < 17; i++) r.v[i] = strtod(p, &p);
            target->push_back(r);
            left--;
            continue;
        }
        if (!strcmp(line, "end")) break;
        char* sp = strchr(line, ' ');
        std::string k = sp ? std::string(line, sp - line) : std::string(line);
        std::string v = sp ? std::string(sp + 1) : std::string();
        if (k == "pre" || k == "step") {
            target = k == "pre" ? &s.pre : &s.step;
            left = atoi(v.c_str());
            continue;
        }
        s.kv[k] = v;
    }
    fclose(f);
    return !s.step.empty();
}

// Tabellen-Zeile an der Stelle x (0..1), stetige Werte linear, Schalter von der unteren Zeile
static Row at(const std::vector<Row>& t, double x) {
    x = std::max(0.0, std::min(1.0, x));
    double fi = x * (t.size() - 1);
    size_t i = (size_t)fi;
    if (i + 1 >= t.size()) return t.back();
    double f = fi - i;
    Row r = t[i];
    for (int k = 0; k < 17; k++) {
        if (k == 9 || k == 14) continue;       // Echo an/aus, Loop an/aus
        r.v[k] = t[i].v[k] * (1 - f) + t[i + 1].v[k] * f;
    }
    return r;
}

struct LoopState { bool on = false; double start = -1, len = -1; };

// Regler wie DeckModel/MixerModel (EQ-Band: Engine-Index = 2 − Kotlin-Index)
static void apply(Engine& e, const Row& r, LoopState& loop, bool applyA) {
    if (applyA) {
        for (int i = 0; i < 3; i++) e.deck[0].setEq(2 - i, (float)r.v[1 + i]);
        e.deck[0].setFilter((float)r.v[7]);
        e.deck[0].setEcho(r.v[9] > 0.5, (float)r.v[10]);
        e.deck[0].setEchoFeedback((float)r.v[11]);
        e.deck[0].setReverb((float)r.v[12]);
        bool on = r.v[14] > 0.5;
        if (on != loop.on || (on && (std::fabs(r.v[15] - loop.start) > 1e-6 || std::fabs(r.v[16] - loop.len) > 1e-6))) {
            e.deck[0].setLoop(r.v[15], r.v[16], on);
            loop.on = on; loop.start = r.v[15]; loop.len = r.v[16];
        }
        e.setCrossfader((float)r.v[13]);
    }
    for (int i = 0; i < 3; i++) e.deck[1].setEq(2 - i, (float)r.v[4 + i]);
    e.deck[1].setFilter((float)r.v[8]);
}

static void resetEffects(Deck& d) {
    for (int i = 0; i < 3; i++) d.setEq(i, 0.f);
    d.setFilter(0.f);
    d.setEcho(false, 0.4f);
    d.setEchoFeedback(0.55f);
    d.setReverb(0.f);
    d.setLoop(0, 0, false);
}

static int openPcm(const std::string& path, int64_t& len) {
    int fd = open(path.c_str(), O_RDONLY);
    struct stat st{};
    if (fd >= 0 && fstat(fd, &st) == 0) len = st.st_size;
    return fd;
}

// mode: 0 = Mix, 1 = nur A, 2 = nur B
static int render(const Script& s, int mode, const std::string& outPath, FILE* log) {
    Engine e;
    if (!e.start()) { fprintf(stderr, "Engine-Start fehlgeschlagen\n"); return 1; }
    oboe::AudioStream stream;
    const int rate = 48000, block = 480;               // 10 ms
    std::vector<float> buf(block * 2);
    int64_t la = 0, lb = 0;
    int fa = openPcm(s.kv.at("apcm"), la), fb = openPcm(s.kv.at("bpcm"), lb);
    if (fa < 0 || fb < 0) { fprintf(stderr, "PCM fehlt\n"); return 1; }
    e.deck[0].load(fa, la, rate);
    e.deck[1].load(fb, lb, rate);
    auto ready = [&](int d) { return e.deck[d].loadState() == LoadState::Ready; };
    for (int w = 0; w < 6000 && !(ready(0) && ready(1)); w++) std::this_thread::sleep_for(std::chrono::milliseconds(5));
    if (!(ready(0) && ready(1))) { fprintf(stderr, "Laden fehlgeschlagen\n"); return 1; }

    const double mixSec = s.num("mixSec"), bStart = s.num("bStart"), ratio = s.num("ratio", 1);
    const double lenSec = std::max(0.05, s.num("lenSec", 4)), preSec = s.num("preSec"), centerSec = s.num("centerSec", -1);
    const bool stopsA = s.num("stopsA") > 0.5, ep = s.num("equalPower") > 0.5;
    const double lead = std::max(preSec, centerSec >= 0 ? mixSec - centerSec : 0.0);
    // Vorlauf/Nachlauf (Sekunden): Messung 12/6, Hörproben z. B. 20/20 (Umgebungsvariablen MIXRENDER_PRE/_AFTER)
    const double preRoll = getenv("MIXRENDER_PRE") ? atof(getenv("MIXRENDER_PRE")) : 12.0;
    const long afterBlocks = (long)((getenv("MIXRENDER_AFTER") ? atof(getenv("MIXRENDER_AFTER")) : 6.0) * 100);
    const double a0 = std::max(0.0, mixSec - lead - preRoll);

    // Ausgangslage wie im Auto-Mix: A läuft allein (Crossfader bei A), B vorbereitet und stumm
    e.setCrossfader(-1.f);
    e.setCrossfaderCurve(ep ? 1 : 0);
    e.deck[0].setGain(1.f);
    e.deck[1].setGain((float)s.num("gainB", 1));
    if (mode == 1) e.deck[1].setVolume(0.f);
    if (mode == 2) e.deck[0].setVolume(0.f);
    e.deck[0].seekSeconds(a0);
    e.deck[1].setTempo((float)ratio);
    e.deck[1].setKeylock(true);
    e.deck[1].seekSeconds(bStart);
    LoopState loop;
    apply(e, s.step.front(), loop, false);             // Vorbereitung von B (B ist noch still)
    e.deck[0].setPlaying(true);
    e.scheduleStart(1, 0, mixSec, stopsA);

    FILE* out = fopen(outPath.c_str(), "wb");
    bool centered = false, finished = false;
    long blocks = 0, afterEnd = 0;
    const long maxBlocks = (long)((mixSec - a0 + lenSec / std::max(0.5, ratio) + 30.0) * 100) + afterBlocks;
    while (blocks < maxBlocks) {
        const double aPos = e.deck[0].positionSeconds(), bPos = e.deck[1].positionSeconds();
        const bool bPlay = e.deck[1].playing();
        double p = -1;
        if (!finished) {
            if (!bPlay) {
                if (!s.pre.empty() && aPos >= mixSec - preSec) apply(e, at(s.pre, (aPos - (mixSec - preSec)) / std::max(1e-3, preSec)), loop, true);
                if (centerSec >= 0 && !centered && aPos >= centerSec) { e.setCrossfader(0.f); centered = true; }
            } else {
                p = (bPos - bStart) / lenSec;
                apply(e, at(s.step, p), loop, true);
                if (p >= 1.0) {
                    // Aufräumen wie AutoMix: A stoppt, Effekte zurück, Crossfader ganz zu B, Kurve zurück
                    e.deck[0].setPlaying(false);
                    resetEffects(e.deck[0]);
                    resetEffects(e.deck[1]);
                    e.setCrossfader(1.f);
                    e.setCrossfaderCurve(0);
                    finished = true;
                }
            }
        }
        if (log) fprintf(log, "%.4f %.5f %.5f %d %d %.4f\n", blocks * 0.01, aPos, bPos, e.deck[0].playing() ? 1 : 0, bPlay ? 1 : 0, p);
        e.onAudioReady(&stream, buf.data(), block);
        fwrite(buf.data(), sizeof(float), buf.size(), out);
        blocks++;
        if (finished && ++afterEnd >= afterBlocks) break;      // Nachlauf nach dem Übergang
    }
    fclose(out);
    e.stop();
    close(fa);
    close(fb);
    if (!finished) fprintf(stderr, "Übergang nicht beendet (B gestartet: %d)\n", e.deck[1].playing() ? 1 : 0);
    return finished ? 0 : 3;
}

int main(int argc, char** argv) {
    if (argc < 3) { fprintf(stderr, "Aufruf: mixrender datei.mix ausgabe-präfix\n"); return 2; }
    Script s;
    if (!readScript(argv[1], s)) { fprintf(stderr, "Abspiel-Datei unlesbar: %s\n", argv[1]); return 2; }
    std::string pre = argv[2];
    FILE* log = fopen((pre + ".log").c_str(), "w");
    int r = render(s, 0, pre + ".mix.f32", log);
    fclose(log);
    if (r == 0) r = render(s, 1, pre + ".a.f32", nullptr);
    if (r == 0) r = render(s, 2, pre + ".b.f32", nullptr);
    return r;
}
