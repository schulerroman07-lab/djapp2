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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Icon
import app.fadelux.automix.AutoMix
import app.fadelux.automix.TransitionType
import app.fadelux.ui.components.AppIcons
import androidx.compose.ui.Alignment
import app.fadelux.R
import app.fadelux.data.AppSettings
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.OverlayScaffold
import app.fadelux.ui.components.SectionTitle
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent

/** Bereiche der Einstellungen (Reihenfolge = Anzeige). */
private val sections = listOf(
    R.string.set_flow, R.string.set_transitions, R.string.set_library, R.string.io_title, R.string.set_design,
    R.string.set_language, R.string.set_help, R.string.set_about, R.string.set_credits, R.string.set_privacy,
)

/** Einstellungen: Querformat = Bereiche links, Inhalt rechts; Hochformat = Liste → Bereich (mit Zurück). */
@Composable
fun SettingsScreen(vm: DjViewModel, onLanguageChanged: () -> Unit, onClose: () -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(-1) }
    OverlayScaffold(stringResource(R.string.nav_settings), onClose, actions = {
        if (section >= 0) NeonButton(Modifier.size(44.dp), icon = AppIcons.ArrowLeft, color = AppColors.TextDim) { section = -1 }
    }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            if (wide) {
                val sel = if (section < 0) 0 else section
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsNav(sel) { section = it }
                    Column(Modifier.weight(1f).fillMaxHeight().glass().padding(14.dp).verticalScroll(rememberScrollState())) {
                        SectionContent(sel, vm, onLanguageChanged)
                    }
                }
            } else if (section < 0) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().glass(corner = 14.dp).clickable { section = i }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(s), style = AppType.bodyBold, modifier = Modifier.weight(1f))
                            Text("›", style = AppType.title.copy(color = AppColors.TextDim))
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().glass().padding(14.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(sections[section]), style = AppType.title)
                    SectionContent(section, vm, onLanguageChanged)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SectionContent(i: Int, vm: DjViewModel, onLanguageChanged: () -> Unit) {
    when (i) {
        0 -> FlowSection(vm)
        1 -> TransitionSection(vm)
        2 -> LibrarySection(vm)
        3 -> ImportExportSection(vm)
        4 -> DesignSection(vm)
        5 -> LanguageSection(onLanguageChanged)
        6 -> HelpSection(vm)
        7 -> AboutSection()
        8 -> CreditsSection()
        else -> Text(stringResource(R.string.privacy_text), style = AppType.body)
    }
}

/** Linke Navigation der Einstellungen (Querformat). */
@Composable
fun SettingsNav(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.width(200.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sections.forEachIndexed { i, s ->
            NeonButton(Modifier.fillMaxWidth().height(44.dp), text = stringResource(s), active = selected == i, fontSize = 13) { onSelect(i) }
        }
    }
}

/** Ablauf: Energie-Kurve, Genre-Blöcke, automatisches Einreihen. */
@Composable
private fun ColumnScope.FlowSection(vm: DjViewModel) {
    FlowOptions(vm.settings)
    Spacer(Modifier.height(12.dp))
    SectionTitle(stringResource(R.string.am_sort_title))
    NeonButton(
        Modifier.fillMaxWidth().height(44.dp),
        text = stringResource(if (vm.settings.queueAutoSort) R.string.am_sort_on else R.string.am_sort_off),
        icon = AppIcons.Auto, active = vm.settings.queueAutoSort, fontSize = 12, iconSize = 16.dp,
    ) { vm.settings.queueAutoSort = !vm.settings.queueAutoSort }
    Text(stringResource(R.string.am_sort_help), style = AppType.small)
}

/** Übergänge: Stil, Früh-Übergänge, Länge, erlaubte Typen, Protokoll. */
@Composable
private fun ColumnScope.TransitionSection(vm: DjViewModel) {
    val s = vm.settings
    SectionTitle(stringResource(R.string.am_style))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.style_calm, R.string.style_balanced, R.string.style_club).forEachIndexed { i, label ->
            NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(label), active = s.mixStyle == i, fontSize = 12) { s.mixStyle = i }
        }
    }
    Text(stringResource(R.string.style_help), style = AppType.small)
    SectionTitle(stringResource(R.string.early_setting))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.early_ask, R.string.early_always, R.string.early_never).forEachIndexed { i, label ->
            NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(label), active = s.earlyMode == i, fontSize = 12) { s.earlyMode = i }
        }
    }
    Text(stringResource(R.string.early_help), style = AppType.small)
    SectionTitle(stringResource(R.string.am_length))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0, 8, 16, 32).forEach { b ->
            NeonButton(
                Modifier.weight(1f).height(40.dp),
                text = if (b == 0) stringResource(R.string.am_length_auto) else "$b",
                active = s.transitionBars == b, fontSize = 12,
            ) { s.transitionBars = b }
        }
    }
    SectionTitle(stringResource(R.string.am_types))
    val enabled = vm.autoMix.plannerSettings().enabled
    TransitionType.entries.chunked(2).forEach { row ->
        Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { t ->
                NeonButton(Modifier.weight(1f).height(40.dp), text = transitionLabel(t), color = AppColors.Violet, active = t in enabled, fontSize = 11) {
                    vm.toggleTransitionType(t)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(10.dp))
    LogSection(vm.autoMix.logLines)
}

@Composable
private fun ColumnScope.DesignSection(vm: DjViewModel) {
    AccentPicker(vm.settings.accentIndex) { vm.settings.accentIndex = it }
    // Animierter Hintergrund: aus / Energiesparen / voll
    SectionTitle(stringResource(R.string.set_bg_anim))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.set_bg_off, R.string.set_bg_eco, R.string.set_bg_full).forEachIndexed { i, label ->
            NeonButton(Modifier.weight(1f).height(44.dp), text = stringResource(label), active = vm.settings.bgAnimation == i, fontSize = 13) {
                vm.settings.bgAnimation = i
            }
        }
    }
    Text(stringResource(R.string.set_bg_help), style = AppType.small)
}

