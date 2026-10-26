# Changelog

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
