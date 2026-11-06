# Changelog

## 2.0 – Umbau zum Auto-DJ

Die App ist jetzt ein einfacher, eleganter Auto-DJ: **Playlist wählen, Play drücken** – die App kennt
alle Songs, ordnet sie nach Stimmung und spielt die Party mit abwechslungsreichen Übergängen im Takt
selbstständig durch. Gästewünsche per QR-Code werden passend eingebaut.

### 1. Aufgeräumt
- **Entfernt:** DJ-Deck-Ansicht (2 Decks, Wellenformen, Jog), Crossfader, EQ-/Effekt-Regler,
  Aufnahme (UI und Aufnahme-Code in der Engine), Ausgabe-Modi für Kopfhörer-Vorhören, Spektrum-Berechnung,
  Song-Vorschläge, Einführung.
- **Bleibt:** Audio-Engine (Oboe, Time-Stretch, Filter, Effekte – jetzt nur intern für Übergänge),
  Bibliothek, Playlists inkl. M3U-/Ordner-Import/Export, Hintergrund-Wiedergabe,
  Benachrichtigungs-Steuerung, Gästewünsche, Einstellungen.

### 2. Start-Animation
- System-Startbildschirm (SplashScreen-API): schwarz mit leuchtendem Punkt. Daraus **wächst das Logo**
  ("Beat-Ring": Ring mit drei Equalizer-Balken, als Vektor gezeichnet), schwingt wie eine Schallwelle
  und beruhigt sich; die Balken steigen, darunter erscheint der Name. Animation 1,0 s + 0,25 s weiche
  Überblendung in die App, mit fester Obergrenze 1,05 s (Uhrzeit) – auch wenn das Gerät beim Start
  beschäftigt ist oder langsam zeichnet. Im Emulator gemessen (ohne Bildschirmfotos): 1,18–1,68 s + 0,25 s
  = **1,43–1,93 s** (vorher bis 2,3 s).
- Neues App-Symbol im selben Stil (inkl. einfarbiger Version für Themensymbole).
- **Name = Platzhalter, nur in `strings.xml` (`app_name`)** – auch die Download-Dateien (`<Name>.apk`)
  übernehmen ihn automatisch. Das Logo enthält keinen Text.

### 3. Hauptbildschirm (Player)
- Grosses Cover (atmet leicht im Beat), Titel, Interpret, Länge (+ BPM und Tonart).
- **Fortschrittsbalken:** tippen = springen, ziehen = spulen (Sprung beim Loslassen).
- **Zurück · Play/Pause · Weiter** – Zurück/Weiter immer mit kurzem, sauberem Übergang im Takt.
- Lautstärke über die Handy-Tasten.
- Oben: **QR-Knopf mit Zahl** neuer Wünsche, **Menü** (Playlists, Wünsche, Als Nächstes, Bibliothek,
  Einstellungen).
- Unten: nächster Song + nächster Übergang mit Countdown; **hochwischen** öffnet "Als Nächstes"
  (Drag & Drop mit Auto-Scroll, Menü pro Song, nach Stimmung ordnen, Smart Shuffle).
- Ohne Musik: Startansicht "Bereit für die Party" mit "Playlist wählen".
- Lebendiger Hintergrund (Schwarz/Smaragd), reagiert auf Beat und Energie.
- **Flüssigkeit:** höchste Bildrate des Displays (z.B. 120 Hz) wird angefordert (ausser im
  Energiesparmodus); Position/Puls werden nur beim Zeichnen gelesen (keine Neuaufbauten 60×/s);
  Baseline-Profil (vorkompilierter Code); nichts läuft im Audio-Thread.
- Hoch- und Querformat (Querformat: Cover links, Bedienung rechts). Abstand zu den Rändern für die
  Wischgesten.

### 4. Ablauf: Playlist → Play
- Erster Song startet **sofort**; die übrigen werden im Hintergrund analysiert – **Songs der
  Warteschlange zuerst** – und das Ergebnis in Room gespeichert (pro Datei nur einmal).
- Analyse pro Song: BPM, Beat-Raster, Downbeats, Phrasen (8/16/32), Intro/Outro, Energie-Verlauf,
  Breaks, Gesang (grob), **Lautheit** (neu, wie EBU R128), **Tonart** (neu: Chroma + Krumhansl-Profile,
  Camelot-Anzeige wie "8A"), **Genre** (ID3-Tag, sonst Schätzung aus Tempo/Energie).
- **Reihenfolge nach Stimmung** (Einstellungen und beim Start wählbar):
  - Energie-Kurve **"Steigernd"** (ruhig → Höhepunkt) oder **"Party-Bogen"**
    (Warm-up → Peak bei ca. 70 % → Ausklang)
  - **Genre-Blöcke** (z.B. Rock → Party/Schlager → Techno), innerhalb jedes Blocks gilt die Kurve
  - **Originalreihenfolge**
- Sobald die Analyse neue Werte liefert, ordnet sich die Warteschlange neu (manuell verschobene Songs
  bleiben, wo sie sind). Nie "passt nicht"-Meldungen.
- Datenbank v5 (Tonart, Lautheit) mit Migration ohne Datenverlust; bestehende Songs werden einmal
  nachanalysiert.

### 5. Übergänge
- Standard kurz vor dem Liedende (Outro / letzte Phrasen), fertig vor dem natürlichen Ende,
  Stille am Ende ignoriert; im Takt, auf Phrasengrenzen, Lautheit angeglichen, Begrenzer gegen Übersteuern.
