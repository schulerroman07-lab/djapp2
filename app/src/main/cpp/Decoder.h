// MP3-Dekodierung mit dem Android-NDK (AMediaExtractor + AMediaCodec).
#pragma once
#include <atomic>
#include <cstdint>
#include <functional>
#include <vector>

namespace dj {

// Wird für jedes dekodierte Stück aufgerufen: Stereo-Samples (L,R,L,R...) als 16 Bit.
using PcmChunkCallback = std::function<void(const int16_t* stereo, size_t frames, int sampleRate)>;

// Dekodiert eine Datei (Dateideskriptor). Gibt false bei Fehler zurück.
// maxSeconds begrenzt die Länge (0 = unbegrenzt).
bool decodeAudio(int fd, int64_t length, const PcmChunkCallback& onChunk,
                 const std::atomic<bool>* cancel, double maxSeconds = 0);

// Dekodiert eine ganze Datei und rechnet sie auf die Ziel-Abtastrate um.
// Ergebnis: Stereo 16 Bit interleaved.
bool decodeToRate(int fd, int64_t length, int targetRate, std::vector<int16_t>& out,
                  const std::atomic<bool>* cancel);

}  // namespace dj
