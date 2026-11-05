package app.fadelux.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * App-Logo "Beat-Ring": ein leuchtender Ring mit drei Equalizer-Balken – Musik, die von selbst läuft.
 * Als Vektor gezeichnet (scharf in jeder Grösse). Der App-Name steht NICHT im Logo, er kommt aus
 * strings.xml und wird darunter geschrieben – so ist der Name jederzeit austauschbar.
 *
 * @param morph 0 = Punkt, 1 = fertiges Logo. Dazwischen wächst der Ring aus einem Punkt,
 *              "schwingt" wie eine Schallwelle und beruhigt sich zu einem sauberen Kreis.
 * @param bars 0..1 Höhe der Balken (für die Animation), 1 = fertig.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, color: Color, morph: Float = 1f, bars: Float = 1f, pulse: Float = 0f) {
    Canvas(modifier) { drawLogo(color, morph, bars, pulse) }
}

/** Zeichnet das Logo in die Fläche (zentriert, quadratisch). */
fun DrawScope.drawLogo(color: Color, morph: Float, bars: Float, pulse: Float = 0f) {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val m = morph.coerceIn(0f, 1f)
    // Radius: wächst mit leichtem Überschwingen (wie eine gezupfte Saite)
    val grow = 1f - exp(-6f * m) * cos(9f * m)
    val radius = s * 0.36f * grow.coerceAtLeast(0.04f) * (1f + 0.03f * pulse)
    // Welligkeit des Rings: am Anfang stark, verschwindet am Ende ganz (sauberer Kreis)
    val wobble = 0.16f * (1f - m) * (1f - m)
    val stroke = s * 0.055f

    val path = Path()
    val n = 120
    for (i in 0..n) {
        val a = i / n.toFloat() * 2f * PI.toFloat()
        val r = radius * (1f + wobble * sin(5f * a + m * 9f))
        val x = c.x + r * cos(a)
        val y = c.y + r * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    // Weiches Leuchten hinter dem Ring
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.35f * m), Color.Transparent), c, radius * 1.6f + 1f),
        radius * 1.6f + 1f, c,
    )
    drawPath(path, color.copy(alpha = 0.25f), style = Stroke(stroke * 2.6f))
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))

    // Drei Equalizer-Balken, nacheinander wachsend
    val b = bars.coerceIn(0f, 1f)
    if (b > 0f) {
        val bw = s * 0.07f
        val gap = s * 0.045f
        val heights = floatArrayOf(0.26f, 0.42f, 0.30f)
        for (k in 0..2) {
            val local = ((b - k * 0.15f) / 0.7f).coerceIn(0f, 1f)
            val ease = local * local * (3 - 2 * local)
            val h = s * heights[k] * ease * (1f + 0.08f * pulse * (if (k == 1) 1f else 0.6f))
            if (h <= 0f) continue
            val x = c.x + (k - 1) * (bw + gap) - bw / 2
            drawRoundRect(
                color = if (k == 1) color else color.copy(alpha = 0.85f),
                topLeft = Offset(x, c.y - h / 2),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2, bw / 2),
            )
        }
    }
}
