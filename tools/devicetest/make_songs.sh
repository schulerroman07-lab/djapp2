#!/bin/bash
# Erzeugt Test-Songs (MP3) für den Gerätetest: Intro/Outro ohne Bassdrum, Hauptteil mit Beat,
# verschiedene Tempi, Tonarten, Lautstärken und Genre-Tags, 3 s Stille am Schluss.
# Aufruf: bash make_songs.sh <Zielordner>
set -e
OUT=${1:-songs}
mkdir -p "$OUT"
# BPM  Grundton(Hz)  Lautstärke  Genre      Länge(s)
SONGS=(
"124 220.00 1.0 House 170"
"124 220.00 0.9 House 175"
"126 246.94 1.0 House 165"
"128 261.63 0.8 Techno 170"
"132 293.66 1.0 Techno 160"
"138 196.00 1.0 Techno 165"
"100 220.00 0.6 Rock 175"
"104 329.63 0.7 Rock 170"
"118 174.61 0.5 Schlager 170"
"120 196.00 0.6 Schlager 165"
"122 233.08 0.7 Schlager 170"
"95 261.63 0.4 Chill 180"
"125 220.00 0.9 House 170"
"127 277.18 0.85 House 165"
"130 207.65 1.0 Techno 170"
"110 246.94 0.6 Pop 175"
"116 293.66 0.65 Pop 170"
"124 220.00 0.95 House 170"
)
i=0
for s in "${SONGS[@]}"; do
  read BPM F V GENRE LEN <<< "$s"
  i=$((i+1))
  P=$(python3 -c "print(60/$BPM)")
  BODY_END=$((LEN-16))
  # Bassdrum nur im Hauptteil (Intro/Outro 16 s ruhiger), Hi-Hat auf dem Offbeat, Dur-Dreiklang
  EXPR="$V*(0.55*between(t,16,$BODY_END)*exp(-mod(t,$P)*14)*sin(2*PI*55*mod(t,$P))+0.06*between(t,8,$((LEN-8)))*(random(0)-0.5)*exp(-mod(t+$P/2,$P)*60)+0.07*sin(2*PI*$F*t)+0.05*sin(2*PI*$F*1.2599*t)+0.05*sin(2*PI*$F*1.4983*t)+0.06*sin(2*PI*$F/2*t))"
  ffmpeg -loglevel error -y -f lavfi -i "aevalsrc='$EXPR':s=44100:d=$LEN:c=stereo" \
    -af "apad=pad_dur=3" -c:a libmp3lame -b:a 160k \
    -metadata title="Soak $(printf %02d $i) ($BPM BPM)" -metadata artist="Generator" -metadata genre="$GENRE" \
    "$OUT/soak_$(printf %02d $i).mp3"
done
ls "$OUT" | wc -l
