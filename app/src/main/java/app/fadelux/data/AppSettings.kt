package app.fadelux.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Alle Einstellungen der App (werden automatisch gespeichert). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var accentIndex by pref("accent", 0)
    var outputMode by pref("outputMode", 0)
    var masterDevice by pref("masterDevice", 0)
    var cueDevice by pref("cueDevice", 0)
    var bufferBursts by pref("bufferBursts", 2)
    var cueDelayMs by prefF("cueDelayMs", 0f)
    var libraryFolder by prefS("libraryFolder", "")
    var onboardingDone by prefB("onboardingDone", false)
    var transitionBars by pref("transitionBarsV2", 0)    // Auto-Mix: 0 = automatisch, sonst 4/8/16/32 Takte
    var mixStyle by pref("mixStyle", 1)                  // 0 ruhig, 1 ausgewogen, 2 Club
    var disabledTransitions by prefS("disabledTransitions", "")   // abgeschaltete Typen (Komma-getrennt)
    var eventId by prefS("eventId", "")
    var wishMode by pref("wishMode", 0)                  // 0 = Internet, 1 = lokales WLAN
    var queueAutoSort by prefB("queueAutoSort", true)    // neue Songs passend einreihen (Genre, BPM, Energie)
    var bgAnimation by pref("bgAnimation", 2)            // Hintergrund: 0 aus, 1 Energiesparen, 2 voll

    // --- kleine Helfer: Compose-Zustand + automatisches Speichern ---
    private fun pref(key: String, def: Int) = object {
        var state by mutableIntStateOf(prefs.getInt(key, def))
        operator fun getValue(t: Any?, p: Any?) = state
        operator fun setValue(t: Any?, p: Any?, v: Int) { state = v; prefs.edit().putInt(key, v).apply() }
    }
    private fun prefF(key: String, def: Float) = object {
        var state by mutableFloatStateOf(prefs.getFloat(key, def))
        operator fun getValue(t: Any?, p: Any?) = state
        operator fun setValue(t: Any?, p: Any?, v: Float) { state = v; prefs.edit().putFloat(key, v).apply() }
    }
    private fun prefS(key: String, def: String) = object {
        var state by mutableStateOf(prefs.getString(key, def) ?: def)
        operator fun getValue(t: Any?, p: Any?) = state
        operator fun setValue(t: Any?, p: Any?, v: String) { state = v; prefs.edit().putString(key, v).apply() }
    }
    private fun prefB(key: String, def: Boolean) = object {
        var state by mutableStateOf(prefs.getBoolean(key, def))
        operator fun getValue(t: Any?, p: Any?) = state
        operator fun setValue(t: Any?, p: Any?, v: Boolean) { state = v; prefs.edit().putBoolean(key, v).apply() }
    }

    companion object {
        /** Sprache wird separat gelesen (schon beim Start der Activity nötig). */
        fun language(context: Context): String =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", "") ?: ""

        fun setLanguage(context: Context, lang: String) =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("language", lang).commit()
    }
}
