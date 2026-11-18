#!/usr/bin/env python3
"""
Misst die Beat-Erkennung der App an den Referenzen.
  - Beat-Trefferquote: F-Mass (wie in der Musik-Forschung üblich), aber mit ±30 ms statt ±70 ms:
      Treffer = Referenz-Beat, zu dem ein erkannter Beat höchstens 30 ms entfernt liegt (jeder nur einmal),
      F = 2·Treffer / (Referenz-Beats + erkannte Beats)  → bestraft auch doppeltes/halbes Tempo.
  - Downbeat-Trefferquote: genauso für "die 1" jedes Takts.
Aufruf: python3 eval_beats.py <pcm- oder mp3-Ordner> <analyze_cli> [--list dev] [Stellgrösse=Wert ...]
"""
import json, os, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import refs as R
from fetch import songs

TOL = 0.030


def hits(ref, est, tol=TOL):
    """Anzahl Referenz-Zeiten mit einem (noch nicht verbrauchten) erkannten Wert innerhalb tol."""
    ref, est = np.asarray(ref), np.asarray(est)
    if len(ref) == 0 or len(est) == 0:
        return 0
    used = np.zeros(len(est), dtype=bool)
    n = 0
    j = 0
    for r in ref:
        while j < len(est) and est[j] < r - tol:
            j += 1
        k = j
        while k < len(est) and est[k] <= r + tol:
            if not used[k]:
                used[k] = True
                n += 1
                break
            k += 1
    return n


def fmeasure(ref, est, tol=TOL):
    h = hits(ref, est, tol)
    return 2 * h / max(1, len(ref) + len(est)), h


def analyze(cli, pcm, extra):
    """Analyse wie in der App; liegt nur die MP3 vor (gleicher Name), wird sie mit ffmpeg dekodiert."""
    if not os.path.exists(pcm) and os.path.exists(pcm[:-4] + ".mp3"):
        dec = subprocess.Popen(["ffmpeg", "-v", "error", "-i", pcm[:-4] + ".mp3", "-f", "s16le", "-ar", "44100", "-ac", "2", "-"],
                               stdout=subprocess.PIPE)
        out = subprocess.run([cli, "-"] + extra, stdin=dec.stdout, capture_output=True, text=True, check=True).stdout
        dec.wait()
    else:
        out = subprocess.run([cli, pcm] + extra, capture_output=True, text=True, check=True).stdout
    return json.loads(out)


def evaluate(argv, quiet=False):
    pcm_dir, cli = argv[1], argv[2]
    extra = [a for a in argv[3:] if "=" in a]
    lst = songs(argv)
    folder = R.list_paths(R.list_name(argv))[1]
    rows = []

    def one(s):
        path = os.path.join(folder, s["id"] + ".json")
        if not os.path.exists(path):
            return None
        ref = R.load(s["id"], folder)
        a = analyze(cli, os.path.join(pcm_dir, s["id"] + ".pcm"), extra)
        # nur Beats im hörbaren Teil des Songs werten
        end = ref["sound_end"]
        rb = [b for b in ref["beats"] if b <= end]
        eb = [b for b in a["beats"] if b <= end]
        f, h = fmeasure(rb, eb)
        rd = [b for b in ref["downbeats"] if b <= end]
        ed = [b for i, b in enumerate(a["beats"]) if i % 4 == a["downbeat"] and b <= end]
        fd, hd = fmeasure(rd, ed)
        f70, _ = fmeasure(rb, eb, 0.07)
        # mittlerer Versatz der Treffer (für die Eichung)
        offs = []
        ea = np.asarray(eb)
        for r in rb:
            if len(ea):
                k = int(np.argmin(np.abs(ea - r)))
                if abs(ea[k] - r) <= 0.07:
                    offs.append(ea[k] - r)
        ref_bpm = 60.0 / float(np.median(np.diff(rb))) if len(rb) > 2 else 0
        return dict(id=s["id"], genre=s["genre"], f=f, f70=f70, fd=fd, nref=len(rb), nest=len(eb), hits=h, ndref=len(rd), ndest=len(ed), dhits=hd,
                    bpm=a["bpm"], ref_bpm=ref_bpm, conf=a["confidence"], steady=a["steady"], off=float(np.median(offs)) if offs else 0.0,
                    title=f"{s['artist']} – {s['title']}")

    with ThreadPoolExecutor(4) as ex:
        rows = [r for r in ex.map(one, lst) if r]
    tot = lambda k: sum(r[k] for r in rows)
    F = 2 * tot("hits") / max(1, tot("nref") + tot("nest"))
    FD = 2 * tot("dhits") / max(1, tot("ndref") + tot("ndest"))
    if not quiet:
        for r in sorted(rows, key=lambda r: r["f"]):
            print(f"{r['id']} {r['genre'][:7]:7s} F={r['f']*100:5.1f}% (±70ms {r['f70']*100:5.1f}%) Down={r['fd']*100:5.1f}% bpm={r['bpm']:6.1f} ref={r['ref_bpm']:6.1f} "
                  f"Vertrauen={r['conf']:.2f} {'fest' if r['steady'] else 'frei'} Versatz={r['off']*1000:+5.1f}ms  {r['title'][:40]}")
        print(f"\nSongs: {len(rows)}   Beat-Trefferquote (±30 ms, F-Mass über alle Beats): {F*100:.1f} %   Downbeat: {FD*100:.1f} %")
        print(f"Mittel pro Song: Beat {np.mean([r['f'] for r in rows])*100:.1f} %  Downbeat {np.mean([r['fd'] for r in rows])*100:.1f} %  "
              f"Median-Versatz {np.median([r['off'] for r in rows])*1000:+.1f} ms")
        conf = [r for r in rows if r["conf"] >= 0.6]
        if conf:
            Fc = 2 * sum(r["hits"] for r in conf) / max(1, sum(r["nref"] + r["nest"] for r in conf))
            print(f"Songs mit Vertrauen ≥ 0,6: {len(conf)} von {len(rows)}, Beat-Trefferquote dort {Fc*100:.1f} %")
    return F, FD, rows


if __name__ == "__main__":
    evaluate(sys.argv)