- Typen (je eine Klasse in `automix/transitions/`): Bass-Swap, Filter-Sweep, Echo-Out, Loop, Hall
  (Reverb-Wash), **Schnitt auf die "1" nach einem Break** (neu), **langer Blend** (neu: 32 Takte bei
  gleichem Tempo und passender Tonart).
- Typ pro Song-Paar nach Tempo-Abstand, Tonart, Energie, Intro/Outro und Gesang (nie zwei Gesangsteile
  übereinander); **Rotation** für Abwechslung (nie derselbe direkt hintereinander, lange nicht
  benutzte Typen bevorzugt). Grosse Tempo-Unterschiede → Echo-Out, Hall oder Schnitt.
- Tempo kehrt nach dem Übergang in 8–16 Takten fliessend zum Original zurück (Tonhöhe bleibt).
  Bei sehr kurzen Songs waren es bisher nur 4 Takte → jetzt **nie weniger als 8 Takte** (Fix).
  Im Gerätetest (30 min) belegt: "Tempo zurück auf 100 % (-0,8 % über 8 Takte)", "(3,4 % über 16 Takte)" u.a.
- **Früh-Übergang:** passen zwei Songs fast perfekt (Tempo ±2 %, harmonische Tonart, kein Gesang),
  darf ab ca. 55 % des Songs auf einer 32er-Phrase gemischt werden. Einstellung **Fragen** (Standard) /
  **Immer** / **Nie**. Bei "Fragen": Einblendung "Passt perfekt – jetzt mischen?" mit Ja/Nein und
  10-Sekunden-Kreis; keine Antwort = normal bis zum Ende.
- Unten klein: nächster Übergang mit Countdown. Protokoll der Entscheidungen (Einstellungen →
  Übergänge) und in Logcat ("AutoMix").
- Gesangs-Erkennung verbessert: Vergleich mit den instrumentalen Teilen (unteres Viertel) statt dem
  Median – erkennt jetzt auch Songs mit Gesang in mehr als der Hälfte.

### 6. Gästewünsche
- QR-Knopf oben + eigene Seite. Der QR-Code erscheint **sofort ohne Einrichten** (lokaler Webserver
  im WLAN bzw. Hotspot des Handys; Internet-Modus nur, wenn der Entwickler ein Backend eingetragen hat).
- Wünsche, die in der Bibliothek sind, werden **automatisch an einer passenden Stelle unter den
  nächsten 5 Songs** eingereiht (Tempo, Energie, Tonart, Genre) und angeheftet. Fehlende Wünsche werden
  angezeigt und können abgehakt werden.

### 7. Einstellungen (vereinfacht)
- Ablauf · Übergänge (Stil ruhig/ausgewogen/Club, Früh-Übergänge, Länge, Typen, Protokoll) ·
  Bibliothek · Import & Export · Design (Farbe, Hintergrund Aus/Energiesparen/Voll) · Sprache ·
  Hilfe (neue FAQ, Hintergrund-Wiedergabe) · Über · Credits · Datenschutz.
- Hochformat: Liste → Bereich; Querformat: Bereiche links.

### Entscheidungen (selbst getroffen)
- **Hoch- und Querformat** statt nur Querformat: Ein Player mit grossem Cover wirkt im Hochformat am
  besten; im Querformat wird die Bedienung neben das Cover gelegt.
- **"Zurück"** spielt den vorherigen Song nochmals (kurzer Übergang) und macht danach mit dem Song
  weiter, der lief. Ohne Verlauf: aktueller Song von vorne (ebenfalls mit Übergang).
- **Spulen** ist während eines laufenden Übergangs gesperrt (sonst stimmen die Beats nicht mehr).
- **Playlist starten, während Musik läuft:** der aktuelle Song läuft fertig, dann geht es mit der neuen
  Playlist weiter (sauberer Übergang).
- **Ausgabe:** immer Stereo (Lautsprecher/Kopfhörer/Bluetooth); grösserer Audio-Puffer (4 Bursts) für
  Aussetzer-Sicherheit – Latenz spielt beim Auto-DJ keine Rolle.
- **Wünsche** kommen unter die nächsten 5 Songs (Gäste wollen ihren Song bald hören), aber an die Stelle,
  die musikalisch am besten passt.
- **Genre-Schätzung** ohne Tag: ruhig (Energie < 0,38) → Chill; ≥ 134 BPM und energiegeladen →
  Techno/Uptempo; 118–134 BPM energiegeladen → House; sonst Pop.
- **Version 2.0** (grosser Umbau).

### Tests
- Unit-Tests (lokal und auf GitHub): Planer (inkl. langer Blend, Schnitt nach Break, Weiter/Zurück,
  Früh-Übergang), **3000 zufällige Song-Paare** (jede Entscheidung gültig), **2-Stunden-Party-Kette**,
  stimmungsgeordnete Party (alle Typen kommen vor, keiner dominiert, keine Wiederholung),
  Stimmungs-Ordnung (Kurven, Bogen, Genre-Blöcke, Pins), Tonarten (Camelot), Genres, Warteschlange,
  Tempo-Rückführung, M3U.
- C++ am PC: Tonart-Erkennung (24/24 Tonarten bei synthetischen Akkordfolgen), Lautheit (±0,1 dB),
  Tempo-Rampe ohne Klicks, **2-Stunden-Dauertest der echten Engine** (beide Decks, Mixer, Begrenzer,
  geplanter Start, Effekte, Loop, Tempo-Rampen) – **Ergebnis:** 2 h simuliert, 55 Übergänge,
  0 verpasste Starts, 0 NaN/Inf, 0 stille 100-ms-Blöcke (keine Aussetzer), Spitzenpegel 0,64,
  Speicher stabil (119 MB nach 24 min und nach 2 h). Wiederholbar mit `bash tools/enginetest/run.sh`.
