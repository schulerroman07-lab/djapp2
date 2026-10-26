// Verbindung zwischen Kotlin (App) und C++ (Audio-Engine).
#include <jni.h>
#include <unistd.h>

#include "BpmAnalyzer.h"
#include "Decoder.h"
#include "Engine.h"

using namespace dj;

static Engine* g = nullptr;

#define FN(name) Java_ch_hirschexpress_dj_audio_NativeEngine_##name
#define DECK(d) (g->deck[(d) & 1])

extern "C" {

JNIEXPORT jboolean JNICALL FN(create)(JNIEnv*, jobject) {
    if (g) {  // App wurde neu geöffnet: Engine läuft schon, nur Ausgabe neu starten
        g->reopen();
        return g->sampleRate() > 0;
    }
    g = new Engine();
    return g->start();
}

JNIEXPORT void JNICALL FN(reopen)(JNIEnv*, jobject) {
    if (g) g->reopen();
}

JNIEXPORT void JNICALL FN(loadTrack)(JNIEnv*, jobject, jint d, jint fd, jlong length) {
    if (!g || g->sampleRate() <= 0) {
        close(fd);
        return;
    }
    DECK(d).load(fd, length, g->sampleRate());
}

// Zustand eines Decks: [ladezustand, spielt, position, dauer, bpm, erster beat, tempo, cue, pegel]
JNIEXPORT void JNICALL FN(getDeckState)(JNIEnv* env, jobject, jint d, jdoubleArray out) {
    if (!g) return;
    Deck& k = DECK(d);
    jdouble v[9] = {(double)k.loadState(), k.playing() ? 1.0 : 0.0, k.positionSeconds(),
                    k.durationSeconds(), k.bpm(), k.firstBeat(), k.tempo(), k.cueSeconds(),
                    k.level()};
    env->SetDoubleArrayRegion(out, 0, 9, v);
}

JNIEXPORT jfloatArray JNICALL FN(getWaveform)(JNIEnv* env, jobject, jint d, jint points) {
    std::vector<float> w = g ? DECK(d).waveform(points) : std::vector<float>(points, 0.f);
    jfloatArray arr = env->NewFloatArray(points);
    env->SetFloatArrayRegion(arr, 0, points, w.data());
    return arr;
}

JNIEXPORT void JNICALL FN(setPlaying)(JNIEnv*, jobject, jint d, jboolean p) { if (g) DECK(d).setPlaying(p); }
JNIEXPORT void JNICALL FN(cuePress)(JNIEnv*, jobject, jint d) { if (g) DECK(d).cuePress(); }
JNIEXPORT void JNICALL FN(seek)(JNIEnv*, jobject, jint d, jdouble s) { if (g) DECK(d).seekSeconds(s); }
JNIEXPORT void JNICALL FN(setTempo)(JNIEnv*, jobject, jint d, jfloat t) { if (g) DECK(d).setTempo(t); }
JNIEXPORT void JNICALL FN(setKeylock)(JNIEnv*, jobject, jint d, jboolean k) { if (g) DECK(d).setKeylock(k); }
JNIEXPORT void JNICALL FN(setVolume)(JNIEnv*, jobject, jint d, jfloat v) { if (g) DECK(d).setVolume(v); }
JNIEXPORT void JNICALL FN(setEq)(JNIEnv*, jobject, jint d, jint band, jfloat v) { if (g) DECK(d).setEq(band % 3, v); }
JNIEXPORT void JNICALL FN(setKill)(JNIEnv*, jobject, jint d, jint band, jboolean k) { if (g) DECK(d).setKill(band % 3, k); }
JNIEXPORT void JNICALL FN(setFilter)(JNIEnv*, jobject, jint d, jfloat v) { if (g) DECK(d).setFilter(v); }
JNIEXPORT void JNICALL FN(setEcho)(JNIEnv*, jobject, jint d, jboolean on, jfloat a) { if (g) DECK(d).setEcho(on, a); }
JNIEXPORT void JNICALL FN(setPfl)(JNIEnv*, jobject, jint d, jboolean on) { if (g) DECK(d).setPfl(on); }

JNIEXPORT void JNICALL FN(setCrossfader)(JNIEnv*, jobject, jfloat x) { if (g) g->setCrossfader(x); }
JNIEXPORT void JNICALL FN(setMasterVolume)(JNIEnv*, jobject, jfloat v) { if (g) g->setMasterVolume(v); }
JNIEXPORT void JNICALL FN(setHeadphoneVolume)(JNIEnv*, jobject, jfloat v) { if (g) g->setHeadphoneVolume(v); }
JNIEXPORT void JNICALL FN(setCueMix)(JNIEnv*, jobject, jfloat v) { if (g) g->setCueMix(v); }
JNIEXPORT void JNICALL FN(setCueDelayMs)(JNIEnv*, jobject, jfloat ms) { if (g) g->setCueDelayMs(ms); }
JNIEXPORT void JNICALL FN(setOutput)(JNIEnv*, jobject, jint mode, jint master, jint cue) {
    if (g) g->setOutput(mode, master, cue);
}

// Sync: Tempo (und Beat-Phase) dieses Decks an das andere anpassen. Gibt neues Tempo zurück.
JNIEXPORT jfloat JNICALL FN(sync)(JNIEnv*, jobject, jint d) {
    if (!g) return 1.f;
    Deck& me = DECK(d);
    Deck& other = DECK(d + 1);
    if (me.bpm() <= 0 || other.bpm() <= 0) return me.tempo();
    float otherBpm = other.bpm() * other.tempo();
    // Halbe/doppelte Tempi berücksichtigen (z.B. 70 und 140 BPM)
    float myBpm = me.bpm();
    float ratio = otherBpm / myBpm;
    if (ratio > 1.5f) ratio /= 2.f;
    if (ratio < 0.67f) ratio *= 2.f;
    me.setTempo(ratio);

    // Phase angleichen, wenn das andere Deck läuft
    if (other.playing()) {
        double oBeat = 60.0 / other.bpm();
        double mBeat = 60.0 / myBpm;
        double oPhase = std::fmod((other.positionSeconds() - other.firstBeat()) / oBeat, 1.0);
        double mPhase = std::fmod((me.positionSeconds() - me.firstBeat()) / mBeat, 1.0);
        if (oPhase < 0) oPhase += 1;
        if (mPhase < 0) mPhase += 1;
        double diff = oPhase - mPhase;
        if (diff > 0.5) diff -= 1;
        if (diff < -0.5) diff += 1;
        me.seekSeconds(me.positionSeconds() + diff * mBeat);
    }
    return ratio;
}

JNIEXPORT jboolean JNICALL FN(startRecording)(JNIEnv*, jobject, jint fd) {
    if (!g || g->sampleRate() <= 0) {
        close(fd);
        return false;
    }
    return g->recorder.start(fd, g->sampleRate());
}
JNIEXPORT void JNICALL FN(stopRecording)(JNIEnv*, jobject) { if (g) g->recorder.stop(); }
JNIEXPORT jdouble JNICALL FN(recordingSeconds)(JNIEnv*, jobject) { return g ? g->recorder.seconds() : 0; }

JNIEXPORT jfloat JNICALL FN(getMasterLevel)(JNIEnv*, jobject) { return g ? g->masterLevel() : 0.f; }

// Volle Wellenform (100 Werte pro Sekunde) für die Zoom-Ansicht
JNIEXPORT jfloatArray JNICALL FN(getPeaks)(JNIEnv* env, jobject, jint d) {
    std::vector<float> p = g ? DECK(d).peaks() : std::vector<float>();
    jfloatArray arr = env->NewFloatArray((jsize)p.size());
    if (!p.empty()) env->SetFloatArrayRegion(arr, 0, (jsize)p.size(), p.data());
    return arr;
}

// [Abtastrate, Latenz ms, Kanäle, Master-Pegel]
JNIEXPORT void JNICALL FN(getEngineInfo)(JNIEnv* env, jobject, jdoubleArray out) {
    if (!g) return;
    jdouble v[4] = {(double)g->sampleRate(), g->latencyMs(), (double)g->channelCount(),
                    g->masterLevel()};
    env->SetDoubleArrayRegion(out, 0, 4, v);
}

// BPM-Analyse für die Bibliothek (blockiert – im Hintergrund aufrufen!). [bpm, erster Beat]
JNIEXPORT jfloatArray JNICALL FN(analyzeFile)(JNIEnv* env, jobject, jint fd, jlong length) {
    BpmAnalyzer an;
    decodeAudio(
        fd, length, [&](const int16_t* s, size_t frames, int rate) { an.feed(s, frames, rate); },
        nullptr, 60 * 8);
    close(fd);
    BpmResult r = an.result();
    jfloat v[2] = {r.bpm, r.firstBeat};
    jfloatArray arr = env->NewFloatArray(2);
    env->SetFloatArrayRegion(arr, 0, 2, v);
    return arr;
}

}  // extern "C"
