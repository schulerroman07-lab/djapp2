package ch.hirschexpress.dj.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.hirschexpress.dj.DjViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Gästewünsche: Event starten, QR-Code zeigen, Wünsche live sehen. */
@Composable
fun WishesPanel(vm: DjViewModel, onClose: () -> Unit) {
    LaunchedEffect(Unit) { vm.clearNewWishes() }
    LaunchedEffect(vm.wishes.size) { vm.clearNewWishes() }
    var eventName by rememberSaveable { mutableStateOf("Hirsch Express") }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    Row(
        Modifier
            .fillMaxSize()
            .background(DjColors.Background)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Links: Event / QR-Code
        Column(
            Modifier
                .width(300.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gästewünsche", color = DjColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                PadButton("✕", Modifier.width(56.dp).height(44.dp), DjColors.Red) { onClose() }
            }
            Spacer(Modifier.height(8.dp))
            if (!vm.firebaseConfigured) {
                Text(
                    "Für Gästewünsche braucht es ein (gratis) Firebase-Projekt.\n\n" +
                        "Anleitung: Datei FIREBASE_ANLEITUNG.md im Projekt.\n" +
                        "Danach API-Key und Projekt-ID unter ⚙ Einstellungen eintragen.",
                    color = DjColors.TextDim, fontSize = 14.sp,
                )
            } else if (vm.eventId == null) {
                OutlinedTextField(eventName, { eventName = it }, label = { Text("Name des Events") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                PadButton(
                    if (vm.eventBusy) "Startet…" else "Event starten", Modifier.fillMaxWidth().height(56.dp),
                    DjColors.Play, active = true, fontSize = 18,
                ) { if (!vm.eventBusy) vm.startEvent(eventName) }
                Text(
                    "Die Songliste deiner Bibliothek wird hochgeladen, damit Gäste darin suchen können.",
                    color = DjColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                vm.qrBitmap?.let {
                    Image(it.asImageBitmap(), "QR-Code", Modifier.size(200.dp))
                }
                Text(vm.wishUrl, color = DjColors.TextDim, fontSize = 11.sp)
                Text("Gäste scannen den QR-Code mit der Handy-Kamera.", color = DjColors.Text, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                PadButton("Event beenden", Modifier.fillMaxWidth().height(44.dp), DjColors.Red) { vm.endEvent() }
            }
            vm.wishError?.let { Text(it, color = DjColors.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
        // Rechts: Liste der Wünsche
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("${vm.wishes.count { !it.done }} offen · ${vm.wishes.size} total", color = DjColors.TextDim, fontSize = 13.sp)
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.wishes, key = { it.id }) { w ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(DjColors.Panel)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(timeFmt.format(w.createdAt), color = DjColors.TextDim, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                        Column(Modifier.weight(1f)) {
                            val deco = if (w.done) TextDecoration.LineThrough else null
                            if (w.song.isNotBlank()) Text(w.song, color = DjColors.Cue, fontSize = 16.sp, fontWeight = FontWeight.Bold, textDecoration = deco)
                            if (w.text.isNotBlank()) Text(w.text, color = if (w.done) DjColors.TextDim else DjColors.Text, fontSize = 15.sp, textDecoration = deco)
                        }
                        PadButton("✓", Modifier.width(52.dp).height(44.dp), DjColors.Play, active = w.done, fontSize = 20) { vm.markWish(w) }
                        Spacer(Modifier.width(6.dp))
                        PadButton("🗑", Modifier.width(52.dp).height(44.dp), DjColors.Red, fontSize = 18) { vm.deleteWish(w) }
                    }
                }
            }
        }
    }
}
