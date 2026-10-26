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

/** Alles, was die Auto-Mix-Anzeige zeigt. */
data class AutoMixUi(
    val queue: List<Track> = emptyList(),
    val running: Boolean = false,
    val status: String? = null,
    val nextTrack: Track? = null,
    val nextPlan: TransitionPlan? = null,
    val forcedType: TransitionType? = null,
    val countdown: () -> Double? = { null },
    val style: Int = 1,
    val lengthBars: Int = 0,
    val enabled: Set<TransitionType> = TransitionType.entries.toSet(),
    val suggestions: List<Track> = emptyList(),
    val log: List<String> = emptyList(),
    val pinned: Set<Long> = emptySet(),
    val autoSort: Boolean = true,
)

/** Alle Aktionen der Auto-Mix-Anzeige. */
data class AutoMixActions(
    val startStop: () -> Unit = {},
    val mixNow: () -> Unit = {},
    val optimize: () -> Unit = {},
    val clear: () -> Unit = {},
    val remove: (Int) -> Unit = {},
    val move: (Int, Int) -> Unit = { _, _ -> },
    val playNext: (Int) -> Unit = {},
    val moveToEnd: (Int) -> Unit = {},
    val smartShuffle: () -> Unit = {},
    val add: (Track) -> Unit = {},
    val changeType: (TransitionType?) -> Unit = {},
    val skip: () -> Unit = {},
    val setStyle: (Int) -> Unit = {},
    val setLength: (Int) -> Unit = {},
    val toggleType: (TransitionType) -> Unit = {},
    val setAutoSort: (Boolean) -> Unit = {},
    val close: () -> Unit = {},
)

/** Auto-Mix: verbindet die Anzeige mit der App-Logik. */
@Composable
fun AutoMixScreen(vm: DjViewModel, onClose: () -> Unit) {
    val am = vm.autoMix
    val ps = am.plannerSettings()
    AutoMixContent(
        AutoMixUi(
            queue = am.queue, running = am.running, status = am.status?.resolve(),
            nextTrack = am.nextTrack, nextPlan = am.nextPlan, forcedType = am.forcedType,
            countdown = { am.secondsUntilNext() },
            style = vm.settings.mixStyle, lengthBars = vm.settings.transitionBars, enabled = ps.enabled,
            suggestions = remember(am.queue.size, vm.decks[0].track, vm.decks[1].track) { vm.queueSuggestions() },
            log = am.logLines,
            pinned = am.pinned.toSet(),
            autoSort = vm.settings.queueAutoSort,
        ),
        AutoMixActions(
            startStop = {
                if (am.running) am.stop() else {
                    am.start()
                    if (!vm.batteryUnrestricted()) vm.message = UiText(R.string.bg_hint_short)
                }
            },
            mixNow = { am.mixNow() },
            optimize = { am.optimize(); vm.message = UiText(R.string.am_optimized) },
            clear = { am.clear() },
            remove = { am.remove(it) },
            move = { from, to -> am.move(from, to) },
            playNext = { am.playNext(it) },
            moveToEnd = { am.moveToEnd(it) },
            smartShuffle = { am.smartShuffle(); vm.message = UiText(R.string.am_shuffled) },
            add = { am.add(it) },
            changeType = { am.changeType(it) },
            skip = { am.skipNext() },
            setStyle = { vm.settings.mixStyle = it },
            setLength = { vm.settings.transitionBars = it },
            toggleType = { vm.toggleTransitionType(it) },
            setAutoSort = { vm.settings.queueAutoSort = it },
            close = onClose,
        ),
    )
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
    },
)

/** Auto-Mix-Anzeige: Warteschlange links, Steuerung + nächster Übergang rechts. */
@Composable
fun AutoMixContent(ui: AutoMixUi, act: AutoMixActions) {
    val accent = LocalAccent.current
    OverlayScaffold(stringResource(R.string.nav_automix), act.close) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QueueList(
                ui.queue, act.remove, act.move, Modifier.weight(1f).fillMaxHeight(),
                pinned = ui.pinned, onPlayNext = act.playNext, onMoveToEnd = act.moveToEnd,
            )
            Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton(
                    Modifier.fillMaxWidth().height(56.dp),
                    text = stringResource(if (ui.running) R.string.am_stop else R.string.am_start),
                    icon = if (ui.running) AppIcons.Stop else AppIcons.Play, active = true,
                    color = if (ui.running) AppColors.Red else accent, fontSize = 17,
                ) { act.startStop() }
                ui.status?.let { Text(it, style = AppType.small.copy(color = AppColors.Text)) }
                if (ui.nextPlan != null && ui.nextTrack != null) NextTransitionCard(ui, act)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(R.string.am_optimize), icon = AppIcons.Auto, color = AppColors.Violet, fontSize = 12, iconSize = 16.dp) {
                        act.optimize()
                    }
                    // Smart Shuffle: zufällig, aber mit gutem Flow (BPM/Energie/Genre)
                    NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(R.string.am_smart_shuffle), icon = AppIcons.Shuffle, color = AppColors.Pink, fontSize = 12, iconSize = 16.dp) {
                        act.smartShuffle()
                    }
                }
                AutoMixSettings(ui.style, ui.lengthBars, ui.enabled, act.setStyle, act.setLength, act.toggleType, ui.autoSort, act.setAutoSort)
                if (ui.suggestions.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.lib_tab_suggest))
                    ui.suggestions.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoverThumb(t, 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = AppType.small.copy(color = AppColors.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.bpm?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.0f BPM", it) } ?: t.artist, style = AppType.tiny, maxLines = 1)
                            }
                            NeonButton(Modifier.size(36.dp), icon = AppIcons.Add, iconSize = 18.dp) { act.add(t) }
                        }
                    }
                }
                NeonButton(Modifier.fillMaxWidth().height(40.dp), text = stringResource(R.string.am_clear), icon = AppIcons.Delete, color = AppColors.Red, fontSize = 12, iconSize = 16.dp) { act.clear() }
                Text(stringResource(R.string.am_help2), style = AppType.small)
                LogSection(ui.log)
            }
        }
    }
}

