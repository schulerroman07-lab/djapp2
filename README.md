# Hirsch Express DJ

DJ-App für Android (Samsung): zwei Decks, Mixer, BPM-Sync, EQ, Effekte, Vorhören, Aufnahme und Gästewünsche per QR-Code.

## App aufs Handy bringen (ohne Android Studio)

GitHub baut die App automatisch, sobald neuer Code hochgeladen wird.

1. Am Handy im Browser (bei GitHub angemeldet) öffnen:
   **https://github.com/schulerroman07-lab/djapp2/releases/latest/download/HirschExpressDJ.apk**
   → die neueste App-Datei wird heruntergeladen.
2. (Alternative: https://github.com/schulerroman07-lab/djapp2/releases → oberste Version → `HirschExpressDJ.apk`.)
3. Die Datei in der Download-Benachrichtigung bzw. in „Eigene Dateien“ → Downloads antippen.
4. Android fragt: „Installation aus unbekannten Quellen erlauben?“ → **Einstellungen** → Schalter für „Eigene Dateien“ (bzw. Chrome) einschalten → zurück → **Installieren**.
5. Beim ersten Start „Zugriff auf Musik und Audio“ **erlauben**.

Neue Versionen lassen sich einfach darüber installieren (die Einstellungen bleiben).

## Bedienung kurz

| Element | Funktion |
|---|---|
| ♫ Bibliothek | Titel suchen, mit **A** oder **B** ins Deck laden. „Neu einlesen“ nach dem Kopieren neuer MP3s. |
| ▶ / ❚❚ | Abspielen / Pause |
| CUE | Steht das Deck: aktuelle Stelle merken. Spielt es: zurück zur gemerkten Stelle. |
| SYNC | Tempo (und Takt) an das andere Deck anpassen |
| Tempo-Regler | ±8 % oder ±16 % (Knopf oben). Doppeltippen = 0 %. **KEY** = Tonhöhe bleibt gleich. |
| ‹ › | Gedrückt halten = kurz langsamer/schneller (Beats von Hand angleichen) |
| HOCH/MITTE/TIEF | Equalizer. Nach oben/unten ziehen. Doppeltippen = Mitte. **KILL** = Band ganz weg. |
| FILTER | Links = nur Bässe, rechts = nur Höhen |
| ECHO | Knopf ein/aus, Drehregler = Stärke (im Takt) |
| 🎧 | Deck im Kopfhörer vorhören |
| Crossfader | Unten in der Mitte: zwischen A und B überblenden. Doppeltippen = Mitte. |
| ● Aufnahme | Mix als WAV in **Musik/HirschExpressDJ** speichern |
| ★ Wünsche | Gästewünsche (siehe `FIREBASE_ANLEITUNG.md`) |
| ⚙ | Audio-Ausgabe, Kopfhörer-Verzögerung, Firebase |

## Vorhören mit Kopfhörer

In ⚙ Einstellungen den Ausgabe-Modus wählen:
- **Splitter-Kabel** (Y-Kabel „Stereo auf 2× Mono“): links = Master für die Box, rechts = Kopfhörer.
- **USB-Audio-Interface mit 4 Kanälen**: Kanal 1/2 Master, 3/4 Kopfhörer.
- **Zwei Geräte**: z.B. Master auf Bluetooth-Box, Kopfhörer am USB-C-Adapter. Bluetooth ist ca. 150–300 ms verspätet → „Kopfhörer-Verzögerung“ entsprechend einstellen.

## Technik

- Kotlin + Jetpack Compose (Oberfläche), C++ mit Oboe (Audio, tiefe Latenz)
- MP3-Dekodierung: Android NDK (AMediaCodec)
- Tempo ohne Tonhöhenänderung: eigener WSOLA-Algorithmus (statt SoundTouch – Quelle hier nicht erreichbar, kann später ersetzt werden)
- BPM-Erkennung: eigene Beat-Analyse, gespeichert in Room-Datenbank
- Gästewünsche: Firebase (Firestore + Hosting), Webseite in `web/`
