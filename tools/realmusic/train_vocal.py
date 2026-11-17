#!/usr/bin/env python3
"""
Trainiert den Gesangs-Klassifikator der App (kleines neuronales Netz, 92 → 24 → 1) und schreibt ihn als
C++-Datei app/src/main/cpp/VocalModel.h.

Lern-Daten: NUR die Übungs-Songs (songs_dev.json) – Eingänge aus dem App-Analysator (analyze_cli
--vocal-features), Vorlage = hörbarer Gesang laut Referenz (Demucs-Gesangsspur, Regel in refs.py).
Die Test-Songs (songs.json) werden nur zur Kontrolle gemessen, nie zum Lernen benutzt.

Aufruf: python3 train_vocal.py <analyze_cli> <audio_dev-Ordner> <audio_test-Ordner> [--epochs N]
"""
import json, os, struct, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import refs as R

HOP = 0.25


def inputs(cli, mp3, cache):
    """Klassifikator-Eingänge eines Songs (alle 0,25 s, 92 Werte) – aus dem Cache oder neu berechnet."""
    if not os.path.exists(cache):
        dec = subprocess.Popen(["ffmpeg", "-v", "error", "-i", mp3, "-f", "s16le", "-ar", "44100", "-ac", "2", "-"], stdout=subprocess.PIPE)
        subprocess.run([cli, "-", "--vocal-features", cache], stdin=dec.stdout, stdout=subprocess.DEVNULL, check=True)
        dec.wait()
    d = open(cache, "rb").read()
    hop, dim = struct.unpack("di", d[:12])
    return np.frombuffer(d[12:], dtype=np.float32).reshape(-1, dim)


def labels(ref, n):
    """Pro 0,25-s-Schritt: 1 = hörbarer Gesang (Mitte des Schritts liegt in einem Gesangsabschnitt)."""
    y = np.zeros(n, dtype=np.float32)
    for a, b in ref["vocals"]:
        i0, i1 = int(np.ceil(a / HOP)), int(np.floor(b / HOP))
        y[max(0, i0):max(0, min(n, i1 + 1))] = 1
    return y


def load_set(cli, listfile, audio_dir, ref_dir, cache_dir):
    songs = json.load(open(os.path.join(HERE, listfile), encoding="utf-8"))
    os.makedirs(cache_dir, exist_ok=True)

    def one(s):
        rp = os.path.join(ref_dir, s["id"] + ".json")
        mp3 = os.path.join(audio_dir, s["id"] + ".mp3")
        if not (os.path.exists(rp) and os.path.exists(mp3)):
            return None
        x = inputs(cli, mp3, os.path.join(cache_dir, s["id"] + ".bin"))
        ref = R.load(s["id"], ref_dir)
        return s["id"], x, labels(ref, len(x)), ref

    with ThreadPoolExecutor(4) as ex:
        return [r for r in ex.map(one, songs) if r]


class MLP:
    def __init__(self, dim, hidden, seed=1):
        rng = np.random.default_rng(seed)
        self.W1 = rng.normal(0, 1 / np.sqrt(dim), (dim, hidden)).astype(np.float32)
        self.b1 = np.zeros(hidden, np.float32)
        self.W2 = rng.normal(0, 1 / np.sqrt(hidden), (hidden,)).astype(np.float32)
        self.b2 = np.float32(0)

    def forward(self, x):
        h = np.tanh(x @ self.W1 + self.b1)
        z = h @ self.W2 + self.b2
        return h, 1 / (1 + np.exp(-z))

    def fit(self, x, y, w, epochs=400, lr=0.01, l2=1e-4):
        params = [self.W1, self.b1, self.W2]
        m = [np.zeros_like(p) for p in params] + [0.0]
        v = [np.zeros_like(p) for p in params] + [0.0]
        for ep in range(1, epochs + 1):
            h, p = self.forward(x)
            g = (p - y) * w / w.sum()                        # gewichtete Kreuzentropie
            gW2 = h.T @ g + l2 * self.W2
            gb2 = g.sum()
            gh = np.outer(g, self.W2) * (1 - h * h)
            gW1 = x.T @ gh + l2 * self.W1
            gb1 = gh.sum(0)
            grads = [gW1, gb1, gW2, gb2]
            for i, gr in enumerate(grads):
                m[i] = 0.9 * m[i] + 0.1 * gr
                v[i] = 0.999 * v[i] + 0.001 * gr * gr
                step = lr * (m[i] / (1 - 0.9 ** ep)) / (np.sqrt(v[i] / (1 - 0.999 ** ep)) + 1e-8)
                if i == 0: self.W1 -= step
                elif i == 1: self.b1 -= step
                elif i == 2: self.W2 -= step
                else: self.b2 = np.float32(self.b2 - step)
        return self


