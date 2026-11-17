# Bericht: Übergänge mit echter Musik – objektive Messung

Stand: 17.11.2026. Alle Zahlen sind gemessen, nicht geschätzt. Kurzfassung am Ende.

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
| Kein Übergang vor dem Ende des letzten Gesangs | **2 von 102** zu früh, beide derselbe Song (s51), je 0,6 s | fast |
| Mittlerer Beat-Versatz bei Beatmatching < 20 ms | **23,3 ms** (nur 4 Übergänge: 6,5 / 9,6 / 17,0 / 60,1 ms) | nein (ein Ausreisser, siehe Ursachen) |
| Drag-Test hoch und runter | grün (40 Songs: nach oben 10,1 s, nach unten 4,6 s) | ja |
| Daumen + Export (UI-Test) | grün (Speichern, Ausblenden nach 15 s, Export lesbar) | ja |
| Build sauber, alle Tests grün | 55 Unit-Tests grün; Engine-Tests am PC grün; CI: App-Build grün, Gerätetest grün (Dauerbetrieb 0 Aussetzer, Rand-Prüfung, Drag, Bewertung/Export) | ja |

Weitere Messwerte der 110 Übergänge:
- **Pegel:** Spitzenwert höchstens 0,79; der Begrenzer greift bei 0,90, also nie übersteuert. Höchster
  Lautheits-Buckel +4,0 dB (6 Übergänge über 3 dB).
- **Gesang von A nach dem Einsatz von B:** in keinem Übergang hörbar.
- **Einsatz nach dem letzten Gesang von A:** im Median 4,3 s danach.
- **"Loch" zwischen den Songs** (Mix mehr als 20 dB leiser): Median 0,7 s, höchstens 2,9 s.
  - Gemessen ist das ganz das natürliche Ausklingen von A (Schlussakkord, Fade-out). Sobald B startet,
    gibt es kein Loch mehr.
- **Gemessene Verzögerung der Engine** zwischen den Decks: +0,1 ms, also keine.
- Mit dem neuen Beat-Netz neu gemessen: Gesang, Einsatz, Pegel und Loch unverändert; Übergangsarten gleich
  (FADE 105, BLEND 2, FILTER 2, ECHO_OUT 1).
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
   - **Beat-Versatz 23,3 ms:** Nur 4 Übergänge mit Beatmatching. Drei liegen bei 6,5 / 9,6 / 17,0 ms. Einer
     (s19 → s46) bei 60 ms: s46 verschiebt sein Timing zwischen Intro und Hauptteil um ca. 40 ms, das feste
     Raster der App passt im Intro nicht. Eine örtliche Korrektur machte die Übungs-Songs schlechter (siehe
     oben). Mit dem vorigen Netz waren es 19,5 ms (dieselben 4 Übergänge, 59 ms beim selben Paar) – der
     Unterschied liegt im Bereich des Zufalls.
2. **Kaum lange Blends.** 105 von 110 Übergängen sind kurze Fades ganz am Ende. Das ist die Folge der harten
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
3. **Beatmatching nur 4-mal.** Die Gründe sind dieselben. Deshalb ist der Mittelwert von 23,3 ms
   statistisch schwach.
4. **s51 (Schlager):** Die Referenz hört noch 1,2 s Gesang, nachdem der Song schon unhörbar leise ist
   (Hall). Die App wartet bis zum hörbaren Ende plus 0,5 s.

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
