package ch.hirschexpress.dj

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import ch.hirschexpress.dj.data.Track
import ch.hirschexpress.dj.ui.DeckPanel
import ch.hirschexpress.dj.ui.DjColors
import ch.hirschexpress.dj.ui.DjTheme
import ch.hirschexpress.dj.ui.MixerPanel
import ch.hirschexpress.dj.ui.TopBar
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** Erstellt Bildschirmfotos der Decks (Samsung-ähnlicher Bildschirm im Querformat). */
class LayoutTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 2340,
            screenHeight = 1080,
            xdpi = 480,
            ydpi = 480,
            density = Density.XXHIGH,
            orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    private fun demoDeck(i: Int) = DeckModel(i).apply {
        track = Track(1, "", if (i == 0) "Ein sehr langer Titelname für den Test" else "Party Song", "Interpret $i", 215000, 126f)
        loadState = 2
        playing = i == 0
        position = 60.0 + i * 100
        duration = 215.0
        bpm = 126f
        cue = 30.0
        waveform = FloatArray(600) { abs(sin(it * 0.3f)) * 0.8f }
        peaks = FloatArray(21500) { abs(sin(it * 0.21f)) * if (it % 48 < 6) 1f else 0.4f }
        kill[1] = i == 1
        echoOn = i == 0
        pfl = i == 1
        level = 0.6f
    }

    @Test
    fun decks() {
        paparazzi.snapshot {
            DjTheme {
                Column(Modifier.fillMaxSize().background(DjColors.Background).padding(6.dp)) {
                    TopBar(true, 3, true, 754.0, {}, {}, {}, {})
                    Box(Modifier.height(6.dp))
                    val decks = listOf(demoDeck(0), demoDeck(1))
                    val mixer = MixerModel().apply { crossfader = -0.3f; masterLevel = 0.7f }
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DeckPanel(decks[0], Modifier.weight(1f).fillMaxHeight())
                        MixerPanel(decks, mixer, Modifier.width(204.dp).fillMaxHeight())
                        DeckPanel(decks[1], Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}
