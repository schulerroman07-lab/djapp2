package ch.hirschexpress.dj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Grosser Knopf, gut mit dem Finger zu treffen. */
@Composable
fun PadButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DjColors.PanelLight,
    active: Boolean = false,
    fontSize: Int = 16,
    onClick: () -> Unit,
) {
    val bg = if (active) color else DjColors.PanelLight
    val fg = if (active) Color.Black else color.takeIf { it != DjColors.PanelLight } ?: DjColors.Text
    val click by rememberUpdatedState(onClick)
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, if (active) bg else color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { click() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Knopf, der nur wirkt, solange er gedrückt gehalten wird (z.B. Tempo-Schubs). */
@Composable
fun HoldButton(text: String, modifier: Modifier = Modifier, onDown: () -> Unit, onUp: () -> Unit) {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DjColors.PanelLight)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    down()
                    tryAwaitRelease()
                    up()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DjColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Drehregler: mit dem Finger nach oben/unten ziehen. Doppeltippen = Mitte.
 * value: min..max
 */
@Composable
fun Knob(
    value: Float,
    onChange: (Float) -> Unit,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    knobSize: Dp = 46.dp,
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
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { change(default) })
                }
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
            drawCircle(DjColors.PanelLight, r)
            val start = 135f
            val sweepAll = 270f
            val frac = (value - min) / (max - min)
            val zeroFrac = (default - min) / (max - min)
            val arcColor = if (dimmed) DjColors.TextDim else color
            drawArc(
                color = Color(0xFF3A3A46), startAngle = start, sweepAngle = sweepAll, useCenter = false,
                topLeft = Offset(center.x - r + 3, center.y - r + 3), size = Size(2 * r - 6, 2 * r - 6),
                style = Stroke(5f, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor, startAngle = start + sweepAll * zeroFrac,
                sweepAngle = sweepAll * (frac - zeroFrac), useCenter = false,
                topLeft = Offset(center.x - r + 3, center.y - r + 3), size = Size(2 * r - 6, 2 * r - 6),
                style = Stroke(6f, cap = StrokeCap.Round),
            )
            val ang = Math.toRadians((start + sweepAll * frac).toDouble())
            drawLine(
                DjColors.Text, center,
                Offset(center.x + (r * 0.7f * cos(ang)).toFloat(), center.y + (r * 0.7f * sin(ang)).toFloat()),
                strokeWidth = 5f, cap = StrokeCap.Round,
            )
        }
        Text(label, color = DjColors.TextDim, fontSize = 11.sp)
    }
}

/**
 * Schieberegler senkrecht (Lautstärke, Tempo). value 0..1 (unten..oben).
 * Ziehen verändert den Wert relativ – kein Springen beim Antippen.
 */
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
        val capH = 26.dp.toPx()
        val track = h - capH
        drawRoundRect(Color(0xFF0A0A0E), Offset(w / 2 - 4, capH / 2), Size(8f, track), CornerRadius(4f))
        if (centerMark) drawLine(DjColors.TextDim, Offset(4f, h / 2), Offset(w - 4f, h / 2), 2f)
        for (i in 0..10) {
            val y = capH / 2 + track * i / 10f
            drawLine(Color(0xFF3A3A46), Offset(w / 2 + 10, y), Offset(w / 2 + 18, y), 2f)
        }
        val y = capH / 2 + track * (1f - value)
        drawRoundRect(
            DjColors.PanelLight, Offset(4f, y - capH / 2), Size(w - 8f, capH), CornerRadius(8f),
        )
        drawLine(color, Offset(8f, y), Offset(w - 8f, y), 5f, cap = StrokeCap.Round)
    }
}

/** Waagrechter Regler (Crossfader). value -1..1. Doppeltippen = Mitte. */
@Composable
fun HFader(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val v by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Canvas(
        modifier
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
        drawRoundRect(Color(0xFF0A0A0E), Offset(capW / 2, h / 2 - 4), Size(track, 8f), CornerRadius(4f))
        drawLine(DjColors.TextDim, Offset(w / 2, 4f), Offset(w / 2, h - 4f), 2f)
        val x = capW / 2 + track * (value + 1f) / 2f
        drawRoundRect(DjColors.PanelLight, Offset(x - capW / 2, 4f), Size(capW, h - 8f), CornerRadius(8f))
        drawLine(DjColors.Text, Offset(x, 8f), Offset(x, h - 8f), 5f, cap = StrokeCap.Round)
    }
}

/** Pegelanzeige (VU-Meter), senkrecht. */
@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val segs = 12
        val gap = 2f
        val segH = (size.height - gap * (segs - 1)) / segs
        val lit = (level.coerceIn(0f, 1f) * segs).toInt()
        for (i in 0 until segs) {
            val color = when {
                i >= segs - 1 -> DjColors.Red
                i >= segs - 3 -> DjColors.Cue
                else -> DjColors.Play
            }
            val y = size.height - (i + 1) * segH - i * gap
            drawRect(if (i < lit) color else color.copy(alpha = 0.15f), Offset(0f, y), Size(size.width, segH))
        }
    }
}

/** Wellenform mit Abspielposition und Cue-Markierung. Antippen = dorthin springen. */
@Composable
fun Waveform(
    peaks: FloatArray,
    progress: Float,
    cueProgress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
) {
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0A0E))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { o -> seek(o.x / size.width) })
            },
    ) {
        val w = size.width
        val h = size.height
        if (peaks.isNotEmpty()) {
            val n = peaks.size
            val barW = w / n
            for (i in 0 until n) {
                val x = i * barW
                val ph = peaks[i] * h * 0.95f
                val c = if (i.toFloat() / n <= progress) color.copy(alpha = 0.45f) else color
                drawRect(c, Offset(x, (h - ph) / 2), Size(barW.coerceAtLeast(1f), ph))
            }
        }
        drawLine(DjColors.Cue, Offset(w * cueProgress, 0f), Offset(w * cueProgress, h), 3f)
        drawLine(Color.White, Offset(w * progress, 0f), Offset(w * progress, h), 4f)
    }
}
