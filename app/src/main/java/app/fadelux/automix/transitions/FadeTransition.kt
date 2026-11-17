package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Kurzer, sauberer Fade (Pop/Schlager/Rock, oder wenn das Beat-Raster unsicher ist):
 * A wird über die Länge ausgeblendet, B kommt gleichzeitig hoch (Equal-Power: kein Loch, kein Buckel).
 * Keine Effekte. Liegt immer nach dem letzten Gesang von A.
 */
class FadeTransition : Transition(TransitionType.FADE) {
    override val equalPower = true

    override fun step(ctx: TransitionContext, p: Float) {
        ctx.crossfader(ctx.ease(p))
    }
}
