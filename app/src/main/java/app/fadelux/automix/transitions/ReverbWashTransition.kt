package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Hall-Übergang (Reverb Wash): Der alte Song versinkt in immer mehr Hall, stoppt auf dem
 * Downbeat, und der Hall-Schweif trägt in den neuen Song hinein.
 */
class ReverbWashTransition : Transition(TransitionType.REVERB_WASH) {
    override val preBars = 2
    override val stopsAAtStart = true
    override val centerBeforeStart = true

    override fun pre(ctx: TransitionContext, p: Float) {
        ctx.a.setReverb(0.15f + 0.75f * p)
        ctx.a.changeFilter(-0.3f * p)
    }

    override fun step(ctx: TransitionContext, p: Float) {
        if (p > 0f) ctx.a.setReverb(0f)          // nur noch der Schweif klingt aus
        ctx.crossfader(0.5f + 0.5f * ctx.ease(p))
    }
}
