# Bericht: Übergänge mit echter Musik – objektive Messung

Stand: 18.11.2026. Alle Zahlen sind gemessen, nicht geschätzt. Oben die aktuelle Runde ("Mehr echte DJ-Übergänge"),
darunter die frühere Runde (Beat- und Gesangserkennung).

## Runde 3: Mehr echte DJ-Übergänge statt kurzer Fades

Gemessen wie bisher: 56 Test-Songs, zwei ganze Partys mit dem echten Planer (**110 Übergänge**), jeder Übergang mit
der echten Audio-Engine abgespielt (Mix, nur A, nur B) und gegen die Referenzen gemessen. Die Netze der App sind
jetzt **nur mit CC0/CC-BY/PD-Songs** gelernt (siehe `LIZENZEN_TRAINING.md`).

| Ziel | Gemessen | Erreicht? |
|---|---|---|
| Fades ≤ 20 % (≥ 80 % andere Arten) | **0 %** Fades (vorher 97 %) | ja |
| Keine volle Gesang-über-Gesang-Überlappung | **0 von 110** | ja |
| Ausklingen max. 2 Takte, mind. 6 dB leiser | **0** Fälle mit hörbarem Ausklingen über dem neuen Gesang | ja |
| Kein Start vor Ende der letzten vollen Gesangsphrase | **0 von 100** | ja |
| Beat-Versatz bei beatgematchten Übergängen < 20 ms | **22,3 ms** (5 Übergänge; 22,0 ms bei sicherer Referenz) | knapp nein |
| Lizenzliste, App-Netze nur CC0/CC-BY/PD | `LIZENZEN_TRAINING.md`, 155 erlaubte Lern-Songs | ja |
| 20 Hörbeispiele | `hoerproben/` (je 40 s, mit Quellen und Lizenzen) | ja |
| Build fehlerfrei, alle Tests grün | 60 Unit-Tests, Engine-Tests, CI (siehe CHANGELOG) | ja |

Weitere Messwerte (110 Übergänge):
- **Arten:** Echo-Out 38 (35 %), Filter-Out 36 (33 %), Hall 35 (32 %),
  Schnitt auf die 1: 1. Pop/Schlager/Rock: Echo 25, Filter 22, Hall 21. Techno/House: Echo 13, Filter 14, Hall 14, Schnitt 1.
- **B setzt mitten in einer Gesangsphrase ein:** 0 von 110.
- **Pegel:** höchstens 0,79 (Begrenzer bei 0,90); Buckel durch Überlagerung höchstens +3,0 dB.
- **Loch zwischen den Songs:** Median 0,4 s (vorher 0,7 s), höchstens 2,2 s (vorher 2,9 s).
- **Im Takt von A** (B tempo-angeglichen, Einsatz auf dem Raster von A): 5 Übergänge; die übrigen setzen im eigenen
  Tempo ein (Tempo zu verschieden oder Raster an der Stelle nicht sicher).

### Was der neue Planer macht
- **Prüfreihenfolge:** (a) Blend/Bass-Swap im Takt (Techno/House 16–32 Takte, Pop 4–8 Takte), (d) Loop als Brücke,
  (b) Echo-Out bzw. Filter-Sweep, (c) Schnitt auf die 1, (e) Fade – gleich gute Arten wechseln sich ab.
- **Singt A bis zum Schluss** (das ist bei den Test-Songs der Normalfall), klingt das letzte Wort mit Echo, Filter-Out
  oder dezentem Hall aus, und B setzt auf seinem Downbeat ein – bei passendem Tempo im Takt von A.
- **Einstieg von B:** auf einem Downbeat; fast stille Anfangstakte und lange ruhige Intros werden übersprungen; nie
  mitten in einer Gesangsphrase und nicht direkt vor einem Gesangsbeginn (Auftakt).
- **Ende des Gesangs von A:** Schwelle 0,10, Gesang < 8 s vor dem Ende gilt bis zum Ende, Übergang frühestens 3 s nach
  dem letzten erkannten Gesang – an 239 Übungs- und Lern-Songs bestimmt (jeder von einem Netz beurteilt, das ihn nie
  gelernt hat): zu früh bei 4 %, instrumentaler Schluss bei 19 % nutzbar.

