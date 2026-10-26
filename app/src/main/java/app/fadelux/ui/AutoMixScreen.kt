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

/** Auto-Mix: Warteschlange (Drag & Drop) und Steuerung. */
@Composable
fun AutoMixScreen(vm: DjViewModel, onClose: () -> Unit) {
    val am = vm.autoMix
    val accent = LocalAccent.current
    OverlayScaffold(stringResource(R.string.nav_automix), onClose) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---- Warteschlange ----
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(R.string.am_queue, am.queue.size), style = AppType.small)
                if (am.queue.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.am_queue_empty), style = AppType.body.copy(color = AppColors.TextDim))
                    }
                } else {
                    val rowH = 58.dp
                    val rowPx = with(LocalDensity.current) { rowH.toPx() }
                    var dragId by remember { mutableIntStateOf(-1) }
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(am.queue, key = { _, t -> t.id }) { index, t ->
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
                                            val from = am.queue.indexOfFirst { it.id == t.id }
                                            val steps = (dragOffset / rowPx).roundToInt()
                                            if (from >= 0 && steps != 0) {
                                                val to = (from + steps).coerceIn(0, am.queue.size - 1)
                                                am.move(from, to)
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
                                NeonButton(Modifier.size(40.dp), icon = AppIcons.Delete, color = AppColors.Red, iconSize = 18.dp) { am.remove(index) }
                            }
                        }
                    }
                }
            }
            // ---- Steuerung ----
            Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton(
                    Modifier.fillMaxWidth().height(56.dp),
                    text = stringResource(if (am.running) R.string.am_stop else R.string.am_start),
                    icon = if (am.running) AppIcons.Stop else AppIcons.Play, active = true,
                    color = if (am.running) AppColors.Red else accent, fontSize = 17,
                ) { if (am.running) am.stop() else am.start() }
                am.status?.let { Text(it.resolve(), style = AppType.small.copy(color = AppColors.Text)) }
                NeonButton(Modifier.fillMaxWidth().height(44.dp), text = stringResource(R.string.am_mix_now), icon = AppIcons.Next, enabledColor(am.running), fontSize = 13, iconSize = 18.dp) { am.mixNow() }
                NeonButton(Modifier.fillMaxWidth().height(44.dp), text = stringResource(R.string.am_optimize), icon = AppIcons.Shuffle, color = AppColors.Violet, fontSize = 13, iconSize = 18.dp) {
                    am.optimize()
                    vm.message = UiText(R.string.am_optimized)
                }
                SectionTitle(stringResource(R.string.am_length))
                val options = listOf(4, 8, 16, -10, -20)
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { o ->
                            NeonButton(
                                Modifier.weight(1f).height(40.dp),
                                text = if (o > 0) stringResource(R.string.am_bars, o) else stringResource(R.string.am_seconds, -o),
                                active = vm.settings.transitionBars == o, fontSize = 12,
                            ) { vm.settings.transitionBars = o }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                NeonButton(Modifier.fillMaxWidth().height(40.dp), text = stringResource(R.string.am_clear), icon = AppIcons.Delete, color = AppColors.Red, fontSize = 12, iconSize = 16.dp) { am.clear() }
                Text(stringResource(R.string.am_help), style = AppType.small)
            }
        }
    }
}

@Composable
private fun enabledColor(enabled: Boolean) = if (enabled) AppColors.Cyan else AppColors.TextFaint