- Übergangs-Verteilung einer stimmungsgeordneten Party mit 80 Übergängen: Echo-Out 20, Filter 19,
  Bass-Swap 17, Hall 11, Loop 8, Schnitt 5 – keine direkte Wiederholung.
- Bildschirmfotos (Hoch- und Querformat): Start-Animation, Player, Startansicht, Früh-Übergang-Frage,
  Als Nächstes, Bibliothek, Wünsche, Playlist, Einstellungen.

### Nachträge aus dem Gerätetest (Android-Emulator auf GitHub)
Der Gerätetest (`.github/workflows/device-test.yml`, Test `DeviceSoakTest`) startet ein echtes Android 14
im Emulator mit echter Oboe-Audioausgabe, kopiert 18 Test-Songs (MP3) aufs Gerät und bedient die App
wie ein Mensch: Start-Animation, Bibliothek, Play, Weiter, Zurück, Gästewunsch über den QR-Webserver,
App verlassen (Hintergrund) und Dauerbetrieb. Gefundene und behobene Fehler:
- **Erster Song startete erst nach bis zu 2 Minuten** (ganzer Song wurde zuerst dekodiert). Jetzt:
  **schrittweises Laden** – der Song spielt, sobald 6 s dekodiert sind, der Rest wird im Hintergrund
  nachgeladen und nahtlos übernommen. Ergebnis im Emulator: **1,7 s** von Play bis Musik.
- **Dekodieren um ein Vielfaches schneller** (Decoder-Pipeline voll ausnutzen statt 1 Block pro Runde).
- Songs, die länger als 30 s zum Laden brauchten, wurden als "nicht lesbar" übersprungen → Wartezeit
  180 s; "Zurück"/"Weiter" bricht ein laufendes Laden sofort ab.
- Die Engine nahm "Play" im Zustand "spielbar (lädt noch)" nicht an → behoben.
- Bibliotheks-Analyse pausiert, solange ein Song für die Wiedergabe lädt (Wiedergabe hat Vorrang).
- **Im Hintergrund wurde der nächste Übergang manchmal mit BPM 0 geplant** (die Anzeige-Werte werden
  dort nur alle 100 ms aktualisiert) → dann gab es nur einen Echo-Ausklang statt eines Übergangs im
  Takt. Jetzt liest der Auto-DJ BPM, Beat und Dauer direkt nach dem Laden aus der Engine.
- Gästewunsch, der schon weit hinten in der Warteschlange stand, blieb dort → wird jetzt passend unter
  die nächsten 5 Songs geholt.
- Test-Songs wie DJ-Tracks gebaut (Intro/Outro nur Schlagzeug, Bass + Akkord, Break), damit Outro,
  Break und Früh-Übergang realistisch geprüft werden.

- Ordnung "Steigernd": Ein Song, der im Tempo/Genre zu keinem anderen passt (z.B. ein ruhiger
  Chill-Song), blieb bis zum Schluss übrig und landete ausgerechnet am Höhepunkt → die Übergangs-Kosten
  zählen jetzt nur noch begrenzt, die Energie-Kurve hat Vorrang (neuer Unit-Test).

- **"Weiter"/"Zurück" warteten, bis der Ziel-Song ganz dekodiert war** (im Emulator 10–47 s). Jetzt
  startet der kurze Übergang, sobald der Song spielbar ist: Beat-Raster und Struktur kommen aus der
  gespeicherten Analyse (Datenbank) bzw. – bei "Zurück" – aus dem Speicher des eben gespielten Songs;
  der Song startet dann ab seinem ersten Downbeat. Gemessen im Emulator: **"Weiter" nach 2,5–3,7 s,
  "Zurück" nach 4,2–4,6 s hörbar** (vorher 30 s).
- Ist der Ziel-Song noch nie analysiert worden, misst die Engine sein Tempo aus den ersten ca. 18 s
  (zwei überlappende Messungen müssen übereinstimmen) → trotzdem kurzer Übergang **mit angeglichenem
  Tempo im Takt** (Emulator: Filter-Übergang nach 2,9 s hörbar; alle 19 Test-Songs am PC richtig
  erkannt). Nur wenn die Messung unsicher ist (z.B. Intro ohne Beat), klingt der laufende Song im Takt
  mit Echo aus. "Weiter" wartet auch nicht mehr, bis der laufende Song fertig geladen ist.

- Start-Animation im Emulator fotografiert (Punkt → Welle → Ring mit Balken → Name) und gemessen.

- **Hintergrund-Puls** las die Song-Position aus der Anzeige (im Hintergrund/bei voller Auslastung zu
  selten aktualisiert) und pulsierte nicht, solange der Song noch lud → liest jetzt direkt aus der
  Engine und nutzt beim Laden das gespeicherte Beat-Raster. Gemessen: **17 Pulse in 8 s bei 124 BPM
  (erwartet 16,5)**.
- **Bildschirmrand (Samsung-Gesten):** neue Rand-Prüfung im Gerätetest liest die echten Positionen aller
  Bedienelemente aus der App (Emulator auf Samsung-Grösse 1080×2340, 420 dpi, hoch + quer, Player und
  "Als Nächstes"; zusätzlich kleiner 320×640-Bildschirm) und verlangt mind. 16 dp Abstand zum Rand bzw.
  zu den Gesten-Zonen. Gefunden und behoben:
  - QR- und Menü-Knopf lagen nur ca. 6 dp unter dem oberen Rand (Wischzone Benachrichtigungen) → 20 dp.
  - Listen (z.B. "Als Nächstes") reichten bis 6 dp an den unteren Rand (Wischzone Home) → 16 dp.
  - Knöpfe, Fortschrittsbalken und Griffe waren für Bedienungshilfen (TalkBack) nicht als solche
    erkennbar → jetzt gemeldet.
  Ergebnis jetzt: **kein Bedienelement am Rand** (alle vier Ansichten und kleiner Bildschirm).

