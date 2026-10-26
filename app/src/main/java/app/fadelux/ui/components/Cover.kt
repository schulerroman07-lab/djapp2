package app.fadelux.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import app.fadelux.data.CoverRepository
import app.fadelux.data.Track
import app.fadelux.ui.theme.LocalAccent

/** Lädt das Cover eines Titels (mit Cache). null = kein Cover vorhanden. */
@Composable
fun rememberCover(track: Track?): ImageBitmap? {
    val context = LocalContext.current
    val state by produceState(initialValue = track?.let { CoverRepository.cached(it.id) }, track?.id) {
        value = track?.let { CoverRepository.load(context, it.id, it.uri) }
    }
    return state
}

/** Platzhalter, wenn ein Titel kein Cover hat: stilisierte Schallplatte. */
@Composable
fun CoverPlaceholder(modifier: Modifier = Modifier, round: Boolean = false) {
    val accent = LocalAccent.current
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        if (round) drawCircle(Color(0xFF0E1013), r, c)
        else drawRect(Brush.linearGradient(listOf(Color(0xFF1A1E24), Color(0xFF0B0D10))))
        for (i in 1..4) drawCircle(Color(0x18FFFFFF), r * (0.35f + i * 0.14f), c, style = Stroke(1.5f))
        drawCircle(Brush.radialGradient(listOf(accent, accent.copy(alpha = 0.3f)), c, r * 0.3f), r * 0.3f, c)
        drawCircle(Color(0xFF050608), r * 0.06f, c)
    }
}

/** Eckiges Cover für Listen (Bibliothek, Auto-Mix). */
@Composable
fun CoverThumb(track: Track?, size: Dp, modifier: Modifier = Modifier) {
    val img = rememberCover(track)
    Box(modifier.size(size).clip(RoundedCornerShape(8))) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else CoverPlaceholder(Modifier.fillMaxSize())
    }
}

/**
 * Rundes Cover, das sich wie eine Schallplatte dreht, solange das Deck spielt.
 * Die Drehung läuft über die Bildwiederholrate (flüssig, 60 fps) und nur in der Grafik –
 * nicht im Audio-Thread.
 */
@Composable
fun VinylCover(
    track: Track?,
    playing: Boolean,
    speed: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    onJog: ((Int) -> Unit)? = null,     // Wischen: -1 langsamer, +1 schneller, 0 = losgelassen
) {
    val img = rememberCover(track)
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, speed) {
        var last = 0L
        while (playing) {
            withFrameNanos { now ->
                if (last != 0L) angle.floatValue = (angle.floatValue + (now - last) / 1e9f * 200f * speed) % 360f
                last = now
            }
        }
    }
    val jog by rememberUpdatedState(onJog)
    Box(
        modifier
            .size(size)
            // Jog: seitlich wischen = Deck kurz schneller/langsamer (Beats von Hand angleichen)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { jog?.invoke(0) },
                    onDragCancel = { jog?.invoke(0) },
                ) { change, amount ->
                    change.consume()
                    jog?.invoke(if (amount > 0) 1 else -1)
                    angle.floatValue = (angle.floatValue + amount) % 360f
                }
            }
            .graphicsLayer { rotationZ = angle.floatValue }
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // Mittelloch wie bei einer Platte
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                drawCircle(Color(0x55000000), r, style = Stroke(r * 0.12f))
                drawCircle(Color(0xFF050608), r * 0.09f)
            }
        } else {
            CoverPlaceholder(Modifier.fillMaxSize(), round = true)
        }
    }
}
