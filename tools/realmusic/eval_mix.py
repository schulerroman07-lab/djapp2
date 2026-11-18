#!/usr/bin/env python3
"""
Objektive Prüfung der Übergänge (Punkt 9): misst die mit der echten Engine abgespielten Übergänge
(mixrender) gegen die Referenzen der Test-Songs (Beat This!/madmom-Beats, Demucs-Gesangsspur).

Pro Übergang:
  - Beat-Versatz (nur bei Beatmatching): hörbare Beats von A und B im Mix – Referenz-Beats über die
    Deck-Positionen in Mix-Zeit umgerechnet, plus die gemessene Verzögerung jedes Decks (WSOLA usw.,
    aus dem Vergleich der Solo-Aufnahme mit dem Original). Mittlerer Abstand A-Beat ↔ nächster B-Beat.
  - Gesang über Gesang: Gesangsspur (Referenz) jedes Decks × tatsächliche Verstärkung des Decks im
    Sprachbereich (Solo-Aufnahme gegen Original) – hörbar, wenn wie in der Referenz-Regel ≥ −45 dBFS und
    höchstens 15 dB unter dem Mix. Überschneidung beider = Gesang über Gesang.
    Nach dem Stopp von A zählt auch der Effekt-Schweif (Echo/Hall) von A: Er trägt den Gesangsanteil der
    letzten Sekunde vor dem Stopp und klingt so ab wie das, was A tatsächlich noch ausgibt.
    "Voll" = A höchstens 6 dB leiser als B; "Ausklingen" = A mindestens 6 dB leiser (erlaubt bis 2 Takte).
  - Einsatz: Position in A, an der B startet, gegen das Ende der letzten VOLLEN Gesangsphrase von A
    (Referenz, Abschnitt ≥ 2 s) und gegen das Ende des letzten Gesangs überhaupt.
  - B-Einstieg: startet B mitten in einer Gesangsphrase (Referenz)?
  - Pegel: Spitzenwert, Lautheits-Buckel/-Loch im Übergang gegen vorher/nachher.

Aufruf: python3 eval_mix.py <mix-Ordner> <render-Ordner> <pcm-Ordner> [--report bericht.md]
"""
import json, os, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import refs as R

OUT_RATE, SRC_RATE, FR = 48000, 44100, 0.1


def read_mix(path):
    kv = {}
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line in ("end",) or line.startswith(("pre ", "step ")):
            if line.startswith(("pre ", "step ")):
                break
            continue
        k, _, v = line.partition(" ")
        kv[k] = v
    return kv


def mono(x):
    return x.reshape(-1, 2).mean(1)


def load_f32(path):
    return mono(np.fromfile(path, dtype=np.float32))


def load_pcm(path):
    return mono(np.fromfile(path, dtype=np.int16).astype(np.float32) / 32768.0)


def env_db(x, rate, hop=FR):
    n = int(rate * hop)
    k = len(x) // n
    rms = np.sqrt(np.mean(x[:k * n].reshape(k, n) ** 2, 1) + 1e-12)
    return 20 * np.log10(rms + 1e-9)


def band_power(seg, rate, lo=300.0, hi=3400.0):
    if len(seg) < 64:
        return 1e-12
    w = np.hanning(len(seg))
    X = np.fft.rfft(seg * w)
    f = np.fft.rfftfreq(len(seg), 1 / rate)
    m = (f >= lo) & (f < hi)
    return float(np.sum(np.abs(X[m]) ** 2) / (np.sum(w ** 2) * len(seg))) + 1e-12


def flux_env(x, rate, hop_s=0.0025, n=1024):
    """Onset-Hüllkurve (Spektralfluss, log) mit Schritt hop_s."""
    hop = hop_s * rate
    frames = int((len(x) - n) / hop)
    if frames <= 2:
        return np.zeros(1)
    idx = (np.arange(frames) * hop).astype(int)
    w = np.hanning(n)
    out = np.zeros(frames)
    prev = None
    for i, a in enumerate(idx):
        mag = np.log1p(100 * np.abs(np.fft.rfft(x[a:a + n] * w)))
        if prev is not None:
            out[i] = np.sum(np.maximum(0, mag - prev))
        prev = mag
    return out


