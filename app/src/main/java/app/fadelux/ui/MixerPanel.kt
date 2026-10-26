package app.fadelux.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import app.fadelux.DeckModel
import app.fadelux.MixerModel
import app.fadelux.R
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.HFader
import app.fadelux.ui.components.Knob
import app.fadelux.ui.components.LevelMeter
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.SpectrumBars
import app.fadelux.ui.components.VFader
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType

/** Mitte: Lautstärke pro Deck, Vorhören, Master, Live-Spektrum, Crossfader. */
@Composable
fun MixerPanel(decks: List<DeckModel>, m: MixerModel, colors: List<Color>, modifier: Modifier = Modifier) {
    Column(modifier.glass().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChannelStrip(decks[0], colors[0])
            Column(
                Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Knob(m.masterVolume, { m.changeMasterVolume(it) }, stringResource(R.string.mix_master), AppColors.Text, min = 0f, max = 1f, default = 0.9f)
                LevelMeter({ m.masterLevel }, colors[0], Modifier.width(10.dp).weight(1f).padding(vertical = 4.dp))
                Knob(m.headphoneVolume, { m.changeHeadphoneVolume(it) }, stringResource(R.string.mix_phones), AppColors.Cue, min = 0f, max = 1f, default = 0.8f)
                Knob(m.cueMix, { m.changeCueMix(it) }, stringResource(R.string.mix_cue_mix), AppColors.Cue, min = 0f, max = 1f, default = 0f)
            }
            ChannelStrip(decks[1], colors[1])
        }
        SpectrumBars({ m.spectrum }, colors[0], Modifier.fillMaxWidth().height(30.dp).padding(vertical = 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = AppType.bodyBold, color = colors[0])
            HFader(m.crossfader, { m.changeCrossfader(it) }, colors[0], colors[1], Modifier.weight(1f).height(42.dp).padding(horizontal = 4.dp))
            Text("B", style = AppType.bodyBold, color = colors[1])
        }
    }
}

@Composable
private fun ChannelStrip(d: DeckModel, color: Color) {
    Column(Modifier.width(54.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        NeonButton(Modifier.fillMaxWidth().height(32.dp), icon = AppIcons.Headphones, color = AppColors.Cue, active = d.pfl, instant = true, iconSize = 18.dp) { d.togglePfl() }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.weight(1f)) {
            if (d.index == 1) LevelMeter({ d.level }, color, Modifier.width(7.dp).fillMaxHeight().padding(vertical = 12.dp))
            VFader(d.volume, { d.changeVolume(it) }, color, Modifier.width(44.dp).fillMaxHeight())
            if (d.index == 0) LevelMeter({ d.level }, color, Modifier.width(7.dp).fillMaxHeight().padding(vertical = 12.dp))
        }
    }
}
