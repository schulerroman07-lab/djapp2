# DJ-App „Hirsch Express DJ" – Projektanleitung für Claude Code

## Ziel
Android-App (Samsung) zum Live-Mixen von eigenen MP3-Dateien mit 2 Decks, gedacht für Events mit dem Barwagen-Verein „Hirsch Express". Gäste können per QR-Code Musikwünsche schicken.

## Über den Entwickler
- Roman hat **keine Programmiererfahrung**. Claude schreibt den ganzen Code, testet ihn und erklärt jeden Schritt kurz und einfach (auf Deutsch).
- Arbeite in kleinen Schritten. Nach jedem Schritt: was wurde gemacht, wie testet Roman es auf dem Handy.
- Befehle immer vollständig angeben (copy-paste-fähig). Keine Annahmen über Vorwissen.
- Bestehende Dateien nicht ohne Rückfrage grundlegend umbauen.

## Technik
- **Sprache/UI:** Kotlin + Jetpack Compose, Querformat, grosse Bedienelemente (dunkle Umgebung, Bedienung mit Fingern).
- **Audio-Engine:** C++ mit **Oboe** (tiefe Latenz), MP3-Dekodierung über Android NDK (AMediaCodec).
- **Time-Stretch/Tempo:** SoundTouch (LGPL) – Tempo ändern ohne Tonhöhe.
- **BPM-Erkennung:** eigene einfache Beat-Analyse beim Import, Ergebnis in lokaler Datenbank (Room) speichern.
- **Gästewünsche:** Firebase (Firestore + Hosting) für eine einfache Wunsch-Webseite.
- Min. Android-Version: 10 (API 29).

## Funktionen (Reihenfolge = Bau-Reihenfolge)
1. **Musikbibliothek:** MP3s vom Handy einlesen, Liste mit Titel, Interpret, Länge, BPM, Suche.
2. **Deck A + Deck B:** Laden, Play/Pause, Cue-Punkt, Wellenform, Position/Zeit.
3. **Crossfader** und Lautstärke pro Deck.
4. **Tempo-Regler** (±8 / ±16 %) und **BPM-Sync**-Knopf.
5. **EQ pro Deck** (Hoch/Mitte/Tief, mit Kill) und **Effekte** (Filter, Echo).
6. **Vorhören (Cue) über Kopfhörer** – Master über Lautsprecher/Bluetooth-Box.
7. **Mix aufnehmen** als Datei (MP3 oder WAV) im Ordner „Musik/HirschExpressDJ".
8. **Gästewünsche:** Event starten → App zeigt QR-Code → Gäste öffnen Webseite, suchen in der Songliste des DJs oder schreiben freien Wunsch → Wünsche erscheinen live in der App (abhaken/löschen). Spam-Schutz: max. 1 Wunsch pro Gerät alle 5 Minuten.

## Wichtige Hinweise
- **Bluetooth hat Verzögerung** (ca. 150–300 ms). Für Master + Kopfhörer gleichzeitig braucht es ein USB-Audio-Interface oder ein Splitter-Kabel. App soll Ausgabegeräte erkennen und eine Latenz-Korrektur anbieten.
- Wünsche-Webseite braucht Internet (Handy-Hotspot reicht).
- Öffentliche Events: Musiknutzung ist in der Schweiz SUISA-pflichtig – kein Teil der App, nur zur Info.

## Testen
- Auf Romans Samsung per USB-Debugging testen. Anleitung zum Aktivieren beim ersten Mal geben.
- Jede Funktion erst als fertig melden, wenn sie auf dem Handy getestet ist.

## Stil
- Antworten kurz halten, Fachbegriffe erklären.
- Code sauber kommentieren (Deutsch).
