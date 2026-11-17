#!/bin/bash
# Objektive Prüfung der Übergänge mit echter Musik (Punkt 9) – ganze Kette am PC:
#   1) Test-Songs laden (fetch.py) und in rohes PCM umwandeln
#   2) jeden Song genau wie die App analysieren (analyze_cli: Beat-Netz, Gesangs-Netz, Tonart, Lautheit)
#   3) ganze Partys planen (RealMixPlanTest: echter Planer der App, zwei Reihenfolgen + Früh-Übergänge)
#   4) jeden Übergang mit der echten Engine abspielen (mixrender: Mix, nur A, nur B)
#   5) messen und Bericht schreiben (eval_mix.py gegen die Referenzen in refs/)
# Aufruf aus dem Projektordner:  bash tools/realmusic/run_mixtest.sh <Arbeitsordner>
# Braucht: g++, ffmpeg, python3 + numpy, Java (Gradle). Dauer: ca. 15–30 Minuten.
set -e
W=${1:?Arbeitsordner angeben}
P=$(cd "$(dirname "$0")/../.." && pwd)
SRC=$P/app/src/main/cpp
mkdir -p "$W"/audio "$W"/pcm "$W"/ana "$W"/mix "$W"/render
echo "== Werkzeuge bauen"
g++ -std=c++17 -O2 -I"$SRC" "$P"/tools/realmusic/analyze_cli.cpp "$SRC"/Analyzer.cpp -o "$W"/analyze_cli
g++ -std=c++17 -O2 -I"$P"/tools/enginetest/fake -I"$SRC" "$P"/tools/realmusic/mixrender.cpp "$SRC"/Engine.cpp \
    "$SRC"/Deck.cpp "$SRC"/BpmAnalyzer.cpp "$SRC"/Analyzer.cpp -lpthread -o "$W"/mixrender
echo "== Songs laden und analysieren"
python3 "$P"/tools/realmusic/fetch.py "$W"/audio
for f in "$W"/audio/*.mp3; do
    id=$(basename "$f" .mp3)
    [ -f "$W/pcm/$id.pcm" ] || ffmpeg -v error -i "$f" -f s16le -ar 44100 -ac 2 "$W/pcm/$id.pcm"
    [ -f "$W/ana/$id.json" ] || "$W"/analyze_cli "$W/pcm/$id.pcm" > "$W/ana/$id.json"
done
echo "== Partys planen (Planer der App)"
rm -f "$W"/mix/*.mix
(cd "$P" && REALMIX_SONGS="$P/tools/realmusic/songs.json" REALMIX_ANALYSIS="$W/ana" REALMIX_PCM="$W/pcm" \
    REALMIX_OUT="$W/mix" ./gradlew -q :app:testDebugUnitTest --tests app.fadelux.RealMixPlanTest --rerun-tasks)
echo "== Übergänge mit der echten Engine abspielen"
for m in "$W"/mix/*.mix; do
    n=$(basename "$m" .mix)
    "$W"/mixrender "$m" "$W/render/$n" 2>/dev/null || echo "Übergang $n: Abspielen fehlgeschlagen"
done
echo "== Messen"
python3 "$P"/tools/realmusic/eval_mix.py "$W"/mix "$W"/render "$W"/pcm --report "$W"/bericht.txt
