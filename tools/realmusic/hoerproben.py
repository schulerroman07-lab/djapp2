#!/usr/bin/env python3
"""
Hörproben: 20 Übergänge (je ca. 40 s um den Einsatz) als MP3, aus der Messung mit der echten Engine
(mixrender), gemischt über Genres und Übergangsarten.

Lizenz: Ein Übergang ist eine Bearbeitung beider Songs. Darum nur Paare, bei denen BEIDE Songs Bearbeitungen
erlauben und sich die Bedingungen vertragen: CC0, CC-BY oder CC-BY-NC (nicht "ND" = keine Bearbeitung, nicht
"SA" = Weitergabe unter gleicher Lizenz, nicht "Sampling+"). Quellen und Lizenzen stehen in QUELLEN.md.

Aufruf: python3 hoerproben.py <mix-Ordner> <render-Ordner> <Ausgabe-Ordner> [--n 20]
"""
import json, os, subprocess, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
RATE = 48000


def remix_ok(lic):
    u = (lic or "").lower()
    return ("/licenses/by/" in u or "/licenses/by-nc/" in u or "publicdomain/zero" in u) and "sampling" not in u


def read_mix(path):
    kv = {}
    for line in open(path, encoding="utf-8"):
        if line.startswith(("pre ", "step ")):
            break
        k, _, v = line.rstrip("\n").partition(" ")
        kv[k] = v
    return kv


def main():
    mixdir, rdir, out = sys.argv[1:4]
    want = int(sys.argv[sys.argv.index("--n") + 1]) if "--n" in sys.argv else 20
    songs = {s["id"]: s for s in json.load(open(os.path.join(HERE, "songs.json"), encoding="utf-8"))}
    rows = [r for r in json.load(open(os.path.join(rdir, "eval.json"))) if "error" not in r and not r.get("early")]
    cand = [r for r in rows if remix_ok(songs[r["a"]]["license"]) and remix_ok(songs[r["b"]]["license"])]
    print(f"Übergänge mit Lizenz für Bearbeitungen: {len(cand)} von {len(rows)}")
    # Auswahl: reihum nach Übergangsart, dabei möglichst viele Genre-Paare und jeder Song höchstens zweimal
    by_type = {}
    for r in cand:
        by_type.setdefault(r["type"], []).append(r)
    pick, used, pairs = [], {}, set()
    while len(pick) < want and any(by_type.values()):
        for t in sorted(by_type, key=lambda t: len([p for p in pick if p["type"] == t])):
            lst = by_type[t]
            best = None
            for r in lst:
                g = (songs[r["a"]]["genre"], songs[r["b"]]["genre"])
                if used.get(r["a"], 0) >= 2 or used.get(r["b"], 0) >= 2 or (r["a"], r["b"]) in {(p["a"], p["b"]) for p in pick}:
                    continue
                if best is None or (g not in pairs and best[1] in pairs):
                    best = (r, g)
            if best is None:
                by_type[t] = []
                continue
            r, g = best
            lst.remove(r)
            pick.append(r); pairs.add(g)
            used[r["a"]] = used.get(r["a"], 0) + 1; used[r["b"]] = used.get(r["b"], 0) + 1
            if len(pick) >= want:
                break
    os.makedirs(out, exist_ok=True)
    lines = ["# Hörproben: Übergänge der App mit echter Musik", "",
             "Jede Datei: ca. 40 s rund um den Übergang, abgespielt mit der echten Audio-Engine der App (am PC, ohne Handy).",
             "Erzeugt mit `tools/realmusic/hoerproben.py` aus der objektiven Messung (siehe `tools/realmusic/BERICHT.md`).", "",
             "**Lizenz:** Die Hörproben sind Bearbeitungen (Mischungen) der unten genannten Songs. Verwendet wurden nur Songs",
             "unter CC0, CC-BY oder CC-BY-NC. Die Hörproben dürfen deshalb nur **nicht-kommerziell** und **mit Namensnennung**",
             "der Originale weitergegeben werden (CC BY-NC 4.0). Die Songs wurden für die Übergänge geschnitten, im Tempo",
             "angepasst, gefiltert und mit Effekten versehen.", "",
             "| Datei | Übergang | Von (A) | Nach (B) |", "|---|---|---|---|"]
    srcs = {}
    for k, r in enumerate(pick, 1):
        kv = read_mix(os.path.join(mixdir, f"{r['n']:03d}.mix"))
        mix = np.fromfile(os.path.join(rdir, f"{r['n']:03d}.mix.f32"), dtype=np.float32).reshape(-1, 2)
        t0 = r["t_start"]
        s0 = max(0, int((t0 - 20.0) * RATE)); s1 = min(len(mix), int((t0 + 20.0) * RATE))
        seg = mix[s0:s1].copy()
        # 0,5 s ein- und ausblenden (nur am Rand der Hörprobe, nicht am Übergang)
        f = int(0.5 * RATE)
        ramp = np.linspace(0, 1, f, dtype=np.float32)[:, None]
        seg[:f] *= ramp; seg[-f:] *= ramp[::-1]
        A, B = songs[r["a"]], songs[r["b"]]
        name = f"{k:02d}_{r['type'].lower()}_{A['genre'].lower()}-{B['genre'].lower()}.mp3".replace("/", "-")
        raw = os.path.join(out, name + ".f32")
        seg.astype(np.float32).tofile(raw)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "2", "-i", raw,
                        "-codec:a", "libmp3lame", "-b:a", "128k",
                        "-metadata", f"title=Übergang {r['type']}: {A['title']} → {B['title']}",
                        "-metadata", f"comment=CC BY-NC 4.0 – Bearbeitung von: {A['artist']} – {A['title']} ({A['license']}); "
                                     f"{B['artist']} – {B['title']} ({B['license']})",
                        os.path.join(out, name)], check=True)
        os.remove(raw)
        why = kv.get("reason", "").split(" · ")
        lines.append(f"| `{name}` | {r['type']} – Einsatz nach 20 s; {why[-2] if len(why) > 1 else ''} | "
                     f"{A['artist']} – {A['title']} ({A['genre']}) | {B['artist']} – {B['title']} ({B['genre']}) |")
        srcs[A["id"]] = A; srcs[B["id"]] = B
        print(name, r["a"], "→", r["b"], flush=True)
    lines += ["", "## Quellen und Lizenzen der Originale", "", "| Song | Interpret | Lizenz | Quelle |", "|---|---|---|---|"]
    for s in sorted(srcs.values(), key=lambda s: s["id"]):
        lines.append(f"| {s['title']} | {s['artist']} | {s['license']} | {s['page']} |")
    open(os.path.join(out, "QUELLEN.md"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(f"{len(pick)} Hörproben in {out}")


if __name__ == "__main__":
    main()
