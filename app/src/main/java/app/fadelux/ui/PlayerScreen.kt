package app.fadelux.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.fadelux.DjViewModel
import app.fadelux.R
import app.fadelux.automix.EarlyOffer
import app.fadelux.automix.Keys
import app.fadelux.automix.TransitionPlan
import app.fadelux.data.Track
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.AppLogo
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.glass
import app.fadelux.ui.components.glow
import app.fadelux.ui.components.rememberCover
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent
import java.util.Locale

/** Ziele im Menü oben rechts. */
enum class MenuTarget { PLAYLISTS, WISHES, UP_NEXT, LIBRARY, SETTINGS }

/** Alles, was der Player anzeigt (ohne Abhängigkeit zur Audio-Engine – so auch im Screenshot-Test). */
data class PlayerUi(
    val track: Track? = null,
    val playing: Boolean = false,
    val transitioning: Boolean = false,
    val position: () -> Double = { 0.0 },
    val duration: Double = 0.0,
    val nextTrack: Track? = null,
    val nextPlan: TransitionPlan? = null,
    val queueSize: Int = 0,
    val countdown: () -> Double? = { null },
    val newWishes: Int = 0,
    val earlyOffer: EarlyOffer? = null,
    val pulse: () -> Float = { 0f },
)

/** Alle Aktionen des Players. */
data class PlayerActions(
    val onMenu: (MenuTarget) -> Unit = {},
    val onPicker: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeek: (Double) -> Unit = {},
    val onEarlyAnswer: (Boolean) -> Unit = {},
)

/** Verbindet den Player mit der App-Logik. */
@Composable
fun PlayerScreen(vm: DjViewModel, onMenu: (MenuTarget) -> Unit, onPicker: () -> Unit) {
    val am = vm.autoMix
    val deck = vm.decks[am.currentDeck]
    PlayerContent(
        PlayerUi(
            track = am.current, playing = vm.isPlaying, transitioning = am.transitioning,
            position = { deck.position }, duration = deck.duration,
            nextTrack = am.nextTrack ?: am.queue.firstOrNull(), nextPlan = am.nextPlan, queueSize = am.queue.size,
            countdown = { am.secondsUntilNext() }, newWishes = vm.newWishes, earlyOffer = am.earlyOffer,
            pulse = { vm.beatPulse() },
        ),
        PlayerActions(
            onMenu = onMenu, onPicker = onPicker,
            onPrevious = { vm.remotePrevious() }, onPlayPause = { vm.togglePlay() }, onNext = { vm.remoteNext() },
            onSeek = { vm.seekTo(it) }, onEarlyAnswer = { am.answerEarly(it) },
        ),
    )
}

/**
 * Hauptbildschirm (Player): grosses Cover, Titel/Interpret/Länge, Fortschrittsbalken
 * (tippen = springen, ziehen = spulen), Zurück / Play-Pause / Weiter, darunter klein der nächste
 * Übergang und "Als Nächstes" (hochwischen). Oben: QR-Knopf für Wünsche (mit Zahl) und Menü.
 * Hochformat: alles untereinander; Querformat: Cover links, Bedienung rechts.
 */
@Composable
fun PlayerContent(ui: PlayerUi, act: PlayerActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            TopBar(ui.newWishes, onQr = { act.onMenu(MenuTarget.WISHES) }, onMenu = act.onMenu)
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val coverSize = min(maxHeight * 0.72f, maxWidth * 0.4f)
                    Box(Modifier.weight(0.45f), contentAlignment = Alignment.Center) {
                        BigCover(ui.track, coverSize, pulse = ui.pulse)
                    }
                    Column(Modifier.weight(0.55f).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayerBody(ui, act)
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val coverSize = min(maxWidth * 0.8f, maxHeight * 0.42f)
                    BigCover(ui.track, coverSize, pulse = ui.pulse)
                    Spacer(Modifier.height(22.dp))
                    PlayerBody(ui, act)
                }
            }
        }
        // Früh-Übergang: "Passt perfekt – jetzt mischen?"
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            EarlyOfferCard(ui.earlyOffer, onAnswer = act.onEarlyAnswer)
        }
    }
}

