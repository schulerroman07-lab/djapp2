package app.fadelux.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.automix.TransitionPlan
import app.fadelux.automix.TransitionType
import app.fadelux.data.Track
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.CoverThumb
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.OverlayScaffold
import app.fadelux.ui.components.SectionTitle
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent
import java.util.Locale
import kotlin.math.roundToInt


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import app.fadelux.automix.AutoMix
import app.fadelux.ui.components.BottomSheet

/** Zeit lesbar: "3:05". */
fun formatTime(sec: Double): String {
    val s = sec.coerceAtLeast(0.0).toInt()
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/** Name eines Übergangstyps (übersetzt). */
@Composable
fun transitionLabel(t: TransitionType): String = stringResource(
    when (t) {
        TransitionType.BASS_SWAP -> R.string.tr_bass_swap
        TransitionType.FILTER -> R.string.tr_filter
        TransitionType.ECHO_OUT -> R.string.tr_echo_out
        TransitionType.LOOP -> R.string.tr_loop
        TransitionType.REVERB_WASH -> R.string.tr_reverb
        TransitionType.CUT -> R.string.tr_cut
        TransitionType.LONG_BLEND -> R.string.tr_long_blend
    },
)


/** Protokoll zum Testen: welcher Übergang wann und warum gewählt wurde (aufklappbar). */
@Composable
fun LogSection(lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    NeonButton(
        Modifier.fillMaxWidth().height(38.dp),
        text = stringResource(R.string.am_log, lines.size),
        color = AppColors.TextDim, active = open, fontSize = 11,
    ) { open = !open }
    if (open) {
        Column(Modifier.fillMaxWidth().glass(corner = 10.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (lines.isEmpty()) Text(stringResource(R.string.am_log_empty), style = AppType.tiny)
            lines.take(30).forEach { Text(it, style = AppType.tiny.copy(color = AppColors.Text)) }
        }
    }
}


/**
 * "Als Nächstes": Liste der kommenden Songs, von unten eingeblendet (Hochwischen oder Tippen).
 * Verschieben per Drag & Drop mit Auto-Scroll, Menü pro Song, Smart Shuffle, nach Stimmung ordnen.
 * Schliessen: nach unten wischen, ausserhalb tippen oder Zurück-Taste.
 */
@Composable
fun QueueSheet(visible: Boolean, am: AutoMix, onMessage: (UiText) -> Unit, onClose: () -> Unit) {
    val accent = LocalAccent.current
    BottomSheet(visible, stringResource(R.string.nav_up_next), onClose) {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton(Modifier.weight(1f).height(42.dp), text = stringResource(R.string.q_arrange), icon = AppIcons.Auto, color = accent, fontSize = 12, iconSize = 16.dp) {
                am.arrangeQueue(); onMessage(UiText(R.string.q_arranged))
            }
            NeonButton(Modifier.weight(1f).height(42.dp), text = stringResource(R.string.am_smart_shuffle), icon = AppIcons.Shuffle, color = AppColors.Pink, fontSize = 12, iconSize = 16.dp) {
                am.smartShuffle(); onMessage(UiText(R.string.am_shuffled))
            }
        }
        QueueList(
            am.queue, { am.remove(it) }, { from, to -> am.move(from, to) }, Modifier.weight(1f).fillMaxWidth(),
            pinned = am.pinned.toSet(), onPlayNext = { am.playNext(it) }, onMoveToEnd = { am.moveToEnd(it) },
        )
    }
}

/**
 * Warteschlange mit Drag & Drop (am Griff ziehen). Hält man einen Song an den oberen oder
 * unteren Rand, scrollt die Liste mit – so lässt er sich in einem Zug ganz nach oben/unten ziehen.
 * Menü (⋮) pro Song: Als Nächstes spielen, Ans Ende, Entfernen.
 */
@Composable
fun QueueList(
    queue: List<Track>,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    pinned: Set<Long> = emptySet(),
    onPlayNext: (Int) -> Unit = {},
    onMoveToEnd: (Int) -> Unit = {},
) {
    val accent = LocalAccent.current
    Column(modifier) {
        Text(stringResource(R.string.am_queue, queue.size), style = AppType.small)
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.am_queue_empty), style = AppType.body.copy(color = AppColors.TextDim))
            }
        } else {
            val rowH = 58.dp
            val density = LocalDensity.current
            val rowPx = with(density) { rowH.toPx() }
            val edgePx = with(density) { 70.dp.toPx() }
            val listState = rememberLazyListState()
            val currentQueue by rememberUpdatedState(queue)
            val move by rememberUpdatedState(onMove)
            var dragId by remember { mutableLongStateOf(-1L) }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            /** Gezogenen Song um so viele Plätze verschieben, wie er sich bewegt hat. */
            fun applyDrag() {
                val q = currentQueue
                val from = q.indexOfFirst { it.id == dragId }
                val steps = (dragOffset / rowPx).roundToInt()
                if (from >= 0 && steps != 0) {
                    val to = (from + steps).coerceIn(0, q.size - 1)
                    if (to != from) {
                        move(from, to)
                        dragOffset -= (to - from) * rowPx
                    }
                }
            }

            // Auto-Scroll: gezogener Song nahe am Rand → Liste läuft mit (schneller, je näher am Rand)
            LaunchedEffect(dragId) {
                if (dragId < 0) return@LaunchedEffect
                while (true) {
                    val info = listState.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.key == dragId }
                    var speed = 0f
                    if (item != null) {
                        val top = item.offset + dragOffset
                        val bottom = top + item.size
                        val start = info.viewportStartOffset.toFloat()
                        val end = info.viewportEndOffset.toFloat()
                        if (top < start + edgePx) speed = -((start + edgePx - top) / edgePx).coerceIn(0f, 1f) * 28f
                        else if (bottom > end - edgePx) speed = ((bottom - (end - edgePx)) / edgePx).coerceIn(0f, 1f) * 28f
                    }
                    if (speed != 0f) {
                        val used = listState.scrollBy(speed)
                        dragOffset += used          // Song bleibt unter dem Finger
                        applyDrag()
                    }
                    delay(16)
                }
            }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(queue, key = { _, t -> t.id }) { index, t ->
                    val dragging = dragId == t.id
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(rowH)
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) dragOffset else 0f
                                scaleX = if (dragging) 1.02f else 1f
                                scaleY = if (dragging) 1.02f else 1f
                            }
                            .padding(vertical = 3.dp)
                            .glass(corner = 12.dp, glowColor = if (dragging) accent else null)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Griff zum Verschieben
                        Box(
                            Modifier.size(40.dp).pointerInput(t.id) {
                                detectDragGestures(
                                    onDragStart = { dragId = t.id; dragOffset = 0f },
                                    onDragEnd = { dragId = -1L; dragOffset = 0f },
                                    onDragCancel = { dragId = -1L; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    applyDrag()
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Drag, null, tint = AppColors.TextDim) }
                        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                            // Angeheftet (vom DJ platziert) → Stecknadel statt Nummer
                            if (t.id in pinned) Icon(AppIcons.Pin, null, tint = accent, modifier = Modifier.size(16.dp))
                            else Text("${index + 1}", style = AppType.small)
                        }
                        CoverThumb(t, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(t.bpm?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.0f", it) } ?: "–", style = AppType.bodyBold, color = accent)
                        EnergyBar(t.energy, Modifier.width(30.dp).height(10.dp))
                        Box {
                            NeonButton(Modifier.size(40.dp), icon = AppIcons.More, color = AppColors.TextDim, iconSize = 18.dp) { menu = true }
                            DropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false },
                                modifier = Modifier.background(androidx.compose.ui.graphics.Color(0xFF14171C)),
                            ) {
                                QueueMenuItem(stringResource(R.string.q_play_next), AppIcons.Next, accent) { menu = false; onPlayNext(index) }
                                QueueMenuItem(stringResource(R.string.q_to_end), AppIcons.ArrowDown, AppColors.Cyan) { menu = false; onMoveToEnd(index) }
                                QueueMenuItem(stringResource(R.string.q_remove), AppIcons.Delete, AppColors.Red) { menu = false; onRemove(index) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = AppType.body) },
        leadingIcon = { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        modifier = Modifier.height(52.dp),
    )
}
