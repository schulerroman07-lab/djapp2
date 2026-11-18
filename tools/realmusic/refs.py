#!/usr/bin/env python3
"""
Referenz-Daten laden und daraus ableiten, was die Messungen brauchen:
  - Beats / Downbeats (aus der Referenz-Software, Zeiten über die Nachbar-Beats verfeinert)
  - Zuverlässigkeit: Beat This! und madmom (zwei unabhängige Programme) stimmen überein
  - Gesangs-Abschnitte: Gesang ist HÖRBAR, wenn die Gesangsspur (Demucs) höchstens 15 dB leiser ist als
      der ganze Song an derselben Stelle (Haupt- und Chorgesang liegen typisch 2–10 dB darunter,
      Übersprechen von Instrumenten in die Gesangsspur 20–60 dB) und lauter als −45 dBFS.
      Lücken < 0,5 s werden geschlossen, Abschnitte < 0,4 s verworfen.
  - Ende des letzten Gesangs, hörbares Songende, Beginn des Fade-outs (aus der Lautstärke des ganzen Songs)
Diese Schwellen sind hier an EINER Stelle festgelegt und gelten für alle Messungen.
"""
import glob, json, os
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
VOCAL_ABS_DB = -45.0      # Gesangsspur muss mindestens so laut sein (dBFS)
VOCAL_REL_DB = 15.0       # und höchstens so viel leiser als der ganze Song an derselben Stelle (dB)
VOCAL_MAX_OVER_MIX_DB = 3.0   # lauter als der ganze Song kann die Stimme nicht sein: dort ist es Rauschen der
                              # Stimm-Trennung (z. B. im verklingenden Schluss, wenn der Song schon fast still ist)
GAP_FILL = 0.5
MIN_SEG = 0.4
FULL_PHRASE_SEC = 2.0     # "volle Gesangsphrase": zusammenhängender Gesang von mindestens dieser Länge (kürzer: Einwurf,
                          # einzelnes Wort, Ad-lib, Vocal-Sample)

# Song-Listen: Name → (Liste, Referenz-Ordner)
#   test  = 56 Test-Songs (nur zum Messen, nie zum Lernen oder Abstimmen)
#   dev   = Übungs-Songs (Abstimmen; zum Lernen nur die mit erlaubter Lizenz)
#   train = zusätzliche Lern-Songs, ausschliesslich CC0 / CC-BY
LISTS = {"test": ("songs.json", "refs"), "dev": ("songs_dev.json", "refs_dev"), "train": ("songs_train.json", "refs_train")}


def list_name(argv, default="test"):
    return argv[argv.index("--list") + 1] if "--list" in argv else default


def list_paths(name):
    lst, ref = LISTS[name]
    return os.path.join(HERE, lst), os.path.join(HERE, ref)


def training_allowed(license_url):
    """Darf ein Song zum LERNEN der Netze in der App benutzt werden? Nur CC0, CC-BY (jede Version, ohne NC/ND/SA)
    und Public Domain – siehe LIZENZEN_TRAINING.md."""
    u = (license_url or "").lower()
    return ("creativecommons.org/licenses/by/" in u or "creativecommons.org/publicdomain/zero/" in u
            or "creativecommons.org/publicdomain/mark/" in u)


def training_songs(lists=("dev", "train")):
    """Alle Songs mit erlaubter Lizenz aus den Listen, je mit Referenz-Ordner: [(song, ref_dir, liste), ...]."""
    out = []
    for name in lists:
        lst, ref = list_paths(name)
        if not os.path.exists(lst):
            continue
        for s in json.load(open(lst, encoding="utf-8")):
            if training_allowed(s.get("license")):
                out.append((s, ref, name))
    return out


def load(song_id, folder=None):
    folder = folder or os.path.join(HERE, "refs")
    with open(os.path.join(folder, song_id + ".json")) as f:
        return derive(json.load(f))


def load_all(folder=None):
    folder = folder or os.path.join(HERE, "refs")
    return {os.path.basename(p)[:-5]: load(os.path.basename(p)[:-5], folder) for p in sorted(glob.glob(os.path.join(folder, "*.json")))}


def segments(mask, hop):
    segs, start = [], None
    for i, m in enumerate(list(mask) + [False]):
        if m and start is None:
            start = i
        elif not m and start is not None:
            segs.append([start * hop, i * hop])
            start = None
    return segs


