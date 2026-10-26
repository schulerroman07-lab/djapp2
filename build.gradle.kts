// Oberste Gradle-Datei: hier werden nur die Plugin-Versionen festgelegt.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    // Bildschirmfotos der Oberfläche ohne Handy (nur zum Prüfen des Layouts)
    id("app.cash.paparazzi") version "1.3.5" apply false
}
