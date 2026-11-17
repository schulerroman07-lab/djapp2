// Beat-Netz – Platzhalter (noch nicht trainiert). Wird von tools/realmusic/train_beat_net.py überschrieben.
#pragma once
namespace dj { namespace bn {
constexpr bool kReady = false;
constexpr int kF = 40, kC1 = 8, kC2 = 8, kCh = 32, kK = 5, kLayers = 6;
static const int kDil[kLayers] = {1, 2, 4, 8, 16, 32};
static const float kMean[kF] = {}, kStd[kF] = {};
static const float W1[kC1 * 9] = {}, B1[kC1] = {};
static const float W2[kC2 * kC1 * 9] = {}, B2[kC2] = {};
static const float Wi[kCh * kC2 * 4] = {}, Bi[kCh] = {};
static const float Wt[kLayers * kCh * kCh * kK] = {}, Bt[kLayers * kCh] = {};
static const float Wo[2 * kCh] = {}, Bo[2] = {};
}}  // namespace dj::bn
