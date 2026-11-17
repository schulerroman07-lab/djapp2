package app.fadelux.ui

import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.platform.LocalConfiguration
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
        TransitionType.FADE -> R.string.tr_fade
        TransitionType.BLEND -> R.string.tr_blend
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
    state: androidx.compose.foundation.lazy.LazyListState? = null,
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
            val maxSpeedPx = with(density) { 900.dp.toPx() }     // höchstens ca. 15 Zeilen pro Sekunde
            val listState = state ?: rememberLazyListState()
            val currentQueue by rememberUpdatedState(queue)
            val move by rememberUpdatedState(onMove)
            var dragId by remember { mutableLongStateOf(-1L) }
            // Oberkante der gezogenen Zeile im sichtbaren Bereich (folgt dem Finger)
            var dragTop by remember { mutableFloatStateOf(0f) }

            /**
             * Gezogenen Song auf den Platz der Zeile setzen, über der seine Mitte gerade liegt.
             * Wichtig: Vor dem Verschieben die Scroll-Position festhalten. Sonst "folgt" die Liste der
             * obersten sichtbaren Zeile, sobald diese ihren Platz tauscht – die gezogene Zeile rutscht
             * dann aus dem Bild und bleibt stecken (Fehler beim Ziehen nach oben).
             */
            fun applyDrag() {
                val q = currentQueue
                val from = q.indexOfFirst { it.id == dragId }
                if (from < 0) return
                val visible = listState.layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) return
                val to = DragLogic.targetIndex(dragTop + rowPx / 2f, visible.map { Triple(it.index, it.offset, it.size) }, from)
                    .coerceIn(0, q.size - 1)
                if (to != from) {
                    listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    move(from, to)
                }
            }

            // Auto-Scroll: gezogener Song nahe am oberen/unteren Rand → Liste läuft mit
            // (schneller, je näher am Rand); der Song bleibt dabei unter dem Finger.
            LaunchedEffect(dragId) {
                if (dragId < 0) return@LaunchedEffect
                var last = System.nanoTime()
                while (true) {
                    val now = System.nanoTime()
                    val dt = ((now - last) / 1e9f).coerceIn(0.004f, 0.05f)
                    last = now
                    val info = listState.layoutInfo
                    val speed = DragLogic.scrollSpeed(
                        dragTop, dragTop + rowPx, info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat(), edgePx, maxSpeedPx,
                    )
                    if (speed != 0f) listState.scrollBy(speed * dt)
                    applyDrag()
                    delay(8)
                }
            }

            // Schmal (Hochformat): Energie-Balken weglassen, damit der Titel Platz hat
            val narrow = LocalConfiguration.current.screenWidthDp < 480
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
                                // gezogene Zeile: vom eigenen Listenplatz zur Finger-Position verschieben
                                translationY = if (dragging) {
                                    val slot = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == t.id }
                                    if (slot != null) dragTop - slot.offset else 0f
                                } else 0f
                                scaleX = if (dragging) 1.02f else 1f
                                scaleY = if (dragging) 1.02f else 1f
                            }
                            .padding(vertical = 3.dp)
                            .glass(corner = 12.dp, glowColor = if (dragging) accent else null)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 8.dp),
                    ) {
                        // Griff zum Verschieben
                        Box(
                            Modifier.size(if (narrow) 32.dp else 40.dp).testTag("touch:Griff").pointerInput(t.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        val slot = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == t.id }
                                        dragTop = (slot?.offset ?: 0).toFloat()
                                        dragId = t.id
                                    },
                                    onDragEnd = { dragId = -1L },
                                    onDragCancel = { dragId = -1L },
                                ) { change, amount ->
                                    change.consume()
                                    val info = listState.layoutInfo
                                    dragTop = (dragTop + amount.y).coerceIn(
                                        info.viewportStartOffset - rowPx / 2f, info.viewportEndOffset - rowPx / 2f,
                                    )
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
                        CoverThumb(t, if (narrow) 34.dp else 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(t.bpm?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.0f", it) } ?: "–", style = AppType.bodyBold, color = accent)
                        if (!narrow) EnergyBar(t.energy, Modifier.width(30.dp).height(10.dp))
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
