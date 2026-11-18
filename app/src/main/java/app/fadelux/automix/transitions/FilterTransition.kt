package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Filter-Übergang: Der neue Song öffnet sich langsam aus einem Hochpass (erst dünn,
 * dann voll), erst danach wird der alte mit einem Tiefpass "zugedreht" (dumpf).
 * Die Bewegungen überlappen nur in der Mitte – so bleibt es immer klar und gleich laut.
 */
class FilterTransition : Transition(TransitionType.FILTER) {
    override val equalPower = true

    /** Filter-Sweep (≤ 2 Takte): A wird mit einem Hochpass in einem Takt "weggezogen", B setzt auf seinem Downbeat
     *  ein. Sonst (≥ 4 Takte, Techno/House): Filter-Blend – B öffnet sich, A wird dumpfer. */
    private fun sweep(ctx: TransitionContext) = ctx.plan.lengthBars <= 2

    override fun prepare(ctx: TransitionContext) {
        ctx.b.changeFilter(if (sweep(ctx)) 0f else 0.45f)
    }

    override fun step(ctx: TransitionContext, p: Float) {
        if (sweep(ctx)) {
            // erster Takt: A Hochpass hoch und weg (Bass und Kick zuerst – kein Durcheinander der Beats), B voll
            val q = ctx.ease(ctx.span(p, 0f, 0.5f))
            ctx.a.changeFilter(0.75f * q)
            ctx.crossfader(0.4f + 0.6f * q)
            return
        }
        ctx.b.changeFilter(0.45f * (1f - ctx.ease(ctx.span(p, 0f, 0.6f))))  // B öffnet sich (dezent)
        ctx.a.changeFilter(-0.5f * ctx.ease(ctx.span(p, 0.4f, 1f)))        // A wird etwas dumpfer
        ctx.crossfader(ctx.ease(p))
    }

    override fun finish(ctx: TransitionContext) {
        ctx.b.changeFilter(0f)
    }
}
