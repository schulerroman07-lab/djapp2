# Gästewünsche einrichten (Firebase) – einmalig, ca. 20 Minuten

Firebase ist ein Gratis-Dienst von Google. Er speichert die Wünsche und zeigt die Wunsch-Webseite.
Du brauchst dafür einen Google-Account und einmal einen PC.

## 1. Projekt anlegen
1. https://console.firebase.google.com öffnen → **Projekt hinzufügen**.
2. Name z.B. `hirsch-express-dj` → Google Analytics **aus** → **Projekt erstellen**.
3. Die **Projekt-ID** steht danach unter ⚙ → *Projekteinstellungen* (z.B. `hirsch-express-dj` oder `hirsch-express-dj-1a2b3`). Notieren.

## 2. Anonyme Anmeldung einschalten
Links **Build → Authentication** → *Jetzt starten* → Reiter **Anmeldemethode** → **Anonym** → aktivieren → Speichern.
(Damit wird jedes Gäste-Handy erkannt – für den Spam-Schutz – ohne dass sich jemand anmelden muss.)

## 3. Datenbank anlegen
Links **Build → Firestore Database** → *Datenbank erstellen* → Standort **europe-west6 (Zürich)** → *Im Produktionsmodus starten*.

Dann Reiter **Regeln**: alles löschen, den Inhalt der Datei `web/firestore.rules` aus diesem Projekt hineinkopieren → **Veröffentlichen**.

## 4. Web-App registrieren (für den API-Schlüssel)
⚙ → *Projekteinstellungen* → unten **Meine Apps** → Symbol `</>` (Web) → Name `Wunschseite` → Häkchen **„Firebase Hosting einrichten“** setzen → *App registrieren*.
Im angezeigten Code steht `apiKey: "AIza…"` → diesen Schlüssel notieren.

## 5. Wunsch-Webseite hochladen (am PC)
1. Node.js installieren: https://nodejs.org → „LTS“ herunterladen und installieren.
2. Dieses Projekt herunterladen: https://github.com/schulerroman07-lab/djapp2 → grüner Knopf **Code** → **Download ZIP** → entpacken.
3. Eingabeaufforderung (Windows: Start → „cmd“) öffnen und nacheinander eingeben
   (Pfad anpassen, `DEINE-PROJEKT-ID` ersetzen):
   ```
   npm install -g firebase-tools
   firebase login
   cd %USERPROFILE%\Downloads\djapp2-claude-new-session-ggqrmj\web
   firebase deploy --project DEINE-PROJEKT-ID
   ```
4. Am Ende steht `Hosting URL: https://DEINE-PROJEKT-ID.web.app` – fertig.

## 6. In der App eintragen
App → ⚙ Einstellungen → *Gästewünsche (Firebase)* → Projekt-ID und API-Schlüssel eintragen → **Speichern**.

## Benutzen
★ Wünsche → Name eingeben → **Event starten** → QR-Code zeigen (z.B. ausdrucken oder Handy hinstellen).
Gäste scannen, suchen in deiner Songliste oder schreiben frei. Wünsche erscheinen nach wenigen Sekunden in der App:
✓ = gespielt (abgehakt), 🗑 = löschen. Pro Gerät ist nur alle 5 Minuten ein Wunsch möglich.

Das Handy braucht dafür Internet (mobile Daten / Hotspot reicht).
