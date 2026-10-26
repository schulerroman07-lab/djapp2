package app.fadelux.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fadelux.BuildConfig
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.data.AppSettings
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.OverlayScaffold
import app.fadelux.ui.components.SectionTitle
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent

/** Einstellungen mit Kategorien links und Inhalt rechts. */
@Composable
fun SettingsScreen(vm: DjViewModel, onLanguageChanged: () -> Unit, onClose: () -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    OverlayScaffold(stringResource(R.string.nav_settings), onClose) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsNav(section) { section = it }
            Column(Modifier.weight(1f).fillMaxHeight().glass().padding(14.dp).verticalScroll(rememberScrollState())) {
                when (section) {
                    0 -> DesignSection(vm)
                    1 -> LanguageSection(onLanguageChanged)
                    2 -> AudioSection(vm)
                    3 -> LibrarySection(vm)
                    4 -> HelpSection(vm)
                    5 -> AboutSection()
                    else -> Text(stringResource(R.string.privacy_text), style = AppType.body)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DesignSection(vm: DjViewModel) {
    AccentPicker(vm.settings.accentIndex) { vm.settings.accentIndex = it }
}

/** Auswahl der Akzentfarbe (runde Farbfelder). */
@Composable
fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    SectionTitle(stringResource(R.string.set_accent))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppColors.accents.forEachIndexed { i, c ->
            val sel = selected == i
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(c)
                    .border(if (sel) 3.dp else 0.dp, Color.White, CircleShape)
                    .clickable { onSelect(i) },
            )
        }
    }
}

/** Linke Navigation der Einstellungen. */
@Composable
fun SettingsNav(selected: Int, onSelect: (Int) -> Unit) {
    val sections = listOf(
        R.string.set_design, R.string.set_language, R.string.set_audio, R.string.set_library,
        R.string.set_help, R.string.set_about, R.string.set_privacy,
    )
    Column(Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sections.forEachIndexed { i, s ->
            NeonButton(Modifier.fillMaxWidth().height(44.dp), text = stringResource(s), active = selected == i, fontSize = 13) { onSelect(i) }
        }
    }
}

@Composable
private fun ColumnScope.LanguageSection(onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppSettings.language(context)) }
    val langs = listOf("" to stringResource(R.string.lang_system), "de" to "Deutsch", "en" to "English", "fr" to "Français", "it" to "Italiano")
    SectionTitle(stringResource(R.string.set_language))
    langs.forEach { (code, label) ->
        NeonButton(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp), text = label, active = current == code, fontSize = 14) {
            if (current != code) {
                current = code
                AppSettings.setLanguage(context, code)
                onLanguageChanged()
            }
        }
    }
}

@Composable
private fun ColumnScope.AudioSection(vm: DjViewModel) {
    var devices by remember { mutableStateOf(vm.outputDevices()) }
    LaunchedEffect(Unit) { vm.refreshEngineInfo() }
    val s = vm.settings
    val info = vm.engineInfo
    Text(stringResource(R.string.audio_info, info[0].toInt(), info[1].toInt(), info[2].toInt()), style = AppType.small)
    SectionTitle(stringResource(R.string.audio_mode))
    listOf(R.string.audio_mode_stereo, R.string.audio_mode_split, R.string.audio_mode_4ch, R.string.audio_mode_2dev).forEachIndexed { i, label ->
        NeonButton(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp), text = stringResource(label), color = AppColors.Cyan, active = s.outputMode == i, fontSize = 12) {
            vm.setOutput(i, s.masterDevice, s.cueDevice)
        }
    }
    SectionTitle(stringResource(R.string.audio_master_device))
    DevicePicker(devices, s.masterDevice) { vm.setOutput(s.outputMode, it, s.cueDevice) }
    if (s.outputMode == 3) {
        SectionTitle(stringResource(R.string.audio_cue_device))
        DevicePicker(devices, s.cueDevice) { vm.setOutput(s.outputMode, s.masterDevice, it) }
    }
    NeonButton(Modifier.width(200.dp).height(40.dp), text = stringResource(R.string.audio_refresh), fontSize = 12) { devices = vm.outputDevices() }
    SectionTitle(stringResource(R.string.audio_latency))
    Text(stringResource(R.string.audio_latency_hint), style = AppType.small)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Slider(s.cueDelayMs, { vm.changeCueDelay(it) }, valueRange = 0f..500f, modifier = Modifier.weight(1f))
        Text("${s.cueDelayMs.toInt()} ms", style = AppType.body, modifier = Modifier.width(70.dp).padding(start = 8.dp))
    }
    SectionTitle(stringResource(R.string.audio_quality))
    Text(stringResource(R.string.audio_quality_hint), style = AppType.small)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1 to R.string.audio_q_fast, 2 to R.string.audio_q_normal, 4 to R.string.audio_q_safe).forEach { (b, label) ->
            NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(label), active = s.bufferBursts == b, fontSize = 12) { vm.changeBuffer(b) }
        }
    }
}

