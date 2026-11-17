package app.fadelux.automix.transitions

import app.fadelux.automix.TrackStructure
import app.fadelux.automix.TransitionPlan
import app.fadelux.automix.TransitionType

/**
 * Was ein Übergang an einem Deck verstellen darf. In der App ist das das echte Deck (DeckModel),
 * im PC-Messwerkzeug ein Aufzeichner (so prüfen beide exakt dieselbe Übergangs-Logik).
 * EQ: i = 0 Höhen, 1 Mitten, 2 Bass; -1 = ganz weg, 0 = neutral.
 */
interface MixDeck {
    fun setEq(i: Int, v: Float)
    fun changeFilter(v: Float)
    fun setEcho(on: Boolean, amount: Float, feedback: Float = 0.55f)
    fun setReverb(send: Float)
    fun setLoop(startSec: Double, lengthSec: Double, on: Boolean)
}

/** Crossfader (-1 = Deck A, +1 = Deck B). */
interface MixBus {
    fun moveCrossfader(x: Float)
}

/** Alles, was ein Übergang braucht: die beiden Decks, das Mischpult und der Plan. */
class TransitionContext(
    val a: MixDeck,                // alter Song (läuft)
    val b: MixDeck,                // neuer Song (kommt)
    val mixer: MixBus,
    val plan: TransitionPlan,
    val aStruct: TrackStructure?,
    val bStruct: TrackStructure?,
    val aIsLeft: Boolean = true,   // liegt A auf der linken Crossfader-Seite (Deck 1)?
) {
    val sideA = if (aIsLeft) -1f else 1f
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
        TransitionType.LONG_BLEND -> LongBlendTransition()
        TransitionType.FADE -> FadeTransition()
        TransitionType.BLEND -> BlendTransition()
    }
}
