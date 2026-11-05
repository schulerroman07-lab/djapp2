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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.automix.Keys
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

/** Musikbibliothek: Suche, Sortierung, Analyse-Stand; pro Song "Als Nächstes" und "Warteschlange". */
@Composable
fun LibraryScreen(vm: DjViewModel, onClose: () -> Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val unanalyzed by vm.unanalyzed.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sortBpm by rememberSaveable { mutableStateOf(false) }

    OverlayScaffold(stringResource(R.string.nav_library), onClose) {
        val filtered = remember(tracks, query, sortBpm) {
            val q = query.trim().lowercase()
            val list = if (q.isEmpty()) tracks else tracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.genre.lowercase().contains(q)
            }
            if (sortBpm) list.sortedBy { it.bpm ?: 999f } else list
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.lib_search)) },
            leadingIcon = { Icon(AppIcons.Search, null, tint = AppColors.TextDim) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(R.string.lib_play_all), icon = AppIcons.Play, active = true, fontSize = 13, iconSize = 16.dp) {
                vm.playLibrary(); onClose()
            }
            NeonButton(Modifier.width(92.dp).height(44.dp), text = if (sortBpm) "BPM ↑" else "A–Z", fontSize = 13) { sortBpm = !sortBpm }
            NeonButton(Modifier.size(44.dp), icon = AppIcons.Sync, fontSize = 13, iconSize = 18.dp) { vm.scanLibrary() }
        }
        Text(
            stringResource(R.string.lib_count, tracks.size) + (if (unanalyzed > 0) " · " + stringResource(R.string.lib_analyzing, unanalyzed) else "") +
                " · " + stringResource(R.string.pl_hint_longpress),
            style = AppType.small, modifier = Modifier.padding(bottom = 4.dp),
        )
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.lib_empty), style = AppType.body.copy(color = AppColors.TextDim))
            }
            return@OverlayScaffold
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { t -> LibraryRow(vm, t) }
        }
    }
}

/** Verbindet eine Zeile mit der App-Logik. Lange drücken = Menü (Als Nächstes, Ans Ende, Playlist). */
@Composable
private fun LibraryRow(vm: DjViewModel, t: Track) {
    var menu by remember { mutableStateOf(false) }
    TrackRow(
        t,
        onNext = { vm.autoMix.addNext(t); vm.message = UiText(R.string.msg_queued_next, t.title) },
        onQueue = { vm.autoMix.add(t); vm.message = UiText(R.string.msg_queued, t.title) },
        onLongPress = { menu = true },
    )
    if (menu) AddToPlaylistDialog(vm, t) { menu = false }
}

/** Eine Zeile: Cover, Titel, Interpret, Länge, BPM, Tonart, Energie, Knöpfe "Als Nächstes" und "Warteschlange". */
@Composable
fun TrackRow(t: Track, onNext: () -> Unit, onQueue: () -> Unit, onLongPress: (() -> Unit)? = null) {
    val accent = LocalAccent.current
    val view = androidx.compose.ui.platform.LocalView.current
    val longPress by androidx.compose.runtime.rememberUpdatedState(onLongPress)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(corner = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    longPress?.let { cb ->
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        cb()
                    }
                })
            }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoverThumb(t, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(t.title, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(t.artist, formatTime(t.durationMs / 1000.0)).joinToString(" · "),
                style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (val b = t.bpm) {
                    null -> "…"
                    0f -> "?"
                    else -> String.format(Locale.US, "%.0f", b)
                },
                style = AppType.bodyBold, color = accent,
            )
            Text(Keys.label(t.musicalKey), style = AppType.tiny)
        }
        EnergyBar(t.energy, Modifier.width(28.dp).height(10.dp))
        NeonButton(Modifier.size(42.dp), icon = AppIcons.Next, color = AppColors.Cyan, iconSize = 18.dp) { onNext() }
        NeonButton(Modifier.size(42.dp), icon = AppIcons.Queue, color = AppColors.Violet, iconSize = 18.dp) { onQueue() }
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
