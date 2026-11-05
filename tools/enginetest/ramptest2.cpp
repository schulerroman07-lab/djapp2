#include "Deck.h"
#include <cstdio>
#include <cmath>
#include <unistd.h>
#include <chrono>
#include <thread>
using namespace dj;
// Max. erlaubte Änderung pro Sample des Testsignals: Summe A*w ≈ 0.15*0.0576+0.12*0.0816+0.1*0.0127+0.4*0.0079 ≈ 0.023
int main(int argc, char** argv){
  float start = argc > 1 ? atof(argv[1]) : 1.04f;
  Deck d; d.load(dup(0),0,48000); while(d.loadState()!=LoadState::Ready) std::this_thread::sleep_for(std::chrono::milliseconds(5));
  d.setTempo(start); d.setKeylock(true); d.setPlaying(true);
  std::vector<float> m(480*2), c(480*2);
  float prev=0, maxJump=0; int jumpBlock=-1; int bigJumps=0;
  for(int b=0;b<1500;b++){
    if(b>=200){ float k=std::min(1.f,(b-200)/1000.f); float s=k*k*(3-2*k); d.setTempo(start+(1.f-start)*s); }
    d.render(m.data(),c.data(),480,48000);
    for(int i=0;i<480;i++){ float v=m[2*i]; float j=std::fabs(v-prev); if(b>10 && j>maxJump){maxJump=j; jumpBlock=b;} if(b>10&&j>0.05f) bigJumps++; prev=v; }
  }
  printf("start %.3f: maxJump %.4f at block %d (limit ~0.03), jumps>0.05: %d, pos %.2fs\n", start, maxJump, jumpBlock, bigJumps, d.positionSeconds());
}
