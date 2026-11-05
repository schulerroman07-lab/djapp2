#include "Decoder.h"
#include <cmath>
#include <algorithm>
namespace dj {
// Testsignal: 3 nicht-harmonische Töne + "Bassdrum" (60 Hz, 120 BPM, abklingend)
bool decodeToRate(int, int64_t, int rate, std::vector<int16_t>& out, const std::atomic<bool>*){
  size_t n=rate*30; out.resize(n*2);
  for(size_t i=0;i<n;i++){ double t=(double)i/rate;
    double v=0.15*sin(2*M_PI*440.0*t)+0.12*sin(2*M_PI*623.3*t)+0.1*sin(2*M_PI*97.1*t);
    double bt=fmod(t,0.5); v+=0.4*exp(-bt*12)*sin(2*M_PI*60*bt);
    out[2*i]=out[2*i+1]=(int16_t)(v*32767);} return true;}
bool decodeAudio(int fd, int64_t len, const PcmChunkCallback& cb, const std::atomic<bool>* cancel, double) {
  std::vector<int16_t> pcm; decodeToRate(fd, len, 44100, pcm, cancel);
  for (size_t f = 0; f < pcm.size() / 2; f += 1152) { if (cancel && cancel->load()) return false;
    cb(&pcm[f * 2], std::min<size_t>(1152, pcm.size() / 2 - f), 44100); }
  return true; }
}
