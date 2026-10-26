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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.ui.components.AppBackground
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.glass
import app.fadelux.ui.components.safeEdges
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.Dimens
import app.fadelux.ui.theme.LocalAccent
import kotlinx.coroutines.delay

// Welche Ansicht über dem Mixer liegt
const val VIEW_MIXER = 0
const val VIEW_LIBRARY = 1
const val VIEW_AUTOMIX = 2
const val VIEW_WISHES = 3
const val VIEW_SETTINGS = 4
const val VIEW_PLAYLISTS = 5

@Composable
fun MainScreen(vm: DjViewModel, onLanguageChanged: () -> Unit) {
    var view by rememberSaveable { mutableIntStateOf(VIEW_MIXER) }
    val accent = LocalAccent.current
    val colors = listOf(AppColors.deck(0, accent), AppColors.deck(1, accent))

    // Zurück-Taste: Fenster schliessen – aber die App nie aus Versehen beenden
    BackHandler {
        if (view != VIEW_MIXER) view = VIEW_MIXER
        else vm.message = UiText(R.string.msg_use_home)
    }

    AppBackground {
        if (!vm.settings.onboardingDone) {
            OnboardingScreen { vm.settings.onboardingDone = true }
            return@AppBackground
        }
        Column(Modifier.fillMaxSize().safeEdges().padding(Dimens.gap)) {
            TopBar(
                vm.engineOk, vm.newWishes, vm.recording, vm.recordSeconds, vm.autoMix.running,
                onLibrary = { view = VIEW_LIBRARY }, onPlaylists = { view = VIEW_PLAYLISTS }, onAutoMix = { view = VIEW_AUTOMIX },
                onWishes = { view = VIEW_WISHES; vm.clearNewWishes() },
                onRecord = { vm.toggleRecording() }, onSettings = { view = VIEW_SETTINGS },
            )
            Spacer(Modifier.height(Dimens.gap))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                DeckPanel(vm.decks[0], colors[0], Modifier.weight(1f).fillMaxHeight())
                MixerPanel(
                    vm.decks, vm.mixer, colors, Modifier.width(204.dp).fillMaxHeight(),
                    autoMix = vm.autoMix.running, onTakeOver = { vm.autoMix.takeOver() },
                )
                DeckPanel(vm.decks[1], colors[1], Modifier.weight(1f).fillMaxHeight())
            }
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
                VIEW_LIBRARY -> LibraryScreen(vm) { view = VIEW_MIXER }
                VIEW_AUTOMIX -> AutoMixScreen(vm) { view = VIEW_MIXER }
                VIEW_WISHES -> WishesScreen(vm) { view = VIEW_MIXER }
                VIEW_SETTINGS -> SettingsScreen(vm, onLanguageChanged) { view = VIEW_MIXER }
                VIEW_PLAYLISTS -> PlaylistsScreen(vm) { view = VIEW_MIXER }
                else -> Box(Modifier)
            }
        }
        // Import/Export: Fortschritt und Ergebnis
        vm.ioProgress?.let { IoProgressDialog(it) { vm.cancelIo() } }
        vm.importResult?.let { ImportResultDialog(it) { vm.importResult = null } }
        // Kurze Meldungen unten
        val msg = vm.message
        LaunchedEffect(msg) {
            if (msg != null) { delay(3500); vm.message = null }
        }
        AnimatedVisibility(
            visible = msg != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut(),
        ) {
            Box(Modifier.glass(glowColor = accent, strong = true).padding(horizontal = 18.dp, vertical = 10.dp)) {
                msg?.let { Text(it.resolve(), style = AppType.body) }
            }
        }
    }
}

@Composable
fun TopBar(
    engineOk: Boolean,
    newWishes: Int,
    recording: Boolean,
    recordSeconds: Double,
    autoMixRunning: Boolean,
    onLibrary: () -> Unit,
    onPlaylists: () -> Unit,
    onAutoMix: () -> Unit,
    onWishes: () -> Unit,
    onRecord: () -> Unit,
    onSettings: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().height(Dimens.topBar),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        Text(
            stringResource(R.string.app_name).uppercase(),
            style = AppType.logo,
            color = accent,
            maxLines = 1,
        )
        if (!engineOk) Text("⚠", style = AppType.bodyBold, color = AppColors.Red)
        Spacer(Modifier.weight(1f))
        NeonButton(Modifier.width(108.dp).fillMaxHeight(), text = stringResource(R.string.nav_library), icon = AppIcons.Library, fontSize = 12, iconSize = 17.dp) { onLibrary() }
        NeonButton(Modifier.width(108.dp).fillMaxHeight(), text = stringResource(R.string.nav_playlists), icon = AppIcons.Playlist, color = AppColors.Violet, fontSize = 12, iconSize = 17.dp) { onPlaylists() }
        NeonButton(Modifier.width(104.dp).fillMaxHeight(), text = stringResource(R.string.nav_automix), icon = AppIcons.Auto, active = autoMixRunning, fontSize = 12, iconSize = 17.dp) { onAutoMix() }
        NeonButton(
            Modifier.width(104.dp).fillMaxHeight(),
            text = if (newWishes > 0) "${stringResource(R.string.nav_wishes)} $newWishes" else stringResource(R.string.nav_wishes),
            icon = AppIcons.Star, color = AppColors.Amber, active = newWishes > 0, fontSize = 12, iconSize = 17.dp,
        ) { onWishes() }
        NeonButton(
            Modifier.width(88.dp).fillMaxHeight(),
            text = if (recording) formatTime(recordSeconds) else "REC",
            icon = AppIcons.Record, color = AppColors.Red, active = recording, fontSize = 12, iconSize = 15.dp,
        ) { onRecord() }
        NeonButton(Modifier.width(Dimens.topBar).fillMaxHeight(), icon = AppIcons.Settings, color = AppColors.TextDim, iconSize = 20.dp) { onSettings() }
    }
}
