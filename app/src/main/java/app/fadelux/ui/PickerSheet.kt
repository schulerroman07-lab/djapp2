package app.fadelux.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.data.AppSettings
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.BottomSheet
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.SectionTitle
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent

/**
 * "Playlist wählen": Ablauf einstellen (Energie-Kurve / Party-Bogen / Original, Genre-Blöcke),
 * dann eine Playlist (oder die ganze Bibliothek) mit Play starten oder zufällig mischen.
 */
@Composable
fun PlaylistPickerSheet(visible: Boolean, vm: DjViewModel, onClose: () -> Unit) {
    val summaries by vm.playlistSummaries.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    BottomSheet(visible, stringResource(R.string.picker_title), onClose) {
        FlowOptions(vm.settings)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "library") {
                PickerRow(
                    stringResource(R.string.picker_library),
                    stringResource(R.string.lib_count, tracks.size),
                    icon = AppIcons.Library,
                    onPlay = { vm.playLibrary(); onClose() },
                    onShuffle = { vm.playLibrary(shuffle = true); onClose() },
                )
            }
            items(summaries, key = { it.id }) { p ->
                PickerRow(
                    p.name, stringResource(R.string.pl_count, p.count, formatDuration(p.durationMs)),
                    icon = AppIcons.Playlist,
                    onPlay = { vm.playPlaylist(p.id); onClose() },
                    onShuffle = { vm.playPlaylist(p.id, shuffle = true); onClose() },
                )
            }
            if (summaries.isEmpty()) item(key = "hint") {
                Text(stringResource(R.string.picker_no_playlists), style = AppType.small, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).glass(corner = 14.dp).clickable { onPlay() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = AppType.small, maxLines = 1)
        }
        NeonButton(Modifier.size(46.dp), icon = AppIcons.Shuffle, color = AppColors.Pink, corner = 23.dp, iconSize = 20.dp) { onShuffle() }
        NeonButton(Modifier.size(52.dp), icon = AppIcons.Play, active = true, corner = 26.dp, iconSize = 26.dp) { onPlay() }
    }
}

/**
 * Ablauf der Party: Energie-Kurve "Steigernd" oder "Party-Bogen" (oder Originalreihenfolge),
 * optional Genre-Blöcke. Wird auch in den Einstellungen gezeigt.
 */
@Composable
fun FlowOptions(settings: AppSettings) {
    SectionTitle(stringResource(R.string.flow_title))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.flow_rising, R.string.flow_arc, R.string.flow_original).forEachIndexed { i, label ->
            NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(label), active = settings.flowCurve == i, fontSize = 12) {
                settings.flowCurve = i
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    NeonButton(
        Modifier.fillMaxWidth().height(44.dp),
        text = stringResource(if (settings.genreBlocks) R.string.flow_genre_on else R.string.flow_genre_off),
        icon = AppIcons.Library, active = settings.genreBlocks, color = AppColors.Violet, fontSize = 12, iconSize = 16.dp,
    ) { settings.genreBlocks = !settings.genreBlocks }
    Text(
        stringResource(
            when (settings.flowCurve) {
                0 -> R.string.flow_rising_help
                1 -> R.string.flow_arc_help
                else -> R.string.flow_original_help
            },
        ) + if (settings.genreBlocks) " " + stringResource(R.string.flow_genre_help) else "",
        style = AppType.small, modifier = Modifier.padding(top = 4.dp),
    )
}
