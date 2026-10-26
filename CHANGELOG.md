# Changelog

## 1.0 – Fadelux (Überarbeitung)

### 1. Entpersonalisiert & Play-Store-ready
- Neuer Name **Fadelux** (steht nur in `res/values/strings.xml` → `app_name`).
- Neuer Paketname `app.fadelux`, neue native Bibliothek `libfadelux.so`, neue Datenbank `fadelux.db`.
- Alle Bezüge zu „Hirsch Express“ aus App, Gästeseite und Bau-Skript entfernt.
- Neues adaptives App-Symbol (leuchtende Schallplatte + Crossfader), inkl. einfarbiger Version für Themensymbole (Android 13+), eigenes Benachrichtigungs-Symbol.
- GitHub baut jetzt **APK und AAB**; Play-Store-Schlüssel über GitHub-Secrets (**SIGNING.md**).
- Berechtigungen reduziert: `WAKE_LOCK` und `POST_NOTIFICATIONS` entfernt, Backups deaktiviert.
- **PRIVACY.md**: Vorlage Datenschutzerklärung inkl. Hilfe für den Play-Store-Fragebogen.

### 2. Redesign (Neon + Glas)
- Design-System in eigenen Dateien: `ui/theme/Color.kt`, `Dimens.kt`, `Type.kt`, `Theme.kt`.
- Komponenten: Glas-Flächen mit Leucht-Rand, Neon-Knöpfe mit Feder-Animation und Vibration, neue Regler mit Leuchtbogen, Hintergrund mit weichen Farbflecken.
- Deck leuchtet, solange es spielt; Cover dreht sich wie eine Platte (60 fps, nur Grafik).
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

### 7. Einstellungen & Sprachen
- Kategorien: Design (Akzentfarbe), Sprache, Audio (Ausgabe, Geräte, Latenz, Qualität/Puffer), Bibliothek (Ordner, neu einlesen), Hilfe/FAQ, Über (Version, Lizenzen), Datenschutz.
- Alle Texte in `strings.xml`, übersetzt in **Deutsch, Englisch, Französisch, Italienisch**.
- Onboarding (4 Seiten) beim ersten Start, später erneut aufrufbar.

### Audio-Engine
- Unverändert in Klang und Latenz. Neu: Spektrum-Puffer (nur Kopieren), einstellbare Puffergrösse, Energie-Analyse.

### Qualität & Tests
- Automatische Tests (laufen bei jedem Bau auf GitHub): Tempo-Angleichung, Intro/Outro-Erkennung, Reihenfolge optimieren, Vorschläge, lokaler Wunsch-Server inkl. Spam-Schutz.
- Automatische Bildschirmfotos (Mischpult, Bibliothek, Einführung) zur Layout-Kontrolle.
- Leistung: Glas-Flächen in eigener Grafik-Ebene, Anzeigen lesen schnelle Werte erst beim Zeichnen.

## Offene Punkte / bekannte Grenzen
1. **Nicht auf einem echten Handy getestet** – bitte `TESTEN.md` durchgehen.
2. **Internet-Modus der Wünsche** ist erst aktiv, wenn das zentrale Backend eingerichtet und in `BackendConfig.kt` eingetragen ist (**SETUP_BACKEND.md**). Bis dahin: Modus „Lokales WLAN“.
3. **Play-Store-Schlüssel**: eigener Upload-Schlüssel nötig (**SIGNING.md**); ohne ihn ist die AAB nur zum Testen.
4. **Datenschutzerklärung**: Vorlage `PRIVACY.md` mit eigenen Angaben ergänzen und online stellen.
5. **Name „Fadelux“**: kurz im Web gesucht, keine gleichnamige App gefunden – vor Veröffentlichung im Play Store und Markenregister (z.B. swissreg.ch, EUIPO) nochmals prüfen.
6. **SoundTouch** ist nicht eingebaut (Quelle war hier nicht erreichbar); Tempo ohne Tonhöhe läuft über einen eigenen WSOLA-Algorithmus.
7. Aufnahme nur als **WAV** (MP3-Encoder fehlt noch).
8. Bildschirmfotos gibt es für Mischpult, Bibliothek und Einführung; Auto-Mix-, Wunsch- und Einstellungs-Fenster sind nur im Code geprüft.
9. Der lokale Wunsch-Server läuft nur, solange die App offen ist; Wünsche aus dem lokalen Modus werden nicht dauerhaft gespeichert.
10. `CLAUDE.md` (Arbeitsanweisungen für Claude) enthält noch die alte Projektbeschreibung – sie gehört nicht zur App.
