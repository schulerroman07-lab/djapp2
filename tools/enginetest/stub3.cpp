#include "Decoder.h"
#include <cmath>
namespace dj {
// Testsignal: 3 nicht-harmonische Töne + "Bassdrum" (60 Hz, 120 BPM, abklingend)
bool decodeToRate(int, int64_t, int rate, std::vector<int16_t>& out, const std::atomic<bool>*){
  size_t n=rate*30; out.resize(n*2);
  for(size_t i=0;i<n;i++){ double t=(double)i/rate;
    double v=0.15*sin(2*M_PI*440.0*t)+0.12*sin(2*M_PI*623.3*t)+0.1*sin(2*M_PI*97.1*t);
    double bt=fmod(t,0.5); v+=0.4*exp(-bt*12)*sin(2*M_PI*60*bt);
    out[2*i]=out[2*i+1]=(int16_t)(v*32767);} return true;}
bool decodeAudio(int, int64_t, const PcmChunkCallback&, const std::atomic<bool>*, double){return false;}
}
