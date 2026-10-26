package app.fadelux.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.data.PlaylistEntry
import app.fadelux.data.PlaylistSummary
import app.fadelux.data.Track
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.ConfirmDialog
import app.fadelux.ui.components.CoverThumb
import app.fadelux.ui.components.NameDialog
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.OverlayScaffold
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.roundToInt

/** Dauer lesbar: "42 min" oder "1 h 05". */
fun formatDuration(ms: Long): String {
    val min = (ms / 60000).toInt()
    return if (min < 60) "$min min" else String.format(Locale.US, "%d h %02d", min / 60, min % 60)
}

/** Playlist-Seite: Übersicht oder Detail einer Playlist. */
@Composable
fun PlaylistsScreen(vm: DjViewModel, onClose: () -> Unit) {
    var openId by rememberSaveable { mutableLongStateOf(-1L) }
    val summaries by vm.playlistSummaries.collectAsStateWithLifecycle()
    val open = summaries.firstOrNull { it.id == openId }
    if (open != null) {
        PlaylistDetail(vm, open, onBack = { openId = -1L }, onClose = onClose)
    } else {
        PlaylistOverview(vm, summaries, onOpen = { openId = it }, onClose = onClose)
    }
}

@Composable
private fun PlaylistOverview(vm: DjViewModel, summaries: List<PlaylistSummary>, onOpen: (Long) -> Unit, onClose: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }
    OverlayScaffold(stringResource(R.string.nav_playlists), onClose, actions = {
        NeonButton(Modifier.width(170.dp).height(44.dp), text = stringResource(R.string.pl_new), icon = AppIcons.Add, fontSize = 13, iconSize = 18.dp) { creating = true }
    }) {
        if (summaries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pl_empty), style = AppType.body.copy(color = AppColors.TextDim))
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(170.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(summaries, key = { it.id }) { p ->
                    PlaylistCard(vm, p, onOpen = { onOpen(p.id) }, onRename = { renaming = p }, onDelete = { deleting = p })
                }
            }
        }
    }
    if (creating) NameDialog(stringResource(R.string.pl_new), "", stringResource(R.string.pl_create), onConfirm = {
        vm.createPlaylist(it) { id -> onOpen(id) }
        creating = false
    }, onDismiss = { creating = false })
    renaming?.let { p ->
        NameDialog(stringResource(R.string.pl_rename), p.name, stringResource(R.string.pl_ok), onConfirm = {
            vm.renamePlaylist(p.id, it)
            renaming = null
        }, onDismiss = { renaming = null })
    }
    deleting?.let { p ->
        ConfirmDialog(stringResource(R.string.pl_delete), stringResource(R.string.pl_delete_confirm, p.name), stringResource(R.string.pl_delete), onConfirm = {
            vm.deletePlaylist(p.id)
            deleting = null
        }, onDismiss = { deleting = null })
    }
}

/** Karte einer Playlist: Cover-Collage (bis zu 4 Cover), Name, Anzahl, Dauer. */
@Composable
private fun PlaylistCard(vm: DjViewModel, p: PlaylistSummary, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    // Erste 4 Titel für die Collage
    var covers by remember(p.id, p.count) { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(p.id, p.count) { covers = vm.playlists.entries(p.id).first().take(4).map { it.track } }
    Column(Modifier.glass(corner = 14.dp).clickable { onOpen() }.padding(8.dp)) {
        CoverCollage(covers, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(p.name, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.pl_count, p.count, formatDuration(p.durationMs)), style = AppType.small)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            NeonButton(Modifier.weight(1f).height(36.dp), text = stringResource(R.string.pl_rename), fontSize = 11, corner = 8.dp) { onRename() }
            NeonButton(Modifier.size(36.dp), icon = AppIcons.Delete, color = AppColors.Red, iconSize = 16.dp, corner = 8.dp) { onDelete() }
        }
    }
}

