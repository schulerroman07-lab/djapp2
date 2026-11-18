#!/usr/bin/env python3
"""
Trainiert das Beat-Netz der App: aus dem Mel-Spektrum (Mitte, 23 ms pro Rahmen) eine Kurve "hier ist ein
Beat" und eine Kurve "hier ist eine 1 (Downbeat)". Die App ersetzt damit die einfache Onset-Kurve: Tempo,
Beat-Raster, Phase und Downbeat werden aus dieser Kurve bestimmt (Analyzer.cpp, beatNet()).

Lern-Daten: NUR Songs mit Lizenz CC0/CC-BY/Public Domain (Übungs-Songs songs_dev.json mit erlaubter Lizenz und
Lern-Songs songs_train.json) mit Beat-/Downbeat-Referenzen (Beat This!, madmom). Nie die Test-Songs.
Songs, bei denen die zwei Referenzen sich nicht einig sind, werden nicht zum Lernen benutzt.
Die Test-Songs (songs.json) werden nie zum Lernen benutzt.

Aufruf: python3 train_beat_net.py <analyze_cli> <audio_dev-Ordner> [--train-audio <audio_train-Ordner>]
        [--epochs N] [--no-export] [--holdout] [--out Datei.h] [--all-licenses (nur Vergleich)]
Braucht: numpy, torch.
"""
import json, os, sys
import numpy as np
import torch
import torch.nn as nn

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import refs as R
import train_vocal_net as V            # gleicher Mel-Eingang (analyze_cli --mel), gleiches Rahmen-Raster

RATE, HOP, MELBLOCK, NFFT, NMEL = 44100, 256, 4, 1024, 40
C1, C2, CH, DIL, K = 8, 8, 32, (1, 2, 4, 8, 16, 32), 5
AUGMENT = "--no-augment" not in sys.argv     # Tempo-Vielfalt beim Lernen
ALL_REFS = "--all-refs" in sys.argv           # auch Songs lernen, bei denen sich die zwei Referenzen bei den Beats uneins sind


def frame_times(n):
    """Mitte jedes Mel-Rahmens (s) – wie TrackAnalyzer::beatNet."""
    m = np.arange(n)
    return (m * MELBLOCK + 1.5) * HOP / RATE + (NFFT / 2) / RATE


def targets(times, n):
    """Ziel pro Rahmen: 1 am nächsten Rahmen, 0,5 daneben (Toleranz gegen Rundung)."""
    y = np.zeros(n, np.float32)
    ft = frame_times(n)
    dt = ft[1] - ft[0]
    for t in times:
        i = int(round((t - ft[0]) / dt))
        if 0 <= i < n:
            y[i] = 1.0
            for j in (i - 1, i + 1):
                if 0 <= j < n: y[j] = max(y[j], 0.5)
    return y


