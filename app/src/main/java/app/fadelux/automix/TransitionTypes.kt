package app.fadelux.automix

/** Alle Übergangs-Arten. Neue Arten: hier eintragen + eigene Klasse in transitions/. */
enum class TransitionType {
    BASS_SWAP,     // Bass vom alten auf den neuen Song tauschen
    FILTER,        // Hoch-/Tiefpass-Sweep
    ECHO_OUT,      // alter Song endet mit Echo
    LOOP,          // Outro-Takte loopen, neuer Song kommt darüber
    REVERB_WASH,   // alter Song verschwindet im Hall
    CUT,           // harter Schnitt auf dem Downbeat
}

/** Mix-Stil (Einstellungen). */
enum class MixStyle { CALM, BALANCED, CLUB }

data class PlannerSettings(
    val enabled: Set<TransitionType> = TransitionType.entries.toSet(),
    val style: MixStyle = MixStyle.BALANCED,
    val lengthBars: Int = 0,        // 0 = automatisch
)

/** Der geplante Übergang zwischen Titel A (läuft) und Titel B (kommt). */
data class TransitionPlan(
    val type: TransitionType,
    val mixBar: Int,                // Takt in A, an dem B einsetzt (Phrasengrenze)
    val mixSec: Double,             // dieselbe Stelle in A-Sekunden
    val bStartSec: Double,          // ab hier startet B (B-Sekunden, auf einem Downbeat)
    val lengthBars: Int,            // Dauer der Überblendung in Takten
    val tempoRatio: Float,          // Tempo für B (1 = Originaltempo)
    val matchTempo: Boolean,        // Beats laufen synchron?
)
