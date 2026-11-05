package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionType

/**
 * Langer Blend (meist 32 Takte): Beide Songs laufen lange gemeinsam – nur bei gleichem Tempo
 * und passender Tonart. Der neue Song kommt mit weichen Höhen und ohne Bass, der Bass wird in
 * der Mitte auf einem Takt getauscht, danach zieht sich der alte Song langsam zurück.
 * Lautstärke-Verlauf: Equal-Power (gleich laut während des ganzen Übergangs).
 */
class LongBlendTransition : Transition(TransitionType.LONG_BLEND) {
    override val equalPower = true

    override fun prepare(ctx: TransitionContext) {
        ctx.b.setEq(LOW, -1f)
        ctx.b.setEq(HIGH, -0.35f)
    }

    override fun step(ctx: TransitionContext, p: Float) {
        // Lautstärke sehr langsam (S-Kurve über die ganze Länge)
        ctx.crossfader(ctx.ease(p))
        // Höhen von B öffnen sich in der ersten Hälfte, die von A schliessen sich in der zweiten
        ctx.b.setEq(HIGH, -0.35f * (1f - ctx.ease(ctx.span(p, 0f, 0.5f))))
        ctx.a.setEq(HIGH, -0.35f * ctx.ease(ctx.span(p, 0.5f, 1f)))
        // Bass-Tausch auf dem Takt in der Mitte (1 Beat lang)
        val swap = ctx.span(p, 0.5f, 0.5f + ctx.beatFraction)
        ctx.a.setEq(LOW, -swap)
        ctx.b.setEq(LOW, swap - 1f)
    }

    override fun finish(ctx: TransitionContext) {
        ctx.b.setEq(LOW, 0f)
        ctx.b.setEq(HIGH, 0f)
    }

    companion object {
        const val HIGH = 0   // Index des Höhen-Reglers (Hoch, Mitte, Tief)
        const val LOW = 2
    }
}
