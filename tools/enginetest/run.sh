#!/bin/bash
# Engine-Tests am PC (ohne Handy): Tonart, Lautheit, Tempo-Rampe, 2-Stunden-Dauertest.
# Aufruf aus dem Projektordner:  bash tools/enginetest/run.sh   (optional: Stunden für den Dauertest)
set -e
cd "$(dirname "$0")"
SRC=../../app/src/main/cpp
OUT=$(mktemp -d)
g++ -std=c++17 -O2 -I$SRC keytest.cpp -o $OUT/keytest && $OUT/keytest | tail -1
g++ -std=c++17 -O2 -I$SRC loudtest.cpp -o $OUT/loudtest && $OUT/loudtest
g++ -std=c++17 -O2 -I$SRC ramptest2.cpp stub3.cpp $SRC/Deck.cpp $SRC/BpmAnalyzer.cpp -lpthread -o $OUT/ramp && $OUT/ramp 1.04
g++ -std=c++17 -O2 -Ifake -I$SRC longrun.cpp $SRC/Engine.cpp $SRC/Deck.cpp $SRC/BpmAnalyzer.cpp -lpthread -o $OUT/longrun
$OUT/longrun "${1:-2.0}" 2>/dev/null