def refine_beats(beats, win=8, max_res=0.015):
    """
    Beat This! gibt Zeiten auf einem 20-ms-Raster aus (madmom 10 ms). Für die ±30-ms-Messung wird jede
    Beat-Zeit durch eine Gerade durch ihre Nachbarn (±win Beats) ersetzt – so folgt sie langsamen
    Tempo-Änderungen, ohne die Raster-Stufen. Nur wo die Nachbarn gut auf der Geraden liegen
    (Abweichung ≤ 15 ms), sonst bleibt der Originalwert.
    """
    b = np.asarray(beats, dtype=float)
    out = b.copy()
    for i in range(len(b)):
        a, e = max(0, i - win), min(len(b), i + win + 1)
        if e - a < 7:
            continue
        x = np.arange(a, e)
        y = b[a:e]
        k, d = np.polyfit(x, y, 1)
        res = y - (k * x + d)
        if np.max(np.abs(res)) <= max_res:
            out[i] = k * i + d
    return [round(float(v), 4) for v in out]


def derive(ref):
    hop = ref["hop"]
    ref["beats_raw"] = ref["beats"]
    ref["beats"] = refine_beats(ref["beats"])
    # Downbeats: auf die verfeinerten Beats setzen (nächster Beat)
    bb = np.asarray(ref["beats"])
    if len(bb):
        ref["downbeats"] = [float(bb[int(np.argmin(np.abs(bb - d)))]) for d in ref["downbeats"]]
    v = np.array(ref["vocal_db"], dtype=float)
    mix = np.array(ref["mix_db"], dtype=float)
    # leichte Glättung (0,3 s), damit einzelne Konsonanten/Lücken nicht zählen
    k = np.ones(3) / 3
    vs = 10 * np.log10(np.convolve(10 ** (v / 10), k, mode="same") + 1e-12)
    ms = 10 * np.log10(np.convolve(10 ** (mix / 10), k, mode="same") + 1e-12)
    mask = (vs > VOCAL_ABS_DB) & (vs > ms - VOCAL_REL_DB) & (vs <= ms + VOCAL_MAX_OVER_MIX_DB)
    segs = segments(mask, hop)
    merged = []
    for s in segs:
        if merged and s[0] - merged[-1][1] < GAP_FILL:
            merged[-1][1] = s[1]
        else:
            merged.append(s)
    merged = [s for s in merged if s[1] - s[0] >= MIN_SEG]
    ref["vocals"] = [[round(a, 2), round(b, 2)] for a, b in merged]
    ref["vocal_ratio"] = round(sum(b - a for a, b in merged) / max(1e-9, ref["duration"]), 3)
    ref["last_vocal_end"] = merged[-1][1] if merged else 0.0
    full = [s for s in merged if s[1] - s[0] >= FULL_PHRASE_SEC]
    ref["last_full_phrase_end"] = full[-1][1] if full else 0.0
    ref["first_vocal_start"] = merged[0][0] if merged else None
    # hörbares Ende und Fade-out aus der Lautstärke des ganzen Songs
    body = float(np.percentile(mix, 80))                     # typische Lautstärke im Hauptteil
    loud = np.where(mix > body - 45)[0]
    ref["sound_end"] = round(float((loud[-1] + 1) * hop), 2) if len(loud) else ref["duration"]
    sm = np.convolve(mix, np.ones(10) / 10, mode="same")     # 1 s geglättet
    end_i = int(ref["sound_end"] / hop)
    # Fade-out: letzter Zeitpunkt, an dem der Song noch "voll" (≤ 4 dB unter Hauptteil) war –
    # wenn danach mindestens 4 s vergehen, in denen es nur noch leiser wird
    full = np.where(sm[:end_i] > body - 4)[0]
    fade_start = float((full[-1] + 1) * hop) if len(full) else ref["sound_end"]
    ref["fade_out"] = round(fade_start, 2) if ref["sound_end"] - fade_start >= 4.0 else None
    ref["body_db"] = round(body, 1)
    return ref


def vocal_active(ref, t):
    return any(a <= t < b for a, b in ref["vocals"])


if __name__ == "__main__":
    for sid, r in load_all().items():
        print(sid, f"{r['duration']:6.1f}s beats={len(r['beats']):4d} down={len(r['downbeats']):3d} "
              f"Gesang={r['vocal_ratio']*100:4.0f}% letzter Gesang bis {r['last_vocal_end']:6.1f} Ende {r['sound_end']:6.1f} Fade {r['fade_out']}")


def agreement(ref, tol=0.07):
    """Übereinstimmung der beiden Referenz-Programme (F-Mass, Beats und Downbeats)."""
    from eval_beats import fmeasure
    mm, mmd = ref.get("beats_madmom") or [], ref.get("downbeats_madmom") or []
    return fmeasure(ref["beats_raw"], mm, tol)[0], fmeasure(ref["downbeats"], mmd, tol)[0]


def reliable(ref):
    """Beat-Referenz gilt als zuverlässig, wenn beide Programme bei ±70 ms zu ≥ 90 % übereinstimmen."""
    return agreement(ref)[0] >= 0.90


def reliable_downbeats(ref):
    """Downbeat-Referenz zuverlässig: Beats zuverlässig und Downbeats beider Programme ≥ 80 % gleich."""
    fb, fd = agreement(ref)
    return fb >= 0.90 and fd >= 0.80
