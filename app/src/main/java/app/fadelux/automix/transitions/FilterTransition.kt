package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Filter-Übergang: Der neue Song öffnet sich langsam aus einem Hochpass (erst dünn,
 * dann voll), erst danach wird der alte mit einem Tiefpass "zugedreht" (dumpf).
 * Die Bewegungen überlappen nur in der Mitte – so bleibt es immer klar und gleich laut.
 */
class FilterTransition : Transition(TransitionType.FILTER) {
    override val equalPower = true

    override fun prepare(ctx: TransitionContext) {
        ctx.b.changeFilter(0.7f)
    }

    override fun step(ctx: TransitionContext, p: Float) {
        ctx.b.changeFilter(0.7f * (1f - ctx.ease(ctx.span(p, 0f, 0.7f))))   // B öffnet sich
        ctx.a.changeFilter(-0.8f * ctx.ease(ctx.span(p, 0.35f, 1f)))       // A schliesst sich
        ctx.crossfader(ctx.ease(p))
    }

    override fun finish(ctx: TransitionContext) {
        ctx.b.changeFilter(0f)
    }
}