def smooth_db(v, k=3):
    p = 10 ** (np.asarray(v) / 10)
    return 10 * np.log10(np.convolve(p, np.ones(k) / k, mode="same") + 1e-12)


def evaluate(n, mixdir, rdir, pcmdir, cache):
    kv = read_mix(os.path.join(mixdir, f"{n:03d}.mix"))
    pre = os.path.join(rdir, f"{n:03d}")
    log = np.loadtxt(pre + ".log")
    t, aPos, bPos, aPlay, bPlay, prog = log.T
    mix, solo_a, solo_b = load_f32(pre + ".mix.f32"), load_f32(pre + ".a.f32"), load_f32(pre + ".b.f32")
    a_id, b_id = kv["a"], kv["b"]
    if a_id not in cache: cache[a_id] = (load_pcm(os.path.join(pcmdir, a_id + ".pcm")), R.load(a_id))
    if b_id not in cache: cache[b_id] = (load_pcm(os.path.join(pcmdir, b_id + ".pcm")), R.load(b_id))
    (src_a, ref_a), (src_b, ref_b) = cache[a_id], cache[b_id]
    ratio = float(kv["ratio"])
    on = np.where(bPlay > 0.5)[0]
    res = dict(n=n, a=a_id, b=b_id, type=kv["type"], aGenre=kv.get("aGenre"), bGenre=kv.get("bGenre"),
               match=kv.get("matchTempo") == "1", ratio=ratio, early="früh" in kv.get("order", ""))
    if len(on) == 0:
        res["error"] = "B nicht gestartet"
        return res
    i0 = on[0]
    done = np.where(prog >= 1.0)[0]
    i1 = done[0] if len(done) else len(t) - 1
    t0, t1 = t[i0], t[i1]
    res.update(t_start=t0, t_end=t1, a_start=float(aPos[i0]), a_start_plan=float(kv["mixSec"]),
               ref_last_vocal=float(ref_a["last_vocal_end"]), est_last_vocal=float(kv.get("aLastVocal", 0)),
               a_sound_end=float(ref_a["sound_end"]), b_first_vocal=ref_b.get("first_vocal_start"),
               bStart=float(kv["bStart"]), overlap=float(t1 - t0))
    # ---- Einsatz gegen letzten Gesang von A ----
    res["start_vs_vocal"] = res["a_start"] - res["ref_last_vocal"] if res["ref_last_vocal"] > 0 else None
    lf = float(ref_a.get("last_full_phrase_end", 0.0))
    res["start_vs_phrase"] = res["a_start"] - lf if lf > 0 else None
    # B-Einstieg mitten in einer Gesangsphrase? (mehr als 0,3 s nach Phrasenbeginn und vor ihrem Ende)
    bs = float(kv["bStart"])
    res["b_mid_phrase"] = bool(any(a + 0.3 < bs < b - 0.3 for a, b in ref_b["vocals"]))

    # ---- Pegel und Gesang pro 0,1-s-Rahmen der Mix-Zeit ----
    nfr = len(mix) // int(OUT_RATE * FR)
    tf = np.arange(nfr) * FR
    mix_db = env_db(mix, OUT_RATE)[:nfr]
    pa = np.interp(tf, t, aPos); pb = np.interp(tf, t, bPos)
    playa = np.interp(tf, t, aPlay) > 0.5; playb = np.interp(tf, t, bPlay) > 0.5
    N = int(OUT_RATE * FR)

    def deck_gain(solo, src, pos, play, tempo):
        g = np.full(len(pos), -120.0)
        for k in range(len(pos)):
            if not play[k]:
                continue
            o = band_power(solo[k * N:(k + 1) * N], OUT_RATE)
            s0 = int(pos[k] * SRC_RATE)
            s = band_power(src[s0:s0 + int(FR * tempo * SRC_RATE)], SRC_RATE)
            g[k] = 10 * np.log10(o / s) if s > 1e-10 else -120.0
        return np.clip(g, -120, 12)

    # nur um den Übergang herum rechnen (vorher/nachher spielt nur ein Deck)
    k0, k1 = max(0, int(t0 / FR) - 20), min(nfr, int(t1 / FR) + 40)
    ga = np.full(nfr, -120.0); gb = np.full(nfr, -120.0)
    sl = slice(k0, k1)
    ga_part = deck_gain(solo_a[k0 * N:], src_a, pa[sl], playa[sl], 1.0)
    gb_part = deck_gain(solo_b[k0 * N:], src_b, pb[sl], playb[sl], ratio)
    ga[sl] = ga_part[:k1 - k0]; gb[sl] = gb_part[:k1 - k0]

    def vocal_db(ref, pos):
        v = np.asarray(ref["vocal_db"])
        return np.interp(pos / ref["hop"], np.arange(len(v)), v)

    va_raw = vocal_db(ref_a, pa) + ga
    # Effekt-Schweif von A nach dem Stopp (Echo/Hall): Gesangsanteil der letzten Sekunde vor dem Stopp,
    # abklingend wie die tatsächliche Ausgabe von A (Sprachbereich der Solo-Aufnahme)
    solo_a_db = np.full(nfr, -120.0)
    for k in range(k0, k1):
        solo_a_db[k] = 10 * np.log10(band_power(solo_a[k * N:(k + 1) * N], OUT_RATE))
    tail = np.zeros(nfr, bool)
    stop = np.where(playa[:-1] & ~playa[1:])[0]
    for k_stop in stop:
        if not (k0 <= k_stop < k1):
            continue
        w = slice(max(k0, k_stop - 9), k_stop + 1)
        p_lin = lambda v: 10 * np.log10(np.mean(10 ** (np.asarray(v) / 10)) + 1e-12)
        v_ref, s_ref = p_lin(va_raw[w]), p_lin(solo_a_db[w])
        for k in range(k_stop + 1, k1):
            if playa[k] or solo_a_db[k] < -90:
                break
            va_raw[k] = v_ref + (solo_a_db[k] - s_ref)
            tail[k] = True
    va = smooth_db(va_raw); vb = smooth_db(vocal_db(ref_b, pb) + gb)
    ms = smooth_db(mix_db)
    aud_a = (va > R.VOCAL_ABS_DB) & (va > ms - R.VOCAL_REL_DB) & (playa | tail)
    aud_b = (vb > R.VOCAL_ABS_DB) & (vb > ms - R.VOCAL_REL_DB) & playb
    clash = aud_a & aud_b
    full = clash & (va > vb - 6.0)
    soft = clash & (va <= vb - 6.0)
    res["clash_sec"] = float(clash.sum() * FR)
    res["clash_full_sec"] = float(full.sum() * FR)
    res["clash_soft_sec"] = float(soft.sum() * FR)
    rb = np.asarray(ref_b["beats"])
    b_bar = 4 * float(np.median(np.diff(rb))) / ratio if len(rb) > 4 else 2.0      # Taktlänge von B in Mix-Zeit
    res["clash_soft_bars"] = res["clash_soft_sec"] / b_bar
    res["clash_soft_min_db"] = float(np.min((vb - va)[soft])) if soft.any() else None
    res["a_vocal_after_start"] = float((aud_a & (tf >= t0)).sum() * FR)
    # ---- Pegel ----
    res["peak"] = float(np.max(np.abs(mix)))
    st = smooth_db(mix_db, 4)                 # ca. 400 ms
    before = st[max(0, int(t0 / FR) - 100):max(1, int(t0 / FR) - 10)]
    after = st[min(nfr - 1, int(t1 / FR) + 5):min(nfr, int(t1 / FR) + 50)]
    during = st[int(t0 / FR):max(int(t0 / FR) + 1, int(t1 / FR))]
    ref_lvl = max(np.median(before) if len(before) else -99, np.median(after) if len(after) else -99)
    res["bump_db"] = float(np.max(during) - ref_lvl) if len(during) else 0.0
    # Buckel durch die Überlagerung: Mix gegen den lauteren der beiden Songs allein (ohne die Dynamik von B selbst)
    sa = smooth_db(env_db(solo_a, OUT_RATE)[:nfr], 4); sb = smooth_db(env_db(solo_b, OUT_RATE)[:nfr], 4)
    i0s, i1s = int(t0 / FR), max(int(t0 / FR) + 1, int(t1 / FR))
    res["overlap_bump_db"] = float(np.max(st[i0s:i1s] - np.maximum(sa[i0s:i1s], sb[i0s:i1s]))) if i1s > i0s else 0.0
    res["dip_db"] = float(np.min(during) - min(np.median(before) if len(before) else 0, np.median(after) if len(after) else 0)) if len(during) else 0.0
    # "Loch" zwischen den Songs: Zeit, in der der Mix mehr als 20 dB unter dem leiseren der beiden Songs liegt
    body = min(np.median(before) if len(before) else -99, np.median(after) if len(after) else -99)
    win = st[max(0, int(t0 / FR) - 30):min(nfr, int(t1 / FR) + 20)]
    res["gap_sec"] = float(np.sum(win < body - 20) * FR)

    # ---- Beat-Versatz (nur Beatmatching) ----
    if res["match"]:
        lat_a = latency(solo_a, src_a, t, aPos, aPlay, t0, t1, 1.0)
        lat_b = latency(solo_b, src_b, t, bPos, bPlay, t0, t1, ratio)
        res["lat_a_ms"], res["lat_b_ms"] = lat_a * 1000, lat_b * 1000
        ma = (t >= t0) & (t <= t1) & (aPlay > 0.5)
        mb = (t >= t0) & (t <= t1) & (bPlay > 0.5)
        if ma.sum() > 10 and mb.sum() > 10:
            ba = np.asarray(ref_a["beats"]); bb = np.asarray(ref_b["beats"])
            ba = ba[(ba >= aPos[ma][0]) & (ba <= aPos[ma][-1])]
            bb = bb[(bb >= bPos[mb][0]) & (bb <= bPos[mb][-1])]
            ta = np.interp(ba, aPos[ma], t[ma]) + lat_a
            tb = np.interp(bb, bPos[mb], t[mb]) + lat_b
            # nur wo beide Decks hörbar sind (Verstärkung im Sprachbereich > −20 dB)
            def audible(tt, g):
                return g[np.clip((tt / FR).astype(int), 0, nfr - 1)] > -20
            ta = ta[audible(ta, ga) & audible(ta, gb)]
            if len(ta) >= 2 and len(tb) >= 2:
                d = np.array([tb[np.argmin(np.abs(tb - x))] - x for x in ta])
                res["beat_offsets"] = d.tolist()
                res["beat_mean_ms"] = float(np.mean(np.abs(d)) * 1000)
                res["beat_hit30"] = float(np.mean(np.abs(d) <= 0.03))
                da = np.asarray(ref_a["downbeats"]); db = np.asarray(ref_b["downbeats"])
                da = np.interp(da[(da >= aPos[ma][0]) & (da <= aPos[ma][-1])], aPos[ma], t[ma]) + lat_a
                db = np.interp(db[(db >= bPos[mb][0]) & (db <= bPos[mb][-1])], bPos[mb], t[mb]) + lat_b
                da = da[audible(da, ga) & audible(da, gb)]
                if len(da) and len(db):
                    res["downbeat_hit"] = float(np.mean([np.min(np.abs(db - x)) <= 0.07 for x in da]))
        res["refs_reliable"] = bool(R.reliable(ref_a) and R.reliable(ref_b))
        res["downbeats_reliable"] = bool(R.reliable_downbeats(ref_a) and R.reliable_downbeats(ref_b))
    return res