### Ursachen, wo es hakt
1. **Keine Blends in den 110 Übergängen.** Ein Blend braucht nach dem letzten Gesang von A einige sichere
   instrumentale Takte – und passendes Tempo.
   - Die Gesangserkennung meldet am Song-Ende oft noch Gesang (auch Synth-Flächen und Vocal-Chops in
     Techno/House). Weil "nie vor dem Ende der letzten Phrase" Vorrang hat, wartet der Planer dann bis zum Schluss.
     Laut Referenz hätten 57 von 109 Techno/House-Songs ein instrumentales Outro von mindestens 30 s, die Erkennung
     kann aber nur bei etwa 4 % sicher sagen, dass dort kein Gesang mehr kommt.
   - Ein lockererer Ansatz für Techno/House ("kurze Vocal-Samples dürfen mitlaufen") wurde geprüft: Er macht das
     Outro bei 24–39 % nutzbar, startet aber bei 16–34 % vor dem Ende eines Abschnitts, den die Referenz als
     Gesangsphrase zählt – verletzt also das Ziel "kein Start vor Ende der letzten Phrase".
   - In den Party-Reihenfolgen liegt das Tempo oft weiter auseinander als 6 %.
2. **Beat-Versatz 22,3 ms.** Alle 5 Einsätze im Takt liegen 15–26 ms neben dem Raster von A. Ursache ist das
   Beat-Raster der Analyse: am Song-Ende von A weicht es örtlich um 15–18 ms ab, am Start von B um bis zu 11 ms.
3. **Lizenzsaubere Netze:** Beat-Erkennung praktisch gleich gut (88,1 % statt 86,9 %), Gesangserkennung rund
   3–4 Punkte ungenauer (82,4 % statt 86,1 %) – die erlaubten Lern-Songs sind weniger vielfältig.

### Vorschläge (zum Entscheiden)
1. **"Vocal-Samples bei Techno/House erlauben"** (Regel 5 wörtlich): Im Outro elektronischer Songs dürfen kurze
   Vocal-Samples beim Blend mitlaufen. Ergibt lange Blends bei einem Viertel bis einem Drittel der Techno/House-Songs –
   aber die Messung "kein Start vor Ende der letzten Phrase" würde dort Verstösse zählen (sie unterscheidet nicht
   zwischen Sample und Phrase). Wenn gewünscht: eine Einstellung "Vocal-Samples im Outro erlauben".
2. **Mehr CC0/CC-BY-Songs** (v. a. Techno/House, mit und ohne Vocal-Samples) für die Gesangserkennung sammeln und neu
   lernen – das Werkzeug ist fertig.
3. **Tempo-freundlichere Reihenfolge** im Auto-DJ (Nachbar-Songs näher im Tempo), damit öfter im Takt gemischt wird.

### Was am Handy zu prüfen ist
- [ ] Pop/Schlager-Playlist im Auto-DJ: Klingen die Song-Enden mit Echo / Filter / Hall gut? Kommt der neue Song im Takt?
- [ ] Sind die Effekte dezent genug, und ist die Abwechslung (Echo, Filter, Hall) angenehm?
- [ ] Startet der neue Song an einer guten Stelle (nicht im stillen Intro, nicht mitten im Gesang)?
- [ ] Techno/House-Playlist mit ähnlichem Tempo: Wie klingen die Übergänge?
- [ ] Die Hörproben in `hoerproben/` anhören und 👍/👎 in der App weiter nutzen (Protokoll exportieren).
- [ ] Unverändert: Drag in "Als Nächstes", Daumen/Export, Hintergrund, Weiter/Zurück, Benachrichtigung, Playlists.

---

# Frühere Runde: Beat- und Gesangserkennung (17.11.2026)

## Wie gemessen wurde

- **Test-Songs:** 56 frei lizenzierte Songs (Pop 14, Schlager 10, Rock 10, House 10, Techno 11,
  Hip-Hop 1; rund 40 mit Gesang). Zum Abstimmen und Lernen dienten **nur** 102 andere Songs
  ("Übungs-Songs"). Die Test-Songs hat kein Verfahren je "gesehen".
- **Referenzen** (die "richtige Antwort"), automatisch mit Forschungs-Software:
  - Beats und Downbeats: Beat This! und madmom. Wo sich beide nicht einig sind, gilt die Referenz als
    unsicher und wird getrennt ausgewiesen.
  - Gesang: Demucs trennt die Stimme ab. Gesang gilt als hörbar, wenn die Stimme höchstens 15 dB leiser
    ist als der Song an derselben Stelle.
