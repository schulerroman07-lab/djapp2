# Fadelux – Auto-DJ für Android

Playlist wählen, Play drücken – die App kennt alle Songs, ordnet sie nach Stimmung und spielt die
Party mit hochwertigen, abwechslungsreichen Übergängen im Takt selbstständig durch.
Gästewünsche kommen per QR-Code und werden passend eingereiht.
Sprachen: Deutsch, English, Français, Italiano.

## App aufs Handy bringen
1. Am Handy im Browser öffnen (bei GitHub angemeldet):
   **https://github.com/schulerroman07-lab/djapp2/releases/latest/download/Fadelux.apk**
2. Heruntergeladene Datei antippen → „Installation aus unbekannten Quellen erlauben“ → **Installieren**.
3. Beim ersten Start „Musik und Audio“ **erlauben**.

## Play Store
- AAB-Datei: Releases → `Fadelux.aab` (siehe **SIGNING.md** für den eigenen Schlüssel)
- Datenschutzerklärung: **PRIVACY.md** (Vorlage)
- Optional: zentrales Backend für Gästewünsche über das Internet: **SETUP_BACKEND.md**
  (ohne Einrichtung laufen Wünsche über das WLAN/den Hotspot des Handys).

## App umbenennen
Der Name ist ein Platzhalter und steht nur an **einer Stelle**:
`app/src/main/res/values/strings.xml` → `app_name`. Startanimation, Kopfzeile, Benachrichtigung
und die Download-Dateien (`<Name>.apk`) übernehmen ihn automatisch. Danach nur den Download-Link
oben anpassen. Version: `gradle.properties` → `appVersion`.

## Aufbau (für Entwickler)
| Ordner | Inhalt |
|---|---|
| `app/src/main/cpp` | Audio-Engine (C++, Oboe): 2 interne Decks, Mixer, EQ/Filter/Echo/Hall/Loop, Time-Stretch, Begrenzer, sample-genauer Start, Analyse (BPM, Beats, Tonart, Lautheit) |
| `app/src/main/java/app/fadelux/automix` | Auto-DJ: Ablauf, Struktur-Analyse, Übergangs-Planer, Übergangs-Klassen (`transitions/`), Stimmungs-Ordnung (`QueueFlow`), Tonarten/Genres (`MusicInfo`) |
| `app/src/main/java/app/fadelux/ui` | Bildschirme (Player, Als Nächstes, Playlists, Bibliothek, Wünsche, Einstellungen, Start-Animation) |
| `app/src/main/java/app/fadelux/ui/components` | Bausteine (Logo, Knöpfe, Glas, Cover, Fenster von unten) |
| `app/src/main/java/app/fadelux/playback` | Hintergrund-Dienst, Benachrichtigung (Media3), Audio-Fokus |
| `app/src/main/java/app/fadelux/wishes` | Gästewünsche (lokaler Webserver, optional Firebase) |
| `app/src/main/java/app/fadelux/io` | Playlist Import/Export (M3U, Ordner) |
| `web/` | Gästeseite und Firestore-Regeln (nur für den Internet-Modus) |

Gebaut wird automatisch auf GitHub (`.github/workflows/build.yml`): APK, AAB, Unit-Tests und
Bildschirmfotos (Hoch- und Querformat).
