// PC-Werkzeug: analysiert rohe PCM-Daten (s16le, stereo, 44100 Hz) genau wie die App und gibt das
// Ergebnis als JSON aus. Aufruf: analyze_cli <datei.pcm> [--vocal-features out.bin] [Stellgrösse=Wert ...]
#include "Analyzer.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
using namespace dj;

int main(int argc, char** argv) {
    if (argc < 2) { fprintf(stderr, "Aufruf: analyze_cli datei.pcm\n"); return 2; }
    const char* vfOut = nullptr;
    for (int i = 2; i < argc; i++) {
        std::string a = argv[i];
        if (a == "--vocal-features" && i + 1 < argc) { vfOut = argv[++i]; continue; }
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
        else { fprintf(stderr, "unbekannt: %s\n", k.c_str()); return 2; }
    }
    FILE* f = std::string(argv[1]) == "-" ? stdin : fopen(argv[1], "rb");   // "-" = von der Standardeingabe
    if (!f) { perror(argv[1]); return 1; }
    TrackAnalyzer an;
    std::vector<int16_t> buf(4096 * 2);
    size_t n;
    while ((n = fread(buf.data(), 4, 4096, f)) > 0) an.feed(buf.data(), n, 44100);
    if (f != stdin) fclose(f);
    Analysis r = an.result();
    printf("{\"bpm\":%.3f,\"confidence\":%.3f,\"steady\":%s,\"downbeat\":%d,\"downbeatConf\":%.3f,\"energy\":%.3f,\"beats\":[",
           r.bpm, r.confidence, r.steady ? "true" : "false", r.downbeat, r.downbeatConf, r.energy);
    for (size_t i = 0; i < r.beats.size(); i++) printf(i ? ",%.4f" : "%.4f", r.beats[i]);
    printf("],\"vocal\":[");
    for (size_t i = 0; i < r.vocal.size(); i++) printf(i ? ",%d" : "%d", r.vocal[i]);
    printf("],\"features\":\"");
    for (uint8_t b : r.features) printf("%02x", b);
    printf("\"}\n");
    if (vfOut) {
        // Eingänge des Gesangs-Klassifikators (alle 0,25 s), zum Trainieren in tools/realmusic/train_vocal.py
        std::vector<float> vi = an.vocalInputs();
        FILE* o = fopen(vfOut, "wb");
        double hop = 0.25;
        int dim = 2 * TrackAnalyzer::kVocalDim;
        fwrite(&hop, sizeof(double), 1, o);
        fwrite(&dim, sizeof(int), 1, o);
        fwrite(vi.data(), sizeof(float), vi.size(), o);
        fclose(o);
    }
    return 0;
}
