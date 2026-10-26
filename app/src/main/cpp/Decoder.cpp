#include "Decoder.h"

#include <android/log.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <cmath>
#include <cstring>
#include <string>

#define LOG_TAG "HirschDJ"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace dj {

namespace {
constexpr int32_t kEncodingPcm16 = 2;
constexpr int32_t kEncodingFloat = 4;
}  // namespace

bool decodeAudio(int fd, int64_t length, const PcmChunkCallback& onChunk,
                 const std::atomic<bool>* cancel, double maxSeconds) {
    AMediaExtractor* ex = AMediaExtractor_new();
    if (AMediaExtractor_setDataSourceFd(ex, fd, 0, length) != AMEDIA_OK) {
        LOGE("setDataSourceFd fehlgeschlagen");
        AMediaExtractor_delete(ex);
        return false;
    }

    // Erste Audiospur suchen
    int track = -1;
    AMediaFormat* format = nullptr;
    std::string mime;
    size_t count = AMediaExtractor_getTrackCount(ex);
    for (size_t i = 0; i < count; i++) {
        AMediaFormat* f = AMediaExtractor_getTrackFormat(ex, i);
        const char* m = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &m) && m &&
            strncmp(m, "audio/", 6) == 0) {
            track = (int)i;
            format = f;
            mime = m;
            break;
        }
        AMediaFormat_delete(f);
    }
    if (track < 0) {
        LOGE("Keine Audiospur gefunden");
        AMediaExtractor_delete(ex);
        return false;
    }

    int32_t sampleRate = 44100, channels = 2;
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate);
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels);
    AMediaExtractor_selectTrack(ex, track);

    AMediaCodec* codec = AMediaCodec_createDecoderByType(mime.c_str());
    if (!codec || AMediaCodec_configure(codec, format, nullptr, nullptr, 0) != AMEDIA_OK ||
        AMediaCodec_start(codec) != AMEDIA_OK) {
        LOGE("Decoder konnte nicht gestartet werden (%s)", mime.c_str());
        if (codec) AMediaCodec_delete(codec);
        AMediaFormat_delete(format);
        AMediaExtractor_delete(ex);
        return false;
    }
    AMediaFormat_delete(format);

    int32_t encoding = kEncodingPcm16;
    bool inputDone = false, outputDone = false;
    std::vector<int16_t> stereo;
    double decodedFrames = 0;
    bool ok = true;

    while (!outputDone) {
        if (cancel && cancel->load()) {
            ok = false;
            break;
        }
        if (!inputDone) {
            ssize_t inIdx = AMediaCodec_dequeueInputBuffer(codec, 5000);
            if (inIdx >= 0) {
                size_t cap = 0;
                uint8_t* buf = AMediaCodec_getInputBuffer(codec, inIdx, &cap);
                ssize_t size = AMediaExtractor_readSampleData(ex, buf, cap);
                if (size < 0) {
                    AMediaCodec_queueInputBuffer(codec, inIdx, 0, 0, 0,
                                                 AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                } else {
                    int64_t pts = AMediaExtractor_getSampleTime(ex);
                    AMediaCodec_queueInputBuffer(codec, inIdx, 0, size, pts, 0);
                    AMediaExtractor_advance(ex);
                }
            }
        }

        AMediaCodecBufferInfo info;
        ssize_t outIdx = AMediaCodec_dequeueOutputBuffer(codec, &info, 5000);
        if (outIdx >= 0) {
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) outputDone = true;
            size_t outSize = 0;
            uint8_t* out = AMediaCodec_getOutputBuffer(codec, outIdx, &outSize);
            if (out && info.size > 0 && channels > 0) {
                const uint8_t* data = out + info.offset;
                size_t bytesPerSample = (encoding == kEncodingFloat) ? 4 : 2;
                size_t frames = info.size / (bytesPerSample * channels);
                stereo.resize(frames * 2);
                for (size_t f = 0; f < frames; f++) {
                    float l, r;
                    if (encoding == kEncodingFloat) {
                        const float* s = reinterpret_cast<const float*>(data) + f * channels;
                        l = s[0];
                        r = channels > 1 ? s[1] : s[0];
                        stereo[f * 2] = (int16_t)std::fmax(-32768.f, std::fmin(32767.f, l * 32767.f));
                        stereo[f * 2 + 1] = (int16_t)std::fmax(-32768.f, std::fmin(32767.f, r * 32767.f));
                    } else {
                        const int16_t* s = reinterpret_cast<const int16_t*>(data) + f * channels;
                        stereo[f * 2] = s[0];
                        stereo[f * 2 + 1] = channels > 1 ? s[1] : s[0];
                    }
                }
                if (frames > 0) onChunk(stereo.data(), frames, sampleRate);
                decodedFrames += frames;
                if (maxSeconds > 0 && decodedFrames / sampleRate >= maxSeconds) outputDone = true;
            }
            AMediaCodec_releaseOutputBuffer(codec, outIdx, false);
        } else if (outIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* of = AMediaCodec_getOutputFormat(codec);
            AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate);
            AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels);
            int32_t enc = kEncodingPcm16;
            if (AMediaFormat_getInt32(of, "pcm-encoding", &enc)) encoding = enc;
            AMediaFormat_delete(of);
        }
    }

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaExtractor_delete(ex);
    return ok && decodedFrames > 0;
}

bool decodeToRate(int fd, int64_t length, int targetRate, std::vector<int16_t>& out,
                  const std::atomic<bool>* cancel) {
    std::vector<int16_t> src;
    src.reserve(44100 * 2 * 60 * 6);
    int srcRate = 44100;
    bool ok = decodeAudio(
        fd, length,
        [&](const int16_t* s, size_t frames, int rate) {
            srcRate = rate;
            src.insert(src.end(), s, s + frames * 2);
        },
        cancel, 60 * 30);  // max. 30 Minuten pro Titel
    if (!ok) return false;

    size_t srcFrames = src.size() / 2;
    if (srcRate == targetRate || srcRate <= 0) {
        out.swap(src);
        return true;
    }
    // Abtastrate umrechnen (kubische Interpolation, gute Qualität)
    double ratio = (double)srcRate / targetRate;
    size_t outFrames = (size_t)((double)srcFrames / ratio);
    out.assign(outFrames * 2, 0);
    auto at = [&](long i, int ch) -> float {
        if (i < 0) i = 0;
        if (i >= (long)srcFrames) i = (long)srcFrames - 1;
        return src[i * 2 + ch];
    };
    for (size_t o = 0; o < outFrames; o++) {
        double p = o * ratio;
        long i = (long)p;
        float t = (float)(p - i);
        for (int ch = 0; ch < 2; ch++) {
            float y0 = at(i - 1, ch), y1 = at(i, ch), y2 = at(i + 1, ch), y3 = at(i + 2, ch);
            // Hermite-Interpolation
            float c1 = 0.5f * (y2 - y0);
            float c2 = y0 - 2.5f * y1 + 2.f * y2 - 0.5f * y3;
            float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
            float v = ((c3 * t + c2) * t + c1) * t + y1;
            if (v > 32767.f) v = 32767.f;
            if (v < -32768.f) v = -32768.f;
            out[o * 2 + ch] = (int16_t)v;
        }
    }
    return true;
}

}  // namespace dj