def latency(solo, src, t, pos, play, t0, t1, tempo):
    """Verzögerung (s) zwischen Deck-Position und hörbarem Ton (Solo-Aufnahme gegen Original)."""
    m = (t >= t0 - 2) & (t <= t1 + 2) & (play > 0.5)
    if m.sum() < 50:
        return 0.0
    ts, te = t[m][0], t[m][-1]
    seg = solo[int(ts * OUT_RATE):int(te * OUT_RATE)]
    eo = flux_env(seg, OUT_RATE)
    hop = 0.0025
    to = ts + np.arange(len(eo)) * hop + 512 / OUT_RATE          # Fenstermitte
    p0, p1 = pos[m][0], pos[m][-1]
    s0 = max(0, int(p0 * SRC_RATE) - 4096)
    es = flux_env(src[s0:int(p1 * SRC_RATE) + 4096], SRC_RATE)
    tsrc = s0 / SRC_RATE + np.arange(len(es)) * hop + 512 / SRC_RATE
    best, lag_best = -1e9, 0.0
    lags = np.arange(-0.08, 0.0801, hop)
    scores = []
    for lag in lags:
        # Ton im Solo bei Zeit x entspricht Original bei Position pos(x − lag)
        pp = np.interp(to - lag, t[m], pos[m])
        ref = np.interp(pp, tsrc, es)
        c = np.corrcoef(eo, ref)[0, 1] if np.std(ref) > 0 and np.std(eo) > 0 else -1
        scores.append(c)
    scores = np.array(scores)
    i = int(np.argmax(scores))
    lag_best = lags[i]
    if 0 < i < len(scores) - 1:                                 # Feinabgleich (Parabel)
        a, b, c = scores[i - 1], scores[i], scores[i + 1]
        den = a - 2 * b + c
        if den < 0:
            lag_best += 0.5 * (a - c) / den * hop
    return float(lag_best)


