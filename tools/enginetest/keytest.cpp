#include "KeyAnalyzer.h"
#include <cstdio>
#include <string>
using namespace dj;
static const char* names[24]={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B","c","c#","d","d#","e","f","f#","g","g#","a","a#","b"};
// Akkord aus Grundton (MIDI) und Intervallen, mit Obertönen; Bass eine Oktave tiefer
static void addChord(std::vector<int16_t>& out, size_t start, size_t len, int rate, int root, bool minor) {
  int iv[3]={0, minor?3:4, 7};
  for(size_t i=0;i<len;i++){ double t=(double)i/rate; double v=0; double env=std::exp(-t*1.5)*0.7+0.3;
    for(int n=0;n<3;n++){ double f=440*std::pow(2,(root+iv[n]-69)/12.0); for(int h=1;h<=4;h++) v+=0.08/h*std::sin(2*M_PI*f*h*t);}
    double fb=440*std::pow(2,(root-12-69)/12.0); v+=0.15*std::sin(2*M_PI*fb*t);
    // etwas Schlagzeug-Rauschen
    double bt=std::fmod(t,0.5); v+=0.1*std::exp(-bt*30)*((rand()%2000)/1000.0-1);
    int16_t s=(int16_t)std::max(-32767.0,std::min(32767.0,v*env*20000)); out[2*(start+i)]=out[2*(start+i)+1]=s; }
}
int main(){
  int rate=44100; int ok=0, rel=0, wrong=0;
  for(int key=0;key<24;key++){
    bool minor=key>=12; int tonic=60+(key%12);
    // Dur: I IV V I vi IV V I   Moll: i iv v i VI iv V i
    int degM[8]={0,5,7,0,9,5,7,0}; bool qM[8]={0,0,0,0,1,0,0,0};
    int degm[8]={0,5,7,0,8,5,7,0}; bool qm[8]={1,1,1,1,0,1,0,1};
    size_t chordLen=rate*2; std::vector<int16_t> pcm(chordLen*8*4*2);
    for(int rep=0;rep<4;rep++) for(int c=0;c<8;c++){ int r=tonic+(minor?degm[c]:degM[c]); if(r>66) r-=12; addChord(pcm,(rep*8+c)*chordLen,chordLen,rate,r,minor?qm[c]:qM[c]); }
    KeyAnalyzer ka; for(size_t f=0;f<pcm.size()/2;f+=4096) ka.feed(&pcm[f*2], std::min<size_t>(4096,pcm.size()/2-f), rate);
    float conf; int k=ka.result(&conf);
    // relative Tonart: C-Dur <-> a-Moll
    int relKey = minor ? (key%12+3)%12 : 12+(key%12+9)%12;
    const char* tag = k==key?"OK":(k==relKey?"relativ":"FALSCH");
    if(k==key) ok++; else if(k==relKey) rel++; else wrong++;
    printf("%-3s -> %-3s %.2f %s\n", names[key], k>=0?names[k]:"?", conf, tag);
  }
  printf("richtig %d, relativ %d, falsch %d\n", ok, rel, wrong);
}
