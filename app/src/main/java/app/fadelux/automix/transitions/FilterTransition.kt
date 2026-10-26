package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Filter-Übergang: Der alte Song wird mit einem Tiefpass "zugedreht" (dumpf),
 * der neue öffnet sich aus einem Hochpass (erst dünn, dann voll).
 */
class FilterTransition : Transition(TransitionType.FILTER) {
    override fun prepare(ctx: TransitionContext) {
        ctx.b.changeFilter(0.75f)
    }

    override fun step(ctx: TransitionContext, p: Float) {
        val e = ctx.ease(p)
        ctx.a.changeFilter(-0.85f * e)
        ctx.b.changeFilter(0.75f * (1f - e))
        ctx.crossfader(e)
    }

    override fun finish(ctx: TransitionContext) {
        ctx.b.changeFilter(0f)
    }
}
