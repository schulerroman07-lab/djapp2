package app.fadelux.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.OverlayScaffold
import app.fadelux.ui.components.SectionTitle
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Gästewünsche: Anleitung, Event starten (Internet oder lokales WLAN), QR-Code, Liste. */
@Composable
fun WishesScreen(vm: DjViewModel, onClose: () -> Unit) {
    val accent = LocalAccent.current
    var eventName by rememberSaveable { mutableStateOf("") }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    // Übersetzte Texte für die lokale Gästeseite
    val localTexts = mapOf(
        "title" to stringResource(R.string.guest_title),
        "search" to stringResource(R.string.guest_search),
        "free" to stringResource(R.string.guest_free),
        "send" to stringResource(R.string.guest_send),
        "thanks" to stringResource(R.string.guest_thanks),
        "wait" to stringResource(R.string.guest_wait),
        "empty" to stringResource(R.string.guest_empty),
    )

    OverlayScaffold(stringResource(R.string.nav_wishes), onClose) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // ---- Links: Anleitung / Event ----
            Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!vm.eventActive) {
                    // 3-Schritte-Anleitung
                    Step(1, AppIcons.Play, stringResource(R.string.wish_step1))
                    Step(2, AppIcons.QrCode, stringResource(R.string.wish_step2))
                    Step(3, AppIcons.Star, stringResource(R.string.wish_step3))
                    SectionTitle(stringResource(R.string.wish_mode))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NeonButton(
                            Modifier.weight(1f).height(44.dp), text = stringResource(R.string.wish_mode_online), icon = AppIcons.Star,
                            active = vm.settings.wishMode == 0 && vm.onlineAvailable, fontSize = 12, iconSize = 16.dp,
                        ) { if (vm.onlineAvailable) vm.settings.wishMode = 0 else vm.message = UiText(R.string.wish_online_unavailable) }
                        NeonButton(
                            Modifier.weight(1f).height(44.dp), text = stringResource(R.string.wish_mode_local), icon = AppIcons.Wifi,
                            active = vm.settings.wishMode == 1 || !vm.onlineAvailable, fontSize = 12, iconSize = 16.dp,
                        ) { vm.settings.wishMode = 1 }
                    }
                    Text(
                        stringResource(if (vm.settings.wishMode == 1 || !vm.onlineAvailable) R.string.wish_mode_local_hint else R.string.wish_mode_online_hint),
                        style = AppType.small, modifier = Modifier.padding(vertical = 6.dp),
                    )
                    OutlinedTextField(eventName, { eventName = it }, label = { Text(stringResource(R.string.wish_event_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    NeonButton(
                        Modifier.fillMaxWidth().height(54.dp),
                        text = stringResource(if (vm.eventBusy) R.string.wish_starting else R.string.wish_start), active = true, fontSize = 16,
                    ) { if (!vm.eventBusy) vm.startEvent(eventName.ifBlank { "Party" }, localTexts) }
                } else {
                    vm.qrBitmap?.let {
                        Box(Modifier.glass(glowColor = accent).padding(10.dp)) {
                            Image(it.asImageBitmap(), "QR", Modifier.size(210.dp).clip(RoundedCornerShape(8.dp)))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(vm.wishUrl, style = AppType.small)
                    Text(stringResource(R.string.wish_scan_hint), style = AppType.small.copy(color = AppColors.Text))
                    if (vm.settings.wishMode == 1) Text(stringResource(R.string.wish_local_same_wifi), style = AppType.small.copy(color = AppColors.Amber))
                    Spacer(Modifier.height(10.dp))
                    NeonButton(Modifier.fillMaxWidth().height(44.dp), text = stringResource(R.string.wish_end), color = AppColors.Red, fontSize = 13) { vm.endEvent() }
                }
                vm.wishError?.let { Text(it.resolve(), style = AppType.small.copy(color = AppColors.Red), modifier = Modifier.padding(top = 6.dp)) }
            }
            // ---- Rechts: Liste ----
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(R.string.wish_count, vm.wishes.count { !it.done }, vm.wishes.size), style = AppType.small)
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.wishes, key = { it.id }) { w ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(corner = 12.dp, glowColor = if (!w.done) AppColors.Amber else null).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(timeFmt.format(w.createdAt), style = AppType.small, modifier = Modifier.width(48.dp))
                            Column(Modifier.weight(1f)) {
                                val deco = if (w.done) TextDecoration.LineThrough else null
                                if (w.song.isNotBlank()) Text(w.song, style = AppType.bodyBold.copy(color = AppColors.Amber, textDecoration = deco))
                                if (w.text.isNotBlank()) Text(w.text, style = AppType.body.copy(color = if (w.done) AppColors.TextDim else AppColors.Text, textDecoration = deco))
                            }
                            // Wunsch ist in der Bibliothek: direkt laden oder in die Warteschlange
                            val match = remember(w.id, vm.tracks.value.size) { vm.trackForWish(w) }
                            if (match != null && !w.done) {
                                NeonButton(Modifier.size(width = 40.dp, height = 42.dp), text = "A", fontSize = 15) { vm.loadTrack(0, match) }
                                Spacer(Modifier.width(4.dp))
                                NeonButton(Modifier.size(width = 40.dp, height = 42.dp), text = "B", color = AppColors.Cyan, fontSize = 15) { vm.loadTrack(1, match) }
                                Spacer(Modifier.width(4.dp))
                                NeonButton(Modifier.size(width = 40.dp, height = 42.dp), icon = AppIcons.Queue, color = AppColors.Violet, iconSize = 18.dp) {
                                    vm.autoMix.add(match)
                                    vm.message = UiText(R.string.msg_queued, match.title)
                                }
                                Spacer(Modifier.width(10.dp))
                            }
                            NeonButton(Modifier.size(width = 48.dp, height = 42.dp), icon = AppIcons.Check, color = AppColors.Emerald, active = w.done) { vm.markWish(w) }
                            Spacer(Modifier.width(6.dp))
                            NeonButton(Modifier.size(width = 48.dp, height = 42.dp), icon = AppIcons.Delete, color = AppColors.Red) { vm.deleteWish(w) }
                        }
                    }
                }
            }
        }
    }
}

/** Ein Schritt der Anleitung: Nummer, Symbol, Text. */
@Composable
private fun Step(n: Int, icon: ImageVector, text: String) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(corner = 12.dp).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Text("$n", style = AppType.bodyBold.copy(color = Color.Black))
        }
        Spacer(Modifier.width(8.dp))
        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = AppType.small.copy(color = AppColors.Text))
    }
}
