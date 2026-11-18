## Vergleich: frühere Netze (auch NC/ND-Songs gelernt) gegen neue Netze (nur CC0/CC BY/PD)

Die früheren Netze waren an allen 102 Übungs-Songs gelernt – davon nur 18 mit CC0/CC BY, die übrigen unter NC, ND,
SA, Sampling+ oder mit unklarer Lizenz. Beide Netze wurden deshalb **neu gelernt**, nur mit den 118 erlaubten
Songs (18 Übungs-Songs + 100 neue Lern-Songs von ccMixter, alle CC BY 2.5/3.0 oder CC0). Gemessen an den 56
Test-Songs, die keines der Netze je gelernt hat:

| Messwert (56 Test-Songs) | frühere Netze | neue Netze (nur CC0/CC BY) |
|---|---|---|
| Beat-Treffer (±30 ms) | 86,9 % | **87,2 %** |
| Downbeat ("die 1") | 71,6 % | **71,3 %** |
| Beat-Treffer, nur Songs mit sicherer Referenz (49) | 90,5 % | 90,1 % |
| Downbeat, nur Songs mit sicherer Referenz (37) | 80,8 % | 77,3 % |
| Songs mit Beat-Vertrauen ≥ 0,6 | 54 von 56 | 50 von 56 |
| Gesang: Genauigkeit pro Zeitpunkt (Schwelle 0,4) | 86,1 % | 83,0 % |
| Gesang: Genauigkeit pro Zeitpunkt (Schwelle 0,15) | 82,5 % | 79,8 % |
| Ende des Gesangs > 1 s zu früh erkannt (Schwelle 0,15) | 5 von 56 | 6 von 56 |

- Die Beat-Erkennung ist praktisch gleich gut. Beim Downbeat mit sicherer Referenz sind es 1–2 Songs weniger.
- Die Gesangserkennung ist rund 3 Prozentpunkte ungenauer (andere, weniger vielfältige Lern-Songs: fast nur ccMixter).
  Die Grenzwerte der Gesangsregel wurden mit dem neuen Netz neu bestimmt (an 202 Übungs- und Lern-Songs, jeder nur
  von einem Netz beurteilt, das ihn nie gelernt hat): Schwelle 0,10, Ende-Regel 8 s, 3 s Abstand.
- Zusätzlich mit dem neuen Beat-Netz an 84 Übungs-Songs geprüft, die es nie gelernt hat: Gewicht "Klangwechsel" für
  die 1 (0,1) und Grenze der örtlichen Raster-Prüfung (20 ms) bleiben richtig.
- **Vorschlag:** Mehr CC0/CC-BY-Songs mit Gesang aus anderen Quellen (Free Music Archive mit CC-BY-Filter, Jamendo,
  Wikimedia Commons) sammeln und neu lernen – das Werkzeug dafür ist fertig (`songs_train.json` ergänzen,
  Referenzen im CI erzeugen, `train_vocal_net.py` / `train_beat_net.py` laufen lassen).
