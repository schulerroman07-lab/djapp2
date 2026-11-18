#!/usr/bin/env python3
"""
Erzeugt die Lizenzliste aller Songs, die für die Netze der App (Gesangs- und Beat-Erkennung) und für die Messung
benutzt werden: LIZENZEN_TRAINING.md im Projektordner. Die Regel, welche Songs zum LERNEN benutzt werden dürfen,
steht an einer Stelle: refs.training_allowed() (nur CC0, CC-BY, Public Domain).

Aufruf: python3 lizenzen.py [Zusatztext.md]   (Zusatztext = Vergleich der Messwerte, wird unten angehängt)
"""
import collections, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import refs as R

NAMES = {"test": "Test-Songs (nur Messung, nie gelernt)", "dev": "Übungs-Songs (Abstimmen; Lernen nur mit erlaubter Lizenz)",
         "train": "Lern-Songs (nur CC0/CC-BY)"}


def short(lic):
    u = (lic or "").strip().lower()
    if not u or u == "?":
        return "unklar"
    if "publicdomain/zero" in u:
        return "CC0 1.0"
    if "publicdomain/mark" in u:
        return "Public Domain"
    m = re.search(r"licenses/([a-z+\-]+)/([0-9.]+)", u)
    if not m:
        return lic
    kind = {"by": "CC BY", "by-sa": "CC BY-SA", "by-nc": "CC BY-NC", "by-nd": "CC BY-ND", "by-nc-sa": "CC BY-NC-SA",
            "by-nc-nd": "CC BY-NC-ND", "sampling+": "Sampling+", "nc-sampling+": "NC Sampling+"}.get(m.group(1), m.group(1))
    return f"{kind} {m.group(2)}"


def main():
    out = [
        "# Lizenzen der Songs für Training und Messung", "",
        "Die Netze in der App (Gesangserkennung `VocalNet.h`, Beat-Erkennung `BeatNet.h`) sind **nur** mit Songs",
        "unter **CC0, CC BY oder Public Domain** gelernt. Songs mit NC (nicht-kommerziell), ND (keine Bearbeitung),",
        "SA (Weitergabe unter gleicher Lizenz), Sampling+ oder unklarer Lizenz werden nie zum Lernen benutzt.",
        "Die Regel steht an einer Stelle im Code: `tools/realmusic/refs.py`, Funktion `training_allowed()`;",
        "die Lern-Skripte (`train_vocal_net.py`, `train_beat_net.py`) benutzen nur diese Songs.", "",
        "Die Audiodateien liegen **nicht** im Projekt – nur Titel, Interpret, Lizenz, Quelle und die daraus",
        "berechneten Referenzen (Beat-Zeiten, Lautstärke-Hüllkurven).", "",
        "- **Lern-Songs** (`songs_train.json`): eigens ausgewählt, alle CC BY oder CC0 (ccMixter).",
        "- **Übungs-Songs** (`songs_dev.json`): zum Lernen nur die mit erlaubter Lizenz. Die übrigen dienen nur zum",
        "  Prüfen und Abstimmen einzelner Grenzwerte (z. B. ab welcher Wahrscheinlichkeit Gesang zählt) – sie werden",
        "  analysiert und gemessen, aber nicht in die Netze gelernt.",
        "- **Test-Songs** (`songs.json`): nur zum Messen der Ergebnisse (Bericht), nie zum Lernen oder Abstimmen.", "",
    ]
    total_learn = 0
    for name in ("train", "dev", "test"):
        lst, _ = R.list_paths(name)
        songs = json.load(open(lst, encoding="utf-8"))
        cnt = collections.Counter(short(s.get("license")) for s in songs)
        learn = [s for s in songs if name != "test" and R.training_allowed(s.get("license"))]
        total_learn += len(learn)
        out += [f"## {NAMES[name]}: {len(songs)} Songs, davon {len(learn)} zum Lernen", "",
                "Lizenzen: " + ", ".join(f"{k} ({v})" for k, v in sorted(cnt.items(), key=lambda x: -x[1])), "",
                "| ID | Titel | Interpret | Genre | Lizenz | Lernen | Quelle |", "|---|---|---|---|---|---|---|"]
        for s in songs:
            ok = "ja" if (name != "test" and R.training_allowed(s.get("license"))) else "nein"
            title = s["title"].replace("|", "/")
            artist = s["artist"].replace("|", "/")
            out.append(f"| {s['id']} | {title} | {artist} | {s['genre']} | {short(s.get('license'))} | {ok} | {s['page']} |")
        out.append("")
    out.insert(17, f"**Zusammen {total_learn} Songs zum Lernen der App-Netze** (alle CC0/CC BY/PD).\n")
    if len(sys.argv) > 1:
        out += ["", open(sys.argv[1], encoding="utf-8").read()]
    open(os.path.join(HERE, "..", "..", "LIZENZEN_TRAINING.md"), "w", encoding="utf-8").write("\n".join(out) + "\n")
    print("LIZENZEN_TRAINING.md geschrieben, Lern-Songs:", total_learn)


if __name__ == "__main__":
    main()
