cmake_minimum_required(VERSION 3.22.1)
project(fadelux CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe kommt als fertiges Paket (Prefab) aus dem Gradle-Abhängigkeiten.
find_package(oboe REQUIRED CONFIG)

add_library(fadelux SHARED
        Decoder.cpp
        BpmAnalyzer.cpp
        Deck.cpp
        Engine.cpp
        Recorder.cpp
        jni_bridge.cpp)

target_compile_options(fadelux PRIVATE -O2 -Wall)
target_link_libraries(fadelux oboe::oboe mediandk log android)

# Google Play verlangt seit Nov. 2025 Unterstützung für 16-KB-Speicherseiten
target_link_options(fadelux PRIVATE "-Wl,-z,max-page-size=16384")