def main():
    mixdir, rdir, pcmdir = sys.argv[1:4]
    report = sys.argv[sys.argv.index("--report") + 1] if "--report" in sys.argv else None
    ns = sorted(int(f[:3]) for f in os.listdir(mixdir) if f.endswith(".mix"))
    ns = [n for n in ns if os.path.exists(os.path.join(rdir, f"{n:03d}.log"))]
    cache, rows = {}, []
    for n in ns:
        r = evaluate(n, mixdir, rdir, pcmdir, cache)
        rows.append(r)
        bm = f"{r['beat_mean_ms']:5.1f} ms" if "beat_mean_ms" in r else "   -    "
        sv = f"{r['start_vs_vocal']:+6.1f}" if r.get("start_vs_vocal") is not None else "     -"
        sp = f"{r['start_vs_phrase']:+6.1f}" if r.get("start_vs_phrase") is not None else "     -"
        print(f"{n:03d} {r['a']}→{r['b']} {r['type']:10s} Beat {bm}  Gesang voll {r.get('clash_full_sec', 0):4.1f} s, "
              f"ausklingend {r.get('clash_soft_bars', 0):3.1f} Takte  Einsatz−Phrase {sp} s (−Gesang {sv} s)  "
              f"Buckel {r.get('bump_db', 0):+4.1f} dB  Loch {r.get('gap_sec', 0):.1f} s  Spitze {r.get('peak', 0):.2f}"
              + ("  B-MITTEN-IN-PHRASE" if r.get("b_mid_phrase") else "")
              + (f"  FEHLER {r['error']}" if "error" in r else ""), flush=True)
    json.dump(rows, open(os.path.join(rdir, "eval.json"), "w"), indent=1)
    summary(rows, report)