class Net(nn.Module):
    def __init__(self):
        super().__init__()
        self.c1 = nn.Conv2d(1, C1, 3, padding=1); self.n1 = nn.BatchNorm2d(C1)
        self.c2 = nn.Conv2d(C1, C2, 3, padding=1); self.n2 = nn.BatchNorm2d(C2)
        self.pool = nn.MaxPool2d((3, 1))
        f = NMEL // 3 // 3                                   # 40 → 13 → 4
        self.inp = nn.Conv1d(C2 * f, CH, 1); self.ni = nn.BatchNorm1d(CH)
        self.tcn = nn.ModuleList([nn.Conv1d(CH, CH, K, padding=d * (K // 2), dilation=d) for d in DIL])
        self.tbn = nn.ModuleList([nn.BatchNorm1d(CH) for _ in DIL])
        self.drop = nn.Dropout(0.15)
        self.out = nn.Conv1d(CH, 2, 1)

    def forward(self, x):                # x [B, 1, 40, T] → Logits [B, 2, T] (Beat, Downbeat)
        h = self.pool(torch.relu(self.n1(self.c1(x))))
        h = self.pool(torch.relu(self.n2(self.c2(h))))
        h = h.flatten(1, 2)
        h = torch.relu(self.ni(self.inp(h)))
        for c, n in zip(self.tcn, self.tbn):
            h = h + self.drop(torch.relu(n(c(h))))
        return self.out(h)


def learn_songs(audio):
    """Lern-Songs: [(song, ref_dir, audio_dir)]. Standard: nur erlaubte Lizenzen (CC0/CC-BY/PD, siehe
    LIZENZEN_TRAINING.md); --all-licenses nur zum Vergleich mit den früheren Netzen."""
    if "--all-licenses" in sys.argv:
        lst, ref = R.list_paths("dev")
        return [(s, ref, audio["dev"]) for s in json.load(open(lst, encoding="utf-8"))]
    return [(s, ref, audio[name]) for s, ref, name in R.training_songs(tuple(audio))]


def load(cli, audio, cache_dir):
    os.makedirs(cache_dir, exist_ok=True)
    data = []
    for s, ref_dir, audio_dir in learn_songs(audio):
        rp, mp3 = os.path.join(ref_dir, s["id"] + ".json"), os.path.join(audio_dir, s["id"] + ".mp3")
        if not (os.path.exists(rp) and os.path.exists(mp3)):
            continue
        ref = R.load(s["id"], ref_dir)
        x = V.mel_input(cli, mp3, os.path.join(cache_dir, s["id"] + ".mel"))[:, :NMEL]
        n = len(x)
        ft = frame_times(n)
        data.append(dict(id=s["id"], genre=s.get("genre", ""), x=x, ref=ref,
                         yb=targets(ref["beats"], n), yd=targets(ref["downbeats"], n),
                         fb=(np.asarray(ref["beats"]) - ft[0]) / (ft[1] - ft[0]),
                         fd=(np.asarray(ref["downbeats"]) - ft[0]) / (ft[1] - ft[0]),
                         use_b=R.reliable(ref) or ALL_REFS, use_d=R.reliable_downbeats(ref)))
    return data


def to_tensor(x, mu, sd):
    return torch.from_numpy(np.ascontiguousarray(((x - mu) / sd).T[None]))     # [1, 40, T]


def train(tr, epochs, seed=0, log=None):
    torch.manual_seed(seed)
    rng = np.random.default_rng(seed)
    tr = [s for s in tr if s["use_b"]]
    X = np.concatenate([s["x"] for s in tr])
    mu, sd = X.mean(0).astype(np.float32), (X.std(0) + 1e-3).astype(np.float32)
    net = Net()
    opt = torch.optim.Adam(net.parameters(), lr=2e-3, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, epochs)
    L, M = 1536, 64                         # Ausschnitt ≈ 36 s, Rand ohne Wertung
    for ep in range(1, epochs + 1):
        net.train()
        crops = []
        for s in tr:
            n = len(s["x"])
            for _ in range(3):
                # Tempo-Vielfalt: Ausschnitt zufällig 15 % schneller/langsamer (Mel-Rahmen umgerechnet)
                st = float(np.exp(rng.uniform(np.log(0.85), np.log(1.15)))) if AUGMENT else 1.0
                span = L * st
                a = float(rng.uniform(0, max(1.0, n - span)))
                src = a + np.arange(L) * st
                i0 = np.clip(np.floor(src).astype(int), 0, n - 1); i1 = np.clip(i0 + 1, 0, n - 1)
                fr = (src - np.floor(src))[:, None]
                x = s["x"][i0] * (1 - fr) + s["x"][i1] * fr
                x[src >= n] = s["x"].min(0)                   # hinter dem Song-Ende: leise
                yb, yd = np.zeros(L, np.float32), np.zeros(L, np.float32)
                for pos, y in ((s["fb"], yb), (s["fd"], yd)):
                    for p_ in (pos - a) / st:
                        k = int(round(p_))
                        if 0 <= k < L:
                            y[k] = 1.0
                            for j in (k - 1, k + 1):
                                if 0 <= j < L: y[j] = max(y[j], 0.5)
                x = (x + rng.normal(0, 3)).astype(np.float32)    # Lautstärke
                crops.append((x, yb, yd, 1.0 if s["use_d"] else 0.0))
        order = rng.permutation(len(crops))
        for b in range(0, len(order), 8):
            idx = order[b:b + 8]
            xb = torch.stack([to_tensor(crops[i][0], mu, sd) for i in idx])
            yb = torch.from_numpy(np.stack([crops[i][1] for i in idx]))
            yd = torch.from_numpy(np.stack([crops[i][2] for i in idx]))
            wd = torch.tensor([crops[i][3] for i in idx])[:, None]
            z = net(xb)
            mask = torch.ones_like(yb); mask[:, :M] = 0; mask[:, -M:] = 0
            # Beats sind selten (ca. 1 von 25 Rahmen): positive Rahmen stärker gewichten
            wb = mask * (1 + 9 * (yb > 0).float())
            wdd = mask * wd * (1 + 30 * (yd > 0).float())
            lb = (nn.functional.binary_cross_entropy_with_logits(z[:, 0], yb, reduction="none") * wb).sum() / wb.sum()
            ld = (nn.functional.binary_cross_entropy_with_logits(z[:, 1], yd, reduction="none") * wdd).sum() / wdd.sum().clamp(min=1)
            loss = lb + 0.5 * ld
            opt.zero_grad(); loss.backward(); opt.step()
        sched.step()
        if log and ep % 10 == 0:
            print(f"Epoche {ep}: Verlust {loss.item():.3f}", flush=True)
    return net, mu, sd


def predict(net, x, mu, sd):
    net.eval()
    with torch.no_grad():
        return torch.sigmoid(net(to_tensor(x, mu, sd)[None])[0]).numpy()      # [2, T]


def export(net, mu, sd, path):
    net.eval()
    def fold(conv, bn):
        w, b = conv.weight.detach().numpy(), conv.bias.detach().numpy()
        g = (bn.weight / torch.sqrt(bn.running_var + bn.eps)).detach().numpy()
        shape = (-1,) + (1,) * (w.ndim - 1)
        return w * g.reshape(shape), (b - bn.running_mean.detach().numpy()) * g + bn.bias.detach().numpy()
    f = lambda a: ",".join(f"{v:.6g}f" for v in np.asarray(a, np.float32).reshape(-1))
    W1, B1 = fold(net.c1, net.n1); W2, B2 = fold(net.c2, net.n2); Wi, Bi = fold(net.inp, net.ni)
    with open(path, "w") as o:
        o.write("// Beat-Netz – AUTOMATISCH ERZEUGT von tools/realmusic/train_beat_net.py (nicht von Hand ändern).\n")
        o.write("// Mel-Spektrum (Mitte) → Beat- und Downbeat-Kurve pro 23-ms-Rahmen. Gelernt an den Übungs-Songs\n")
        o.write("// (nur CC0/CC-BY/PD: songs_dev.json + songs_train.json) mit Beat This!/madmom-Referenzen. Gerechnet in Analyzer.cpp (beatNet).\n")
        o.write("#pragma once\nnamespace dj { namespace bn {\nconstexpr bool kReady = true;\n")
        o.write(f"constexpr int kF = {NMEL}, kC1 = {C1}, kC2 = {C2}, kCh = {CH}, kK = {K}, kLayers = {len(DIL)};\n")
        o.write(f"static const int kDil[kLayers] = {{{','.join(str(d) for d in DIL)}}};\n")
        o.write(f"static const float kMean[kF] = {{{f(mu)}}};\n")
        o.write(f"static const float kStd[kF] = {{{f(sd)}}};\n")
        o.write(f"static const float W1[{W1.size}] = {{{f(W1)}}};\nstatic const float B1[{B1.size}] = {{{f(B1)}}};\n")
        o.write(f"static const float W2[{W2.size}] = {{{f(W2)}}};\nstatic const float B2[{B2.size}] = {{{f(B2)}}};\n")
        o.write(f"static const float Wi[{Wi.size}] = {{{f(Wi)}}};\nstatic const float Bi[{Bi.size}] = {{{f(Bi)}}};\n")
        wt, bt = [], []
        for c, n in zip(net.tcn, net.tbn):
            w, b = fold(c, n); wt.append(w.reshape(-1)); bt.append(b)
        o.write(f"static const float Wt[{sum(w.size for w in wt)}] = {{{f(np.concatenate(wt))}}};\n")
        o.write(f"static const float Bt[{sum(b.size for b in bt)}] = {{{f(np.concatenate(bt))}}};\n")
        o.write(f"static const float Wo[{net.out.weight.numel()}] = {{{f(net.out.weight.detach().numpy())}}};\n")
        o.write(f"static const float Bo[2] = {{{f(net.out.bias.detach().numpy())}}};\n")
        o.write("}}  // namespace dj::bn\n")


def main():
    cli, dev_audio = sys.argv[1], sys.argv[2]
    epochs = int(sys.argv[sys.argv.index("--epochs") + 1]) if "--epochs" in sys.argv else 60
    torch.set_num_threads(os.cpu_count() or 4)
    cache = os.path.join(os.path.dirname(os.path.abspath(cli)), "mel_cache", "dev")
    audio = {"dev": dev_audio}
    if "--train-audio" in sys.argv:
        audio["train"] = sys.argv[sys.argv.index("--train-audio") + 1]
    dev = load(cli, audio, cache)
    print(f"Übungs-Songs: {len(dev)} (Beats sicher: {sum(s['use_b'] for s in dev)}, Downbeats sicher: {sum(s['use_d'] for s in dev)})")
    if "--holdout" in sys.argv:
        # 20 % der Übungs-Songs zurückhalten (zum ehrlichen Prüfen der Nachverarbeitung in der App)
        order = np.random.default_rng(3).permutation(len(dev))
        held = {dev[i]["id"] for i in order[: len(dev) // 5]}
        print("zurückgehalten:", ",".join(sorted(held)))
        dev = [s for s in dev if s["id"] not in held]
    net, mu, sd = train(dev, epochs, log=True)
    out = sys.argv[sys.argv.index("--out") + 1] if "--out" in sys.argv else \
        os.path.join(HERE, "..", "..", "app", "src", "main", "cpp", "BeatNet.h")
    if "--no-export" not in sys.argv:
        export(net, mu, sd, out)
        torch.save(dict(state=net.state_dict(), mu=mu, sd=sd), os.path.join(os.path.dirname(os.path.abspath(cli)), "beatnet.pt"))
        print("Netz geschrieben:", out)


if __name__ == "__main__":
    main()
