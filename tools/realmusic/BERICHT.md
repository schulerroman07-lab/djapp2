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
| Beat-Treffer ≥ 90 % (±30 ms) | **85,6 %** (vorher 80,1 %); bei sicherer Referenz 88,9 % | nein, knapp |
| Downbeat ≥ 80 % | **63,1 %** (vorher 54,2 %); bei sicherer Referenz 72,6 % | nein |
| 0 % hörbarer Gesang über Gesang | **0 von 110 (0 %)** | ja |
| Kein Übergang vor dem Ende des letzten Gesangs | **2 von 102** zu früh, beide derselbe Song (s51), je 0,6 s | fast |
| Mittlerer Beat-Versatz bei Beatmatching < 20 ms | **19,5 ms** (nur 4 Übergänge, einer mit 59 ms) | ja, aber kleine Zahl |
| Drag-Test hoch und runter | grün (40 Songs: nach oben 10,1 s, nach unten 4,6 s) | ja |
| Daumen + Export (UI-Test) | grün (Speichern, Ausblenden nach 15 s, Export lesbar) | ja |
| Build sauber, alle Tests grün | 55 Unit-Tests grün; CI: App-Build grün, Gerätetest grün (Dauerbetrieb 0 Aussetzer, Rand-Prüfung, Drag, Bewertung/Export) | ja |

Weitere Messwerte der 110 Übergänge:
- **Pegel:** Spitzenwert höchstens 0,79; der Begrenzer greift bei 0,90, also nie übersteuert. Höchster
  Lautheits-Buckel +3,8 dB (6 Übergänge über 3 dB).
- **Gesang von A nach dem Einsatz von B:** in keinem Übergang hörbar.
- **Einsatz nach dem letzten Gesang von A:** im Median 4,3 s danach.
- **"Loch" zwischen den Songs** (Mix mehr als 20 dB leiser): Median 0,7 s, höchstens 2,9 s.
  - Gemessen ist das ganz das natürliche Ausklingen von A (Schlussakkord, Fade-out). Sobald B startet,
    gibt es kein Loch mehr.
- **Gemessene Verzögerung der Engine** zwischen den Decks: +0,1 ms, also keine.
- **Früh-Übergänge:** 0 Angebote in 110 Übergängen. Mit diesen echten Songs war kein Fall sicher genug
  (Beat-Raster, Tonart, Gesang).

### Gesangserkennung (neu: kleines neuronales Netz)

- Genauigkeit pro Zeitpunkt an den Test-Songs: **86 %** (vorher 70 %).
- Letzter Gesang mehr als 0,5 s zu früh erkannt: **2 von 52** Test-Songs (vorher 14).
- Erster Gesang von B mehr als 0,5 s zu spät erkannt: 5 von 52.

## Ursachen, wo die Ziele verfehlt sind

1. **Beats und Downbeats.**
   - Die meisten Fehler kommen von Songs ohne festes Schlagzeug (Rock live, Balladen).
   - Dazu kommen:
     - Tempo-Oktaven: 100 statt 200 BPM, bei denen auch die Referenzen uneins sind;
     - ungerade Takte (s01 im 3/4-Takt);
     - einzelne Songs mit konstantem Versatz von ca. 40 ms (s20).
   - Beim Downbeat verwechselt das Netz oft die "1" mit der "3". Bei 15 der 56 Songs widersprechen sich
     schon die zwei Referenz-Programme.
   - Das Beat-Netz der App ist absichtlich klein: ca. 1 s Rechenzeit pro Song auf dem Handy. Es ist mit nur
     71 Übungs-Songs trainiert. Grosse Forschungs-Netze lernen an Hunderten Stunden Musik.
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
3. **Beatmatching nur 4-mal.** Die Gründe sind dieselben. Deshalb ist der Mittelwert von 19,5 ms
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
