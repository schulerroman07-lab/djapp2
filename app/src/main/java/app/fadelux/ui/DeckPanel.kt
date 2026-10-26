package app.fadelux.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fadelux.DeckModel
import app.fadelux.R
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.HoldButton
import app.fadelux.ui.components.Knob
import app.fadelux.ui.components.Label
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.VFader
import app.fadelux.ui.components.VinylCover
import app.fadelux.ui.components.Waveform
import app.fadelux.ui.components.ZoomWaveform
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.Dimens
import java.util.Locale

fun formatTime(sec: Double): String {
    val s = sec.coerceAtLeast(0.0).toInt()
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/** Ein Deck: Cover (dreht sich), Titelinfo, Wellenformen, EQ/Effekte, Transport, Tempo. */
@Composable
fun DeckPanel(d: DeckModel, color: Color, modifier: Modifier = Modifier) {
    val mirrored = d.index == 1
    Row(modifier.glass(glowColor = if (d.playing) color else null).padding(6.dp)) {
        if (!mirrored) TempoColumn(d, color)
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp)) {
            DeckHeader(d, color)
            Spacer(Modifier.height(4.dp))
            ZoomWaveform(d.peaks, { d.position }, d.bpm, d.firstBeat, { d.cue }, color, Modifier.fillMaxWidth().height(40.dp))
            Spacer(Modifier.height(3.dp))
            Waveform(
                d.waveform,
                { if (d.duration > 0) (d.position / d.duration).toFloat() else 0f },
                { if (d.duration > 0) (d.cue / d.duration).toFloat() else 0f },
                color, Modifier.fillMaxWidth().height(24.dp),
            ) { d.seekFraction(it) }
            Spacer(Modifier.height(4.dp))
            EffectsRow(d, color)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NeonButton(Modifier.weight(1f).fillMaxHeight(), text = "CUE", color = AppColors.Cue, instant = true, fontSize = 17) { d.cuePress() }
                NeonButton(
                    Modifier.weight(1.3f).fillMaxHeight(), icon = if (d.playing) AppIcons.Pause else AppIcons.Play,
                    color = color, active = d.playing, instant = true, iconSize = 30.dp,
                ) { d.togglePlay() }
                NeonButton(Modifier.weight(1f).fillMaxHeight(), text = "SYNC", icon = AppIcons.Sync, color = color, instant = true, fontSize = 13, iconSize = 16.dp) { d.sync() }
            }
        }
        if (mirrored) TempoColumn(d, color)
    }
}

@Composable
private fun DeckHeader(d: DeckModel, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            VinylCover(d.track, d.playing, d.tempo, 50.dp) { dir ->
                if (dir == 0) d.changeTempo(d.tempoSlider) else d.nudge(dir)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val t = d.track
            Text(
                when {
                    t == null -> stringResource(R.string.deck_empty)
                    d.loadState == 1 -> stringResource(R.string.deck_loading)
                    else -> t.title
                },
                style = AppType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(t?.artist ?: stringResource(R.string.deck_empty_hint), style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            DeckTime(d)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (d.bpm > 0) String.format(Locale.US, "%.1f", d.effectiveBpm) else "--.-",
                style = AppType.number, color = color,
            )
            Text(String.format(Locale.US, "%+.1f%%", (d.tempo - 1f) * 100f), style = AppType.small)
        }
    }
}

/** Zeit-Anzeige als eigene kleine Einheit: nur sie wird 60× pro Sekunde neu gezeichnet. */
@Composable
private fun DeckTime(d: DeckModel) {
    val remaining = d.duration - d.position
    val ending = d.playing && remaining < 30
    Row {
        Text(formatTime(d.position), style = AppType.small.copy(color = AppColors.Text))
        Spacer(Modifier.width(8.dp))
        Text(
            "-" + formatTime(remaining),
            style = AppType.small.copy(
                color = if (ending) AppColors.Red else AppColors.TextDim,
                fontWeight = if (ending) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun EffectsRow(d: DeckModel, color: Color) {
    val labels = listOf(R.string.eq_high, R.string.eq_mid, R.string.eq_low)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0..2) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Knob(d.eq[i], { d.setEq(i, it) }, stringResource(labels[i]), color, dimmed = d.kill[i])
                NeonButton(Modifier.width(40.dp).height(24.dp), text = "KILL", color = AppColors.Red, active = d.kill[i], instant = true, fontSize = 10, corner = 8.dp) { d.toggleKill(i) }
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Knob(d.filter, { d.changeFilter(it) }, stringResource(R.string.fx_filter), AppColors.Amber)
            Label(
                when {
                    d.filter < -0.02f -> "LPF"
                    d.filter > 0.02f -> "HPF"
                    else -> "—"
                },
                Modifier.padding(top = 5.dp),
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Knob(d.echoAmount, { d.changeEchoAmount(it) }, stringResource(R.string.fx_echo), AppColors.Pink, min = 0f, max = 1f, default = 0.4f)
            NeonButton(Modifier.width(40.dp).height(24.dp), text = "FX", color = AppColors.Pink, active = d.echoOn, instant = true, fontSize = 10, corner = 8.dp) { d.toggleEcho() }
        }
    }
}

/** Tempo-Regler mit Bereich (±8/±16 %), Keylock und Schubs-Knöpfen. */
@Composable
private fun TempoColumn(d: DeckModel, color: Color) {
    Column(
        Modifier.width(50.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NeonButton(Modifier.fillMaxWidth().height(28.dp), text = "±${d.tempoRange}", color = color, fontSize = 12, corner = 8.dp) { d.changeRange() }
        NeonButton(Modifier.fillMaxWidth().height(28.dp), text = "KEY", color = color, active = d.keylock, fontSize = 12, corner = 8.dp) { d.toggleKeylock() }
        Text("+", style = AppType.small)
        VFader(
            (d.tempoSlider + 1f) / 2f, { d.changeTempo(it * 2f - 1f) }, color,
            Modifier.fillMaxWidth().weight(1f), centerMark = true, onDoubleTap = { d.resetTempo() },
        )
        Text("−", style = AppType.small)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HoldButton(AppIcons.ArrowDown, Modifier.width(23.dp).height(Dimens.button - 16.dp), { d.nudge(-1) }, { d.changeTempo(d.tempoSlider) })
            HoldButton(AppIcons.ArrowUp, Modifier.width(23.dp).height(Dimens.button - 16.dp), { d.nudge(1) }, { d.changeTempo(d.tempoSlider) })
        }
    }
}
