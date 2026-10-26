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
)

/** Alle Aktionen der Auto-Mix-Anzeige. */
data class AutoMixActions(
    val startStop: () -> Unit = {},
    val mixNow: () -> Unit = {},
    val optimize: () -> Unit = {},
    val clear: () -> Unit = {},
    val remove: (Int) -> Unit = {},
    val move: (Int, Int) -> Unit = { _, _ -> },
    val add: (Track) -> Unit = {},
    val changeType: (TransitionType?) -> Unit = {},
    val skip: () -> Unit = {},
    val setStyle: (Int) -> Unit = {},
    val setLength: (Int) -> Unit = {},
    val toggleType: (TransitionType) -> Unit = {},
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
            add = { am.add(it) },
            changeType = { am.changeType(it) },
            skip = { am.skipNext() },
            setStyle = { vm.settings.mixStyle = it },
            setLength = { vm.settings.transitionBars = it },
            toggleType = { vm.toggleTransitionType(it) },
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
            QueueList(ui.queue, act.remove, act.move, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton(
                    Modifier.fillMaxWidth().height(56.dp),
                    text = stringResource(if (ui.running) R.string.am_stop else R.string.am_start),
                    icon = if (ui.running) AppIcons.Stop else AppIcons.Play, active = true,
                    color = if (ui.running) AppColors.Red else accent, fontSize = 17,
                ) { act.startStop() }
                ui.status?.let { Text(it, style = AppType.small.copy(color = AppColors.Text)) }
                if (ui.nextPlan != null && ui.nextTrack != null) NextTransitionCard(ui, act)
                NeonButton(Modifier.fillMaxWidth().height(44.dp), text = stringResource(R.string.am_optimize), icon = AppIcons.Shuffle, color = AppColors.Violet, fontSize = 13, iconSize = 18.dp) {
                    act.optimize()
                }
                AutoMixSettings(ui.style, ui.lengthBars, ui.enabled, act.setStyle, act.setLength, act.toggleType)
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
            }
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
) {
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

/** Warteschlange mit Drag & Drop (am Griff ziehen). */
@Composable
fun QueueList(queue: List<Track>, onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(modifier) {
        Text(stringResource(R.string.am_queue, queue.size), style = AppType.small)
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.am_queue_empty), style = AppType.body.copy(color = AppColors.TextDim))
            }
        } else {
            val rowH = 58.dp
            val rowPx = with(LocalDensity.current) { rowH.toPx() }
            var dragId by remember { mutableIntStateOf(-1) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(queue, key = { _, t -> t.id }) { index, t ->
                    val dragging = dragId == t.id.hashCode()
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
                                    onDragStart = { dragId = t.id.hashCode(); dragOffset = 0f },
                                    onDragEnd = { dragId = -1; dragOffset = 0f },
                                    onDragCancel = { dragId = -1; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val from = queue.indexOfFirst { it.id == t.id }
                                    val steps = (dragOffset / rowPx).roundToInt()
                                    if (from >= 0 && steps != 0) {
                                        val to = (from + steps).coerceIn(0, queue.size - 1)
                                        onMove(from, to)
                                        dragOffset -= (to - from) * rowPx
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Drag, null, tint = AppColors.TextDim) }
                        Text("${index + 1}", style = AppType.small, modifier = Modifier.width(22.dp))
                        CoverThumb(t, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(t.bpm?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.0f", it) } ?: "–", style = AppType.bodyBold, color = accent)
                        EnergyBar(t.energy, Modifier.width(30.dp).height(10.dp))
                        NeonButton(Modifier.size(40.dp), icon = AppIcons.Delete, color = AppColors.Red, iconSize = 18.dp) { onRemove(index) }
                    }
                }
            }
        }
    }
}
