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
    # Beat-Referenz 1: "Beat This!" (Modell wird von der Uni Linz geladen – der Server ist nicht immer erreichbar)
    f2b = None
    ckpt = os.environ.get("BEAT_THIS_CKPT", "final0")
    try:
        from beat_this.inference import File2Beats
        f2b = File2Beats(checkpoint_path=ckpt, device="cpu", dbn=False)
        print("Beat This! geladen", flush=True)
    except Exception as e:
        print("Beat This! nicht verfügbar:", repr(e)[:300], flush=True)
    # Beat-Referenz 2: madmom (RNN + DBN, seit Jahren Standard in der Forschung)
    mm = None
    try:
        from madmom.features.downbeats import RNNDownBeatProcessor, DBNDownBeatTrackingProcessor
        mm = (RNNDownBeatProcessor(), DBNDownBeatTrackingProcessor(beats_per_bar=[3, 4], fps=100))
        print("madmom geladen", flush=True)
    except Exception as e:
        print("madmom nicht verfügbar:", repr(e)[:300], flush=True)
    if f2b is None and mm is None:
        raise SystemExit("Keine Beat-Referenz-Software verfügbar")
    # schon vorhandene Referenzen (im Projekt) überspringen – nur neue Songs rechnen (--redo: alle)
    import refs as R
    have = R.list_paths(R.list_name(sys.argv))[1]
    for s in songs(sys.argv):
        if "--redo" not in sys.argv and os.path.exists(os.path.join(have, s["id"] + ".json")):
            print("vorhanden:", s["id"], flush=True)
            continue
        mp3 = fetch(s, audio)
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, s["id"] + ".wav")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", mp3, "-ar", "44100", "-ac", "2", wav], check=True)
            x, rate = sf.read(wav, dtype="float32")
            extra = {}
            tools = []
            if f2b is not None:
                b, d = f2b(wav)
                extra["bt"] = ([round(float(v), 3) for v in b], [round(float(v), 3) for v in d])
                tools.append("beat_this final0 (ohne DBN)")
            if mm is not None:
                act = mm[1](mm[0](wav))
                extra["mm"] = ([round(float(t), 3) for t, _ in act], [round(float(t), 3) for t, p in act if int(p) == 1])
                tools.append("madmom 0.16 RNNDownBeat + DBN")
            beats, downbeats = extra.get("bt") or extra["mm"]
            subprocess.run([sys.executable, "-m", "demucs", "--two-stems", "vocals", "-n", "htdemucs", "-d", "cpu",
                            "-o", tmp, wav], check=True, stdout=subprocess.DEVNULL)
            v, vrate = sf.read(os.path.join(tmp, "htdemucs", s["id"], "vocals.wav"), dtype="float32")
            # Mitte/Seite der Gesangsspur: Gesang liegt fast immer in der Mitte
            ref = {
                "id": s["id"], "duration": round(len(x) / rate, 3),
                "beats": beats, "downbeats": downbeats,
                "beats_madmom": extra["mm"][0] if "mm" in extra else None,
                "downbeats_madmom": extra["mm"][1] if "mm" in extra else None,
                "hop": HOP,
                "mix_db": envelope_db(x, rate).tolist(),
                "vocal_db": envelope_db(v, vrate).tolist(),
                "tools": "; ".join(tools) + "; demucs htdemucs two-stems vocals",
            }
        json.dump(ref, open(os.path.join(out, s["id"] + ".json"), "w"))
        packed = base64.b64encode(gzip.compress(json.dumps(ref, separators=(",", ":")).encode())).decode()
        print(f"REFJSON {s['id']} {packed}", flush=True)
        print(f"fertig {s['id']}: {len(ref['beats'])} Beats, {len(ref['downbeats'])} Downbeats, "
              f"Gesang max {max(ref['vocal_db']):.1f} dB", flush=True)


if __name__ == "__main__":
    main()
