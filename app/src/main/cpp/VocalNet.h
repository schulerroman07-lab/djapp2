// Gesangs-Netz – Platzhalter (noch nicht trainiert). Wird von tools/realmusic/train_vocal_net.py überschrieben.
#pragma once
namespace dj { namespace vn {
constexpr bool kReady = false;
constexpr int kIn = 2, kF = 40, kC1 = 8, kC2 = 16, kC3 = 16, kC4 = 32, kF4 = 5, kK4 = 9;
static const float kMean[kIn * kF] = {}, kStd[kIn * kF] = {};
static const float W1[kC1 * kIn * 9] = {}, B1[kC1] = {};
static const float W2[kC2 * kC1 * 9] = {}, B2[kC2] = {};
static const float W3[kC3 * kC2 * 9] = {}, B3[kC3] = {};
static const float W4[kC4 * kC3 * kF4 * kK4] = {}, B4[kC4] = {};
static const float W5[kC4] = {};
static const float B5 = 0.f;
}}  // namespace dj::vn
