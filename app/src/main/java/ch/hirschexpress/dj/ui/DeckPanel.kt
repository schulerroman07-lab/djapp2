package ch.hirschexpress.dj.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.hirschexpress.dj.DeckModel
import java.util.Locale

fun formatTime(sec: Double): String {
    val s = sec.coerceAtLeast(0.0).toInt()
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/** Ein Deck: Titelinfo, Wellenform, Play/Cue/Sync, EQ + Effekte, Tempo-Regler. */
@Composable
fun DeckPanel(d: DeckModel, modifier: Modifier = Modifier) {
    val color = DjColors.deck(d.index)
    val mirrored = d.index == 1  // Deck B: Tempo-Regler rechts
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DjColors.Panel)
            .padding(6.dp),
    ) {
        if (!mirrored) TempoColumn(d, color)
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
        ) {
            // --- Titelinfo ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) { Text(d.name, color = DjColors.Background, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    val t = d.track
                    Text(
                        when {
                            t == null -> "Kein Titel geladen"
                            d.loadState == 1 -> "Lädt… ${t.title}"
                            else -> t.title
                        },
                        color = DjColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(t?.artist ?: "Bibliothek öffnen und laden", color = DjColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (d.bpm > 0) String.format(Locale.US, "%.1f", d.effectiveBpm) else "--.-",
                        color = color, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    )
                    Text(
                        "-" + formatTime(d.duration - d.position),
                        color = DjColors.TextDim, fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // --- Wellenform ---
            val prog = if (d.duration > 0) (d.position / d.duration).toFloat() else 0f
            val cueProg = if (d.duration > 0) (d.cue / d.duration).toFloat() else 0f
            Waveform(
                d.waveform, prog, cueProg, color,
                Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                onSeek = { d.seekFraction(it) },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(d.position), color = DjColors.Text, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "Tempo %+.1f%%", (d.tempo - 1f) * 100f),
                    color = DjColors.TextDim, fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            // --- Effekte und EQ ---
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                val labels = listOf("HOCH", "MITTE", "TIEF")
                for (i in 0..2) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Knob(d.eq[i], { d.setEq(i, it) }, labels[i], color, dimmed = d.kill[i])
                        PadButton(
                            "KILL", Modifier.width(46.dp).height(24.dp), DjColors.Red,
                            active = d.kill[i], fontSize = 10,
                        ) { d.toggleKill(i) }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Knob(d.filter, { d.changeFilter(it) }, "FILTER", DjColors.Cue)
                    Text(
                        when {
                            d.filter < -0.02f -> "Tief"
                            d.filter > 0.02f -> "Hoch"
                            else -> "aus"
                        },
                        color = DjColors.TextDim, fontSize = 10.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Knob(d.echoAmount, { d.changeEchoAmount(it) }, "ECHO", DjColors.Play, min = 0f, max = 1f, default = 0.4f)
                    PadButton(
                        "ECHO", Modifier.width(46.dp).height(24.dp), DjColors.Play,
                        active = d.echoOn, fontSize = 10,
                    ) { d.toggleEcho() }
                }
            }
            Spacer(Modifier.weight(1f))
            // --- Transport ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PadButton("CUE", Modifier.weight(1f).fillMaxHeight(), DjColors.Cue, fontSize = 18) { d.cuePress() }
                PadButton(
                    if (d.playing) "❚❚" else "▶", Modifier.weight(1.3f).fillMaxHeight(), DjColors.Play,
                    active = d.playing, fontSize = 22,
                ) { d.togglePlay() }
                PadButton("SYNC", Modifier.weight(1f).fillMaxHeight(), color, fontSize = 16) { d.sync() }
            }
        }
        if (mirrored) TempoColumn(d, color)
    }
}

/** Tempo-Regler mit Bereichsumschalter, Keylock und "Schubs"-Knöpfen. */
@Composable
private fun TempoColumn(d: DeckModel, color: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .width(58.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PadButton("±${d.tempoRange}%", Modifier.fillMaxWidth().height(28.dp), color, fontSize = 12) { d.changeRange() }
        PadButton("KEY", Modifier.fillMaxWidth().height(28.dp), color, active = d.keylock, fontSize = 12) { d.toggleKeylock() }
        Text("+", color = DjColors.TextDim, fontSize = 14.sp)
        VFader(
            (d.tempoSlider + 1f) / 2f, { d.changeTempo(it * 2f - 1f) }, color,
            Modifier
                .fillMaxWidth()
                .weight(1f),
            centerMark = true,
            onDoubleTap = { d.resetTempo() },
        )
        Text("−", color = DjColors.TextDim, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HoldButton("‹", Modifier.width(27.dp).height(32.dp), { d.nudge(-1) }, { d.changeTempo(d.tempoSlider) })
            HoldButton("›", Modifier.width(27.dp).height(32.dp), { d.nudge(1) }, { d.changeTempo(d.tempoSlider) })
        }
    }
}
