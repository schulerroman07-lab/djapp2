package app.fadelux.automix.transitions

import app.fadelux.automix.TransitionPlan
import app.fadelux.automix.TransitionType

/**
 * Filter-Übergang: Der neue Song öffnet sich langsam aus einem Hochpass (erst dünn,
 * dann voll), erst danach wird der alte mit einem Tiefpass "zugedreht" (dumpf).
 * Die Bewegungen überlappen nur in der Mitte – so bleibt es immer klar und gleich laut.
 */
class FilterTransition(private val mode: Mode = Mode.BLEND) : Transition(TransitionType.FILTER) {
    /**
     * OUT (1 Takt): A wird in seinem letzten Takt mit einem Hochpass weggezogen (z. B. das letzte Wort), endet beim
     *   Einsatz; B setzt auf seinem Downbeat ein – keine Überlappung.
     * SWEEP (2 Takte): B setzt auf der 1 ein, A wird im ersten Takt mit dem Hochpass "weggezogen".
     * BLEND (≥ 4 Takte, Techno/House): Filter-Blend – B öffnet sich, A wird dumpfer.
     */
    enum class Mode { OUT, SWEEP, BLEND }

    companion object {
        fun modeFor(plan: TransitionPlan?): Mode = when {
            plan == null -> Mode.BLEND
            plan.lengthBars <= 1 -> Mode.OUT
            plan.lengthBars <= 2 -> Mode.SWEEP
            else -> Mode.BLEND
        }
    }

    override val equalPower = mode != Mode.OUT
    override val preBars = if (mode == Mode.OUT) 1 else 0
    override val stopsAAtStart = mode == Mode.OUT
    override val centerBeforeStart = mode == Mode.OUT

    override fun prepare(ctx: TransitionContext) {
        ctx.b.changeFilter(if (mode == Mode.BLEND) 0.45f else 0f)
    }

    override fun pre(ctx: TransitionContext, p: Float) {
        if (mode == Mode.OUT) ctx.a.changeFilter(0.7f * ctx.ease(p))     // Hochpass zieht A im letzten Takt weg (dezent)
    }

    override fun step(ctx: TransitionContext, p: Float) {
        when (mode) {
            Mode.OUT -> ctx.crossfader(0.5f + 0.5f * ctx.ease(p))      // A ist schon weg, B steht voll
            Mode.SWEEP -> {
                // erster Takt: A Hochpass hoch und weg (Bass und Kick zuerst – kein Durcheinander der Beats), B voll
                val q = ctx.ease(ctx.span(p, 0f, 0.5f))
                ctx.a.changeFilter(0.75f * q)
                ctx.crossfader(0.4f + 0.6f * q)
            }
            Mode.BLEND -> {
                ctx.b.changeFilter(0.45f * (1f - ctx.ease(ctx.span(p, 0f, 0.6f))))  // B öffnet sich (dezent)
                ctx.a.changeFilter(-0.5f * ctx.ease(ctx.span(p, 0.4f, 1f)))        // A wird etwas dumpfer
                ctx.crossfader(ctx.ease(p))
            }
        }
    }

    override fun finish(ctx: TransitionContext) {
        ctx.b.changeFilter(0f)
    }
}