def summary(rows, report=None):
    L = []
    normal = [r for r in rows if not r.get("early")]
    early = [r for r in rows if r.get("early")]
    L += block(normal, "NORMALE ÜBERGÄNGE")
    if early:
        L += [""] + block(early, "FRÜH-ÜBERGÄNGE (wenn angenommen)")
    print("\n".join(L))
    if report:
        open(report, "w", encoding="utf-8").write("\n".join(L) + "\n")


def block(rows, title):
    ok = [r for r in rows if "error" not in r]
    L = [f"== {title} =="]
    L.append(f"Übergänge gemessen: {len(ok)} von {len(rows)}")
    types = {}
    for r in ok:
        types[r["type"]] = types.get(r["type"], 0) + 1
    fades = types.get("FADE", 0)
    L.append(f"Fades: {fades} von {len(ok)} = {100 * fades / max(1, len(ok)):.1f} %  (andere Arten: {100 * (len(ok) - fades) / max(1, len(ok)):.1f} %)")
    L.append("Arten: " + ", ".join(f"{k} {v} ({100 * v / max(1, len(ok)):.0f} %)" for k, v in sorted(types.items(), key=lambda x: -x[1])))
    full = [r for r in ok if r["clash_full_sec"] >= 0.3]
    L.append(f"Gesang über Gesang VOLL (A weniger als 6 dB leiser, ≥ 0,3 s): {len(full)} von {len(ok)}"
             + (f"  → {[(r['n'], r['a'], r['b'], r['type'], round(r['clash_full_sec'], 1)) for r in full]}" if full else ""))
    soft = [r for r in ok if r["clash_soft_sec"] >= 0.1]
    long_soft = [r for r in soft if r["clash_soft_bars"] > 2.0]
    L.append(f"Gesang ausklingend (A ≥ 6 dB leiser): in {len(soft)} Übergängen, längstens "
             f"{max([r['clash_soft_bars'] for r in soft], default=0):.1f} Takte; über 2 Takte: {len(long_soft)}"
             + (f"  → {[(r['n'], r['a'], r['b'], round(r['clash_soft_bars'], 1)) for r in long_soft]}" if long_soft else ""))
    ph = [r for r in ok if r.get("start_vs_phrase") is not None]
    early_p = [r for r in ph if r["start_vs_phrase"] < -0.3]
    L.append(f"Einsatz vor dem Ende der letzten vollen Gesangsphrase von A (> 0,3 s): {len(early_p)} von {len(ph)}"
             + (f"  → {[(r['n'], r['a'], r['type'], round(r['start_vs_phrase'], 1)) for r in early_p]}" if early_p else ""))
    voc = [r for r in ok if r.get("start_vs_vocal") is not None]
    early = [r for r in voc if r["start_vs_vocal"] < -0.3]
    L.append(f"  (zum Vergleich: vor dem Ende des letzten Gesangs überhaupt, inkl. Einwürfe: {len(early)} von {len(voc)})")
    mid = [r for r in ok if r.get("b_mid_phrase")]
    L.append(f"B startet mitten in einer Gesangsphrase: {len(mid)} von {len(ok)}"
             + (f"  → {[(r['n'], r['b'], r['type']) for r in mid]}" if mid else ""))
    m = [r for r in ok if "beat_mean_ms" in r]
    if m:
        allm = np.mean([r["beat_mean_ms"] for r in m])
        rel = [r for r in m if r.get("refs_reliable")]
        L.append(f"Beatmatching-Übergänge: {len(m)} – mittlerer Beat-Versatz {allm:.1f} ms (alle), "
                 + (f"{np.mean([r['beat_mean_ms'] for r in rel]):.1f} ms (Referenz sicher, {len(rel)})" if rel else "–"))
        L.append(f"  Anteil Beats innerhalb ±30 ms: {100 * np.mean(np.concatenate([np.abs(r['beat_offsets']) <= 0.03 for r in m])):.1f} %")
        dh = [r["downbeat_hit"] for r in m if "downbeat_hit" in r and r.get("downbeats_reliable")]
        if dh:
            L.append(f"  Downbeats (die '1') übereinander: {100 * np.mean(dh):.1f} % (Referenz-Downbeats sicher, {len(dh)} Übergänge)")
        bad = [(r["n"], r["a"], r["b"], round(r["beat_mean_ms"], 1)) for r in m if r["beat_mean_ms"] > 30]
        if bad:
            L.append(f"  Übergänge mit Versatz > 30 ms: {bad}")
        lat = [r["lat_b_ms"] - r["lat_a_ms"] for r in m]
        L.append(f"  gemessene Verzögerung B gegen A (Engine): Mittel {np.mean(lat):+.1f} ms")
    gaps = [r["gap_sec"] for r in ok]
    L.append(f"Loch zwischen den Songs (Mix > 20 dB leiser): Median {np.median(gaps):.1f} s, max {max(gaps):.1f} s, "
             f"Übergänge mit Loch ≥ 1 s: {sum(g >= 1.0 for g in gaps)}")
    ob = [r for r in ok if r.get("overlap_bump_db", 0) > 3.0]
    L.append(f"Buckel durch Überlagerung (Mix > lauterer Song allein + 3 dB): {len(ob)}, höchstens "
             f"{max([r.get('overlap_bump_db', 0) for r in ok], default=0):+.1f} dB" + (f" → {[(r['n'], round(r['overlap_bump_db'], 1)) for r in ob]}" if ob else ""))
    bumps = [r for r in ok if r["bump_db"] > 3.0]
    L.append(f"  Pegel-Sprung zum neuen Song > 3 dB (Mix gegen vorher/nachher, enthält die Dynamik von B): {len(bumps)}"
             + (f" → {[(r['n'], round(r['bump_db'], 1)) for r in bumps]}" if bumps else ""))
    L.append(f"Höchster Spitzenwert: {max(r['peak'] for r in ok):.2f} (Begrenzer bei 0,90)")
    return L


if __name__ == "__main__":
    main()
