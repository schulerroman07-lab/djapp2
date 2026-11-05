#include "Dsp.h"
#include <cstdio>
using namespace dj;
// Sinus 1 kHz mit Amplitude a → erwartete Lautheit ≈ 20*log10(a/sqrt2)
int main(){
  int rate=48000; size_t n=rate*20; std::vector<int16_t> p(n*2);
  for (float a : {0.5f, 0.25f, 0.1f}) {
    for(size_t i=0;i<n;i++){ float v = i < n/4 ? 0 : a*sin(2*M_PI*1000*i/rate); p[2*i]=p[2*i+1]=(int16_t)(v*32767);} // erstes Viertel still
    printf("a=%.2f: %.2f dB (expect %.2f)\n", a, gatedLoudnessDb(p.data(), n, rate), 20*log10(a/sqrt(2)));
  }
}
