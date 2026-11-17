#!/usr/bin/env python3
"""
Trainiert das Gesangs-Netz der App (kleines Faltungsnetz auf dem Mel-Spektrum, Mitte + Seite) und schreibt es
als C++-Datei app/src/main/cpp/VocalNet.h. Die App rechnet das Netz in Analyzer.cpp (vocalNet) genau gleich.

Lern-Daten: NUR die Übungs-Songs (songs_dev.json) – Eingang aus dem App-Analysator (analyze_cli --mel),
Vorlage = hörbarer Gesang laut Referenz (Demucs-Gesangsspur, Regel in refs.py).
Die Test-Songs (songs.json) werden nur zur Kontrolle gemessen, nie zum Lernen benutzt.

Aufruf: python3 train_vocal_net.py <analyze_cli> <audio_dev-Ordner> <audio_test-Ordner> [--epochs N] [--no-export]
Braucht: numpy, torch (CPU reicht, ein paar Minuten).
"""
import json, os, struct, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
import numpy as np
import torch
import torch.nn as nn

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import refs as R

RATE, HOP, MELBLOCK, NFFT = 44100, 256, 4, 1024
POOL = 8                    # Ausgabe-Schritt des Netzes = 8 Mel-Rahmen
GRID = 0.25                 # Raster der App (StructureAnalyzer.VOCAL_HOP)
C1, C2, C3, C4, K4 = 8, 16, 16, 32, 9
NMEL = 40


def mel_input(cli, mp3, cache):
    """Netz-Eingang eines Songs [Rahmen, 2·40] aus dem Cache oder neu mit analyze_cli berechnet."""
    if not os.path.exists(cache):
        dec = subprocess.Popen(["ffmpeg", "-v", "error", "-i", mp3, "-f", "s16le", "-ar", "44100", "-ac", "2", "-"],
                               stdout=subprocess.PIPE)
        subprocess.run([cli, "-", "--mel", cache], stdin=dec.stdout, stdout=subprocess.DEVNULL, check=True)
        dec.wait()
    d = open(cache, "rb").read()
    hop, dim = struct.unpack("di", d[:12])
    return np.frombuffer(d[12:], dtype=np.float32).reshape(-1, dim)


def out_times(n):
    """Zeit (s) der Ausgabe-Schritte – wie TrackAnalyzer::vocalNet."""
    i = np.arange(n)
    return ((POOL * i + 3.5) * MELBLOCK + 1.5) * HOP / RATE + (NFFT / 2) / RATE


def seg_labels(segs, t):
    y = np.zeros(len(t), np.float32)
    for a, b in segs:
        y[(t >= a) & (t < b)] = 1
    return y