@Composable
private fun DevicePicker(devices: List<app.fadelux.OutputDevice>, selected: Int, onSelect: (Int) -> Unit) {
    NeonButton(Modifier.fillMaxWidth().height(40.dp).padding(vertical = 2.dp), text = stringResource(R.string.audio_auto), active = selected == 0, fontSize = 12) { onSelect(0) }
    devices.forEach { d ->
        NeonButton(Modifier.fillMaxWidth().height(40.dp).padding(vertical = 2.dp), text = "${deviceType(d.type)} · ${d.name}", active = selected == d.id, fontSize = 12) { onSelect(d.id) }
    }
}

@Composable
private fun deviceType(type: Int): String = stringResource(
    when (type) {
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.dev_speaker
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.dev_wired
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> R.string.dev_bluetooth
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY -> R.string.dev_usb
        else -> R.string.dev_other
    },
)

@Composable
private fun ColumnScope.LibrarySection(vm: DjViewModel) {
    SectionTitle(stringResource(R.string.lib_folder))
    NeonButton(Modifier.fillMaxWidth().height(40.dp).padding(vertical = 2.dp), text = stringResource(R.string.lib_folder_all), active = vm.settings.libraryFolder.isEmpty(), fontSize = 12) { vm.setLibraryFolder("") }
    vm.folders.forEach { f ->
        NeonButton(Modifier.fillMaxWidth().height(40.dp).padding(vertical = 2.dp), text = f, active = vm.settings.libraryFolder == f, fontSize = 12) { vm.setLibraryFolder(f) }
    }
    Spacer(Modifier.height(10.dp))
    NeonButton(Modifier.width(220.dp).height(44.dp), text = stringResource(R.string.lib_rescan), fontSize = 13) { vm.scanLibrary() }
}

@Composable
private fun ColumnScope.HelpSection(vm: DjViewModel) {
    val faq = listOf(
        R.string.faq_q1 to R.string.faq_a1, R.string.faq_q2 to R.string.faq_a2, R.string.faq_q3 to R.string.faq_a3,
        R.string.faq_q4 to R.string.faq_a4, R.string.faq_q5 to R.string.faq_a5, R.string.faq_q6 to R.string.faq_a6,
    )
    faq.forEach { (q, a) ->
        Text(stringResource(q), style = AppType.bodyBold.copy(color = LocalAccent.current), modifier = Modifier.padding(top = 10.dp))
        Text(stringResource(a), style = AppType.body)
    }
    Spacer(Modifier.height(12.dp))
    NeonButton(Modifier.width(240.dp).height(44.dp), text = stringResource(R.string.onb_show_again), fontSize = 13) { vm.settings.onboardingDone = false }
}

@Composable
private fun ColumnScope.AboutSection() {
    Text(stringResource(R.string.app_name), style = AppType.title.copy(color = LocalAccent.current))
    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = AppType.small)
    Text(stringResource(R.string.about_text), style = AppType.body, modifier = Modifier.padding(vertical = 8.dp))
    SectionTitle(stringResource(R.string.about_licenses))
    listOf(
        "Oboe — Apache License 2.0 (Google)",
        "Jetpack Compose, Room, AndroidX — Apache License 2.0 (Google)",
        "Kotlin & kotlinx.coroutines — Apache License 2.0 (JetBrains)",
        "ZXing (QR codes) — Apache License 2.0",
        "Firebase JS SDK (guest page) — Apache License 2.0 (Google)",
        stringResource(R.string.about_timestretch),
    ).forEach { Text("• $it", style = AppType.small.copy(color = AppColors.Text)) }
}