def smooth(p, k=5):
    """Wie in der App: gleitender Median über 5 Schritte (1,25 s)."""
    pad = np.pad(p, (k // 2, k // 2), mode="edge")
    return np.array([np.median(pad[i:i + k]) for i in range(len(p))])


def report(name, data, model, mu, sd, thr):
    tp = fp = fn = tn = 0
    late_err = []
    for sid, x, y, ref in data:
        p = smooth(model.forward((x - mu) / sd)[1])
        pr = p >= thr
        tp += np.sum(pr & (y > 0.5)); fp += np.sum(pr & (y < 0.5)); fn += np.sum(~pr & (y > 0.5)); tn += np.sum(~pr & (y < 0.5))
        idx = np.where(pr)[0]
        est_last = (idx[-1] + 1) * HOP if len(idx) else 0.0
        late_err.append((sid, est_last - ref["last_vocal_end"], ref["last_vocal_end"]))
    acc = (tp + tn) / max(1, tp + tn + fp + fn)
    rec = tp / max(1, tp + fn)
    prec = tp / max(1, tp + fp)
    early = [e for e in late_err if e[1] < -1.0 and e[2] > 0]
    print(f"{name}: Genauigkeit {acc*100:.1f} %  Gesang gefunden (Recall) {rec*100:.1f} %  Treffsicherheit {prec*100:.1f} %  "
          f"letzter Gesang zu früh (>1 s) bei {len(early)} von {len(late_err)} Songs")
    return acc, rec, prec, late_err


def export(model, mu, sd, path):
    W1 = (model.W1 / sd[:, None]).astype(np.float32)                 # Normierung in die Gewichte einrechnen
    b1 = (model.b1 - (mu / sd) @ model.W1).astype(np.float32)
    dim, hid = W1.shape
    f = lambda a: ",".join(f"{v:.6g}f" for v in a)
    with open(path, "w") as o:
        o.write("// Gesangs-Klassifikator – AUTOMATISCH ERZEUGT von tools/realmusic/train_vocal.py (nicht von Hand ändern).\n")
        o.write("// Kleines neuronales Netz: %d Eingänge → %d (tanh) → 1 (Wahrscheinlichkeit für hörbaren Gesang).\n" % (dim, hid))
        o.write("// Gelernt an den Übungs-Songs (songs_dev.json) mit Demucs-Gesangsspuren als Vorlage.\n")
        o.write("#pragma once\n#include <cmath>\nnamespace dj {\n")
        o.write(f"constexpr int kVocalIn = {dim}, kVocalHidden = {hid};\n")
        o.write(f"static const float kVocalW1[kVocalIn * kVocalHidden] = {{{f(W1.reshape(-1))}}};\n")
        o.write(f"static const float kVocalB1[kVocalHidden] = {{{f(b1)}}};\n")
        o.write(f"static const float kVocalW2[kVocalHidden] = {{{f(model.W2)}}};\n")
        o.write(f"static const float kVocalB2 = {float(model.b2):.6g}f;\n")
        o.write("inline float vocalModelPredict(const float* x) {\n"
                "    float z = kVocalB2;\n"
                "    for (int j = 0; j < kVocalHidden; j++) {\n"
                "        float a = kVocalB1[j];\n"
                "        for (int i = 0; i < kVocalIn; i++) a += x[i] * kVocalW1[i * kVocalHidden + j];\n"
                "        z += std::tanh(a) * kVocalW2[j];\n"
                "    }\n"
                "    return 1.f / (1.f + std::exp(-z));\n"
                "}\n}  // namespace dj\n")


def main():
    cli, dev_audio, test_audio = sys.argv[1], sys.argv[2], sys.argv[3]
    epochs = int(sys.argv[sys.argv.index("--epochs") + 1]) if "--epochs" in sys.argv else 600
    cache = os.path.join(os.path.dirname(os.path.abspath(cli)), "vocal_cache")
    dev = load_set(cli, "songs_dev.json", dev_audio, os.path.join(HERE, "refs_dev"), cache + "/dev")
    test = load_set(cli, "songs.json", test_audio, os.path.join(HERE, "refs"), cache + "/test")
    print(f"Übungs-Songs: {len(dev)}, Test-Songs: {len(test)}")
    # Kreuzprüfung innerhalb der Übungs-Songs: 80 % lernen, 20 % prüfen (nach Songs getrennt)
    rng = np.random.default_rng(0)
    order = rng.permutation(len(dev))
    cut = int(len(dev) * 0.8)
    tr, va = [dev[i] for i in order[:cut]], [dev[i] for i in order[cut:]]
    X = np.concatenate([d[1] for d in tr]); Y = np.concatenate([d[2] for d in tr])
    mu, sd = X.mean(0), X.std(0) + 1e-3
    # Klassen ausgleichen
    w = np.where(Y > 0.5, 0.5 / max(1e-9, Y.mean()), 0.5 / max(1e-9, 1 - Y.mean())).astype(np.float32)
    model = MLP(X.shape[1], 24).fit((X - mu) / sd, Y, w, epochs=epochs)
    for thr in (0.3, 0.4, 0.5):
        print(f"-- Schwelle {thr}")
        report("  Übung (gelernt)", tr, model, mu, sd, thr)
        report("  Übung (geprüft)", va, model, mu, sd, thr)
    # Endgültiges Modell mit allen Übungs-Songs
    X = np.concatenate([d[1] for d in dev]); Y = np.concatenate([d[2] for d in dev])
    mu, sd = X.mean(0), X.std(0) + 1e-3
    w = np.where(Y > 0.5, 0.5 / max(1e-9, Y.mean()), 0.5 / max(1e-9, 1 - Y.mean())).astype(np.float32)
    model = MLP(X.shape[1], 24).fit((X - mu) / sd, Y, w, epochs=epochs)
    export(model, mu, sd, os.path.join(HERE, "..", "..", "app", "src", "main", "cpp", "VocalModel.h"))
    print("Modell geschrieben: app/src/main/cpp/VocalModel.h")
    for thr in (0.3, 0.4, 0.5):
        report(f"Test-Songs (nie gelernt), Schwelle {thr}", test, model, mu, sd, thr)


if __name__ == "__main__":
    main()
