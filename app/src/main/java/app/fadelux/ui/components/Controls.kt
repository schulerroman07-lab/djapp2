package app.fadelux.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.Dimens
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Drehregler: nach oben/unten ziehen. Doppeltippen = Standardwert. Leuchtender Bogen. */
@Composable
fun Knob(
    value: Float,
    onChange: (Float) -> Unit,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    knobSize: Dp = Dimens.knob,
    min: Float = -1f,
    max: Float = 1f,
    default: Float = 0f,
    dimmed: Boolean = false,
) {
    val v by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(knobSize)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { change(default) }) }
                .pointerInput(Unit) {
                    detectDragGestures { c, drag ->
                        c.consume()
                        val delta = (-drag.y + drag.x) / (knobSize.toPx() * 3f) * (max - min)
                        change((v + delta).coerceIn(min, max))
                    }
                },
        ) {
            val r = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2F36), Color(0xFF111418)), center, r), r)
            drawCircle(Color(0x33FFFFFF), r, style = Stroke(1.5f))
            val start = 135f
            val sweepAll = 270f
            val frac = (value - min) / (max - min)
            val zeroFrac = (default - min) / (max - min)
            val arc = if (dimmed) AppColors.TextFaint else color
            val tl = Offset(center.x - r + 3, center.y - r + 3)
            val sz = Size(2 * r - 6, 2 * r - 6)
            drawArc(Color(0xFF22262C), start, sweepAll, false, tl, sz, style = Stroke(5f, cap = StrokeCap.Round))
            // Leuchten: breiter, transparenter Bogen unter dem eigentlichen Bogen
            drawArc(arc.copy(alpha = 0.25f), start + sweepAll * zeroFrac, sweepAll * (frac - zeroFrac), false, tl, sz, style = Stroke(12f, cap = StrokeCap.Round))
            drawArc(arc, start + sweepAll * zeroFrac, sweepAll * (frac - zeroFrac), false, tl, sz, style = Stroke(5f, cap = StrokeCap.Round))
            val ang = Math.toRadians((start + sweepAll * frac).toDouble())
            drawLine(
                AppColors.Text, center,
                Offset(center.x + (r * 0.62f * cos(ang)).toFloat(), center.y + (r * 0.62f * sin(ang)).toFloat()),
                strokeWidth = 4f, cap = StrokeCap.Round,
            )
        }
        Label(label)
    }
}

/** Senkrechter Schieberegler (Lautstärke, Tempo). value 0..1 (unten..oben). Ziehen = relativ. */
@Composable
fun VFader(
    value: Float,
    onChange: (Float) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    centerMark: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
) {
    val v by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val dbl by rememberUpdatedState(onDoubleTap)
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { dbl?.invoke() }) }
            .pointerInput(Unit) {
                detectDragGestures { c, drag ->
                    c.consume()
                    change((v - drag.y / (size.height * 0.85f)).coerceIn(0f, 1f))
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val capH = 24.dp.toPx()
        val track = h - capH
        val y = capH / 2 + track * (1f - value)
        drawRoundRect(AppColors.Track, Offset(w / 2 - 4, capH / 2), Size(8f, track), CornerRadius(4f))
        // gefüllter, leuchtender Teil
        val fromY = if (centerMark) capH / 2 + track / 2 else capH / 2 + track
        drawRoundRect(color.copy(alpha = 0.7f), Offset(w / 2 - 3, minOf(y, fromY)), Size(6f, kotlin.math.abs(fromY - y)), CornerRadius(3f))
        if (centerMark) drawLine(AppColors.TextDim, Offset(4f, h / 2), Offset(w - 4f, h / 2), 2f)
        for (i in 0..10) {
            val ty = capH / 2 + track * i / 10f
            drawLine(Color(0x22FFFFFF), Offset(w / 2 + 9, ty), Offset(w / 2 + 16, ty), 2f)
        }
        drawRoundRect(color.copy(alpha = 0.25f), Offset(0f, y - capH / 2 - 3), Size(w, capH + 6), CornerRadius(10f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A4048), Color(0xFF1C2026)), y - capH / 2, y + capH / 2), Offset(3f, y - capH / 2), Size(w - 6f, capH), CornerRadius(8f))
        drawLine(color, Offset(8f, y), Offset(w - 8f, y), 4f, cap = StrokeCap.Round)
    }
}

/** Waagrechter Regler (Crossfader). value -1..1. Doppeltippen = Mitte. */
@Composable
fun HFader(value: Float, onChange: (Float) -> Unit, colorA: Color, colorB: Color, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val v by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Canvas(
        if (!enabled) modifier.graphicsLayer { alpha = 0.75f }   // gesperrt: nur Anzeige, keine Berührung
        else modifier
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { change(0f) }) }
            .pointerInput(Unit) {
                detectDragGestures { c, drag ->
                    c.consume()
                    change((v + drag.x * 2f / (size.width * 0.85f)).coerceIn(-1f, 1f))
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val capW = 34.dp.toPx()
        val track = w - capW
        drawRoundRect(Brush.horizontalGradient(listOf(colorA.copy(alpha = 0.5f), AppColors.Track, colorB.copy(alpha = 0.5f))), Offset(capW / 2, h / 2 - 3), Size(track, 6f), CornerRadius(3f))
        drawLine(AppColors.TextDim, Offset(w / 2, 6f), Offset(w / 2, h - 6f), 2f)
        val x = capW / 2 + track * (value + 1f) / 2f
        drawRoundRect(Color(0x33FFFFFF), Offset(x - capW / 2 - 3, 1f), Size(capW + 6, h - 2f), CornerRadius(10f))
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF3A4048), Color(0xFF1C2026)), x - capW / 2, x + capW / 2), Offset(x - capW / 2, 4f), Size(capW, h - 8f), CornerRadius(8f))
        drawLine(AppColors.Text, Offset(x, 9f), Offset(x, h - 9f), 4f, cap = StrokeCap.Round)
    }
}