- **Übergänge:** Der echte Planer der App plant zwei ganze Partys mit allen Test-Songs, insgesamt
  **110 Übergänge**.
  - Jeder Übergang wird mit der **echten Audio-Engine der App** abgespielt: Decks, Tempo mit Keylock,
    EQ, Filter, Echo, Crossfader, Begrenzer und sample-genauer Start.
  - Jeder Übergang läuft dreimal: als Mix, nur A und nur B. Daraus wird gemessen, was man tatsächlich hört.
- **Werkzeuge** (wiederholbar): `run_mixtest.sh`, `mixrender.cpp`, `eval_mix.py`, `eval_beats.py`.

## Ergebnisse gegen die Ziele

| Ziel | Gemessen | Erreicht? |
|---|---|---|
| Beat-Treffer ≥ 90 % (±30 ms) | **86,9 %** (Anfang 80,1 %); bei sicherer Referenz (49 Songs) **90,5 %** | nur bei sicherer Referenz |
| Downbeat ≥ 80 % | **71,6 %** (Anfang 54,2 %); bei sicherer Referenz (37 Songs) **80,8 %** | nur bei sicherer Referenz |
| 0 % hörbarer Gesang über Gesang | **0 von 110 (0 %)** | ja |
| Kein Übergang vor dem Ende des letzten Gesangs | **0 von 102** (mit korrigierter Referenz; vorher 2 – Referenz-Fehler bei s51, siehe unten) | ja |
| Mittlerer Beat-Versatz bei Beatmatching < 20 ms | **13,3 ms** (17,0 / 9,6 ms) – aber nur 2 Übergänge | ja, statistisch schwach |
| Drag-Test hoch und runter | grün (40 Songs: nach oben 10,1 s, nach unten 4,6 s) | ja |
| Daumen + Export (UI-Test) | grün (Speichern, Ausblenden nach 15 s, Export lesbar) | ja |
| Build sauber, alle Tests grün | 57 Unit-Tests grün (davon RealMixPlanTest mit den echten Songs); Engine-Tests am PC grün; CI: App-Build grün, Gerätetest grün (Dauerbetrieb 0 Aussetzer, Rand-Prüfung, Drag, Bewertung/Export) | ja |

Weitere Messwerte der 110 Übergänge:
- **Pegel:** Spitzenwert höchstens 0,79; der Begrenzer greift bei 0,90, also nie übersteuert. Höchster
  Lautheits-Buckel +3,8 dB (5 Übergänge über 3 dB).
- **Gesang von A nach dem Einsatz von B:** in keinem Übergang hörbar.
- **Einsatz nach dem letzten Gesang von A:** im Median 4,3 s danach.
- **"Loch" zwischen den Songs** (Mix mehr als 20 dB leiser): Median 0,7 s, höchstens 2,9 s.
  - Gemessen ist das ganz das natürliche Ausklingen von A (Schlussakkord, Fade-out). Sobald B startet,
    gibt es kein Loch mehr.
- **Gemessene Verzögerung der Engine** zwischen den Decks: +0,2 ms, also keine.
- Übergangsarten: FADE 107, BLEND 2, ECHO_OUT 1.
- **Früh-Übergänge:** 0 Angebote in 110 Übergängen. Mit diesen echten Songs war kein Fall sicher genug
  (Beat-Raster, Tonart, Gesang).

### Beat-Erkennung, letzte Runde

- **Beat-Netz v2:** beim Lernen jedes Übungsstück zusätzlich bis ±15 % schneller/langsamer abgespielt
  (mehr Tempo-Vielfalt). An den 20 zurückgehaltenen Übungs-Songs: Beats 90,3 → 91,5 %, Downbeat 70,3 → 72,2 %.
  Versuch mit zusätzlich den 31 Übungs-Songs mit unsicherer Referenz: nicht besser (90,7 % / 73,1 %), verworfen.
- **"Die 1" mit Klangwechsel:** Neue Instrumente setzen fast immer auf der "1" ein. Dieses Zeichen wird jetzt
  leicht zur Downbeat-Kurve des Netzes dazugezählt. An den zurückgehaltenen Übungs-Songs Downbeat
  72,2 → **80,2 %**, für jedes Gewicht zwischen 0,05 und 0,4 gleich (stabil); mit dem älteren Netz ebenfalls
  +7 Punkte. Kein Song wurde schlechter; geholt werden Techno-Songs, bei denen jeder Beat gleich klingt.