/** Titel, Fortschritt, Bedienknöpfe, nächster Übergang (oder Startansicht ohne Musik). */
@Composable
private fun ColumnScope.PlayerBody(ui: PlayerUi, act: PlayerActions) {
    val track = ui.track
    if (track == null) {
        Text(stringResource(R.string.player_idle_title), style = AppType.title.copy(fontSize = 24.sp), textAlign = TextAlign.Center)
        Text(stringResource(R.string.player_idle_sub), style = AppType.body.copy(color = AppColors.TextDim), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        NeonButton(
            Modifier.fillMaxWidth(0.8f).height(58.dp), text = stringResource(R.string.picker_title),
            icon = AppIcons.Playlist, active = true, fontSize = 17, corner = 29.dp,
        ) { act.onPicker() }
        return
    }
    // Titel / Interpret / Länge (weich überblendet beim Songwechsel)
    AnimatedContent(track, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) }, label = "info") { t ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t.title, style = AppType.title.copy(fontSize = 23.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(t.artist, style = AppType.body.copy(color = AppColors.TextDim, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extra = listOfNotNull(
                formatTime(t.durationMs / 1000.0),
                t.bpm?.takeIf { it > 0f }?.let { String.format(Locale.US, "%.0f BPM", it) },
                t.musicalKey?.takeIf { it >= 0 }?.let { Keys.label(it) },
            ).joinToString("  ·  ")
            Text(extra, style = AppType.small, modifier = Modifier.padding(top = 2.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    val duration = ui.duration.coerceAtLeast(1.0)
    SeekBar(position = ui.position, duration = duration, enabled = !ui.transitioning, onSeek = act.onSeek, modifier = Modifier.fillMaxWidth())
    TimeRow(ui.position, duration, ui.transitioning)
    Spacer(Modifier.height(10.dp))
    // Zurück · Play/Pause · Weiter
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        RoundButton(AppIcons.Prev, 62.dp, enabled = !ui.transitioning) { act.onPrevious() }
        RoundButton(if (ui.playing) AppIcons.Pause else AppIcons.Play, 84.dp, primary = true) { act.onPlayPause() }
        RoundButton(AppIcons.Next, 62.dp, enabled = !ui.transitioning) { act.onNext() }
    }
    Spacer(Modifier.height(14.dp))
    NextUpBar(ui.nextTrack, ui.nextPlan, ui.queueSize, ui.countdown, ui.transitioning) { act.onMenu(MenuTarget.UP_NEXT) }
}

/** Grosses Cover mit Leuchten; atmet leicht im Beat. Ohne Musik: Logo. */
@Composable
fun BigCover(track: Track?, size: Dp, pulse: () -> Float) {
    val accent = LocalAccent.current
    AnimatedContent(
        track?.id,
        transitionSpec = { (fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.92f)) togetherWith fadeOut(tween(400)) },
        label = "cover",
    ) { id ->
        val t = if (id == track?.id) track else null
        val img = rememberCover(t)
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val p = pulse()
                    scaleX = 1f + 0.012f * p
                    scaleY = 1f + 0.012f * p
                }
                .glow(accent, radius = 26.dp, corner = 28.dp, alpha = 0.45f)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF14201B), Color(0xFF07090B)))),
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else AppLogo(Modifier.fillMaxSize(0.62f), accent)
        }
    }
}

/**
 * Fortschrittsbalken: Tippen springt an die Stelle, Ziehen spult (der Sprung passiert beim
 * Loslassen – so wird nicht bei jeder Fingerbewegung neu geladen). Die Position wird nur beim
 * Zeichnen gelesen (kein Neuaufbau der Oberfläche 60× pro Sekunde).
 */
@Composable
fun SeekBar(position: () -> Double, duration: Double, enabled: Boolean, onSeek: (Double) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    var drag by remember { mutableStateOf<Float?>(null) }
    Canvas(
        modifier
            .height(34.dp)
            .pointerInput(enabled, duration) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> onSeek(duration * (o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled, duration) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { onSeek(duration * it) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, dx ->
                    change.consume()
                    drag = ((drag ?: 0f) + dx / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val f = drag ?: (position() / duration).toFloat().coerceIn(0f, 1f)
        val h = 6.dp.toPx()
        val y = size.height / 2
        drawRoundRect(Color(0x26FFFFFF), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(
            Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent)),
            Offset(0f, y - h / 2), Size(size.width * f, h), CornerRadius(h / 2),
        )
        val r = (if (drag != null) 11.dp else 8.dp).toPx()
        drawCircle(accent.copy(alpha = 0.3f), r * 1.8f, Offset(size.width * f, y))
        drawCircle(if (enabled) Color.White else Color(0xFF8D96A0), r, Offset(size.width * f, y))
    }
}

/** Zeit-Anzeige unter dem Balken (nur 1× pro Sekunde neu). */
@Composable
private fun TimeRow(position: () -> Double, duration: Double, transitioning: Boolean) {
    val sec by remember(position) { derivedStateOf { position().toInt() } }
    Row(Modifier.fillMaxWidth()) {
        Text(formatTime(sec.toDouble()), style = AppType.small)
        Spacer(Modifier.weight(1f))
        if (transitioning) Text(stringResource(R.string.player_transitioning), style = AppType.small.copy(color = LocalAccent.current))
        Spacer(Modifier.weight(1f))
        Text("-" + formatTime(duration - sec), style = AppType.small)
    }
}

/** Runder Knopf (Zurück, Play/Pause, Weiter). */
@Composable
fun RoundButton(icon: ImageVector, size: Dp, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.4f }) {
        NeonButton(
            Modifier.size(size), icon = icon, active = primary, color = if (primary) accent else AppColors.Text,
            iconSize = size * 0.42f, corner = size / 2, instant = false,
        ) { if (enabled) onClick() }
    }
}

