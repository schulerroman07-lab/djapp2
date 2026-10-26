# App für den Google Play Store signieren (Release-AAB)

Der Play Store verlangt eine **AAB-Datei** (Android App Bundle), die mit deinem eigenen
**Upload-Schlüssel** unterschrieben ist. GitHub baut die AAB automatisch – du musst nur
einmalig den Schlüssel erstellen und bei GitHub hinterlegen.

> Ohne eigenen Schlüssel baut GitHub die AAB mit einem Test-Schlüssel. Die kann man
> testen, aber **nicht** im Play Store hochladen.

## 1. Schlüssel erstellen (einmalig, am PC)
1. Java installieren: https://adoptium.net → „Temurin 17“ herunterladen und installieren.
2. Eingabeaufforderung öffnen (Windows: Start → `cmd`) und eingeben:
   ```
   keytool -genkeypair -v -keystore upload.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
3. Ein **Passwort** wählen (gut aufschreiben!) und die Fragen beantworten (Name, Land: `CH`).
4. Es entsteht die Datei `upload.keystore`. **Sicher aufbewahren** (z.B. USB-Stick + Passwort-Manager).
   Wer diese Datei verliert, kann keine Updates mehr hochladen.

## 2. Schlüssel bei GitHub hinterlegen
1. Datei in Text umwandeln (Windows, im gleichen Ordner):
   ```
   certutil -encode upload.keystore upload.txt
   ```
   Öffne `upload.txt` und kopiere alles **zwischen** den Zeilen `-----BEGIN CERTIFICATE-----` und `-----END CERTIFICATE-----`.
2. Auf GitHub: Projekt → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
   Lege diese 4 Einträge an:

   | Name | Inhalt |
   |---|---|
   | `KEYSTORE_BASE64` | der kopierte Text aus `upload.txt` |
   | `KEYSTORE_PASSWORD` | dein Passwort |
   | `KEY_ALIAS` | `upload` |
   | `KEY_PASSWORD` | dein Passwort |

## 3. AAB holen
Nach dem nächsten Hochladen (Push) baut GitHub automatisch.
Die AAB findest du unter **Releases** → neueste Version → `Fadelux.aab`
(oder unter **Actions** → letzter Lauf → Artefakt „Fadelux-AAB“).

## 4. Im Play Store hochladen
1. https://play.google.com/console → Entwicklerkonto (einmalig 25 USD).
2. **App erstellen** → Name, Sprache, kostenlos.
3. **Release → Produktion → Neuen Release erstellen** → `Fadelux.aab` hochladen.
4. „Play App Signing“ akzeptieren (Google verwaltet den endgültigen Schlüssel).
5. Store-Eintrag ausfüllen: Beschreibung, Screenshots, Symbol (512×512), **Datenschutzerklärung** (Text aus `PRIVACY.md` online stellen, z.B. als GitHub-Seite oder Google Site).
6. Fragebogen „Datensicherheit“: siehe Abschnitt in `PRIVACY.md`.

## Berechtigungen der App (minimal)
| Berechtigung | Wofür |
|---|---|
| `READ_MEDIA_AUDIO` (ab Android 13) / `READ_EXTERNAL_STORAGE` (Android 10–12) | eigene Musik lesen |
| `INTERNET` | Gästewünsche |
| `FOREGROUND_SERVICE` + `…_MEDIA_PLAYBACK` | Musik läuft weiter, wenn kurz eine andere App offen ist |

> **Play Console – Vordergrunddienst:** Beim Hochladen fragt Google, wofür der Dienst
> „Medienwiedergabe“ gebraucht wird. Antwort: *„Die DJ-Musik spielt weiter, wenn der Benutzer
> kurz eine andere App öffnet (z.B. während eines Events).“* Optional ein kurzes Video davon.
