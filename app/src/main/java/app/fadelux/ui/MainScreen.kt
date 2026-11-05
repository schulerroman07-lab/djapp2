package app.fadelux.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.ui.components.AppBackground
import app.fadelux.ui.components.glass
import app.fadelux.ui.components.safeEdges
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent
import kotlinx.coroutines.delay

// Fenster über dem Player
const val VIEW_PLAYER = 0
const val VIEW_LIBRARY = 1
const val VIEW_WISHES = 3
const val VIEW_SETTINGS = 4
const val VIEW_PLAYLISTS = 5

/**
 * Rahmen der App: Player als Hauptbildschirm, darüber bei Bedarf Bibliothek, Playlists,
 * Wünsche oder Einstellungen; "Als Nächstes" und "Playlist wählen" gleiten von unten herein.
 * Hintergrund: lebendiges Leuchten, das auf Beat und Energie reagiert.
 */
@Composable
fun MainScreen(vm: DjViewModel, onLanguageChanged: () -> Unit) {
    var view by rememberSaveable { mutableIntStateOf(VIEW_PLAYER) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val accent = LocalAccent.current

    // Zurück-Taste: zuerst offene Fenster schliessen – die App nie aus Versehen beenden
    BackHandler {
        when {
            queueOpen -> queueOpen = false
            pickerOpen -> pickerOpen = false
            view != VIEW_PLAYER -> view = VIEW_PLAYER
            else -> vm.message = UiText(R.string.msg_use_home)
        }
    }

    AppBackground(vm.settings.bgAnimation, pulse = { vm.beatPulse() }, level = { vm.mixer.masterLevel }) {
        Box(Modifier.fillMaxSize().safeEdges()) {
            PlayerScreen(
                vm,
                onMenu = { target ->
                    when (target) {
                        MenuTarget.PLAYLISTS -> view = VIEW_PLAYLISTS
                        MenuTarget.WISHES -> { view = VIEW_WISHES; vm.clearNewWishes() }
                        MenuTarget.UP_NEXT -> queueOpen = true
                        MenuTarget.LIBRARY -> view = VIEW_LIBRARY
                        MenuTarget.SETTINGS -> view = VIEW_SETTINGS
                    }
                },
                onPicker = { pickerOpen = true },
            )
        }
        // Fenster weich ein-/ausblenden
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 12 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 12 })
            },
            label = "overlay",
        ) { v ->
            when (v) {
                VIEW_LIBRARY -> LibraryScreen(vm) { view = VIEW_PLAYER }
                VIEW_WISHES -> WishesScreen(vm) { view = VIEW_PLAYER }
                VIEW_SETTINGS -> SettingsScreen(vm, onLanguageChanged) { view = VIEW_PLAYER }
                VIEW_PLAYLISTS -> PlaylistsScreen(vm, onPlayed = { view = VIEW_PLAYER }) { view = VIEW_PLAYER }
                else -> Box(Modifier)
            }
        }
        QueueSheet(queueOpen, vm.autoMix, onMessage = { vm.message = it }) { queueOpen = false }
        PlaylistPickerSheet(pickerOpen, vm) { pickerOpen = false }
        // Import/Export: Fortschritt und Ergebnis
        vm.ioProgress?.let { IoProgressDialog(it) { vm.cancelIo() } }
        vm.importResult?.let { ImportResultDialog(it) { vm.importResult = null } }
        // Kurze Meldungen oben
        val msg = vm.message
        LaunchedEffect(msg) {
            if (msg != null) { delay(3500); vm.message = null }
        }
        AnimatedVisibility(
            visible = msg != null,
            modifier = Modifier.align(Alignment.TopCenter).safeEdges().padding(top = 70.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut(),
        ) {
            Box(Modifier.glass(glowColor = accent, strong = true).padding(horizontal = 18.dp, vertical = 10.dp)) {
                msg?.let { Text(it.resolve(), style = AppType.body) }
            }
        }
    }
}
