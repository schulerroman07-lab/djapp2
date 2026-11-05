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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import app.fadelux.data.Track
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.wishes.Wish
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

/**
 * Gästewünsche: QR-Code (startet automatisch – kein Einrichten nötig), Liste der Wünsche.
 * Wünsche, die in der Bibliothek sind, reiht der Auto-DJ selbst an passender Stelle ein.
 * Fehlende Songs werden angezeigt und können abgehakt werden.
 */
@Composable
fun WishesScreen(vm: DjViewModel, onClose: () -> Unit) {
    val accent = LocalAccent.current
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
    val partyName = stringResource(R.string.app_name)
    // Wünsche sofort bereit: beim Öffnen automatisch starten
    LaunchedEffect(Unit) {
        if (!vm.eventActive && !vm.eventBusy) vm.startEvent(partyName, localTexts)
    }

    OverlayScaffold(stringResource(R.string.nav_wishes), onClose) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        QrPanel(vm, partyName, localTexts)
                    }
                    WishList(vm, timeFmt, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        QrPanel(vm, partyName, localTexts, qrSize = 170.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    WishList(vm, timeFmt, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** QR-Code + Hinweise (oder Fehlermeldung mit "Nochmals versuchen"). */
@Composable
private fun QrPanel(vm: DjViewModel, partyName: String, localTexts: Map<String, String>, qrSize: androidx.compose.ui.unit.Dp = 210.dp) {
    val accent = LocalAccent.current
    if (vm.eventActive) {
        vm.qrBitmap?.let {
            Box(Modifier.glass(glowColor = accent).padding(10.dp)) {
                Image(it.asImageBitmap(), "QR", Modifier.size(qrSize).clip(RoundedCornerShape(8.dp)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.wish_scan_hint), style = AppType.small.copy(color = AppColors.Text))
        Text(vm.wishUrl, style = AppType.tiny)
        if (vm.settings.wishMode == 1) Text(stringResource(R.string.wish_local_same_wifi), style = AppType.small.copy(color = AppColors.Amber))
        Spacer(Modifier.height(6.dp))
        NeonButton(Modifier.fillMaxWidth().height(40.dp), text = stringResource(R.string.wish_end), color = AppColors.Red, fontSize = 12) { vm.endEvent() }
    } else {
        Text(stringResource(if (vm.eventBusy) R.string.wish_starting else R.string.wish_not_running), style = AppType.body)
        Spacer(Modifier.height(8.dp))
        NeonButton(Modifier.fillMaxWidth().height(50.dp), text = stringResource(R.string.wish_start), icon = AppIcons.QrCode, active = true, fontSize = 15) {
            if (!vm.eventBusy) vm.startEvent(partyName, localTexts)
        }
    }
    vm.wishError?.let { Text(it.resolve(), style = AppType.small.copy(color = AppColors.Red), modifier = Modifier.padding(top = 6.dp)) }
}

@Composable
private fun WishList(vm: DjViewModel, timeFmt: DateTimeFormatter, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.wish_count, vm.wishes.count { !it.done }, vm.wishes.size), style = AppType.small)
        if (vm.wishes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.wish_none_yet), style = AppType.body.copy(color = AppColors.TextDim))
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.wishes, key = { it.id }) { w ->
                val match = remember(w.id, vm.tracks.value.size) { vm.trackForWish(w) }
                WishRow(
                    w, timeFmt.format(w.createdAt), match,
                    queued = w.id in vm.queuedWishIds,
                    onQueue = { t -> if (vm.queueWish(w, t)) vm.message = UiText(R.string.wish_queued, t.title) },
                    onDone = { vm.markWish(w) },
                    onDelete = { vm.deleteWish(w) },
                )
            }
        }
    }
}

/** Eine Zeile der Wunschliste: Status "eingereiht" / "nicht in der Bibliothek", abhaken, löschen. */
@Composable
fun WishRow(
    w: Wish,
    time: String,
    match: Track?,
    queued: Boolean,
    onQueue: (Track) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(corner = 12.dp, glowColor = if (!w.done && !queued) AppColors.Amber else null).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = AppType.small, modifier = Modifier.width(42.dp))
        Column(Modifier.weight(1f)) {
            val deco = if (w.done) TextDecoration.LineThrough else null
            if (w.song.isNotBlank()) Text(w.song, style = AppType.bodyBold.copy(color = AppColors.Amber, textDecoration = deco), maxLines = 2)
            if (w.text.isNotBlank()) Text(w.text, style = AppType.body.copy(color = if (w.done) AppColors.TextDim else AppColors.Text, textDecoration = deco), maxLines = 3)
            Text(
                stringResource(
                    when {
                        queued -> R.string.wish_status_queued
                        match != null -> R.string.wish_status_available
                        else -> R.string.wish_status_missing
                    },
                ),
                style = AppType.tiny.copy(color = if (queued) accent else AppColors.TextDim),
            )
        }
        if (match != null && !queued && !w.done) {
            NeonButton(Modifier.size(42.dp), icon = AppIcons.Queue, color = AppColors.Violet, iconSize = 18.dp) { onQueue(match) }
            Spacer(Modifier.width(4.dp))
        }
        NeonButton(Modifier.size(42.dp), icon = AppIcons.Check, color = AppColors.Emerald, active = w.done) { onDone() }
        Spacer(Modifier.width(4.dp))
        NeonButton(Modifier.size(42.dp), icon = AppIcons.Delete, color = AppColors.Red) { onDelete() }
    }
}