def load_set(cli, listfile, audio_dir, ref_dir, cache_dir):
    songs = json.load(open(os.path.join(HERE, listfile), encoding="utf-8"))
    os.makedirs(cache_dir, exist_ok=True)

    def one(s):
        rp = os.path.join(ref_dir, s["id"] + ".json")
        mp3 = os.path.join(audio_dir, s["id"] + ".mp3")
        if not (os.path.exists(rp) and os.path.exists(mp3)):
            return None
        x = mel_input(cli, mp3, os.path.join(cache_dir, s["id"] + ".mel"))
        ref = R.load(s["id"], ref_dir)
        y = seg_labels(ref["vocals"], out_times(len(x) // POOL))
        return dict(id=s["id"], genre=s.get("genre", ""), x=x, y=y, ref=ref)

    with ThreadPoolExecutor(4) as ex:
        return [r for r in ex.map(one, songs) if r]


class Net(nn.Module):
    def __init__(self):
        super().__init__()
        def block(ci, co):
            return nn.Sequential(nn.Conv2d(ci, co, 3, padding=1), nn.BatchNorm2d(co), nn.ReLU(), nn.MaxPool2d(2))
        self.b1, self.b2, self.b3 = block(2, C1), block(C1, C2), block(C2, C3)
        self.c4 = nn.Conv2d(C3, C4, (NMEL // 8, K4), padding=(0, K4 // 2))
        self.bn4 = nn.BatchNorm2d(C4)
        self.drop = nn.Dropout(0.3)
        self.c5 = nn.Conv2d(C4, 1, 1)

    def forward(self, x):            # x [B, 2, 40, T] (normiert) → Logits [B, T/8]
        h = self.b3(self.b2(self.b1(x)))
        h = self.drop(torch.relu(self.bn4(self.c4(h))))
        return self.c5(h)[:, 0, 0, :]


def to_tensor(x, mu, sd):
    """[Rahmen, 80] → [2, 40, Rahmen], normiert."""
    z = (x - mu) / sd
    return torch.from_numpy(np.ascontiguousarray(z.reshape(-1, 2, NMEL).transpose(1, 2, 0)))


def predict(net, song, mu, sd):
    net.eval()
    with torch.no_grad():
        z = net(to_tensor(song["x"], mu, sd)[None])[0]
    return torch.sigmoid(z).numpy()


def to_grid(p, n_frames):
    """Wie TrackAnalyzer::vocalProb: auf das 0,25-s-Raster interpolieren."""
    dur = n_frames * MELBLOCK * HOP / RATE
    t = out_times(len(p))
    g = np.arange(int(dur / GRID) + 1) * GRID
    return np.interp(g, t, p)


def segments(p, thr):
    """Wie StructureAnalyzer.vocalSegments (Median über 5, Lücken < 1 s schliessen, < 0,5 s weglassen)."""
    n = len(p)
    pad = np.pad(p, 2, mode="edge")
    med = np.array([np.median(pad[i:i + 5]) for i in range(n)])
    segs, start = [], -1
    for i in range(n + 1):
        on = i < n and med[i] >= thr
        if on and start < 0: start = i
        if not on and start >= 0: segs.append([start * GRID, i * GRID]); start = -1
    merged = []
    for s in segs:
        if merged and s[0] - merged[-1][1] < 1.0: merged[-1][1] = s[1]
        else: merged.append(s)
    return [s for s in merged if s[1] - s[0] >= 0.5], med


def report(name, data, net, mu, sd, thr, verbose=False):
    tp = fp = fn = tn = 0
    early, late_start, rows = [], [], []
    for s in data:
        g = to_grid(predict(net, s, mu, sd), len(s["x"]))
        segs, med = segments(g, thr)
        y = seg_labels(s["ref"]["vocals"], np.arange(len(g)) * GRID)
        pr = med >= thr
        tp += np.sum(pr & (y > 0.5)); fp += np.sum(pr & (y < 0.5)); fn += np.sum(~pr & (y > 0.5)); tn += np.sum(~pr & (y < 0.5))
        est_last = segs[-1][1] if segs else 0.0
        est_first = segs[0][0] if segs else None
        ref_last = s["ref"]["last_vocal_end"]
        ref_first = s["ref"]["vocals"][0][0] if s["ref"]["vocals"] else None
        if ref_last > 0 and est_last < ref_last - 1.0: early.append(s["id"])
        if ref_first is not None and (est_first is None or est_first > ref_first + 1.0): late_start.append(s["id"])
        rows.append((s["id"], s["genre"], ref_last, est_last, ref_first, est_first))
    acc = (tp + tn) / max(1, tp + tn + fp + fn)
    rec, prec = tp / max(1, tp + fn), tp / max(1, tp + fp)
    print(f"{name}: Genauigkeit {acc*100:.1f} %  Recall {rec*100:.1f} %  Präzision {prec*100:.1f} %  "
          f"letzter Gesang >1 s zu früh: {len(early)}/{len(data)} {early}  erster Gesang >1 s zu spät: {len(late_start)} {late_start}")
    if verbose:
        for r in rows:
            f = lambda v: "-" if v is None else f"{v:6.1f}"
            print(f"   {r[0]} {r[1]:9s} letzter ref {f(r[2])} est {f(r[3])}   erster ref {f(r[4])} est {f(r[5])}")
    return acc, early


def train(tr, epochs, va=None, seed=0, log_every=25):
    torch.manual_seed(seed)
    rng = np.random.default_rng(seed)
    X = np.concatenate([s["x"] for s in tr])
    mu, sd = X.mean(0).astype(np.float32), (X.std(0) + 1e-3).astype(np.float32)
    pos = np.concatenate([s["y"] for s in tr]).mean()
    wpos, wneg = 0.5 / pos, 0.5 / (1 - pos)
    net = Net()
    opt = torch.optim.Adam(net.parameters(), lr=2e-3, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, epochs)
    L, M = 1024, 8                    # Ausschnitt 1024 Mel-Rahmen (≈ 24 s), Rand 8 Ausgabe-Schritte ohne Wertung
    for ep in range(1, epochs + 1):
        net.train()
        crops = []
        for s in tr:
            n = len(s["x"])
            for _ in range(4):
                a = int(rng.integers(0, max(1, n - L))) // POOL * POOL
                x = s["x"][a:a + L].copy()
                if len(x) < L: x = np.pad(x, ((0, L - len(x)), (0, 0)))
                x[:, :NMEL] += rng.normal(0, 3)          # Lautstärke leicht verändern
                y = s["y"][a // POOL:a // POOL + L // POOL]
                y = np.pad(y, (0, L // POOL - len(y)))
                crops.append((x, y))
        order = rng.permutation(len(crops))
        for b in range(0, len(order), 16):
            idx = order[b:b + 16]
            xb = torch.stack([to_tensor(crops[i][0], mu, sd) for i in idx])
            yb = torch.from_numpy(np.stack([crops[i][1] for i in idx]))
            z = net(xb)
            w = (yb * wpos + (1 - yb) * wneg)
            w[:, :M] = 0; w[:, -M:] = 0
            loss = (nn.functional.binary_cross_entropy_with_logits(z, yb, reduction="none") * w).sum() / w.sum()
            opt.zero_grad(); loss.backward(); opt.step()
        sched.step()
        if va is not None and ep % log_every == 0:
            print(f"Epoche {ep}: Verlust {loss.item():.3f}")
            report("   geprüft", va, net, mu, sd, 0.5)
    return net, mu, sd


def export(net, mu, sd, path):
    net.eval()
    def fold(conv, bn):
        w, b = conv.weight.detach().numpy(), conv.bias.detach().numpy()
        g = (bn.weight / torch.sqrt(bn.running_var + bn.eps)).detach().numpy()
        return w * g[:, None, None, None], (b - bn.running_mean.detach().numpy()) * g + bn.bias.detach().numpy()
    W1, B1 = fold(net.b1[0], net.b1[1]); W2, B2 = fold(net.b2[0], net.b2[1]); W3, B3 = fold(net.b3[0], net.b3[1])
    W4, B4 = fold(net.c4, net.bn4)
    W5, B5 = net.c5.weight.detach().numpy().reshape(-1), float(net.c5.bias.item())
    # Normierung: [Rahmen, 80] = Kanal 0 (40), Kanal 1 (40) → kMean[c·40 + m]
    f = lambda a: ",".join(f"{v:.6g}f" for v in np.asarray(a, np.float32).reshape(-1))
    with open(path, "w") as o:
        o.write("// Gesangs-Netz – AUTOMATISCH ERZEUGT von tools/realmusic/train_vocal_net.py (nicht von Hand ändern).\n")
        o.write("// Faltungsnetz auf dem Mel-Spektrum (Mitte relativ zum Song + Stereo-Breite), gelernt an den\n")
        o.write("// Übungs-Songs (songs_dev.json) mit Demucs-Gesangsspuren als Vorlage. Gerechnet in Analyzer.cpp (vocalNet).\n")
        o.write("#pragma once\nnamespace dj { namespace vn {\nconstexpr bool kReady = true;\n")
        o.write(f"constexpr int kIn = 2, kF = {NMEL}, kC1 = {C1}, kC2 = {C2}, kC3 = {C3}, kC4 = {C4}, kF4 = {NMEL // 8}, kK4 = {K4};\n")
        o.write(f"static const float kMean[kIn * kF] = {{{f(mu)}}};\n")
        o.write(f"static const float kStd[kIn * kF] = {{{f(sd)}}};\n")
        for n, w, b in (("1", W1, B1), ("2", W2, B2), ("3", W3, B3), ("4", W4, B4)):
            o.write(f"static const float W{n}[{w.size}] = {{{f(w)}}};\n")
            o.write(f"static const float B{n}[{b.size}] = {{{f(b)}}};\n")
        o.write(f"static const float W5[{W5.size}] = {{{f(W5)}}};\n")
        o.write(f"static const float B5 = {B5:.6g}f;\n")
        o.write("}}  // namespace dj::vn\n")


def main():
    cli, dev_audio, test_audio = sys.argv[1], sys.argv[2], sys.argv[3]
    epochs = int(sys.argv[sys.argv.index("--epochs") + 1]) if "--epochs" in sys.argv else 150
    torch.set_num_threads(os.cpu_count() or 4)
    cache = os.path.join(os.path.dirname(os.path.abspath(cli)), "mel_cache")
    dev = load_set(cli, "songs_dev.json", dev_audio, os.path.join(HERE, "refs_dev"), cache + "/dev")
    test = load_set(cli, "songs.json", test_audio, os.path.join(HERE, "refs"), cache + "/test")
    print(f"Übungs-Songs: {len(dev)}, Test-Songs: {len(test)}")
    if "--cv" in sys.argv:
        # Kreuzprüfung innerhalb der Übungs-Songs: 80 % lernen, 20 % prüfen (nach Songs getrennt)
        order = np.random.default_rng(0).permutation(len(dev))
        cut = int(len(dev) * 0.8)
        tr, va = [dev[i] for i in order[:cut]], [dev[i] for i in order[cut:]]
        net, mu, sd = train(tr, epochs, va)
        for thr in (0.3, 0.4, 0.5):
            report(f"Übung geprüft, Schwelle {thr}", va, net, mu, sd, thr)
    net, mu, sd = train(dev, epochs)
    if "--no-export" not in sys.argv:
        export(net, mu, sd, os.path.join(HERE, "..", "..", "app", "src", "main", "cpp", "VocalNet.h"))
        print("Netz geschrieben: app/src/main/cpp/VocalNet.h")
    for thr in (0.3, 0.4, 0.5):
        report(f"Test-Songs (nie gelernt), Schwelle {thr}", test, net, mu, sd, thr, verbose=(thr == 0.4))


if __name__ == "__main__":
    main()
