package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Loop-Übergang: Die letzten Takte des alten Songs werden im Kreis wiederholt,
 * der neue Song kommt darüber; der Loop wird dabei leiser und dumpfer.
 */
class LoopTransition : Transition(TransitionType.LOOP) {
    private var looping = false

    override fun prepare(ctx: TransitionContext) {
        looping = false
    }

    override fun step(ctx: TransitionContext, p: Float) {
        if (!looping) {
            // Loop über die Takte direkt vor dem Einsatzpunkt (A ist gerade dort angekommen)
            val bars = if (ctx.plan.lengthBars <= 8) 1 else 2
            val end = ctx.plan.mixSec
            ctx.a.setLoop(end - bars * ctx.aBarSec, bars * ctx.aBarSec, true)
            looping = true
        }
        val e = ctx.ease(p)
        ctx.a.changeFilter(-0.6f * e)
        ctx.crossfader(e)
    }

    override fun finish(ctx: TransitionContext) {
        ctx.a.setLoop(0.0, 0.0, false)
    }
}
