// Analysiert rohe PCM-Daten (s16le, stereo, 44100) von stdin wie die App: BPM, Tonart, Lautheit
#include "BpmAnalyzer.h"
#include "KeyAnalyzer.h"
#include "Dsp.h"
#include <cstdio>
using namespace dj;
static const char* names[24]={"C","C#","D","D#","E","F","F#","G","G#","A","A#","H","c","c#","d","d#","e","f","f#","g","g#","a","a#","h"};
int main(){ BpmAnalyzer an; KeyAnalyzer key; LoudnessMeter loud; std::vector<int16_t> buf(4096*2); size_t n;
  while((n=fread(buf.data(),4,4096,stdin))>0){ an.feed(buf.data(),n,44100); key.feed(buf.data(),n,44100); loud.feed(buf.data(),n,44100);} 
  BpmResult r=an.result(); float c; int k=key.result(&c);
  printf("BPM %.1f  Tonart %s (%.2f)  Lautheit %.1f dB  Energie %.2f\n", r.bpm, k>=0?names[k]:"?", c, loud.resultDb(), r.energy); }
