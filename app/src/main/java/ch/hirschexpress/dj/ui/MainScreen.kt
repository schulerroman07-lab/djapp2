package ch.hirschexpress.dj.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.hirschexpress.dj.DjViewModel
import kotlinx.coroutines.delay

// Welche Ansicht über dem Mixer liegt
private const val VIEW_MIXER = 0
private const val VIEW_LIBRARY = 1
private const val VIEW_WISHES = 2
private const val VIEW_SETTINGS = 3

@Composable
fun MainScreen(vm: DjViewModel) {
    var view by rememberSaveable { mutableIntStateOf(VIEW_MIXER) }

    // Zurück-Taste: Fenster schliessen – aber die App nie aus Versehen beenden
    BackHandler {
        if (view != VIEW_MIXER) view = VIEW_MIXER
        else vm.message = "Zum Beenden die Home-Taste benutzen (Musik läuft weiter)"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DjColors.Background),
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp)) {
            TopBar(
                vm.engineOk, vm.newWishes, vm.recording, vm.recordSeconds,
                onLibrary = { view = VIEW_LIBRARY }, onWishes = { view = VIEW_WISHES },
                onRecord = { vm.toggleRecording() }, onSettings = { view = VIEW_SETTINGS },
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeckPanel(vm.decks[0], Modifier.weight(1f).fillMaxHeight())
                MixerPanel(vm.decks, vm.mixer, Modifier.width(204.dp).fillMaxHeight())
                DeckPanel(vm.decks[1], Modifier.weight(1f).fillMaxHeight())
            }
        }
        when (view) {
            VIEW_LIBRARY -> LibraryPanel(vm) { view = VIEW_MIXER }
            VIEW_WISHES -> WishesPanel(vm) { view = VIEW_MIXER }
            VIEW_SETTINGS -> SettingsPanel(vm) { view = VIEW_MIXER }
        }
        // Kurze Meldungen unten einblenden
        vm.message?.let { msg ->
            LaunchedEffect(msg) {
                delay(3500)
                vm.message = null
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DjColors.PanelLight)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text(msg, color = DjColors.Text, fontSize = 15.sp) }
        }
    }
}

@Composable
fun TopBar(
    engineOk: Boolean,
    newWishes: Int,
    recording: Boolean,
    recordSeconds: Double,
    onLibrary: () -> Unit,
    onWishes: () -> Unit,
    onRecord: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("HIRSCH EXPRESS DJ", color = DjColors.DeckA, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        if (!engineOk) Text("⚠ Audio-Fehler", color = DjColors.Red, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        PadButton("♫ Bibliothek", Modifier.width(116.dp).fillMaxHeight(), DjColors.DeckA, fontSize = 14) { onLibrary() }
        PadButton(
            if (newWishes > 0) "★ Wünsche (${newWishes})" else "★ Wünsche",
            Modifier.width(128.dp).fillMaxHeight(), DjColors.Cue, active = newWishes > 0, fontSize = 14,
        ) { onWishes() }
        PadButton(
            if (recording) "● REC " + formatTime(recordSeconds) else "● Aufnahme",
            Modifier.width(124.dp).fillMaxHeight(), DjColors.Red, active = recording, fontSize = 14,
        ) { onRecord() }
        PadButton("⚙", Modifier.width(44.dp).fillMaxHeight(), fontSize = 18) { onSettings() }
    }
}
