# Fadelux – DJ-App für Android

Zwei Decks, Beat-Sync, EQ & Effekte, Auto-Mix, Song-Vorschläge und Gästewünsche per QR-Code.
Neon-/Glas-Design, Sprachen: Deutsch, English, Français, Italiano.

## App aufs Handy bringen
1. Am Handy im Browser öffnen (bei GitHub angemeldet):
   **https://github.com/schulerroman07-lab/djapp2/releases/latest/download/Fadelux.apk**
2. Heruntergeladene Datei antippen → „Installation aus unbekannten Quellen erlauben“ → **Installieren**.
3. Beim ersten Start: kurze Einführung, dann „Musik und Audio“ **erlauben**.

> Hinweis: Fadelux hat einen neuen Paketnamen (`app.fadelux`). Die alte Test-App
> („Hirsch Express DJ“) bleibt daneben installiert und kann gelöscht werden.

## Play Store
- AAB-Datei: Releases → `Fadelux.aab` (siehe **SIGNING.md** für den eigenen Schlüssel)
- Datenschutzerklärung: **PRIVACY.md** (Vorlage)
- Zentrales Backend für Gästewünsche (Internet-Modus): **SETUP_BACKEND.md**

## App umbenennen
Der Name steht nur an **einer Stelle**: `app/src/main/res/values/strings.xml` → `app_name`.

## Aufbau (für Entwickler)
| Ordner | Inhalt |
|---|---|
| `app/src/main/cpp` | Audio-Engine (C++, Oboe): Decks, Mixer, EQ, Effekte, Time-Stretch, Aufnahme, Spektrum |
| `app/src/main/java/app/fadelux/ui/theme` | Design-System (Farben, Abstände, Schriften) |
| `app/src/main/java/app/fadelux/ui/components` | Glas-/Neon-Bausteine (Knöpfe, Regler, Cover, Spektrum) |
| `app/src/main/java/app/fadelux/ui` | Bildschirme |
| `app/src/main/java/app/fadelux/automix` | Auto-Mix und Vorschläge |
| `app/src/main/java/app/fadelux/wishes` | Gästewünsche (Firebase + lokaler Webserver) |
| `web/` | Gästeseite und Firestore-Regeln |

Gebaut wird automatisch auf GitHub (`.github/workflows/build.yml`): APK, AAB und Bildschirmfotos.
