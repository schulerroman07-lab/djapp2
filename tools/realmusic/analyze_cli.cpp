// PC-Werkzeug: analysiert rohe PCM-Daten (s16le, stereo, 44100 Hz) genau wie die App und gibt das
// Ergebnis als JSON aus. Aufruf: analyze_cli <datei.pcm> [--mel out.bin] [Stellgrösse=Wert ...]
#include "Analyzer.h"
#include "Dsp.h"
#include "KeyAnalyzer.h"
#include <cmath>
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
using namespace dj;

int main(int argc, char** argv) {
    if (argc < 2) { fprintf(stderr, "Aufruf: analyze_cli datei.pcm\n"); return 2; }
    const char* melOut = nullptr;
    const char* fluxOut = nullptr;
    const char* netOut = nullptr;
    for (int i = 2; i < argc; i++) {
        std::string a = argv[i];
        if (a == "--mel" && i + 1 < argc) { melOut = argv[++i]; continue; }
        if (a == "--flux" && i + 1 < argc) { fluxOut = argv[++i]; continue; }
        if (a == "--beatnet" && i + 1 < argc) { netOut = argv[++i]; continue; }
        size_t eq = a.find('=');
        if (eq == std::string::npos) continue;
        std::string k = a.substr(0, eq);
        float v = (float)atof(a.c_str() + eq + 1);
        AnalyzerTuning& t = tuning();
        if (k == "lowWeight") t.lowWeight = v; else if (k == "priorBpm") t.priorBpm = v;
        else if (k == "priorOct") t.priorOct = v; else if (k == "tightness") t.tightness = v;
        else if (k == "steadyFrac") t.steadyFrac = v; else if (k == "latencySec") t.latencySec = v;
        else if (k == "confBias") t.confBias = v; else if (k == "confInlier") t.confInlier = v;
        else if (k == "confSalience") t.confSalience = v; else if (k == "confClarity") t.confClarity = v;
        else if (k == "dbLow") t.dbLow = v; else if (k == "dbNov") t.dbNov = v; else if (k == "dbLow3") t.dbLow3 = v;
        else if (k == "netBias") t.netBias = v; else if (k == "netAct") t.netAct = v;
        else if (k == "netInlier") t.netInlier = v; else if (k == "netClarity") t.netClarity = v;
        else if (k == "netEvident") t.netEvident = v;
        else if (k == "netNov") t.netNov = v;
        else { fprintf(stderr, "unbekannt: %s\n", k.c_str()); return 2; }
    }
    FILE* f = std::string(argv[1]) == "-" ? stdin : fopen(argv[1], "rb");   // "-" = von der Standardeingabe
    if (!f) { perror(argv[1]); return 1; }
    TrackAnalyzer an;
    KeyAnalyzer key;
    LoudnessMeter loud;
    std::vector<float> peaks;                 // wie die App: 100 Spitzenwerte pro Sekunde
    std::vector<int16_t> buf(4096 * 2);
    size_t n, total = 0;
    int peak = 0, peakN = 0;
    while ((n = fread(buf.data(), 4, 4096, f)) > 0) {
        an.feed(buf.data(), n, 44100);
        key.feed(buf.data(), n, 44100);
        loud.feed(buf.data(), n, 44100);
        for (size_t i = 0; i < n; i++) {
            peak = std::max(peak, std::max(std::abs((int)buf[i * 2]), std::abs((int)buf[i * 2 + 1])));
            if (++peakN == 441) { peaks.push_back(peak / 32768.f); peak = 0; peakN = 0; }
        }
        total += n;
    }
    // hörbares Ende wie StructureAnalyzer.soundEnd (Spitzenwert < 0,02 = Stille)
    const double duration = total / 44100.0;
    long pi = (long)peaks.size() - 1;
    while (pi > 0 && peaks[pi] < 0.02f) pi--;
    const double soundEnd = peaks.empty() ? duration : std::min(duration, (pi + 1) / 100.0 + 0.1);
    // erster hörbarer Ton wie StructureAnalyzer.soundStart
    size_t si = 0;
    while (si < peaks.size() && peaks[si] < 0.02f) si++;
    const double soundStart = (si == 0 || si >= peaks.size()) ? 0.0 : std::max(0.0, si / 100.0 - 0.05);
    float keyConf = 0;
    const int musicalKey = key.result(&keyConf);
    if (f != stdin) fclose(f);
    Analysis r = an.result();
    printf("{\"inlier\":%.3f,\"salience\":%.3f,\"clarity\":%.3f,\"netAct\":%.3f,", r.inlierFrac, r.salience, r.clarity, r.netAct);
    printf("\"bpm\":%.3f,\"confidence\":%.3f,\"steady\":%s,\"downbeat\":%d,\"downbeatConf\":%.3f,\"energy\":%.3f,"
           "\"duration\":%.3f,\"soundEnd\":%.3f,\"soundStart\":%.3f,\"key\":%d,\"keyConf\":%.3f,\"loudness\":%.2f,\"beats\":[",
           r.bpm, r.confidence, r.steady ? "true" : "false", r.downbeat, r.downbeatConf, r.energy,
           duration, soundEnd, soundStart, musicalKey, keyConf, loud.resultDb());
    for (size_t i = 0; i < r.beats.size(); i++) printf(i ? ",%.4f" : "%.4f", r.beats[i]);
    printf("],\"gridDev\":[");
    for (size_t i = 0; i < r.gridDev.size(); i++) printf(i ? ",%d" : "%d", r.gridDev[i]);
    printf("],\"vocal\":[");
    for (size_t i = 0; i < r.vocal.size(); i++) printf(i ? ",%d" : "%d", r.vocal[i]);
    printf("],\"features\":\"");
    for (uint8_t b : r.features) printf("%02x", b);
    printf("\"}\n");
    if (netOut) {
        // Beat-/Downbeat-Kurve des Beat-Netzes (pro Mel-Rahmen), zum Prüfen gegen das Training
        std::vector<float> nb, nd;
        an.beatNet(nb, nd);
        FILE* o = fopen(netOut, "wb");
        int len = (int)nb.size();
        fwrite(&len, sizeof(int), 1, o);
        fwrite(nb.data(), sizeof(float), nb.size(), o);
        fwrite(nd.data(), sizeof(float), nd.size(), o);
        fclose(o);
    }
    if (fluxOut) {
        // Spektralfluss pro Rahmen (gesamt, Bass, 5 Gruppen) zum Abstimmen der Beat-Erkennung
        FILE* o = fopen(fluxOut, "wb");
        double fps = an.frameRate();
        int dim = 1 + TrackAnalyzer::kGroups;
        fwrite(&fps, sizeof(double), 1, o);
        fwrite(&dim, sizeof(int), 1, o);
        const auto& ose = an.onset();
        const auto& g = an.groupFlux();
        for (size_t i = 0; i < ose.size(); i++) {
            fwrite(&ose[i], sizeof(float), 1, o);
            fwrite(&g[i * TrackAnalyzer::kGroups], sizeof(float), TrackAnalyzer::kGroups, o);
        }
        fclose(o);
    }
    if (melOut) {
        // Eingang des Gesangs-Netzes (Mel-Spektrum Mitte/Seite), zum Trainieren in train_vocal_net.py
        std::vector<float> m = an.melInput();
        FILE* o = fopen(melOut, "wb");
        double hop = an.melHop();
        int dim = 2 * TrackAnalyzer::kMel;
        fwrite(&hop, sizeof(double), 1, o);
        fwrite(&dim, sizeof(int), 1, o);
        fwrite(m.data(), sizeof(float), m.size(), o);
        fclose(o);
    }
    return 0;
}