/** 2×2-Collage aus Covern (Platzhalter, wenn weniger als 4). */
@Composable
fun CoverCollage(tracks: List<Track>, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp))) {
        for (row in 0..1) {
            Row(Modifier.weight(1f)) {
                for (col in 0..1) {
                    val t = tracks.getOrNull(row * 2 + col) ?: tracks.firstOrNull()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            CoverThumb(t, maxWidth)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(vm: DjViewModel, p: PlaylistSummary, onBack: () -> Unit, onClose: () -> Unit) {
    val entries by vm.playlists.entries(p.id).collectAsStateWithLifecycle(emptyList())
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    OverlayScaffold(p.name, onClose, actions = {
        NeonButton(Modifier.size(44.dp), icon = AppIcons.ArrowLeft, color = AppColors.TextDim) { onBack() }
    }) {
        Text(stringResource(R.string.pl_count, p.count, formatDuration(p.durationMs)), style = AppType.small)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton(Modifier.weight(1f).height(42.dp), text = stringResource(R.string.pl_add_songs), icon = AppIcons.Add, fontSize = 12, iconSize = 16.dp) { adding = true }
            NeonButton(Modifier.weight(1f).height(42.dp), text = stringResource(R.string.pl_all_to_queue), icon = AppIcons.Queue, color = AppColors.Violet, fontSize = 12, iconSize = 16.dp) { vm.queuePlaylist(p.id, false) }
            NeonButton(Modifier.weight(1f).height(42.dp), text = stringResource(R.string.pl_start_automix), icon = AppIcons.Auto, active = true, fontSize = 12, iconSize = 16.dp) { vm.queuePlaylist(p.id, true) }
            NeonButton(Modifier.width(110.dp).height(42.dp), text = stringResource(R.string.io_export), color = AppColors.Cyan, fontSize = 12) { exporting = true }
            NeonButton(Modifier.width(110.dp).height(42.dp), text = stringResource(R.string.pl_rename), fontSize = 12) { renaming = true }
            NeonButton(Modifier.size(42.dp), icon = AppIcons.Delete, color = AppColors.Red, iconSize = 18.dp) { deleting = true }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pl_detail_empty), style = AppType.body.copy(color = AppColors.TextDim))
            }
        } else {
            PlaylistEntryList(
                entries,
                onReorder = { ids -> vm.reorderPlaylist(ids) },
                onRemove = { vm.removeFromPlaylist(it.entryId) },
                onQueue = { vm.autoMix.add(it.track); vm.message = UiText(R.string.msg_queued, it.track.title) },
                onLoad = { e, deck -> vm.loadTrack(deck, e.track) },
            )
        }
    }
    if (adding) AddSongsDialog(vm, p) { adding = false }
    if (exporting) ExportDialog(vm, p) { exporting = false }
    if (renaming) NameDialog(stringResource(R.string.pl_rename), p.name, stringResource(R.string.pl_ok), onConfirm = {
        vm.renamePlaylist(p.id, it)
        renaming = false
    }, onDismiss = { renaming = false })
    if (deleting) ConfirmDialog(stringResource(R.string.pl_delete), stringResource(R.string.pl_delete_confirm, p.name), stringResource(R.string.pl_delete), onConfirm = {
        vm.deletePlaylist(p.id)
        deleting = false
        onBack()
    }, onDismiss = { deleting = false })
}