/** Auswahl der Akzentfarbe (runde Farbfelder). */
@Composable
fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    SectionTitle(stringResource(R.string.set_accent))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppColors.accents.forEachIndexed { i, c ->
            val sel = selected == i
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c)
                    .border(if (sel) 3.dp else 0.dp, Color.White, CircleShape)
                    .clickable { onSelect(i) },
            )
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

/** Hinweis zur Akku-Optimierung (sonst kann Android die Musik im Hintergrund stoppen). */
@Composable
private fun BackgroundCard(vm: DjViewModel) {
    val context = LocalContext.current
    // Beim Zurückkehren aus den System-Einstellungen neu prüfen
    var ok by remember { mutableStateOf(vm.batteryUnrestricted()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ok = vm.batteryUnrestricted()
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    SectionTitle(stringResource(R.string.bg_title))
    Column(Modifier.fillMaxWidth().glass(corner = 12.dp, glowColor = if (ok) null else AppColors.Amber).padding(10.dp)) {
        Text(
            stringResource(if (ok) R.string.bg_ok else R.string.bg_restricted),
            style = AppType.small.copy(color = if (ok) AppColors.Emerald else AppColors.Amber),
        )
        if (!ok) {
            Spacer(Modifier.height(6.dp))
            NeonButton(Modifier.fillMaxWidth().height(40.dp), text = stringResource(R.string.bg_open_settings), color = AppColors.Amber, fontSize = 12) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                )
                runCatching { context.startActivity(intent) }
            }
        }
    }
}

@Composable
private fun ColumnScope.LibrarySection(vm: DjViewModel) {
    val unanalyzed by vm.unanalyzed.collectAsStateWithLifecycle()
    Text(
        if (unanalyzed > 0) stringResource(R.string.lib_analyzing, unanalyzed) else stringResource(R.string.lib_analysis_done),
        style = AppType.small,
    )
    SectionTitle(stringResource(R.string.lib_folder))
    NeonButton(Modifier.fillMaxWidth().height(40.dp).padding(vertical = 2.dp), text = stringResource(R.string.lib_folder_all), active = vm.settings.libraryFolder.isEmpty(), fontSize = 12) { vm.setLibraryFolder("") }
    vm.folders.forEach { f ->
        NeonButton(Modifier.fillMaxWidth().height(40.dp).padding(vertical = 2.dp), text = f, active = vm.settings.libraryFolder == f, fontSize = 12) { vm.setLibraryFolder(f) }
    }
    Spacer(Modifier.height(10.dp))
    NeonButton(Modifier.fillMaxWidth().height(44.dp), text = stringResource(R.string.lib_rescan), fontSize = 13) { vm.scanLibrary() }
}

@Composable
private fun ColumnScope.HelpSection(vm: DjViewModel) {
    BackgroundCard(vm)
    val faq = listOf(
        R.string.faq2_q1 to R.string.faq2_a1, R.string.faq2_q2 to R.string.faq2_a2, R.string.faq2_q3 to R.string.faq2_a3,
        R.string.faq2_q4 to R.string.faq2_a4, R.string.faq2_q5 to R.string.faq2_a5, R.string.faq2_q6 to R.string.faq2_a6,
        R.string.faq_q7 to R.string.faq_a7,
    )
    faq.forEach { (q, a) ->
        Text(stringResource(q), style = AppType.bodyBold.copy(color = LocalAccent.current), modifier = Modifier.padding(top = 12.dp))
        Text(stringResource(a), style = AppType.body)
    }
}

@Composable
private fun ColumnScope.AboutSection() {
    Text(stringResource(R.string.app_name), style = AppType.title.copy(color = LocalAccent.current))
    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = AppType.small)
    Text(stringResource(R.string.about_text2), style = AppType.body, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun ColumnScope.CreditsSection() {
    Text(stringResource(R.string.credits_intro), style = AppType.body, modifier = Modifier.padding(bottom = 6.dp))
    listOf(
        "Oboe — Apache License 2.0 (Google)",
        "AndroidX Media3 — Apache License 2.0 (Google)",
        "Jetpack Compose, Room, AndroidX — Apache License 2.0 (Google)",
        "Kotlin & kotlinx.coroutines — Apache License 2.0 (JetBrains)",
        "ZXing (QR codes) — Apache License 2.0",
        "Firebase JS SDK (guest page) — Apache License 2.0 (Google)",
        stringResource(R.string.about_timestretch),
        stringResource(R.string.credits_key),
    ).forEach { Text("• $it", style = AppType.small.copy(color = AppColors.Text), modifier = Modifier.padding(vertical = 2.dp)) }
}
