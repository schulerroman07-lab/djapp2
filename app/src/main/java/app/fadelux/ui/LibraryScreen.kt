package app.fadelux.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.data.Track
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.CoverThumb
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.OverlayScaffold
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent
import java.util.Locale

/** Musikbibliothek: Suche, Cover, BPM, Energie; Tab "Passt als nächstes". */
@Composable
fun LibraryScreen(vm: DjViewModel, onClose: () -> Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val unanalyzed by vm.unanalyzed.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sortBpm by rememberSaveable { mutableStateOf(false) }
    val accent = LocalAccent.current

    OverlayScaffold(stringResource(R.string.nav_library), onClose, actions = {
        NeonButton(Modifier.width(150.dp).height(44.dp), text = stringResource(R.string.lib_tab_all), active = tab == 0, fontSize = 13) { tab = 0 }
        NeonButton(Modifier.width(190.dp).height(44.dp), text = stringResource(R.string.lib_tab_suggest), icon = AppIcons.Bulb, active = tab == 1, fontSize = 13, iconSize = 18.dp) { tab = 1 }
    }) {
        if (tab == 0) {
            val filtered = remember(tracks, query, sortBpm) {
                val q = query.trim().lowercase()
                val list = if (q.isEmpty()) tracks else tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
                if (sortBpm) list.sortedBy { it.bpm ?: 999f } else list
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.lib_search)) },
                    leadingIcon = { Icon(AppIcons.Search, null, tint = AppColors.TextDim) },
                    modifier = Modifier.weight(1f),
                )
                NeonButton(Modifier.width(110.dp).height(48.dp), text = if (sortBpm) "BPM ↑" else "A–Z", fontSize = 13) { sortBpm = !sortBpm }
                NeonButton(Modifier.width(140.dp).height(48.dp), text = stringResource(R.string.lib_rescan), icon = AppIcons.Sync, fontSize = 13, iconSize = 16.dp) { vm.scanLibrary() }
            }
            Text(
                stringResource(R.string.lib_count, tracks.size) + if (unanalyzed > 0) " · " + stringResource(R.string.lib_analyzing, unanalyzed) else "",
                style = AppType.small, modifier = Modifier.padding(vertical = 4.dp),
            )
            if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.lib_empty), style = AppType.body.copy(color = AppColors.TextDim))
                }
                return@OverlayScaffold
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { t -> TrackRow(vm, t, false, onClose) }
            }
        } else {
            val suggestions = remember(tracks, vm.wishes.size, vm.decks[0].track, vm.decks[1].track, vm.mixer.crossfader > 0) { vm.suggestions() }
            Text(stringResource(R.string.lib_suggest_hint), style = AppType.small, modifier = Modifier.padding(bottom = 6.dp))
            if (suggestions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.lib_suggest_empty), style = AppType.body.copy(color = AppColors.TextDim))
                }
                return@OverlayScaffold
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(suggestions, key = { it.track.id }) { s -> TrackRow(vm, s.track, s.isWish, onClose) }
            }
        }
    }
}

/** Eine Zeile: Cover, Titel, Interpret, Länge, BPM, Energie, Knöpfe A / B / Warteschlange. */
@Composable
fun TrackRow(vm: DjViewModel, t: Track, isWish: Boolean, onLoaded: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(corner = 12.dp, glowColor = if (isWish) AppColors.Amber else null).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoverThumb(t, 44.dp)
        Column(Modifier.weight(1f)) {
            Text((if (isWish) "★ " else "") + t.title, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(t.durationMs / 1000.0), style = AppType.small, modifier = Modifier.width(44.dp))
        Text(
            when (val b = t.bpm) {
                null -> "…"
                0f -> "?"
                else -> String.format(Locale.US, "%.1f", b)
            },
            style = AppType.bodyBold, color = accent, modifier = Modifier.width(52.dp),
        )
        EnergyBar(t.energy, Modifier.width(36.dp).height(10.dp))
        NeonButton(Modifier.size(width = 52.dp, height = 42.dp), text = "A", fontSize = 17) { vm.loadTrack(0, t); onLoaded() }
        NeonButton(Modifier.size(width = 52.dp, height = 42.dp), text = "B", color = AppColors.Cyan, fontSize = 17) { vm.loadTrack(1, t); onLoaded() }
        NeonButton(Modifier.size(width = 52.dp, height = 42.dp), icon = AppIcons.Queue, color = AppColors.Violet, iconSize = 20.dp) {
            vm.autoMix.add(t)
            vm.message = UiText(R.string.msg_queued, t.title)
        }
    }
}

/** Kleine Energie-Anzeige (5 Balken). */
@Composable
fun EnergyBar(energy: Float?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        if (energy == null) return@Canvas
        val lit = (energy * 5).toInt().coerceIn(1, 5)
        val bw = size.width / 5f
        for (i in 0 until 5) {
            val h = size.height * (0.4f + 0.12f * i)
            drawRect(
                if (i < lit) AppColors.Amber else AppColors.Amber.copy(alpha = 0.15f),
                androidx.compose.ui.geometry.Offset(i * bw + 1, size.height - h),
                androidx.compose.ui.geometry.Size(bw - 2, h),
            )
        }
    }
}
