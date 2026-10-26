package app.fadelux.automix.transitions

import app.fadelux.DeckModel
import app.fadelux.MixerModel
import app.fadelux.automix.TrackStructure
import app.fadelux.automix.TransitionPlan
import app.fadelux.automix.TransitionType

/** Alles, was ein Übergang braucht: die beiden Decks, das Mischpult und der Plan. */
class TransitionContext(
    val a: DeckModel,              // alter Song (läuft)
    val b: DeckModel,              // neuer Song (kommt)
    val mixer: MixerModel,
    val plan: TransitionPlan,
    val aStruct: TrackStructure?,
    val bStruct: TrackStructure?,
) {
    val sideA = if (a.index == 0) -1f else 1f
    val sideB = -sideA

    /** Crossfader: 0 = ganz beim alten Song, 0.5 = Mitte (beide voll), 1 = ganz beim neuen. */
    fun crossfader(p: Float) = mixer.moveCrossfader(sideA + (sideB - sideA) * p.coerceIn(0f, 1f))

    /** Sanfte Kurve (langsam beginnen, langsam enden). */
    fun ease(p: Float): Float {
        val x = p.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }

    /** Teilbereich [from, to] von p auf 0..1 abbilden (davor 0, danach 1). */
    fun span(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

    /** Länge eines Beats als Anteil der Überblendung (für Bewegungen, die genau 1 Beat dauern). */
    val beatFraction: Float get() = 1f / (4f * plan.lengthBars.coerceAtLeast(1))

    /** Taktlänge des alten Songs in Sekunden (Titel-Zeit). */
    val aBarSec: Double get() = aStruct?.barSec ?: 2.0
}

/**
 * Grundgerüst eines Übergangs. Ablauf:
 *  1. prepare()  – sobald der Plan steht (z.B. Bass des neuen Songs wegdrehen)
 *  2. pre(p)     – in den letzten [preBars] Takten VOR dem Einsatz (z.B. Hall aufbauen)
 *  3. B setzt sample-genau auf dem Downbeat ein (Engine), optional stoppt A gleichzeitig
 *  4. step(p)    – während der Überblendung, p = 0..1 (läuft im Takt des neuen Songs)
 *  5. finish()   – aufräumen; danach wird A gestoppt und zurückgesetzt
 *
 * Neuer Typ = neue Unterklasse + Eintrag in [Transitions.create].
 */
abstract class Transition(val type: TransitionType) {
    /** Takte vor dem Einsatz, in denen A schon bearbeitet wird. */
    open val preBars: Int = 0
    /** A hört genau beim Einsatz von B auf (Schnitt, Echo, Hall). */
    open val stopsAAtStart: Boolean = false
    /** Crossfader schon kurz vor dem Einsatz in die Mitte (B ist bis zum Start stumm). */
    open val centerBeforeStart: Boolean = false
    /**
     * Equal-Power-Kurve am Crossfader: Beim Überblenden bleibt die Gesamtlautstärke gleich
     * (kein Loch, kein Buckel). Für Übergänge, bei denen beide Songs gleichzeitig spielen.
     */
    open val equalPower: Boolean = false

    open fun prepare(ctx: TransitionContext) {}
    open fun pre(ctx: TransitionContext, p: Float) {}
    abstract fun step(ctx: TransitionContext, p: Float)
    open fun finish(ctx: TransitionContext) {}
}

object Transitions {
    fun create(type: TransitionType): Transition = when (type) {
        TransitionType.BASS_SWAP -> BassSwapTransition()
        TransitionType.FILTER -> FilterTransition()
        TransitionType.ECHO_OUT -> EchoOutTransition()
        TransitionType.LOOP -> LoopTransition()
        TransitionType.REVERB_WASH -> ReverbWashTransition()
        TransitionType.CUT -> CutTransition()
    }
}
