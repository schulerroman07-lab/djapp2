package app.fadelux.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.data.PlaylistSummary
import app.fadelux.io.ImportResult
import app.fadelux.io.M3u
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.GlassDialog
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.SectionTitle
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent

/** Export einer Playlist: als .m3u8-Datei oder als Ordner mit allen Songs. */
@Composable
fun ExportDialog(vm: DjViewModel, p: PlaylistSummary, onDone: () -> Unit) {
    var numbered by remember { mutableStateOf(true) }
    val m3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.exportM3u(p.id, uri)
        onDone()
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.exportFolder(p.id, p.name, uri, numbered)
        onDone()
    }
    GlassDialog(stringResource(R.string.io_export) + " · " + p.name, onDone) {
        NeonButton(Modifier.fillMaxWidth().height(46.dp), text = stringResource(R.string.io_export_m3u), color = AppColors.Cyan, fontSize = 13) {
            m3uLauncher.launch(M3u.safeName(p.name) + ".m3u8")
        }
        NeonButton(Modifier.fillMaxWidth().height(46.dp), text = stringResource(R.string.io_export_folder), color = AppColors.Violet, fontSize = 13) {
            folderLauncher.launch(null)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(numbered, { numbered = it })
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.io_numbered), style = AppType.small.copy(color = AppColors.Text))
        }
        Text(stringResource(R.string.io_hint), style = AppType.tiny)
    }
}

/** Einstellungen → Import & Export. */
@Composable
fun ColumnScope.ImportExportSection(vm: DjViewModel) {
    val summaries by vm.playlistSummaries.collectAsStateWithLifecycle()
    var exportOf by remember { mutableStateOf<PlaylistSummary?>(null) }
    val m3uPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPlaylist(uri, isFolder = false)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.importPlaylist(uri, isFolder = true)
    }
    Text(stringResource(R.string.io_hint), style = AppType.small)
    SectionTitle(stringResource(R.string.io_title))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonButton(Modifier.weight(1f).height(46.dp), text = stringResource(R.string.io_import_m3u), icon = AppIcons.Add, fontSize = 12, iconSize = 16.dp) {
            m3uPicker.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "text/plain", "application/octet-stream"))
        }
        NeonButton(Modifier.weight(1f).height(46.dp), text = stringResource(R.string.io_import_folder), icon = AppIcons.Folder, fontSize = 12, iconSize = 16.dp) {
            folderPicker.launch(null)
        }
    }
    SectionTitle(stringResource(R.string.io_choose_playlist))
    if (summaries.isEmpty()) Text(stringResource(R.string.io_no_playlists), style = AppType.small)
    summaries.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(corner = 10.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.pl_count, p.count, formatDuration(p.durationMs)), style = AppType.small)
            }
            NeonButton(Modifier.width(120.dp).height(40.dp), text = stringResource(R.string.io_export), color = AppColors.Cyan, fontSize = 12) { exportOf = p }
        }
    }
    exportOf?.let { p -> ExportDialog(vm, p) { exportOf = null } }
}

/** Fortschritt beim Kopieren (mit Abbrechen). */
@Composable
fun IoProgressDialog(progress: Pair<Int, Int>, onCancel: () -> Unit) {
    GlassDialog(stringResource(R.string.io_export), onCancel) {
        val (done, total) = progress
        Text(stringResource(R.string.io_copying, done, total), style = AppType.body)
        LinearProgressIndicator(
            progress = { if (total > 0) done.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = LocalAccent.current,
            trackColor = Color(0x22FFFFFF),
        )
        Row {
            Spacer(Modifier.weight(1f))
            NeonButton(Modifier.width(140.dp).height(44.dp), text = stringResource(R.string.pl_cancel), color = AppColors.Red, fontSize = 13) { onCancel() }
        }
    }
}

/** Ergebnis eines Imports: wie viele gefunden, welche nicht. */
@Composable
fun ImportResultDialog(r: ImportResult, onDone: () -> Unit) {
    GlassDialog(stringResource(R.string.io_result_title), onDone) {
        Text(
            if (r.found > 0) stringResource(R.string.io_result_found, r.found, r.playlistName) else stringResource(R.string.io_result_none),
            style = AppType.body,
        )
        if (r.missing.isNotEmpty()) {
            Text(stringResource(R.string.io_result_missing, r.missing.size), style = AppType.bodyBold.copy(color = AppColors.Amber))
            Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).glass(corner = 10.dp).padding(8.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    r.missing.forEach { Text("• $it", style = AppType.small.copy(color = AppColors.Text), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Row {
            Spacer(Modifier.weight(1f))
            NeonButton(Modifier.width(120.dp).height(44.dp), text = stringResource(R.string.pl_ok), active = true, fontSize = 13) { onDone() }
        }
    }
}
