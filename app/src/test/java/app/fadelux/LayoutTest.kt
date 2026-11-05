package app.fadelux

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.fadelux.automix.EarlyOffer
import app.fadelux.automix.TransitionPlan
import app.fadelux.automix.TransitionType
import app.fadelux.data.Track
import app.fadelux.ui.AccentPicker
import app.fadelux.ui.IntroContent
import app.fadelux.ui.PlayerContent
import app.fadelux.ui.PlayerUi
import app.fadelux.ui.QueueList
import app.fadelux.ui.SettingsNav
import app.fadelux.ui.TrackRow
import app.fadelux.ui.WishRow
import app.fadelux.ui.components.AppBackground
import app.fadelux.ui.components.BottomSheet
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppTheme
import app.fadelux.wishes.Wish
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** Beispiel-Daten für die Bildschirmfotos. */
internal object Demo {
    val tracks = listOf(
        Track(1, "", "Midnight Drive", "Neon Avenue", 245000, 124f, 0.8f, genre = "House", musicalKey = 21),
        Track(2, "", "Sunset Groove", "The Waves", 198000, 118.5f, 0.4f, musicalKey = 7),
        Track(3, "", "Ein sehr, sehr langer Titel mit vielen Wörtern", "Interpret", 320000, 128f, 0.6f),
        Track(4, "", "Unknown BPM", "Artist", 180000, null, null),
    )
    val plan = TransitionPlan(TransitionType.BASS_SWAP, 80, 150.0, 30.0, 16, 1f, true)

    fun player(early: Boolean = false, idle: Boolean = false) = PlayerUi(
        track = if (idle) null else tracks[0], playing = true, position = { 96.0 }, duration = 245.0,
        nextTrack = tracks[1], nextPlan = plan, queueSize = 12, countdown = { 42.0 }, newWishes = 3,
        earlyOffer = if (early) EarlyOffer("Sunset Groove", System.currentTimeMillis() + 7000, 96) else null,
    )
}

@Composable
private fun Screen(content: @Composable () -> Unit) {
    AppTheme(AppColors.Emerald) { AppBackground { Box(Modifier.fillMaxSize().padding(top = 8.dp)) { content() } } }
}

/** Bildschirmfotos im Hochformat (Samsung-ähnlich, 1080 × 2340). */
class LayoutTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1080, screenHeight = 2340, xdpi = 420, ydpi = 420,
            density = Density.XXHIGH, orientation = ScreenOrientation.PORTRAIT,
        ),
    )

    @Test
    fun intro() = paparazzi.snapshot { AppTheme(AppColors.Emerald) { IntroContent(1f, 1f, 1f) } }

    @Test
    fun introMorphing() = paparazzi.snapshot { AppTheme(AppColors.Emerald) { IntroContent(0.45f, 0.2f, 0f) } }

    @Test
    fun player() = paparazzi.snapshot { Screen { PlayerContent(Demo.player(), app.fadelux.ui.PlayerActions()) } }

    @Test
    fun playerIdle() = paparazzi.snapshot { Screen { PlayerContent(Demo.player(idle = true), app.fadelux.ui.PlayerActions()) } }

    @Test
    fun playerEarlyOffer() = paparazzi.snapshot { Screen { PlayerContent(Demo.player(early = true), app.fadelux.ui.PlayerActions()) } }

    @Test
    fun upNext() = paparazzi.snapshot {
        Screen {
            PlayerContent(Demo.player(), app.fadelux.ui.PlayerActions())
            BottomSheet(true, "Als Nächstes", {}) {
                QueueList(Demo.tracks, {}, { _, _ -> }, Modifier.weight(1f).fillMaxWidth(), pinned = setOf(2L))
            }
        }
    }

    @Test
    fun library() = paparazzi.snapshot {
        Screen { Column(Modifier.padding(12.dp)) { Demo.tracks.forEach { TrackRow(it, {}, {}) } } }
    }

    @Test
    fun wishes() {
        val now = java.time.Instant.now()
        paparazzi.snapshot {
            Screen {
                Column(Modifier.padding(12.dp)) {
                    WishRow(Wish("1", "", "Neon Avenue – Midnight Drive", now, false), "21:04", Demo.tracks[0], queued = true, {}, {}, {})
                    WishRow(Wish("2", "", "The Waves – Sunset Groove", now, false), "21:05", Demo.tracks[1], queued = false, {}, {}, {})
                    WishRow(Wish("3", "Bitte etwas von ABBA, und alles Gute zum Geburtstag an Lisa!", "", now, false), "21:06", null, queued = false, {}, {}, {})
                }
            }
        }
    }

    @Test
    fun playlist() = paparazzi.snapshot {
        Screen {
            Column(Modifier.padding(12.dp)) {
                app.fadelux.ui.CoverCollage(Demo.tracks, Modifier.width(180.dp).height(180.dp))
                app.fadelux.ui.PlaylistEntryList(
                    Demo.tracks.mapIndexed { i, t -> app.fadelux.data.PlaylistEntry(i.toLong(), i, t) },
                    {}, {}, {}, {},
                )
            }
        }
    }
}

/** Bildschirmfotos im Querformat. */
class LayoutLandscapeTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 2340, screenHeight = 1080, xdpi = 420, ydpi = 420,
            density = Density.XXHIGH, orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    @Test
    fun player() = paparazzi.snapshot { Screen { PlayerContent(Demo.player(), app.fadelux.ui.PlayerActions()) } }

    @Test
    fun settings() = paparazzi.snapshot {
        Screen {
            Row(Modifier.fillMaxSize().padding(12.dp)) {
                SettingsNav(4) {}
                Column(Modifier.weight(1f).fillMaxHeight().glass().padding(14.dp)) { AccentPicker(0) {} }
            }
        }
    }
}