**Ergebnis 2-Stunden-Dauerbetrieb im Emulator** (echtes Android 14, echte Oboe-Ausgabe, App im
Hintergrund, Lauf vom 6.11.2026): **bestanden** – 48 Übergänge in 120 min, **0 Aussetzer (xruns)**,
**0 Sekunden ohne Musik**, kein Absturz, Vordergrund-Dienst die ganze Zeit aktiv. Alle Übergänge
starteten im Outro bzw. auf der letzten Phrase vor dem Ende; nie zweimal derselbe Typ hintereinander
(Filter 8, Bass-Swap 7, Echo-Out 6, Loop 6, Hall 2 in den letzten 29). **Früh-Übergang:** 1× gefragt
und angenommen. Erster Song nach 1,7–4,7 s ab Play, "Weiter" nach ca. 12 s und "Zurück" nach
ca. 37 s mit Übergang im Takt, Gästewunsch (HTTP 200) an Platz 2 eingereiht. Kein einziger Plan mehr
mit "kein Tempo erkannt". Der Gerätetest läuft bei jeder App-Änderung automatisch (15 min; für
2 Stunden in `tools/devicetest/minutes.txt` 120 eintragen).

**Tonart-Test mit echten Aufnahmen** (`.github/workflows/key-test.yml`, 30 frei lizenzierte Aufnahmen
von Wikimedia Commons mit bekannter Tonart): 50 % exakt, 10 % Paralleltonart, 20 % Quint-Nachbar,
20 % falsch → **80 % für harmonisches Mischen brauchbar** (Paralleltonart und Quinte gelten beim
Mischen als passend).

### Offene Punkte (2.0)
1. **Noch nicht auf einem echten Samsung-Handy getestet** – getestet im Android-Emulator (siehe oben).
   Bitte `TESTEN.md` durchgehen (Abschnitte A–J). Was der Emulator nicht zeigen kann: echter Klang über
   Lautsprecher/Bluetooth, 120-Hz-Bildschirm, Samsung-Akku-Optimierung, echte Musikdateien.
2. Der 2-Stunden-Dauertest lief im Emulator mit künstlichen Test-Songs; ein 2-h-Test mit echter Musik
   auf dem Handy steht aus (TESTEN.md Punkt 34).
3. Tonart-Erkennung ist bei echten Songs ungenauer als im Test (Schlagzeug, Effekte). Unsichere Songs
   bekommen "–" und werden neutral behandelt.
4. Genre ohne ID3-Tag ist nur geschätzt (aus Tempo und Energie).
5. "Weiter"/"Zurück" zu einem noch nie analysierten Song ohne klaren Beat am Anfang (z.B. Intro nur mit
   Gesang/Klavier): Echo-Ausklang im Takt statt Tempo-Angleichung (Tempo noch unbekannt).
6. Flüssigkeit (60/120 Hz) lässt sich im Emulator nicht messen: er zeichnet ohne Grafikkarte
   (ca. 10 Bilder/s mit, 13 Bilder/s ohne animierten Hintergrund; Logcat: bis 3 s pro Bild im
   Grafik-Treiber). Bitte auf dem Handy prüfen (TESTEN.md Punkt 13); falls es ruckelt:
   Einstellungen → Design → Hintergrund "Energiesparen".
7. Hintergrund "Voll" (60/120 Hz) braucht etwas mehr Akku → bei langen Events "Energiesparen".
8. Gästewünsche ohne Internet funktionieren nur im selben WLAN bzw. Hotspot.
9. Android Auto: nur Steuerung (Zurück, Play/Pause, Weiter), kein Durchblättern der Bibliothek.

## 1.2 – Bessere Auto-Mix-Übergänge, Warteschlange, Design

### Prio 1 – Auto-Mix
1. **Übergang erst am Liedende**
   - Stille am Schluss wird erkannt (Wellenform und Beat-Merkmale) und ignoriert.
   - Der Übergang startet im Outro bzw. in den letzten 32 Takten, nie mitten im Song. Er ist fertig, bevor der alte Song natürlich endet.
   - Ohne erkanntes Outro: Start rechtzeitig vor dem Ende, auf einer Phrasengrenze (16/8, notfalls 4 Takte).
   - Wird Auto-Mix erst kurz vor Schluss gestartet, wird kürzer überblendet oder Echo/Schnitt verwendet.
2. **Tempo zurück aufs Original** *(Fehler behoben)*
   - Die Rückführung wurde bisher sofort abgebrochen, sobald der nächste Song geladen wurde – der Song lief deshalb dauerhaft schneller oder langsamer.
   - Jetzt geht das Tempo nach jedem Übergang in einer weichen S-Kurve über 8–16 Takte auf 100 % zurück. Der Fortschritt richtet sich nach der Song-Position, nicht nach der Uhr.
   - Keylock wird dafür eingeschaltet: Die Tonhöhe bleibt gleich.
   - Der nächste Übergang wird mit dem Originaltempo geplant.
   - Getestet: Engine-Test (keine Klicks, kein Pegeleinbruch, Tonhöhe exakt) und eine Kette von 7 Songs mit 63–128 BPM.