/** Unten: nächster Song + Übergang + Countdown. Tippen oder hochwischen öffnet "Als Nächstes". */
@Composable
private fun NextUpBar(next: Track?, plan: TransitionPlan?, queueSize: Int, countdown: () -> Double?, transitioning: Boolean, onOpen: () -> Unit) {
    var left by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(plan) {
        while (true) { left = countdown(); kotlinx.coroutines.delay(500) }
    }
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpen() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { if (drag < -40f) onOpen(); drag = 0f }) { change, dy ->
                    change.consume(); drag += dy
                }
            }
            .glass(corner = 18.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.TextDim))
        Spacer(Modifier.height(6.dp))
        if (next == null) {
            Text(stringResource(R.string.player_queue_empty), style = AppType.small)
        } else {
            Text(
                stringResource(R.string.player_up_next, next.title, queueSize),
                style = AppType.small.copy(color = AppColors.Text), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (plan != null && !transitioning) {
                Text(
                    stringResource(R.string.player_next_transition, transitionLabel(plan.type), formatTime(left ?: 0.0)),
                    style = AppType.tiny.copy(color = LocalAccent.current), maxLines = 1,
                )
            }
        }
    }
}

/** Einblendung "Passt perfekt – jetzt mischen?" mit 10-Sekunden-Countdown. */
@Composable
fun EarlyOfferCard(offer: EarlyOffer?, onAnswer: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    AnimatedVisibility(offer != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val o = offer ?: return@AnimatedVisibility
        var frac by remember(o) { mutableFloatStateOf(1f) }
        LaunchedEffect(o) {
            while (true) {
                frac = ((o.deadlineMs - System.currentTimeMillis()) / 10_000f).coerceIn(0f, 1f)
                if (frac <= 0f) break
                kotlinx.coroutines.delay(50)
            }
        }
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().glass(corner = 20.dp, glowColor = accent, strong = true).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Countdown-Ring
                Canvas(Modifier.size(34.dp)) {
                    drawCircle(Color(0x33FFFFFF), style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                    drawArc(accent, -90f, 360f * frac, false, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.early_title), style = AppType.bodyBold)
                    Text(o.nextTitle, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton(Modifier.weight(1f).height(48.dp), text = stringResource(R.string.early_no), color = AppColors.TextDim, fontSize = 14) { onAnswer(false) }
                NeonButton(Modifier.weight(1f).height(48.dp), text = stringResource(R.string.early_yes), active = true, fontSize = 14) { onAnswer(true) }
            }
        }
    }
}

/** Kopfzeile: kleines Logo + Name, QR-Knopf (mit Zahl neuer Wünsche), Menü. */
@Composable
private fun TopBar(newWishes: Int, onQr: () -> Unit, onMenu: (MenuTarget) -> Unit) {
    val accent = LocalAccent.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppLogo(Modifier.size(30.dp), accent)
        Text(stringResource(R.string.app_name).uppercase(), style = AppType.logo, color = accent, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Box {
            NeonButton(Modifier.size(48.dp), icon = AppIcons.QrCode, color = AppColors.Amber, corner = 24.dp, iconSize = 22.dp) { onQr() }
            if (newWishes > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(22.dp).clip(CircleShape).background(AppColors.Red),
                    contentAlignment = Alignment.Center,
                ) { Text(if (newWishes > 9) "9+" else "$newWishes", style = AppType.tiny.copy(color = Color.White, fontSize = 11.sp)) }
            }
        }
        Box {
            NeonButton(Modifier.size(48.dp), icon = AppIcons.Menu, color = AppColors.Text, corner = 24.dp, iconSize = 22.dp) { menu = true }
            DropdownMenu(menu, onDismissRequest = { menu = false }, modifier = Modifier.background(Color(0xFF14171C))) {
                MenuItem(stringResource(R.string.nav_playlists), AppIcons.Playlist) { menu = false; onMenu(MenuTarget.PLAYLISTS) }
                MenuItem(stringResource(R.string.nav_wishes), AppIcons.Star) { menu = false; onMenu(MenuTarget.WISHES) }
                MenuItem(stringResource(R.string.nav_up_next), AppIcons.Queue) { menu = false; onMenu(MenuTarget.UP_NEXT) }
                MenuItem(stringResource(R.string.nav_library), AppIcons.Library) { menu = false; onMenu(MenuTarget.LIBRARY) }
                MenuItem(stringResource(R.string.nav_settings), AppIcons.Settings) { menu = false; onMenu(MenuTarget.SETTINGS) }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = AppType.body) },
        leadingIcon = { Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(22.dp)) },
        onClick = onClick,
        modifier = Modifier.height(54.dp),
    )
}
