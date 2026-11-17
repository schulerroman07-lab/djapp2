#!/usr/bin/env python3
"""
Lädt die Test-Songs aus songs.json (frei lizenzierte Musik: Free Music Archive, ccMixter, Jamendo).
Die Audiodateien werden NICHT ins Projekt gelegt – nur Adresse, Lizenz und Referenz-Daten.
Aufruf: python3 fetch.py <Zielordner> [--list dev] [--shard i/n] [--ids s01,s02]
"""
import json, os, subprocess, sys, time

UA = "fadelux-test/1.0 (music test set for an open-source DJ app)"
HERE = os.path.dirname(os.path.abspath(__file__))


def songs(argv):
    # --list dev: Übungs-Songs (zum Abstimmen/Trainieren), sonst die Test-Songs (für die Messung)
    name = "songs_dev.json" if "--list" in argv and argv[argv.index("--list") + 1] == "dev" else "songs.json"
    all_songs = json.load(open(os.path.join(HERE, name), encoding="utf-8"))
    if "--ids" in argv:
        ids = argv[argv.index("--ids") + 1].split(",")
        all_songs = [s for s in all_songs if s["id"] in ids]
    if "--shard" in argv:
        i, n = map(int, argv[argv.index("--shard") + 1].split("/"))
        all_songs = [s for k, s in enumerate(all_songs) if k % n == i]
    return all_songs


def fetch(song, out):
    path = os.path.join(out, song["id"] + ".mp3")
    if os.path.exists(path) and os.path.getsize(path) > 500_000:
        return path
    for attempt in range(4):
        # ccMixter verlangt die Song-Seite als Herkunft (Referer)
        r = subprocess.run(["curl", "-sS", "-L", "-m", "300", "-A", UA, "-e", song["page"], "-o", path,
                            "-w", "%{http_code}", song["url"]], capture_output=True, text=True)
        if r.stdout.strip() == "200" and os.path.getsize(path) > 500_000:
            return path
        time.sleep(5 * (attempt + 1))
    raise RuntimeError(f"Download fehlgeschlagen: {song['id']} {song['url']} ({r.stdout} {r.stderr[:200]})")


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for s in songs(sys.argv):
        fetch(s, out)
        print("ok", s["id"], s["genre"], s["artist"], "-", s["title"], flush=True)
        time.sleep(1)