3. **Flüssigere Übergänge**
   - **Equal-Power**-Kurve am Crossfader bei Blends: Die Gesamtlautstärke bleibt gleich, ohne Loch und ohne Buckel.
   - Bass-Tausch weich über 1 Beat genau auf dem Takt. Die Mitten des neuen Songs öffnen langsam. Beim Filter-Übergang öffnet sich zuerst der neue Song, dann schliesst der alte.
   - Filterbewegungen werden in der Engine geglättet (kein „Zirpen“).
   - **Lautheit pro Song** wird beim Laden gemessen (wie EBU R128, vereinfacht). Der neue Song wird an den alten angeglichen (−9 bis +4 dB). Ein **Begrenzer** am Master verhindert Übersteuern.
   - Länge passend zum Song-Paar: ruhige Songs länger (bis 32 Takte), Club-Tracks knapp (max. 16), kurzes Intro → kürzer.
   - **Protokoll** auf der Auto-Mix-Seite: welcher Übergang wann und warum gewählt wurde. Zusätzlich in Logcat unter „AutoMix“.
4. **Warteschlange automatisch sortieren**
   - Neue Songs werden dort eingereiht, wo Genre (ID3-Tag), BPM und Energie am besten passen. Die Energie steigt sanft an, und es gibt nie eine Meldung „passt nicht“.
   - Vom DJ verschobene Songs sind angeheftet 📌 und bleiben an ihrem Platz.
   - „Optimieren“ respektiert angeheftete Songs. Die automatische Sortierung lässt sich ausschalten (Einstellungen → Auto-Mix).
   - Datenbank v4: neue Spalte „Genre“ (Migration ohne Datenverlust). Das Genre wird beim Einlesen gelesen (Android 10 über die Genre-Tabelle).
5. **Crossfader im Auto-Mix gesperrt**
   - Der Crossfader bewegt sich nur automatisch; ein Schloss-Symbol zeigt die Sperre. Wischgesten (App verlassen) verschieben nichts mehr und stoppen den Auto-Mix nicht.
   - Knopf **„Manuell übernehmen“** unter den Lautstärkereglern beendet Auto-Mix bewusst.
   - Mehr Abstand zum Bildschirmrand: unten so viel wie die Startbildschirm-Geste braucht, seitlich wegen Zurück-Geste und Kamera-Aussparung.

### Prio 2 – Bedienung
6. **Warteschlange verschieben**
   - Drag & Drop mit **Auto-Scroll** am oberen/unteren Rand.
   - Menü ⋮ pro Song: Als Nächstes spielen, Ans Ende, Entfernen.
   - In der Bibliothek (Song lange drücken): „Als Nächstes spielen“ / „Ans Ende der Warteschlange“.
7. **Shuffle**
   - Playlist → „Zufällig“: spielt die Playlist in zufälliger Reihenfolge im Auto-Mix.
   - Auto-Mix → **„Smart Shuffle“**: zufällig, aber mit gutem Flow (wählt jeweils unter den 3 bestpassenden).

### Prio 3 – Design & Name
8. **Animierter Hintergrund**
   - Weiches Leuchten in Schwarz/Smaragd (bzw. der Akzentfarbe). Es wandert langsam, pulsiert im Beat und wird mit der Energie der Musik heller.
   - Es zeichnet nur den Hintergrund neu und stoppt automatisch, wenn die App im Hintergrund ist.
   - Einstellungen → Design: **Aus / Energiesparen (ca. 15 Bilder/s, dezenter) / Voll (60 Bilder/s)**.
9. **App-Name an einer Stelle**
   - Name und Version stehen nur noch in `gradle.properties` (`appName=…`, `appVersion=…`). App, Aufnahme-Ordner, Benachrichtigung und die Download-Dateien (`<Name>.apk/.aab`, Release-Titel) übernehmen ihn automatisch.
   - Nur der Download-Link im README muss dann von Hand angepasst werden.
   - Versionsnummer ab jetzt **1.2.x**.

### Tests
- Neue Unit-Tests (lokal alle grün):
  - Stille am Ende, Start in den letzten Phrasen, Fallback ohne Outro, später Start (`TransitionPlannerTest`)
  - Tempo-Rückführung über mehrere BPM-Paare (`TempoRampTest`)
  - Sortierung/Pins/Smart Shuffle (`QueueFlowTest`)
  - Länge pro Song-Paar und Begründungen
- C++-Tests am PC:
  - Tempo-Rampe mit Keylock (keine Klicks, Tonhöhe exakt)
  - Lautheitsmessung (±0,1 dB genau)
- Neuer Screenshot: Mischpult mit gesperrtem Crossfader. Test-Checkliste `TESTEN.md` Abschnitte N–P.

### Offene Punkte (1.2)
1. **Nicht auf einem echten Handy getestet** – bitte `TESTEN.md` N–P (und J–M) durchgehen.
2. Genre kommt nur aus dem ID3-Tag. Fehlt es, sortiert die App nur nach BPM/Energie.
3. Lautheit wird beim Laden in ein Deck gemessen (nicht in der Datenbank). Der allererste Song einer Session gilt als Referenz.
4. Der Abstand zum Rand richtet sich nach der Gestennavigation. Wie sich das auf deinem Samsung anfühlt, muss ausprobiert werden.
5. Animierter Hintergrund „Voll“ braucht etwas mehr Akku. Bei langen Events „Energiesparen“ wählen.
6. Unverändert aus 1.1: Gesangs-/Downbeat-Erkennung grob, kein Android-Auto-Browser, kein Neustart per Bluetooth-Taste nach komplettem Beenden.

### 10 Namensvorschläge (noch NICHT umbenannt)
Grob geprüft per Suche im Google Play Store (Stand Oktober 2026). „frei“ heisst: keine App mit genau diesem Namen gefunden. Vor der Entscheidung bitte nochmals selbst im Play Store und auf Markenrechte prüfen (z.B. swissreg.ch, EUIPO).