- **Örtliche Feinkorrektur des Rasters** (gegen Songs, die ihr Timing zwischen Teilen verschieben):
  an den Übungs-Songs leicht schlechter (91,5 → 90,9 %), daher verworfen.
- Ergebnis an den Test-Songs (nie zum Abstimmen benutzt): Beats 85,6 → **86,9 %**, Downbeat
  63,1 → **71,6 %**. Nur Songs mit sicherer Referenz: Beats **90,5 %**, Downbeat **80,8 %**.

### Letzte Runde: örtliches Beat-Vertrauen und Referenz-Korrektur

- **Örtliches Beat-Vertrauen:** Die Analyse misst jetzt pro Beat, wie weit die deutlich gehörten Beats in der
  Umgebung (±2 Takte) vom festen Raster abweichen. Der Planer matcht nur noch, wenn das Raster **genau an der
  Mix-Stelle** passt (A ab dem Einsatz, B ab seinem Startpunkt: höchstens 20 ms; unbekannt, z. B. ohne
  Schlagzeug, zählt als unsicher). Sonst gibt es wie bei unsicherem Raster einen kurzen, sauberen Fade.
  - Geprüft an den 20 zurückgehaltenen Übungs-Songs: 4 % aller Stellen werden gesperrt, ein Drittel der
    wirklich schlechten Stellen wird erkannt, der Fehler an den erlaubten Stellen sinkt von 9,1 auf 7,6 ms.
    Grenze 15–25 ms gleich gut, gewählt 20 ms.
  - Bei den Test-Songs: s19 → s46 (vorher 60 ms daneben, s46 verschiebt sein Timing im Intro) wird ein Fade.
    Auch s15 → s43 (vorher gut, 6,5 ms) wird ein Fade, weil s43 ohne deutliches Schlagzeug beginnt – das ist
    der in Kauf genommene Fehlalarm der Regel ("im Zweifel ohne Beatmatching").
- **Referenz-Korrektur (Gesang):** Bei s51 hat die Gesangsspur von Demucs am Schluss ein gleichbleibendes
  Rauschen (−44,4 dB, knapp über der Schwelle von −45 dB). Ab 215 s ist dieses "Gesang" sogar **lauter als der
  ganze Song** (der Song ist dort schon bei −60 bis −98 dB) – physikalisch unmöglich, also ein Trenn-Fehler.
  - Neue Regel: Die Stimme zählt nur, wenn sie nicht mehr als 3 dB lauter ist als der ganze Song.
  - Das ändert nur Song-Schlüsse: 4 Test-Songs (um 0,6–2,2 s) und 5 Übungs-Songs (um 0,5–4,3 s). Zusammen sind
    es 5 von 7399 s bzw. 12 von 10728 s Gesang.
  - Die App macht bei s51 nichts anders als vorher: B setzt bei 216,4 s ein, das Lied ist bei 215,9 s hörbar
    zu Ende. Mit der alten Regel hätte das als 0,6 s "zu früh" gegolten.

### Gesangserkennung (neu: kleines neuronales Netz)

- Genauigkeit pro Zeitpunkt an den Test-Songs: **86 %** (vorher 70 %).
- Letzter Gesang mehr als 0,5 s zu früh erkannt: **2 von 52** Test-Songs (vorher 14).
- Erster Gesang von B mehr als 0,5 s zu spät erkannt: 5 von 52.

## Ursachen, wo die Ziele verfehlt sind

1. **Beats und Downbeats.**
   - Bei sicherer Referenz sind beide Ziele erreicht (90,5 % / 80,8 %). Über alle 56 Songs nicht
     (86,9 % / 71,6 %): Bei 7 Songs (Beats) bzw. 19 Songs (Downbeats) widersprechen sich schon die zwei
     Referenz-Programme – dort ist auch die "richtige Antwort" unsicher.
   - Von den 37 Songs mit sicherer Downbeat-Referenz sind 25 richtig. 6 haben schon falsche Beats: Rock live
     ohne festes Schlagzeug (s12, s13), Tempo-Oktave 100 statt 200 BPM (s31), ungerader Takt (s01, 3/4) u. a.
     Bei 6 ist nur "die 1" verschoben (meist House: jeder Takt klingt gleich).
   - Das Beat-Netz der App ist absichtlich klein: ca. 1 s Rechenzeit pro Song auf dem Handy. Es ist mit nur
     71 Übungs-Songs trainiert. Grosse Forschungs-Netze lernen an Hunderten Stunden Musik.
   - **Beat-Versatz:** Mit dem örtlichen Beat-Vertrauen bleiben nur 2 beatgematchte Übergänge (17,0 / 9,6 ms,
     Mittel 13,3 ms). Ohne diese Regel waren es 4 mit 23,3 ms (einer 60 ms bei s46). Das Ziel ist erreicht, mit
     so wenigen Übergängen aber statistisch schwach.
