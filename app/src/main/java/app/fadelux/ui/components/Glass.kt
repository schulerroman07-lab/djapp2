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
import kotlin.math.sin
import kotlin.math.cos
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
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
 * Hintergrund: Schwarz mit weichem Leuchten in der Akzentfarbe (Standard: Smaragdgrün).
 *
 * mode 0 = ruhend (kein Rechenaufwand)
 * mode 1 = Energiesparen: bewegt sich langsam, ca. 15 Bilder pro Sekunde
 * mode 2 = voll: 60 Bilder pro Sekunde, pulsiert sanft im Beat und wird mit der Energie der Musik heller
 *
 * Es wird nur der Hintergrund neu gezeichnet (nicht die Bedienelemente). Beat und Pegel werden
 * beim Zeichnen gelesen, ohne zusätzliche Neuaufbauten auszulösen. Ist die App im Hintergrund,
 * stoppt Android das Zeichnen automatisch (kein Akkuverbrauch).
 */
@Composable
fun AppBackground(
    mode: Int = 0,
    pulse: () -> Float = { 0f },
    level: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val time = remember { mutableFloatStateOf(0f) }
    val glow = remember { FloatArray(2) }          // geglättet: [0] Energie, [1] Beat-Puls
    LaunchedEffect(mode) {
        if (mode == 0) return@LaunchedEffect
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                time.floatValue = (now - start) / 1e9f
            }
            if (mode == 1) delay(60)               // Energiesparen: ~15 Bilder/s
        }
    }
    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = if (mode == 0) 0f else time.floatValue
            // Musik-Werte lesen, ohne die Oberfläche neu aufzubauen
            val (lv, pl) = Snapshot.withoutReadObservation { level() to pulse() }
            val k = if (mode == 2) 1f else 0.4f    // Energiesparen: nur dezent reagieren
            glow[0] += (lv.coerceIn(0f, 1f) - glow[0]) * 0.04f
            glow[1] += (pl.coerceIn(0f, 1f) - glow[1]) * 0.35f
            val e = glow[0] * k
            val b = glow[1] * k
            val deep = Color(
                red = accent.red * 0.35f, green = accent.green * 0.55f, blue = accent.blue * 0.45f,
            )
            // Drei weiche Leuchtflächen, die langsam wandern (Perioden 50–80 s → kaum merklich)
            fun blob(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
                val c = Offset(w * cx, h * cy)
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 0.6f)), Color.Transparent), c, r), radius = r, center = c)
            }
            blob(
                accent, 0.18f + 0.10f * e + 0.06f * b,
                0.18f + 0.10f * sin(t * 0.11f), 0.25f + 0.12f * cos(t * 0.08f),
                w * (0.45f + 0.05f * e + 0.02f * b),
            )
            blob(
                deep, 0.30f + 0.12f * e,
                0.80f + 0.08f * cos(t * 0.07f), 0.80f + 0.10f * sin(t * 0.09f),
                w * (0.42f + 0.06f * e),
            )
            blob(
                accent, 0.06f + 0.06f * e + 0.05f * b,
                0.55f + 0.20f * sin(t * 0.05f + 1f), 0.10f + 0.15f * cos(t * 0.06f + 2f),
                w * 0.30f,
            )
        }
        content()
    }
}
