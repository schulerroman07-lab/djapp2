package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/** Harter Schnitt: genau auf dem Downbeat stoppt der alte Song und der neue startet. */
class CutTransition : Transition(TransitionType.CUT) {
    override val stopsAAtStart = true
    override val centerBeforeStart = true

    override fun step(ctx: TransitionContext, p: Float) {
        ctx.crossfader(1f)
    }
}
