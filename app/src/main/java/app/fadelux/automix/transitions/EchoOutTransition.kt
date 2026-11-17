package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Echo-Out: Im letzten Takt bekommt der alte Song ein Echo im Takt, dann stoppt er
 * auf dem Downbeat – nur das Echo klingt über den Anfang des neuen Songs aus.
 * Funktioniert auch bei sehr unterschiedlichem Tempo.
 */
class EchoOutTransition : Transition(TransitionType.ECHO_OUT) {
    override val preBars = 1
    override val stopsAAtStart = true
    override val centerBeforeStart = true

    override fun pre(ctx: TransitionContext, p: Float) {
        ctx.a.setEcho(true, 0.15f + 0.25f * p, feedback = 0.4f)     // dezent: leiser, kürzer
    }

    override fun step(ctx: TransitionContext, p: Float) {
        if (p > 0f) ctx.a.setEcho(false, 0.4f, feedback = 0.4f)    // nichts Neues mehr ins Echo, nur ausklingen
        ctx.crossfader(0.5f + 0.5f * ctx.ease(p))
    }
}
