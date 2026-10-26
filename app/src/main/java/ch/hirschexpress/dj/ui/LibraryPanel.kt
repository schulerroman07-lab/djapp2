package ch.hirschexpress.dj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.hirschexpress.dj.DjViewModel
import java.util.Locale

/** Musikbibliothek: Suche, Liste mit Titel/Interpret/Länge/BPM, Laden in Deck A oder B. */
@Composable
fun LibraryPanel(vm: DjViewModel, onClose: () -> Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val unanalyzed by vm.unanalyzed.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sortBpm by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(tracks, query, sortBpm) {
        val q = query.trim().lowercase()
        val list = if (q.isEmpty()) tracks else tracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
        if (sortBpm) list.sortedBy { it.bpm ?: 999f } else list
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DjColors.Background)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bibliothek", color = DjColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Suche Titel oder Interpret") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PadButton(if (sortBpm) "Sort: BPM" else "Sort: A–Z", Modifier.width(100.dp).height(48.dp)) { sortBpm = !sortBpm }
            PadButton("Neu einlesen", Modifier.width(120.dp).height(48.dp)) { vm.scanLibrary() }
            PadButton("✕", Modifier.width(56.dp).height(48.dp), DjColors.Red) { onClose() }
        }
        Text(
            "${tracks.size} Titel" + if (unanalyzed > 0) " · BPM-Analyse läuft (noch $unanalyzed)" else "",
            color = DjColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
        )
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Noch keine Musik gefunden.\nKopiere MP3s auf das Handy (z.B. in den Ordner \"Music\") und tippe auf \"Neu einlesen\".",
                    color = DjColors.TextDim, fontSize = 16.sp,
                )
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(DjColors.Panel)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = DjColors.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = DjColors.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatTime(t.durationMs / 1000.0), color = DjColors.TextDim, fontSize = 14.sp, modifier = Modifier.width(52.dp))
                    Text(
                        when (val b = t.bpm) {
                            null -> "…"
                            0f -> "?"
                            else -> String.format(Locale.US, "%.1f", b)
                        },
                        color = DjColors.Cue, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp),
                    )
                    PadButton("A", Modifier.width(60.dp).height(44.dp), DjColors.DeckA, fontSize = 18) {
                        vm.loadTrack(0, t); onClose()
                    }
                    Spacer(Modifier.width(6.dp))
                    PadButton("B", Modifier.width(60.dp).height(44.dp), DjColors.DeckB, fontSize = 18) {
                        vm.loadTrack(1, t); onClose()
                    }
                }
            }
        }
    }
}