| # | Name | Idee | Play Store |
|---|------|------|-----------|
| 1 | **Seamix** | „seamless mix“ – Songs fliessen nahtlos ineinander. | frei |
| 2 | **Segue** | Musiker-Begriff für einen nahtlosen Übergang zum nächsten Stück. | frei (als App-Name) |
| 3 | **Djinn** | DJ + Flaschengeist: der Party-Geist, der von selbst auflegt. | frei |
| 4 | **Groovepilot** | Autopilot für den Groove – die Party fliegt von selbst. | frei |
| 5 | **Floorfiller** | DJ-Wort für einen Song, der die Tanzfläche füllt. | frei (nur als Wort in Radio-Apps) |
| 6 | **Autoblend** | Sagt genau, was die App tut: automatisch überblenden. | frei |
| 7 | **Blendo** | Kurz, verspielt, leicht zu merken – „blend“ mit Party-Endung. | frei |
| 8 | **Beatdrift** | Beats gleiten sanft ineinander. | frei |
| 9 | **Flowmix** | Flow + Mix: guter Ablauf ohne Pausen. | Name frei, Paketname „com.flowmix“ von einem Spiel belegt |
| 10 | **Vibemix** | Mischt nach Stimmung/Energie. | nur als Funktionsname in einer Foto-App |

Belegt (deshalb nicht in der Liste): PartyPilot, Mixmate, MixPilot, Nightflow, Beatpilot.

## 1.1 – Hintergrund, DJ-Übergänge, Playlists, Import/Export

### 1. Hintergrund-Wiedergabe & Steuerung über Benachrichtigung
- Der App-Kern (Decks, Auto-Mix, Wünsche) lebt jetzt unabhängig vom Fenster: Musik und Auto-Mix laufen weiter bei ausgeschaltetem Bildschirm, im Hintergrund oder wenn eine andere App offen ist.
- Neuer Hintergrund-Dienst mit **Media3 / MediaSession**: Medien-Benachrichtigung und Sperrbildschirm mit Cover, Titel, Interpret, Fortschritt, **Play/Pause**, **Nächster Song** (startet einen sauberen Übergang im Takt statt hartem Schnitt) und **Auto-Mix an/aus**.
- Steuerung über **Bluetooth-Kopfhörer / Auto** (Media-Tasten: Play/Pause/Weiter).
- **Audio-Fokus**: Anruf → Pause, danach automatisch weiter; Navigationsansage → kurz leiser; andere Musik-App → Pause; Kopfhörer ausgesteckt → Pause (im normalen Ausgabe-Modus).
- Auto-Mix wartet während einer Pause (z.B. Anruf) statt zu überblenden; geplante Starts werden ebenfalls angehalten.
- **Akku-Optimierung**: Hinweis in Einstellungen → Audio („Wiedergabe im Hintergrund“) mit Knopf zu den App-Einstellungen, Tipp beim Start des Auto-Mix, neue FAQ (inkl. Samsung „Apps im Standby“).
- Im Hintergrund aktualisiert die App ihre Anzeige seltener (spart Akku); der Auto-Mix liest die Positionen direkt aus der Engine (bleibt genau).

### 2. Auto-Mix: echte DJ-Übergänge
- **Analyse pro Titel** (beim Import, in der Datenbank gespeichert): Beat-Raster, Downbeats, Takte, Phrasen (8/16/32 Takte), Intro/Outro, Energie-Verlauf, Breaks, grobe Gesangs-Erkennung (über den Frequenzbereich der Stimme).
- **6 Übergangs-Typen**, jeder in einer eigenen Klasse (`automix/transitions/`): Bass-Swap, Filter-Sweep, Echo-Out, Loop, Hall-Übergang (Reverb Wash), harter Schnitt auf dem Downbeat.
- **Planer** (`TransitionPlanner`): wählt pro Song-Paar den Typ nach Tempo-Abstand, Energie, Intro/Outro-Länge, Breaks und Gesang; **nie zwei Gesangsteile übereinander**; Abwechslung (gleicher Typ nicht hintereinander); Übergänge im Takt an Phrasengrenzen.
- **Tempo**: kleine Unterschiede werden angeglichen und danach über ~16 Takte unhörbar zum Originaltempo zurückgeführt; grosse Unterschiede → Echo-Out, Schnitt oder Hall statt verzerrtem Tempo.
- **Sample-genauer Start**: Die Engine startet den neuen Song exakt auf dem Downbeat (Genauigkeit ~1 ms).
- Neue Engine-Funktionen: Loop (auch mit Keylock), Hall mit Ausklingen, einstellbare Echo-Rückkopplung.
- **Anzeige**: „Als nächstes“ mit Titel, Übergangstyp, Länge und **Countdown**; Typ vorher ändern, Song überspringen oder sofort mixen.
- **Einstellungen** (im Auto-Mix-Fenster und unter Einstellungen → Auto-Mix): Übergangstypen einzeln an/aus, Stil (ruhig / ausgewogen / Club), Länge (Auto / 4 / 8 / 16 / 32 Takte).

### 3. Playlists
- Neuer Knopf **„Playlists“** oben: Übersicht mit Cover-Collage, Anzahl Songs und Dauer.
- Erstellen, umbenennen, löschen (mit Bestätigung); Songs hinzufügen (Suche), entfernen, Reihenfolge per **Drag & Drop**.
- Ganze Playlist in die Warteschlange oder direkt **Auto-Mix starten**; einzelne Songs auf A/B laden oder in die Warteschlange.
- In der Bibliothek: Song **lange drücken** → „Zu Playlist hinzufügen“ (auch neue Playlist).
- Gespeichert in der lokalen Datenbank (Room, Version 3; Umstellung ohne Datenverlust, Schema geprüft).

