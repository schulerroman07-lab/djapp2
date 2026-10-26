package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Bass-Swap: Der neue Song kommt ohne Bass dazu (Mitten leicht zurück, damit nichts
 * matschig wird). Genau in der Mitte – auf einem Takt – wird der Bass innerhalb eines
 * Beats getauscht: so dröhnen nie zwei Bassdrums gleichzeitig. Danach blendet A aus.
 * Lautstärke-Verlauf: Equal-Power (gleich laut während des ganzen Übergangs).
 */
class BassSwapTransition : Transition(TransitionType.BASS_SWAP) {
    override val equalPower = true

    override fun prepare(ctx: TransitionContext) {
        ctx.b.setEq(LOW, -1f)          // Bass des neuen Songs weg
        ctx.b.setEq(MID, -0.25f)       // Mitten leicht zurück
    }

    override fun step(ctx: TransitionContext, p: Float) {
        // Lautstärke: B in der ersten Hälfte rein, A in der zweiten raus (Equal-Power)
        ctx.crossfader(ctx.ease(p))
        // Bass-Tausch genau auf dem Takt in der Mitte, Dauer 1 Beat (weich, aber schnell)
        val swap = ctx.span(p, 0.5f, 0.5f + ctx.beatFraction)
        ctx.a.setEq(LOW, -swap)
        ctx.b.setEq(LOW, swap - 1f)
        // Mitten von B langsam öffnen (bis 3/4 des Übergangs)
        ctx.b.setEq(MID, -0.25f * (1f - ctx.ease(ctx.span(p, 0.25f, 0.75f))))
    }

    override fun finish(ctx: TransitionContext) {
        ctx.b.setEq(LOW, 0f)
        ctx.b.setEq(MID, 0f)
    }

    companion object {
        const val LOW = 2   // Index des Tief-Reglers (Hoch, Mitte, Tief)
        const val MID = 1
    }
}
