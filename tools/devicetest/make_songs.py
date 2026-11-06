#!/usr/bin/env python3
"""
Erzeugt Test-Songs (MP3) für den Gerätetest – schnell mit numpy:
Intro/Outro nur Schlagzeug, Hauptteil mit Bass + Dur-Dreiklang, ein Break ohne Bassdrum,
verschiedene Tempi/Tonarten/Lautstärken/Genre-Tags, 3 s Stille am Schluss.
Aufruf: python3 make_songs.py <Zielordner>
"""
import os, struct, sys
import lameenc
import numpy as np

SONGS = [  # BPM, Grundton (Hz), Lautstärke, Genre, Länge (s)
    (124, 220.00, 1.0, "House", 170), (124, 220.00, 0.9, "House", 175), (126, 246.94, 1.0, "House", 165),
    (128, 261.63, 0.8, "Techno", 170), (132, 293.66, 1.0, "Techno", 160), (138, 196.00, 1.0, "Techno", 165),
    (100, 220.00, 0.6, "Rock", 175), (104, 329.63, 0.7, "Rock", 170), (118, 174.61, 0.5, "Schlager", 170),
    (120, 196.00, 0.6, "Schlager", 165), (122, 233.08, 0.7, "Schlager", 170), (95, 261.63, 0.4, "Chill", 180),
    (125, 220.00, 0.9, "House", 170), (127, 277.18, 0.85, "House", 165), (130, 207.65, 1.0, "Techno", 170),
    (110, 246.94, 0.6, "Pop", 175), (116, 293.66, 0.65, "Pop", 170), (124, 220.00, 0.95, "House", 170),
    (127, 220.00, 0.92, "House", 85),   # kurz: Tempo-Rückführung hat nur wenig Platz (→ 8 Takte)
]
RATE = 44100

def song(bpm, f, vol, length, seed):
    """Aufbau wie ein DJ-Track: Intro/Outro nur Schlagzeug (16 s), Hauptteil mit Bass + Akkord,
    ein Break (ohne Bassdrum) bei ca. 35-45 %, 3 s Stille am Schluss."""
    rng = np.random.default_rng(seed)
    t = np.arange(int(RATE * length)) / RATE
    p = 60.0 / bpm
    bt = np.mod(t, p)
    body = (t >= 16) & (t < length - 16)
    brk = (t >= length * 0.35) & (t < length * 0.45)
    drums = (t >= 2) & ~brk
    kick = 0.55 * drums * np.exp(-bt * 14) * np.sin(2 * np.pi * 55 * bt)
    hats = 0.06 * drums * (rng.random(t.size) - 0.5) * np.exp(-np.mod(t + p / 2, p) * 60)
    bass = 0.10 * (body & ~brk) * np.sin(2 * np.pi * f / 4 * t) * (np.mod(t + p / 2, p) < p * 0.4)
    chord = (0.07 * np.sin(2 * np.pi * f * t) + 0.05 * np.sin(2 * np.pi * f * 1.2599 * t)
             + 0.05 * np.sin(2 * np.pi * f * 1.4983 * t) + 0.06 * np.sin(2 * np.pi * f / 2 * t))
    # Akkord als rhythmische Stabs (wie Synth-Pads mit Sidechain), im Break liegend
    stab = np.where(brk, 1.0, 0.35 + 0.65 * np.clip(np.mod(t, p) / p * 2, 0, 1))
    x = vol * (kick + hats + bass + chord * body * stab)
    x = np.concatenate([x, np.zeros(RATE * 3)])                   # 3 s Stille am Schluss
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    return np.repeat(pcm, 2)                                       # Stereo


def id3(frames):
    """Minimaler ID3v2.3-Tag (Titel, Interpret, Genre) – Android liest daraus die Song-Infos."""
    body = b""
    for fid, text in frames.items():
        data = b"\x00" + text.encode("latin-1")
        body += fid.encode() + struct.pack(">I", len(data)) + b"\x00\x00" + data
    n = len(body)
    size = bytes([(n >> 21) & 0x7F, (n >> 14) & 0x7F, (n >> 7) & 0x7F, n & 0x7F])
    return b"ID3\x03\x00\x00" + size + body


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "songs"
    raw = "--raw" in sys.argv          # zusätzlich rohe PCM-Dateien (für den PC-Test der Analyse)
    os.makedirs(out, exist_ok=True)
    for i, (bpm, f, vol, genre, length) in enumerate(SONGS, 1):
        pcm = song(bpm, f, vol, length, i)
        if raw:
            pcm.tofile(os.path.join(out, f"soak_{i:02d}.pcm"))
            continue
        name = os.path.join(out, f"soak_{i:02d}.mp3")
        enc = lameenc.Encoder()
        enc.set_bit_rate(160)
        enc.set_in_sample_rate(RATE)
        enc.set_channels(2)
        enc.set_quality(5)
        mp3 = enc.encode(pcm.tobytes()) + enc.flush()
        tag = id3({"TIT2": f"Soak {i:02d} ({bpm} BPM)", "TPE1": "Generator", "TCON": genre})
        with open(name, "wb") as f:
            f.write(tag + mp3)
    print(len(os.listdir(out)), "Songs")

if __name__ == "__main__":
    main()