### 4. Import & Export (Einstellungen → Import & Export, oder in einer Playlist → Exportieren)
- Export als **.m3u8** (UTF-8, Pfade relativ zum Speicher).
- Import von **.m3u/.m3u8**: Zuordnung über Pfad, sonst Dateiname, sonst Interpret + Titel; nicht gefundene Songs werden angezeigt.
- **Export als Ordner**: Zielordner wählen (z.B. USB-Stick), alle Songs werden kopiert (optional mit Nummern für die Reihenfolge) + .m3u8 dazu; Fortschrittsanzeige, Abbrechen möglich.
- **Ordner als Playlist importieren**.

### Tests
- Neue automatische Tests: Struktur-Analyse und Planer (9 Tests), M3U lesen/schreiben/zuordnen (4 Tests), C++-Tests für Loop, Hall/Echo-Ausklang und Beat-Merkmale.
- Neue Bildschirmfotos: Auto-Mix mit „Als nächstes“-Karte, Playlist.

### Offene Punkte (1.1)
1. **Nicht auf einem echten Handy getestet** – bitte `TESTEN.md` Abschnitt J–M durchgehen.
2. Gesangs-Erkennung ist **grob** (Frequenzbereich der Stimme); bei Instrumenten im selben Bereich kann sie sich irren.
3. Downbeat-Erkennung nimmt an, dass die Bassdrum auf der „1“ am stärksten ist – bei manchen Stilen (z.B. Reggae, Breakbeat) kann der Takt verschoben sein.
4. **Android Auto** mit eigener Bibliothek-Ansicht (Durchblättern im Auto-Display) ist nicht eingebaut – nur Steuerung (Play/Pause/Weiter).
5. Nach dem kompletten Beenden der App kann eine Bluetooth-Taste die Wiedergabe nicht „von null“ neu starten (keine Wiedergabe-Fortsetzung nach Neustart).
6. Ordner-Export kopiert die Originaldateien; auf sehr langsamen USB-Sticks kann das dauern.
7. Bestehende Titel werden nach dem Update einmal neu analysiert (für die neuen Merkmale) – das läuft im Hintergrund.

## 1.0 – Fadelux (Überarbeitung)

### 1. Entpersonalisiert & Play-Store-ready
- Neuer Name **Fadelux** (steht nur in `res/values/strings.xml` → `app_name`).
- Neuer Paketname `app.fadelux`, neue native Bibliothek `libfadelux.so`, neue Datenbank `fadelux.db`.
- Alle Bezüge zu „Hirsch Express“ aus App, Gästeseite und Bau-Skript entfernt.
- Neues adaptives App-Symbol (leuchtende Schallplatte + Crossfader), inkl. einfarbiger Version für Themensymbole (Android 13+), eigenes Benachrichtigungs-Symbol.
- GitHub baut jetzt **APK und AAB**; Play-Store-Schlüssel über GitHub-Secrets (**SIGNING.md**).
- Native Bibliothek für **16-KB-Speicherseiten** ausgerichtet (Pflicht im Play Store seit Nov. 2025, geprüft an der fertigen APK).
- Berechtigungen reduziert: `WAKE_LOCK` und `POST_NOTIFICATIONS` entfernt, Backups deaktiviert.
- **PRIVACY.md**: Vorlage Datenschutzerklärung inkl. Hilfe für den Play-Store-Fragebogen.
- **STORE_LISTING.md**: Play-Store-Texte (kurz + lang) in DE/EN/FR/IT.

### 2. Redesign (Neon + Glas)
- Design-System in eigenen Dateien: `ui/theme/Color.kt`, `Dimens.kt`, `Type.kt`, `Theme.kt`.
- Komponenten: Glas-Flächen mit Leucht-Rand, Neon-Knöpfe mit Feder-Animation und Vibration, neue Regler mit Leuchtbogen, Hintergrund mit weichen Farbflecken.
- Deck leuchtet, solange es spielt; Cover dreht sich wie eine Platte (60 fps, nur Grafik).
- **Jog:** Das drehende Cover seitlich wischen = Deck kurz schneller/langsamer (Beats von Hand angleichen).
- **Live-Spektrum** (28 Bänder) im Mischpult. Der Audio-Thread kopiert nur Samples, die FFT läuft im UI-Takt.
- Fenster (Bibliothek, Auto-Mix, Wünsche, Einstellungen) blenden weich ein/aus.
- Positionen/Wellenformen werden 60× pro Sekunde aktualisiert und nur neu gezeichnet (keine unnötige Neuberechnung des Layouts).

### 3. Cover-Bilder
- Cover aus den MP3-Tags (ID3/APIC) – in Bibliothek, Decks (drehend) und Auto-Mix.
- Zwischenspeicher: Arbeitsspeicher (LRU) + kleine JPG-Dateien im Cache-Ordner; Platzhalter-Platte ohne Cover.

### 4. Auto-Mix
- Warteschlange mit Drag & Drop, Entfernen, Leeren.
- Intro/Outro-Erkennung aus der Wellenform, Start am Outro (auf den Takt gerundet), BPM-Angleichung + Beat-Sync, Bass-Tausch in der Mitte.
- Übergangslänge 4/8/16 Takte oder 10/20 Sekunden.
- Vorschläge „Passt als nächstes“ direkt im Auto-Mix-Fenster (passend zum letzten Titel der Warteschlange), mit einem Tipp hinzufügen.
- „Reihenfolge optimieren“ (ähnliche BPM nebeneinander, Energie steigt langsam).
- Intro des neuen Titels wird erkannt: Er startet so, dass sein Hauptteil genau am Ende der Überblendung einsetzt.
- „Jetzt überblenden“; wer selbst am Crossfader dreht, übernimmt sofort.