2. **Kaum lange Blends.** 107 von 110 Übergängen sind kurze Fades ganz am Ende. Das ist die Folge der harten
   Gesangs-Regel:
   - Viele Songs singen bis kurz vor Schluss. Laut Referenz endet der Gesang bei 20 von 52 Songs mit
     Gesang weniger als 3 s vor dem Ende.
   - Bei Techno/House zählen auch Vocal-Samples als Gesang.
   - Weil die Erkennung am Song-Ende nie ganz sicher ist, gilt Gesang kurz vor dem Ende bis zum Ende
     ("im Zweifel warten"). So konnte der "zu frühe Einsatz" von 32 auf wenige Fälle sinken.
   - Bei den 33 Techno/House-Paaren lag es so:
     - 14 × Gesang/Vocal-Samples in A bis kurz vor Schluss;
     - 12 × Tempo zu verschieden (die Test-Songs reichen von 100 bis 150 BPM);
     - 4 × Beat-Raster nicht fest;
     - 3 × B singt gleich am Anfang.
3. **Beatmatching nur 2-mal.** Die Gründe sind dieselben, dazu das örtliche Beat-Vertrauen. Deshalb ist der
   Mittelwert von 13,3 ms statistisch schwach.
4. **s51 (Schlager):** behoben durch die Referenz-Korrektur (siehe oben). Die App wartet bis zum hörbaren
   Ende plus 0,5 s.

## Vorschläge (zum Entscheiden)

1. **Option "Vocal-Samples bei Techno/House erlauben".** Lange Blends im Outro auch dann, wenn A dort
   kurze Vocal-Samples hat, solange B gesangsfrei einsetzt. Gesang über Gesang bleibt damit ausgeschlossen.
   - Ergibt deutlich mehr echte DJ-Blends bei elektronischer Musik.
   - Verletzt aber die strenge Regel "nie vor dem letzten Gesang".
2. **Option "Im Ausklang mischen".** B setzt schon im Schlussakkord bzw. Nachhall von A ein, nicht erst
   danach.
   - Das kurze Loch (Median 0,7 s) verschwindet.
   - A hat dort höchstens noch sehr leisen Nachhall von Gesang.
3. **Beat-Netz weiter verbessern.** Mehr Übungs-Songs mit Referenzen sammeln (das CI erzeugt sie
   automatisch) und dann neu trainieren. Erwartung: einige Prozentpunkte mehr. Das Netz wird dadurch nicht
   grösser.
4. **Echte Bewertungen nutzen.** Mit 👍/👎 und dem exportierten Protokoll von echten Partys lässt sich
   sehen, welche Übergänge wirklich stören.

## Was am Handy zu prüfen ist

- [ ] Eine Playlist mit eigenen Songs (Pop/Schlager mit Gesang) im Auto-DJ laufen lassen:
  - Setzt der neue Song erst ein, wenn der alte fertig gesungen hat?
  - Ist das kurze Ausklingen zwischen den Songs okay?
- [ ] Techno/House-Playlist mit ähnlichem Tempo: Kommen lange, saubere Blends? Laufen die Beats synchron?
- [ ] Nach einigen Übergängen 👍/👎 antippen, dann **Einstellungen → Übergänge → Protokoll exportieren** und
      die Datei schicken.
- [ ] Laden eines Songs: Die Analyse dauert jetzt etwas länger, im Hintergrund. Im Emulator (langsam,
      ARM-Übersetzung) waren es ca. 9 s; auf dem Samsung sollten es nur wenige Sekunden sein. Läuft die Musik
      trotzdem ohne Aussetzer weiter?
- [ ] "Weiter"/"Zurück", Benachrichtigung, Hintergrund-Wiedergabe: unverändert?
- [ ] "Als Nächstes": einen Song von ganz unten nach ganz oben ziehen und umgekehrt.
