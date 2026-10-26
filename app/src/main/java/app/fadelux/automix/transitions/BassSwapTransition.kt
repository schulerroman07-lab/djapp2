package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Bass-Swap: Der neue Song kommt ohne Bass dazu, in der Mitte (auf einem Takt) wird
 * der Bass getauscht – so dröhnen nie zwei Bassdrums gleichzeitig.
 */
class BassSwapTransition : Transition(TransitionType.BASS_SWAP) {
    private var swapped = false

    override fun prepare(ctx: TransitionContext) {
        ctx.b.setEq(LOW, -1f)          // Bass des neuen Songs weg
        swapped = false
    }

    override fun step(ctx: TransitionContext, p: Float) {
        if (p < 0.5f) {
            ctx.crossfader(ctx.ease(p / 0.5f) * 0.5f)                 // neuen Song in die Mitte holen
        } else {
            if (!swapped) {                                           // Bass-Tausch
                ctx.a.setEq(LOW, -1f)
                ctx.b.setEq(LOW, 0f)
                swapped = true
            }
            ctx.crossfader(0.5f + ctx.ease((p - 0.5f) / 0.5f) * 0.5f) // alten Song ausblenden
        }
    }

    companion object {
        const val LOW = 2   // Index des Tief-Reglers (Hoch, Mitte, Tief)
    }
}