### 5. Vorschläge „Passt als nächstes“
- Neue Analyse beim Import: **Energie** (Lautheit + Bass-Anteil), gespeichert in der Datenbank (Migration ohne Datenverlust).
- Vorschläge mit ±6 % BPM (halbes/doppeltes Tempo erkannt) und ähnlicher Energie; passende Gästewünsche ganz oben.

### 6. Gästewünsche vereinfacht
- Kein eigenes Firebase mehr für Benutzer: zentrales Backend fest in der App (`wishes/BackendConfig.kt`), Einrichtung einmalig durch den Entwickler (**SETUP_BACKEND.md**).
- Events laufen nach **24 h** ab (`expiresAt` + Firestore-TTL), strengere Regeln (Längen, Ablauf, Besitzer, Spam-Schutz 5 min).
- **Neu: Modus „Lokales WLAN“** – die App startet einen eigenen kleinen Webserver; Gäste im gleichen WLAN/Hotspot scannen den QR-Code. Kein Internet nötig.
- Gästeseite mehrsprachig (DE/EN/FR/IT) und im neuen Design.
- 3-Schritte-Anleitung mit Symbolen in der App.
- Wünsche, die in der Bibliothek vorhanden sind, lassen sich direkt aus der Wunschliste auf Deck A/B laden oder in die Auto-Mix-Warteschlange legen.

### 7. Einstellungen & Sprachen
- Kategorien: Design (Akzentfarbe), Sprache, Audio (Ausgabe, Geräte, Latenz, Qualität/Puffer), Bibliothek (Ordner, neu einlesen), Hilfe/FAQ, Über (Version, Lizenzen), Datenschutz.
- Alle Texte in `strings.xml`, übersetzt in **Deutsch, Englisch, Französisch, Italienisch**.
- Onboarding (4 Seiten) beim ersten Start, später erneut aufrufbar.

### Audio-Engine
- Unverändert in Klang und Latenz. Neu: Spektrum-Puffer (nur Kopieren), einstellbare Puffergrösse, Energie-Analyse.

### Qualität & Tests
- Automatische Tests (laufen bei jedem Bau auf GitHub): Tempo-Angleichung, Intro/Outro-Erkennung, Reihenfolge optimieren, Vorschläge, lokaler Wunsch-Server inkl. Spam-Schutz.
- Automatische Bildschirmfotos (Mischpult, Bibliothek, Auto-Mix, Wunschliste, Einstellungen, Einführung) zur Layout-Kontrolle.
- Leistung: Glas-Flächen in eigener Grafik-Ebene, Anzeigen lesen schnelle Werte erst beim Zeichnen.

## Offene Punkte / bekannte Grenzen
1. **Nicht auf einem echten Handy getestet** – bitte `TESTEN.md` durchgehen.
2. **Internet-Modus der Wünsche** ist erst aktiv, wenn das zentrale Backend eingerichtet und in `BackendConfig.kt` eingetragen ist (**SETUP_BACKEND.md**). Bis dahin: Modus „Lokales WLAN“.
3. **Play-Store-Schlüssel**: eigener Upload-Schlüssel nötig (**SIGNING.md**); ohne ihn ist die AAB nur zum Testen.
4. **Datenschutzerklärung**: Vorlage `PRIVACY.md` mit eigenen Angaben ergänzen und online stellen.
5. **Name „Fadelux“**: kurz im Web gesucht, keine gleichnamige App gefunden – vor Veröffentlichung im Play Store und Markenregister (z.B. swissreg.ch, EUIPO) nochmals prüfen.
6. **SoundTouch** ist nicht eingebaut (Quelle war hier nicht erreichbar); Tempo ohne Tonhöhe läuft über einen eigenen WSOLA-Algorithmus.
7. Aufnahme nur als **WAV** (MP3-Encoder fehlt noch).
8. Bildschirmfotos gibt es für Mischpult, Bibliothek, Auto-Mix, Wunschliste, Einstellungen (Navigation/Farbe) und Einführung; die übrigen Einstellungs-Seiten sind nur im Code geprüft.
9. Der lokale Wunsch-Server läuft nur, solange die App offen ist; Wünsche aus dem lokalen Modus werden nicht dauerhaft gespeichert.
10. `CLAUDE.md` (Arbeitsanweisungen für Claude) enthält noch die alte Projektbeschreibung – sie gehört nicht zur App.
11. Alte GitHub-Releases (v0.1.x, „HirschExpressDJ.apk“) sind noch vorhanden; bei Bedarf auf GitHub unter „Releases“ löschen.
12. Die Bildschirmfotos (Release „screenshots“) können als Vorlage für Play-Store-Screenshots dienen.

## Namensvorschläge
1. **Fadelux** (gewählt) – „fade“ (Überblenden) + „lux“ (Licht, Neon)
2. **Spindra** – „spin“ (Platte drehen), weich und international
3. **Beatlume** – Beat + Licht
4. **Neonfade** – beschreibt Design und Funktion
5. **Mixalto** – „Mix“ + „alto“ (hoch), klingt in DE/EN/FR/IT gut

Kurz im Web gesucht: „Glowdeck“, „Mixora“ und „Deckora“ sind schon als Apps vergeben. Für „Fadelux“, „Spindra“ und „Beatlume“ wurde keine gleichnamige App gefunden (vor Veröffentlichung nochmals prüfen).