/** Songs einer Playlist; am Griff ziehen = Reihenfolge ändern (wird beim Loslassen gespeichert). */
@Composable
fun PlaylistEntryList(
    entries: List<PlaylistEntry>,
    onReorder: (List<Long>) -> Unit,
    onRemove: (PlaylistEntry) -> Unit,
    onQueue: (PlaylistEntry) -> Unit,
    onLoad: (PlaylistEntry, Int) -> Unit,
) {
    val accent = LocalAccent.current
    val local = remember(entries) { entries.toMutableStateList() }
    val rowH = 58.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragId by remember { mutableLongStateOf(-1L) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(local, key = { _, e -> e.entryId }) { index, e ->
            val dragging = dragId == e.entryId
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowH)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .padding(vertical = 3.dp)
                    .glass(corner = 12.dp, glowColor = if (dragging) accent else null)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(40.dp).pointerInput(e.entryId) {
                        detectDragGestures(
                            onDragStart = { dragId = e.entryId; dragOffset = 0f },
                            onDragEnd = { dragId = -1L; dragOffset = 0f; onReorder(local.map { it.entryId }) },
                            onDragCancel = { dragId = -1L; dragOffset = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val from = local.indexOfFirst { it.entryId == e.entryId }
                            val steps = (dragOffset / rowPx).roundToInt()
                            if (from >= 0 && steps != 0) {
                                val to = (from + steps).coerceIn(0, local.size - 1)
                                local.add(to, local.removeAt(from))
                                dragOffset -= (to - from) * rowPx
                            }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Drag, null, tint = AppColors.TextDim) }
                Text("${index + 1}", style = AppType.small, modifier = Modifier.width(24.dp))
                CoverThumb(e.track, 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(e.track.title, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.track.artist, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatTime(e.track.durationMs / 1000.0), style = AppType.small)
                Text(e.track.bpm?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.0f", it) } ?: "–", style = AppType.bodyBold, color = accent)
                NeonButton(Modifier.size(width = 40.dp, height = 40.dp), text = "A", fontSize = 14) { onLoad(e, 0) }
                NeonButton(Modifier.size(width = 40.dp, height = 40.dp), text = "B", color = AppColors.Cyan, fontSize = 14) { onLoad(e, 1) }
                NeonButton(Modifier.size(40.dp), icon = AppIcons.Queue, color = AppColors.Violet, iconSize = 18.dp) { onQueue(e) }
                NeonButton(Modifier.size(40.dp), icon = AppIcons.Close, color = AppColors.Red, iconSize = 18.dp) { onRemove(e) }
            }
        }
    }
}

/** Songs aus der Bibliothek zu einer Playlist hinzufügen (Suche + Plus-Knopf). */
@Composable
private fun AddSongsDialog(vm: DjViewModel, p: PlaylistSummary, onDone: () -> Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val added = remember { mutableStateOf(setOf<Long>()) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().padding(24.dp).glass(glowColor = LocalAccent.current, strong = true).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pl_add_songs) + " · " + p.name, style = AppType.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                NeonButton(Modifier.width(120.dp).height(44.dp), text = stringResource(R.string.pl_done), active = true, fontSize = 13) { onDone() }
            }
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.lib_search)) },
                leadingIcon = { Icon(AppIcons.Search, null, tint = AppColors.TextDim) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            val q = query.trim().lowercase()
            val list = remember(tracks, q) { if (q.isEmpty()) tracks else tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) } }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list.size, key = { list[it].id }) { i ->
                    val t = list[i]
                    val done = t.id in added.value
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(corner = 10.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoverThumb(t, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        NeonButton(Modifier.size(40.dp), icon = if (done) AppIcons.Check else AppIcons.Add, active = done, iconSize = 18.dp) {
                            vm.addToPlaylist(p.id, p.name, listOf(t))
                            added.value = added.value + t.id
                        }
                    }
                }
            }
        }
    }
}

/** Dialog aus der Bibliothek: Song zu einer Playlist hinzufügen (oder neue Playlist damit anlegen). */
@Composable
fun AddToPlaylistDialog(vm: DjViewModel, track: Track, onDone: () -> Unit) {
    val summaries by vm.playlistSummaries.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(stringResource(R.string.pl_new), track.title, stringResource(R.string.pl_create), onConfirm = {
            vm.createPlaylist(it, listOf(track))
            onDone()
        }, onDismiss = onDone)
        return
    }
    app.fadelux.ui.components.GlassDialog(stringResource(R.string.pl_add_to), onDone) {
        Text(track.title + " – " + track.artist, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Warteschlange: als Nächstes oder ans Ende (wird dort "angeheftet")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(R.string.q_play_next), icon = AppIcons.Next, color = AppColors.Cyan, fontSize = 12, iconSize = 15.dp) {
                vm.autoMix.addNext(track)
                vm.message = UiText(R.string.msg_queued_next, track.title)
                onDone()
            }
            NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(R.string.q_queue_end), icon = AppIcons.Queue, color = AppColors.Cyan, fontSize = 12, iconSize = 15.dp) {
                vm.autoMix.addLast(track)
                vm.message = UiText(R.string.msg_queued, track.title)
                onDone()
            }
        }
        NeonButton(Modifier.fillMaxWidth().height(44.dp), text = stringResource(R.string.pl_new), icon = AppIcons.Add, fontSize = 13, iconSize = 16.dp) { creating = true }
        LazyColumn(Modifier.fillMaxWidth().height(260.dp)) {
            items(summaries.size, key = { summaries[it].id }) { i ->
                val p = summaries[i]
                NeonButton(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp), text = "${p.name}  (${p.count})", color = AppColors.Violet, fontSize = 13) {
                    vm.addToPlaylist(p.id, p.name, listOf(track))
                    onDone()
                }
            }
        }
    }
}
