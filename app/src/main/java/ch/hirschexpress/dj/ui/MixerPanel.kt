package ch.hirschexpress.dj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.hirschexpress.dj.DeckModel
import ch.hirschexpress.dj.MixerModel

/** Mitte: Lautstärke pro Deck, Kopfhörer-Vorhören, Master, Crossfader. */
@Composable
fun MixerPanel(decks: List<DeckModel>, m: MixerModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DjColors.Panel)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ChannelStrip(decks[0])
            Column(
                Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Knob(m.masterVolume, { m.changeMasterVolume(it) }, "MASTER", DjColors.Text, min = 0f, max = 1f, default = 0.9f, knobSize = 38.dp)
                LevelMeter(m.masterLevel, Modifier.width(10.dp).weight(1f).padding(vertical = 4.dp))
                Knob(m.headphoneVolume, { m.changeHeadphoneVolume(it) }, "🎧 LAUT", DjColors.Cue, min = 0f, max = 1f, default = 0.8f, knobSize = 38.dp)
                Knob(m.cueMix, { m.changeCueMix(it) }, "CUE/MIX", DjColors.Cue, min = 0f, max = 1f, default = 0f, knobSize = 38.dp)
            }
            ChannelStrip(decks[1])
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", color = DjColors.DeckA, fontSize = 14.sp)
            HFader(
                m.crossfader, { m.changeCrossfader(it) },
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .padding(horizontal = 4.dp),
            )
            Text("B", color = DjColors.DeckB, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ChannelStrip(d: DeckModel) {
    val color = DjColors.deck(d.index)
    Column(
        Modifier
            .width(54.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PadButton("🎧", Modifier.fillMaxWidth().height(34.dp), DjColors.Cue, active = d.pfl, fontSize = 16, instant = true) { d.togglePfl() }
        Row(
            Modifier
                .weight(1f)
                .padding(top = 4.dp),
        ) {
            if (d.index == 1) LevelMeter(d.level, Modifier.width(8.dp).fillMaxHeight().padding(vertical = 13.dp))
            VFader(d.volume, { d.changeVolume(it) }, color, Modifier.width(44.dp).fillMaxHeight())
            if (d.index == 0) LevelMeter(d.level, Modifier.width(8.dp).fillMaxHeight().padding(vertical = 13.dp))
        }
    }
}
