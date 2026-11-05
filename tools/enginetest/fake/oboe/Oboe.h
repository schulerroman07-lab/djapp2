// Minimaler Ersatz für Oboe – nur für Tests am PC (kein Ton, Callback wird vom Test aufgerufen)
#pragma once
#include <memory>
#include <cstdint>
namespace oboe {
enum class Result { OK = 0, ErrorInternal = -1 };
enum class DataCallbackResult { Continue, Stop };
enum class Direction { Output };
enum class PerformanceMode { LowLatency };
enum class SharingMode { Exclusive, Shared };
enum class AudioFormat { Float };
enum class Usage { Media };
enum class ContentType { Music };
enum class SampleRateConversionQuality { Medium };
inline const char* convertToText(Result) { return "fake"; }
template <typename T> struct ResultWithValue { T v; explicit operator bool() const { return true; } T value() const { return v; } };
class AudioStream;
class AudioStreamDataCallback { public: virtual ~AudioStreamDataCallback() = default;
  virtual DataCallbackResult onAudioReady(AudioStream*, void*, int32_t) = 0; };
class AudioStreamErrorCallback { public: virtual ~AudioStreamErrorCallback() = default;
  virtual void onErrorAfterClose(AudioStream*, Result) {} };
class AudioStream { public:
  int rate = 48000, ch = 2;
  int getSampleRate() const { return rate; } int getChannelCount() const { return ch; }
  int getFramesPerBurst() const { return 192; } Result setBufferSizeInFrames(int) { return Result::OK; }
  Result requestStart() { return Result::OK; } Result stop() { return Result::OK; } Result close() { return Result::OK; }
  ResultWithValue<double> calculateLatencyMillis() { return {20.0}; } };
class AudioStreamBuilder { public:
  int ch = 2;
  AudioStreamBuilder* setDirection(Direction) { return this; } AudioStreamBuilder* setPerformanceMode(PerformanceMode) { return this; }
  AudioStreamBuilder* setSharingMode(SharingMode) { return this; } AudioStreamBuilder* setFormat(AudioFormat) { return this; }
  AudioStreamBuilder* setChannelCount(int c) { ch = c; return this; } AudioStreamBuilder* setUsage(Usage) { return this; }
  AudioStreamBuilder* setContentType(ContentType) { return this; }
  AudioStreamBuilder* setSampleRateConversionQuality(SampleRateConversionQuality) { return this; }
  AudioStreamBuilder* setDataCallback(AudioStreamDataCallback*) { return this; }
  AudioStreamBuilder* setErrorCallback(AudioStreamErrorCallback*) { return this; }
  AudioStreamBuilder* setSampleRate(int) { return this; } AudioStreamBuilder* setDeviceId(int) { return this; }
  Result openStream(std::shared_ptr<AudioStream>& out) { out = std::make_shared<AudioStream>(); out->ch = ch; return Result::OK; } };
}
