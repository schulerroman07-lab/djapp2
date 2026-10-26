package app.fadelux.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.Dimens
import app.fadelux.ui.theme.LocalAccent

/**
 * Neon-Leuchten um eine Fläche (weicher Schatten in der Farbe).
 * Wird hinter dem Element gezeichnet – kostet kaum Rechenzeit.
 */
fun Modifier.glow(color: Color, radius: Dp = 14.dp, corner: Dp = Dimens.radius, alpha: Float = 0.55f, enabled: Boolean = true): Modifier =
    if (!enabled) this else drawBehind {
        drawIntoCanvas { c ->
            val p = Paint().apply {
                isAntiAlias = true
                this.color = color.toArgb()
                setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = alpha).toArgb())
            }
            val nc = c.nativeCanvas
            val r = corner.toPx()
            // Nur AUSSERHALB der Fläche leuchten (die Glasfläche bleibt durchsichtig)
            val path = android.graphics.Path().apply {
                addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW)
            }
            nc.save()
            nc.clipOutPath(path)
            nc.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
            nc.restore()
        }
    }

/** Glas-Fläche: halbtransparent, feiner heller Rand, optional leuchtend. */
fun Modifier.glass(
    corner: Dp = Dimens.radius,
    glowColor: Color? = null,
    strong: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .glow(glowColor ?: Color.Transparent, corner = corner, alpha = 0.35f, enabled = glowColor != null)
        // Eigene Grafik-Ebene: wenn sich der Inhalt ändert (z.B. Wellenform 60×/s),
        // muss das Leuchten nicht jedes Mal neu berechnet werden → flüssiger
        .graphicsLayer {}
        .clip(shape)
        .background(
            Brush.verticalGradient(
                if (strong) listOf(Color(0x2AFFFFFF), Color(0x12FFFFFF)) else listOf(Color(0x1AFFFFFF), Color(0x08FFFFFF)),
            ),
        )
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf((glowColor ?: Color.White).copy(alpha = if (glowColor != null) 0.6f else 0.22f), Color(0x0FFFFFFF)),
            ),
            shape,
        )
}

/**
 * Hintergrund der App: fast schwarz mit weichen farbigen Lichtflecken.
 * Durch die halbtransparenten Glas-Flächen davor entsteht der "Milchglas"-Eindruck.
 */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), Offset(w * 0.15f, h * 0.2f), w * 0.45f),
                radius = w * 0.45f, center = Offset(w * 0.15f, h * 0.2f),
            )
            drawCircle(
                Brush.radialGradient(listOf(AppColors.Violet.copy(alpha = 0.14f), Color.Transparent), Offset(w * 0.85f, h * 0.85f), w * 0.4f),
                radius = w * 0.4f, center = Offset(w * 0.85f, h * 0.85f),
            )
            drawCircle(
                Brush.radialGradient(listOf(AppColors.Cyan.copy(alpha = 0.08f), Color.Transparent), Offset(w * 0.55f, h * 0.05f), w * 0.3f),
                radius = w * 0.3f, center = Offset(w * 0.55f, h * 0.05f),
            )
        }
        content()
    }
}
