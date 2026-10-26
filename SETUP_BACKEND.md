# Zentrales Backend für Gästewünsche einrichten (einmalig, ca. 30 Minuten)

Damit **Benutzer der App nichts einrichten müssen**, läuft der Internet-Modus der Gästewünsche
über **ein einziges Firebase-Projekt von dir (dem App-Entwickler)**.
Solange das nicht eingerichtet ist, funktioniert nur der Modus „Lokales WLAN“ (ohne Internet).

Du brauchst: einen Google-Account, einen PC (Windows/Mac) und diese Projektdateien.

---

## Schritt 1 – Firebase-Projekt anlegen
1. Öffne https://console.firebase.google.com und melde dich mit Google an.
2. **Projekt hinzufügen** → Name: `fadelux-wishes` (oder ähnlich) → Google Analytics **ausschalten** → **Projekt erstellen**.
3. Oben links ⚙ → **Projekteinstellungen**. Notiere die **Projekt-ID** (z.B. `fadelux-wishes` oder `fadelux-wishes-1a2b3`).

## Schritt 2 – Anonyme Anmeldung einschalten
1. Links **Erstellen → Authentication** → **Jetzt starten**.
2. Reiter **Anmeldemethode** → **Anonym** → einschalten → **Speichern**.

## Schritt 3 – Datenbank anlegen
1. Links **Erstellen → Firestore Database** → **Datenbank erstellen**.
2. Standort: **europe-west6 (Zürich)** → **Im Produktionsmodus starten** → **Erstellen**.

## Schritt 4 – Automatisches Löschen nach 24 Stunden (TTL)
1. In Firestore oben den Reiter **TTL** (bzw. „Gültigkeitsdauer“) öffnen → **Richtlinie erstellen**.
2. Sammlungsgruppe: `events` · Zeitstempel-Feld: `expiresAt` → **Erstellen**.
3. Nochmals für die Sammlungsgruppe `requests` mit Feld `createdAt`?
   → **Nein**, nicht nötig: Wünsche werden nach Ablauf nicht mehr angenommen.
   (Optional kannst du für `requests` eine Regel mit `createdAt` anlegen; dann werden alte Wünsche gelöscht, sobald sie älter als das TTL-Datum sind.)

> Hinweis: Firestore löscht abgelaufene Einträge normalerweise innerhalb von 24 Stunden nach Ablauf.

## Schritt 5 – Web-App registrieren (API-Schlüssel)
1. ⚙ **Projekteinstellungen** → unten **Meine Apps** → Symbol **`</>`** (Web).
2. Name: `Gästeseite` → Häkchen **„Firebase Hosting einrichten“** → **App registrieren**.
3. Im angezeigten Code steht `apiKey: "AIza…"` → diesen **API-Schlüssel** notieren. → **Weiter** bis fertig.

## Schritt 6 – Werte in die App eintragen
Öffne die Datei `app/src/main/java/app/fadelux/wishes/BackendConfig.kt` (auf GitHub: Datei öffnen → Stift-Symbol ✏️) und trage ein:
```kotlin
const val PROJECT_ID = "fadelux-wishes"      // deine Projekt-ID
const val API_KEY = "AIza......"              // dein API-Schlüssel
```
Speichern („Commit changes“). GitHub baut die App danach automatisch neu.

> Ist der API-Schlüssel geheim? Nein – Firebase-Web-Schlüssel dürfen in Apps stehen.
> Die Sicherheit kommt von den **Regeln** (Schritt 7).

## Schritt 7 – Gästeseite und Regeln hochladen (am PC)
1. **Node.js** installieren: https://nodejs.org → Version „LTS“ herunterladen → installieren.
2. Projekt herunterladen: GitHub-Seite des Projekts → grüner Knopf **Code** → **Download ZIP** → entpacken.
3. **Eingabeaufforderung** öffnen (Windows: Start → `cmd`). Nacheinander eingeben
   (`DEINE-PROJEKT-ID` ersetzen, Pfad ggf. anpassen):
   ```
   npm install -g firebase-tools
   firebase login
   cd %USERPROFILE%\Downloads\djapp2-claude-new-session-ggqrmj\web
   firebase deploy --project DEINE-PROJEKT-ID
   ```
   (Mac: statt `cd %USERPROFILE%\Downloads\...` → `cd ~/Downloads/djapp2-claude-new-session-ggqrmj/web`)
4. Am Ende erscheint `Hosting URL: https://DEINE-PROJEKT-ID.web.app` → fertig!

## Schritt 8 – Testen
1. Neue App-Version installieren (siehe README).
2. **Wünsche** → Verbindung **Internet** → **Event starten** → QR-Code erscheint.
3. Mit einem zweiten Handy scannen → Song wählen → senden → erscheint nach ca. 5 Sekunden in der App.
4. Sofort nochmal senden → Meldung „nur alle 5 Minuten“.

## Kosten
Firebase „Spark“ (gratis) reicht für normale Nutzung: 50 000 Lesevorgänge und 20 000 Schreibvorgänge pro Tag.
Bei sehr vielen Benutzern: in der Firebase-Konsole Nutzung beobachten; ggf. auf „Blaze“ (nach Verbrauch) wechseln
und ein **Budget-Limit** setzen.

## Sicherheit (was die Regeln garantieren)
- Nur wer ein Event erstellt hat, kann dessen Wünsche lesen, abhaken und löschen.
- Gäste können nur Wünsche **schicken** (max. 200 Zeichen), nur bei aktiven, nicht abgelaufenen Events,
  und nur **1 Wunsch pro Gerät alle 5 Minuten**.
- Events laufen nach 24 Stunden ab (`expiresAt`), die Ablaufzeit kann nachträglich nicht verlängert werden.
