#!/usr/bin/env python3
"""
Tonart-Erkennung an echten Aufnahmen prüfen (läuft auf GitHub, braucht Internet):
gemeinfreie Aufnahmen von Wikimedia Commons, deren Titel die Tonart nennt ("... in C major"),
werden mit derselben Analyse wie in der App ausgewertet. Ergebnis: bericht.md
Aufruf: python3 run.py <analyze_pcm-Programm> [Anzahl]
"""
import json, re, subprocess, sys, time, urllib.error, urllib.parse, urllib.request, os, tempfile

UA = {"User-Agent": "FadeluxKeyTest/1.0 (automated key-detection test)"}
API = "https://commons.wikimedia.org/w/api.php"
NOTES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11, "H": 11}
NAMES = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]

def fetch(url, timeout=120):
    """Download mit Pausen: Wikimedia begrenzt die Anfragen (HTTP 429) – dann warten und nochmals."""
    for attempt in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
                data = r.read()
            time.sleep(2)
            return data
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == 5: raise
            wait = int(e.headers.get("Retry-After") or 0) or 20 * (attempt + 1)
            print(f"  HTTP 429 – warte {wait} s", flush=True)
            time.sleep(min(wait, 120))

def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    return json.loads(fetch(url, 30))

def key_from_title(t):
    m = re.search(r"\bin ([A-H])[ -]?(flat|sharp|♭|♯|b(?=[ -]))?[ -](major|minor|dur|moll)\b", t, re.I)
    if not m: return None
    pc = NOTES[m.group(1).upper()]
    acc = (m.group(2) or "").lower()
    if acc in ("flat", "♭", "b"): pc -= 1
    if acc in ("sharp", "♯"): pc += 1
    minor = m.group(3).lower() in ("minor", "moll")
    return (pc % 12) + (12 if minor else 0)

def label(k):
    return "?" if k is None or k < 0 else NAMES[k % 12] + ("-Moll" if k >= 12 else "-Dur")

def relation(exp, got):
    if got is None or got < 0: return "unklar"
    if exp == got: return "richtig"
    e, g = exp % 12, got % 12
    em, gm = exp >= 12, got >= 12
    if em != gm and ((not em and g == (e + 9) % 12) or (em and g == (e + 3) % 12)): return "Parallele"
    if em == gm and (g - e) % 12 in (5, 7): return "Quinte"
    if em != gm and e == g: return "Dur/Moll"
    return "falsch"

def main():
    prog = sys.argv[1]
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 40
    titles = []
    for q in ['intitle:major filemime:audio/ogg', 'intitle:minor filemime:audio/ogg',
              'intitle:major filemime:audio/x-flac', 'intitle:minor filemime:audio/mpeg']:
        try:
            d = api({"action": "query", "list": "search", "srsearch": q, "srnamespace": 6, "srlimit": 60})
            titles += [x["title"] for x in d["query"]["search"]]
        except Exception as e:
            print("Suche fehlgeschlagen:", q, e)
    seen, rows = set(), []
    for t in titles:
        if t in seen: continue
        seen.add(t)
        exp = key_from_title(t)
        if exp is None: continue
        if len(rows) >= limit: break
        try:
            info = api({"action": "query", "titles": t, "prop": "imageinfo", "iiprop": "url|size|mime"})
            ii = next(iter(info["query"]["pages"].values()))["imageinfo"][0]
            if ii["size"] > 40_000_000: continue
            tmp = tempfile.mktemp()
            with open(tmp, "wb") as fh:
                fh.write(fetch(ii["url"]))
            dec = subprocess.run(["ffmpeg", "-v", "error", "-i", tmp, "-t", "480", "-f", "s16le", "-ac", "2", "-ar", "44100", "-"],
                                 capture_output=True, timeout=300)
            os.remove(tmp)
            out = subprocess.run([prog], input=dec.stdout, capture_output=True, timeout=300).stdout.decode()
            got = int(re.search(r"KEY=(-?\d+)", out).group(1))
            bpm = re.search(r"BPM ([\d.]+)", out).group(1)
            rows.append((t.replace("File:", ""), exp, got, relation(exp, got), bpm))
            print(rows[-1], flush=True)
        except Exception as e:
            print("übersprungen:", t, e)
    total = len(rows)
    cnt = {k: sum(1 for r in rows if r[3] == k) for k in ["richtig", "Parallele", "Quinte", "Dur/Moll", "falsch", "unklar"]}
    with open("bericht.md", "w") as f:
        f.write(f"# Tonart-Erkennung an {total} echten Aufnahmen (Wikimedia Commons, gemeinfrei)\n\n")
        for k, v in cnt.items(): f.write(f"- {k}: {v} ({100*v/max(1,total):.0f} %)\n")
        ok = cnt["richtig"] + cnt["Parallele"] + cnt["Quinte"]
        f.write(f"\n**Für harmonisches Mischen brauchbar (richtig, Parallele oder Quinte = Camelot-Nachbar): {ok} von {total} ({100*ok/max(1,total):.0f} %)**\n\n")
        f.write("| Aufnahme | erwartet | erkannt | Bewertung | BPM |\n|---|---|---|---|---|\n")
        for r in rows: f.write(f"| {r[0]} | {label(r[1])} | {label(r[2])} | {r[3]} | {r[4]} |\n")
    print(open("bericht.md").read())

if __name__ == "__main__":
    main()
