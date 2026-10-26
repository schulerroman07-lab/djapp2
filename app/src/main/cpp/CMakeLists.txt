cmake_minimum_required(VERSION 3.22.1)
project(hirschdj CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe kommt als fertiges Paket (Prefab) aus dem Gradle-Abhängigkeiten.
find_package(oboe REQUIRED CONFIG)

add_library(hirschdj SHARED
        Decoder.cpp
        BpmAnalyzer.cpp
        Deck.cpp
        Engine.cpp
        Recorder.cpp
        jni_bridge.cpp)

target_compile_options(hirschdj PRIVATE -O2 -Wall)
target_link_libraries(hirschdj oboe::oboe mediandk log android)
