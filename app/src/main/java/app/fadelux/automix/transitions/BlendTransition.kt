package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Kurzer Blend im Takt (2–8 Takte): beide Songs laufen synchron, B kommt hoch, A geht runter.
 * Dezent: nur der Bass von B ist in der ersten Hälfte etwas zurückgenommen (zwei Bässe dröhnen),
 * auf dem Takt in der Mitte wird er weich getauscht. Keine Filter, kein Echo.
 */
class BlendTransition : Transition(TransitionType.BLEND) {
    override val equalPower = true

    override fun prepare(ctx: TransitionContext) {
        ctx.b.setEq(LOW, -0.6f)
    }

    override fun step(ctx: TransitionContext, p: Float) {
        ctx.crossfader(ctx.ease(p))
        val swap = ctx.ease(ctx.span(p, 0.5f, 0.5f + 2 * ctx.beatFraction))
        ctx.a.setEq(LOW, -0.6f * swap)
        ctx.b.setEq(LOW, -0.6f * (1f - swap))
    }

    override fun finish(ctx: TransitionContext) {
        ctx.b.setEq(LOW, 0f)
    }

    companion object {
        const val LOW = 2
    }
}
