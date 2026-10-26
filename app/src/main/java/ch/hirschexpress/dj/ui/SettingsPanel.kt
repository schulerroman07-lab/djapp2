package ch.hirschexpress.dj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.hirschexpress.dj.DjViewModel
import ch.hirschexpress.dj.OutputDevice

/** Einstellungen: Audio-Ausgabe, Kopfhörer-Verzögerung, Firebase. */
@Composable
fun SettingsPanel(vm: DjViewModel, onClose: () -> Unit) {
    var devices by remember { mutableStateOf(vm.outputDevices()) }
    LaunchedEffect(Unit) { vm.refreshEngineInfo() }
    var apiKey by remember { mutableStateOf(vm.fbApiKey) }
    var projectId by remember { mutableStateOf(vm.fbProjectId) }

    Column(
        Modifier
            .fillMaxSize()
            .background(DjColors.Background)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Einstellungen", color = DjColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            PadButton("✕", Modifier.width(56.dp).height(44.dp), DjColors.Red) { onClose() }
        }

        Section("Audio-Ausgabe")
        Text(vm.engineInfo, color = DjColors.TextDim, fontSize = 12.sp)
        val modes = listOf(
            "Normal: alles auf ein Gerät (kein Vorhören)",
            "Splitter-Kabel: links = Master, rechts = Kopfhörer (je Mono)",
            "USB-Audio-Interface mit 4 Kanälen: 1/2 Master, 3/4 Kopfhörer",
            "Zwei Geräte: Master auf Gerät 1, Kopfhörer auf Gerät 2",
        )
        modes.forEachIndexed { i, label ->
            PadButton(label, Modifier.fillMaxWidth().height(44.dp), DjColors.DeckB, active = vm.outputMode == i, fontSize = 13) {
                vm.setOutput(i, vm.masterDevice, vm.cueDevice)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Geräte", color = DjColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            PadButton("Liste aktualisieren", Modifier.width(170.dp).height(36.dp), fontSize = 12) { devices = vm.outputDevices() }
        }
        DevicePicker("Master (Lautsprecher/Box):", devices, vm.masterDevice) { vm.setOutput(vm.outputMode, it, vm.cueDevice) }
        if (vm.outputMode == 3) {
            DevicePicker("Kopfhörer:", devices, vm.cueDevice) { vm.setOutput(vm.outputMode, vm.masterDevice, it) }
        }

        Section("Kopfhörer-Verzögerung (Latenz-Korrektur)")
        Text(
            "Bluetooth-Boxen spielen ca. 150–300 ms verspätet. Verzögere den Kopfhörer um denselben Wert, " +
                "damit beide gleich klingen. Bei Kabel: 0 ms.",
            color = DjColors.TextDim, fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(vm.cueDelayMs, { vm.changeCueDelay(it) }, valueRange = 0f..500f, modifier = Modifier.weight(1f))
            Text("${vm.cueDelayMs.toInt()} ms", color = DjColors.Text, modifier = Modifier.width(70.dp).padding(start = 8.dp))
        }

        Section("Gästewünsche (Firebase)")
        Text("Werte aus der Firebase-Konsole → Projekteinstellungen. Siehe FIREBASE_ANLEITUNG.md.", color = DjColors.TextDim, fontSize = 12.sp)
        OutlinedTextField(projectId, { projectId = it }, label = { Text("Projekt-ID (z.B. hirsch-express-dj)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("Web-API-Schlüssel (beginnt mit AIza…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        PadButton("Speichern", Modifier.width(160.dp).height(44.dp), DjColors.Play, active = true) {
            vm.saveFirebase(apiKey, projectId)
            vm.message = "Firebase-Einstellungen gespeichert"
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = DjColors.Cue, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DevicePicker(label: String, devices: List<OutputDevice>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, color = DjColors.Text, fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        PadButton("Automatisch", Modifier.height(40.dp).width(120.dp), DjColors.DeckA, active = selected == 0, fontSize = 12) { onSelect(0) }
    }
    devices.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { dev ->
                PadButton(dev.label, Modifier.height(40.dp).weight(1f), DjColors.DeckA, active = selected == dev.id, fontSize = 11) { onSelect(dev.id) }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
