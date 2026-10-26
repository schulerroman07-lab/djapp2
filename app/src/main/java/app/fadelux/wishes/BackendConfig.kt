package app.fadelux.wishes

/**
 * Zentrales Backend für Gästewünsche (EIN Firebase-Projekt des App-Entwicklers).
 * Benutzer der App müssen nichts einrichten.
 *
 * ➜ Diese zwei Werte trägt der Entwickler EINMAL ein (siehe SETUP_BACKEND.md).
 *   Solange sie leer sind, funktioniert nur der Modus "Lokales WLAN".
 */
object BackendConfig {
    const val PROJECT_ID = ""      // z.B. "fadelux-wishes"
    const val API_KEY = ""         // beginnt mit "AIza..."

    val configured: Boolean get() = PROJECT_ID.isNotBlank() && API_KEY.isNotBlank()
    val guestUrlBase: String get() = "https://$PROJECT_ID.web.app/"

    /** Events laufen nach so vielen Stunden automatisch ab. */
    const val EVENT_HOURS = 24L
}
