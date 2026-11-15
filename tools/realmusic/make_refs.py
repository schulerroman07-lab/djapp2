#!/usr/bin/env python3
"""
Erzeugt Referenz-Daten für die Test-Songs mit bewährter Analyse-Software (läuft im CI):
  - Beats und Downbeats: "Beat This!" (CPJKU, ISMIR 2024 – Stand der Technik)
  - Gesang: Stimm-Trennung mit Demucs (htdemucs), daraus die Lautstärke-Hüllkurve der Gesangsspur
  - Lautstärke-Hüllkurve des ganzen Songs (für Fade-out / Songende)
Ausgabe pro Song: refs_raw/<id>.json  (Schwellen für "Gesang aktiv" setzt refs.py – nachvollziehbar und
ohne die schwere Software wiederholbar).
Aufruf: python3 make_refs.py <Audio-Ordner> <Ausgabe-Ordner> [--shard i/n]
"""
import base64, gzip, json, os, subprocess, sys, tempfile
import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fetch import songs, fetch

HOP = 0.1  # Sekunden pro Hüllkurven-Wert


def envelope_db(x, rate):
    """RMS in dBFS pro 100 ms (Mono-Summe der Kanäle)."""
    if x.ndim == 2:
        x = x.mean(axis=1)
    n = int(rate * HOP)
    frames = len(x) // n
    rms = np.sqrt(np.mean(x[: frames * n].reshape(frames, n) ** 2, axis=1) + 1e-12)
    return np.round(20 * np.log10(rms + 1e-9), 1)


def main():
    audio, out = sys.argv[1], sys.argv[2]
    os.makedirs(audio, exist_ok=True)
    os.makedirs(out, exist_ok=True)
    from beat_this.inference import File2Beats
    f2b = File2Beats(checkpoint_path="final0", device="cpu", dbn=False)
    for s in songs(sys.argv):
        mp3 = fetch(s, audio)
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, s["id"] + ".wav")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", mp3, "-ar", "44100", "-ac", "2", wav], check=True)
            x, rate = sf.read(wav, dtype="float32")
            beats, downbeats = f2b(wav)
            subprocess.run([sys.executable, "-m", "demucs", "--two-stems", "vocals", "-n", "htdemucs", "-d", "cpu",
                            "-o", tmp, wav], check=True, stdout=subprocess.DEVNULL)
            v, vrate = sf.read(os.path.join(tmp, "htdemucs", s["id"], "vocals.wav"), dtype="float32")
            # Mitte/Seite der Gesangsspur: Gesang liegt fast immer in der Mitte
            ref = {
                "id": s["id"], "duration": round(len(x) / rate, 3),
                "beats": [round(float(b), 3) for b in beats],
                "downbeats": [round(float(b), 3) for b in downbeats],
                "hop": HOP,
                "mix_db": envelope_db(x, rate).tolist(),
                "vocal_db": envelope_db(v, vrate).tolist(),
                "tools": "beat_this final0 (ohne DBN); demucs htdemucs two-stems vocals",
            }
        json.dump(ref, open(os.path.join(out, s["id"] + ".json"), "w"))
        packed = base64.b64encode(gzip.compress(json.dumps(ref, separators=(",", ":")).encode())).decode()
        print(f"REFJSON {s['id']} {packed}", flush=True)
        print(f"fertig {s['id']}: {len(ref['beats'])} Beats, {len(ref['downbeats'])} Downbeats, "
              f"Gesang max {max(ref['vocal_db']):.1f} dB", flush=True)


if __name__ == "__main__":
    main()