/** Protokoll zum Testen: welcher Übergang wann und warum gewählt wurde (aufklappbar). */
@Composable
private fun LogSection(lines: List<String>) {
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

/** Karte "Als nächstes": Titel, Übergangstyp, Länge, Countdown; Typ ändern, überspringen, sofort mixen. */
@Composable
private fun NextTransitionCard(ui: AutoMixUi, act: AutoMixActions) {
    val accent = LocalAccent.current
    val plan = ui.nextPlan ?: return
    // Countdown 4× pro Sekunde aktualisieren
    var left by remember { mutableStateOf(ui.countdown()) }
    LaunchedEffect(plan) {
        while (true) {
            left = ui.countdown()
            kotlinx.coroutines.delay(250)
        }
    }
    Column(Modifier.fillMaxWidth().glass(corner = 12.dp, glowColor = accent).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.am_next_title, ui.nextTrack?.title ?: ""), style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.am_next_detail, transitionLabel(plan.type), plan.lengthBars, formatTime(left ?: 0.0)),
            style = AppType.small.copy(color = accent),
        )
        if (!plan.matchTempo) Text(stringResource(R.string.am_tempo_note), style = AppType.tiny.copy(color = AppColors.Amber))
        SectionTitle(stringResource(R.string.am_change_type))
        TransitionType.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { t ->
                    NeonButton(Modifier.weight(1f).height(34.dp), text = transitionLabel(t), active = plan.type == t, fontSize = 10, corner = 8.dp) {
                        act.changeType(if (ui.forcedType == t) null else t)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton(Modifier.weight(1f).height(40.dp), text = stringResource(R.string.am_mix_now), icon = AppIcons.Next, color = AppColors.Cyan, fontSize = 11, iconSize = 14.dp) { act.mixNow() }
            NeonButton(Modifier.weight(1f).height(40.dp), text = stringResource(R.string.am_skip_song), color = AppColors.Amber, fontSize = 11) { act.skip() }
        }
    }
}

/** Auto-Mix-Einstellungen: Stil, Länge, erlaubte Übergangstypen (auch in den Einstellungen verwendet). */
@Composable
fun AutoMixSettings(
    style: Int,
    lengthBars: Int,
    enabled: Set<TransitionType>,
    onStyle: (Int) -> Unit,
    onLength: (Int) -> Unit,
    onToggle: (TransitionType) -> Unit,
    autoSort: Boolean? = null,
    onAutoSort: (Boolean) -> Unit = {},
) {
    if (autoSort != null) {
        SectionTitle(stringResource(R.string.am_sort_title))
        NeonButton(
            Modifier.fillMaxWidth().height(40.dp),
            text = stringResource(if (autoSort) R.string.am_sort_on else R.string.am_sort_off),
            icon = AppIcons.Auto, active = autoSort, fontSize = 12, iconSize = 16.dp,
        ) { onAutoSort(!autoSort) }
        Text(stringResource(R.string.am_sort_help), style = AppType.tiny)
    }
    SectionTitle(stringResource(R.string.am_style))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.style_calm, R.string.style_balanced, R.string.style_club).forEachIndexed { i, label ->
            NeonButton(Modifier.weight(1f).height(38.dp), text = stringResource(label), active = style == i, fontSize = 11) { onStyle(i) }
        }
    }
    SectionTitle(stringResource(R.string.am_length))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0, 4, 8, 16, 32).forEach { b ->
            NeonButton(
                Modifier.weight(1f).height(38.dp),
                text = if (b == 0) stringResource(R.string.am_length_auto) else "$b",
                active = lengthBars == b, fontSize = 11,
            ) { onLength(b) }
        }
    }
    SectionTitle(stringResource(R.string.am_types))
    TransitionType.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { t ->
                NeonButton(Modifier.weight(1f).height(34.dp), text = transitionLabel(t), color = AppColors.Violet, active = t in enabled, fontSize = 10, corner = 8.dp) { onToggle(t) }
            }
        }
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