/** Pegelanzeige (senkrecht). level wird erst beim Zeichnen gelesen (keine Neuzusammensetzung). */
@Composable
fun LevelMeter(level: () -> Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val segs = 14
        val gap = 2f
        val segH = (size.height - gap * (segs - 1)) / segs
        val lit = (level().coerceIn(0f, 1f) * segs).toInt()
        for (i in 0 until segs) {
            val c = when {
                i >= segs - 1 -> AppColors.Red
                i >= segs - 3 -> AppColors.Amber
                else -> color
            }
            val y = size.height - (i + 1) * segH - i * gap
            drawRoundRect(if (i < lit) c else c.copy(alpha = 0.12f), Offset(0f, y), Size(size.width, segH), CornerRadius(2f))
        }
    }
}

/** Live-Spektrum (Equalizer-Balken mit Leuchten). bands wird beim Zeichnen gelesen. */
@Composable
fun SpectrumBars(bands: () -> FloatArray, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22000000))) {
        val b = bands()
        if (b.isEmpty()) return@Canvas
        val n = b.size
        val gap = 2f
        val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val h = (b[i].coerceIn(0f, 1f) * size.height).coerceAtLeast(2f)
            val x = i * (bw + gap)
            val brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.25f)), size.height - h, size.height)
            drawRoundRect(brush, Offset(x, size.height - h), Size(bw, h), CornerRadius(2f))
        }
    }
}

/** Übersichts-Wellenform mit Position und Cue. Antippen = springen. */
@Composable
fun Waveform(
    peaks: FloatArray,
    progress: () -> Float,
    cueProgress: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
) {
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Track)
            .pointerInput(Unit) { detectTapGestures(onTap = { o -> seek(o.x / size.width) }) },
    ) {
        val w = size.width
        val h = size.height
        val p = progress()
        if (peaks.isNotEmpty()) {
            val n = peaks.size
            val barW = w / n
            for (i in 0 until n) {
                val ph = peaks[i] * h * 0.95f
                val c = if (i.toFloat() / n <= p) color.copy(alpha = 0.35f) else color
                drawRect(c, Offset(i * barW, (h - ph) / 2), Size(barW.coerceAtLeast(1f), ph))
            }
        }
        val cx = w * cueProgress()
        drawLine(AppColors.Cue, Offset(cx, 0f), Offset(cx, h), 3f)
        drawLine(Color.White, Offset(w * p, 0f), Offset(w * p, h), 3f)
    }
}

/** Gezoomte Wellenform (±3 s) mit Beat-Raster. Mitte = aktuelle Position. */
@Composable
fun ZoomWaveform(
    peaks: FloatArray,
    position: () -> Double,
    bpm: Float,
    firstBeat: Float,
    cue: () -> Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.Track)) {
        val w = size.width
        val h = size.height
        val span = 6.0
        val start = position() - span / 2
        val pxPerSec = w / span
        if (peaks.isNotEmpty()) {
            val first = (start * 100).toInt().coerceAtLeast(0)
            val last = ((start + span) * 100).toInt().coerceAtMost(peaks.size - 1)
            val barW = (pxPerSec / 100.0).toFloat().coerceAtLeast(1f)
            for (i in first..last) {
                val x = ((i / 100.0 - start) * pxPerSec).toFloat()
                val ph = peaks[i] * h * 0.95f
                drawRect(if (x < w / 2) color.copy(alpha = 0.5f) else color, Offset(x, (h - ph) / 2), Size(barW, ph))
            }
        }
        if (bpm > 0) {
            val beat = 60.0 / bpm
            var k = floor((start - firstBeat) / beat).toLong()
            while (true) {
                val t = firstBeat + k * beat
                if (t > start + span) break
                if (t >= 0) {
                    val x = ((t - start) * pxPerSec).toFloat()
                    val down = Math.floorMod(k, 4L) == 0L
                    drawLine(
                        Color.White.copy(alpha = if (down) 0.75f else 0.25f),
                        Offset(x, 0f), Offset(x, if (down) h else h * 0.25f), if (down) 3f else 2f,
                    )
                }
                k++
            }
        }
        val cx = ((cue() - start) * pxPerSec).toFloat()
        if (cx in 0f..w) drawLine(AppColors.Cue, Offset(cx, 0f), Offset(cx, h), 3f)
        drawLine(AppColors.Red, Offset(w / 2, 0f), Offset(w / 2, h), 3f)
    }
}
