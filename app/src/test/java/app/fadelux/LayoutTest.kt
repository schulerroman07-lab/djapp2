package app.fadelux

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
import app.fadelux.data.Track
import app.fadelux.ui.AutoMixActions
import app.fadelux.ui.AutoMixContent
import app.fadelux.ui.AutoMixUi
import app.fadelux.ui.DeckPanel
import app.fadelux.ui.MixerPanel
import app.fadelux.ui.OnboardingScreen
import app.fadelux.ui.TopBar
import app.fadelux.ui.SettingsNav
import app.fadelux.ui.AccentPicker
import app.fadelux.ui.components.glass
import app.fadelux.ui.TrackRow
import app.fadelux.ui.WishRow
import app.fadelux.ui.WishStep
import app.fadelux.wishes.Wish
import app.fadelux.ui.components.AppBackground
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppTheme
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** Erstellt Bildschirmfotos der Oberfläche (Samsung-ähnlicher Bildschirm im Querformat). */
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
    fun mixer() {
        paparazzi.snapshot {
            AppTheme(AppColors.Emerald) {
                AppBackground {
                    Column(Modifier.fillMaxSize().padding(6.dp)) {
                        TopBar(true, 3, true, 754.0, true, {}, {}, {}, {}, {}, {})
                        Box(Modifier.height(6.dp))
                        val decks = listOf(demoDeck(0), demoDeck(1))
                        val mixer = MixerModel().apply {
                            crossfader = -0.3f; masterLevel = 0.7f
                            spectrum = FloatArray(28) { 0.3f + 0.6f * abs(sin(it * 0.5f)) }
                        }
                        val colors = listOf(AppColors.Emerald, AppColors.Cyan)
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DeckPanel(decks[0], colors[0], Modifier.weight(1f).fillMaxHeight())
                            MixerPanel(decks, mixer, colors, Modifier.width(204.dp).fillMaxHeight())
                            DeckPanel(decks[1], colors[1], Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun library() {
        paparazzi.snapshot {
            AppTheme(AppColors.Emerald) {
                AppBackground {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(12.dp)) {
                        listOf(
                            Track(1, "", "Midnight Drive", "Neon Avenue", 245000, 124f, 0.8f),
                            Track(2, "", "Sunset Groove", "The Waves", 198000, 118.5f, 0.4f),
                            Track(3, "", "Ein sehr, sehr langer Titel mit vielen Wörtern", "Interpret", 320000, null, null),
                            Track(4, "", "Unknown BPM", "Artist", 180000, 0f, 0.6f),
                        ).forEachIndexed { i, t -> TrackRow(t, i == 1, {}, {}, {}) }
                    }
                }
            }
        }
    }

    @Test
    fun automix() {
        val q = listOf(
            Track(1, "", "Midnight Drive", "Neon Avenue", 245000, 124f, 0.8f),
            Track(2, "", "Sunset Groove", "The Waves", 198000, 118.5f, 0.4f),
            Track(3, "", "Ein sehr, sehr langer Titel mit vielen Wörtern", "Interpret", 320000, 128f, 0.6f),
        )
        paparazzi.snapshot {
            AppTheme(AppColors.Emerald) {
                AppBackground {
                    AutoMixContent(
                        AutoMixUi(
                            queue = q, running = true, status = "Next: Sunset Groove – waiting for the outro",
                            nextTrack = q[1],
                            nextPlan = app.fadelux.automix.TransitionPlan(
                                app.fadelux.automix.TransitionType.BASS_SWAP, 72, 140.0, 20.0, 16, 0.98f, true,
                            ),
                            countdown = { 42.0 }, suggestions = q.take(2),
                        ),
                        AutoMixActions(),
                    )
                }
            }
        }
    }

    @Test
    fun wishes() {
        val now = java.time.Instant.now()
        paparazzi.snapshot {
            AppTheme(AppColors.Emerald) {
                AppBackground {
                    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.width(320.dp)) {
                            WishStep(1, app.fadelux.ui.components.AppIcons.Play, "Start an event")
                            WishStep(2, app.fadelux.ui.components.AppIcons.QrCode, "Guests scan the QR code with their phone camera")
                            WishStep(3, app.fadelux.ui.components.AppIcons.Star, "Requests appear here live – tick them off or delete them")
                        }
                        Column(Modifier.weight(1f)) {
                            WishRow(Wish("1", "", "Neon Avenue – Midnight Drive", now, false), "21:04", true, {}, {}, {}, {}, {})
                            WishRow(Wish("2", "Bitte etwas von ABBA, und alles Gute zum Geburtstag an Lisa!", "", now, false), "21:06", false, {}, {}, {}, {}, {})
                            WishRow(Wish("3", "", "The Waves – Sunset Groove", now, true), "21:10", true, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
    }

    @Test
    fun settings() {
        paparazzi.snapshot {
            AppTheme(AppColors.Violet) {
                AppBackground {
                    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingsNav(0) {}
                        Column(Modifier.weight(1f).fillMaxHeight().glass().padding(14.dp)) { AccentPicker(2) {} }
                    }
                }
            }
        }
    }

    @Test
    fun playlist() {
        val tracks = listOf(
            Track(1, "", "Midnight Drive", "Neon Avenue", 245000, 124f, 0.8f),
            Track(2, "", "Sunset Groove", "The Waves", 198000, 118.5f, 0.4f),
            Track(3, "", "Ein sehr, sehr langer Titel mit vielen Wörtern", "Interpret", 320000, 128f, 0.6f),
        )
        paparazzi.snapshot {
            AppTheme(AppColors.Emerald) {
                AppBackground {
                    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.width(180.dp)) {
                            app.fadelux.ui.CoverCollage(tracks, Modifier.width(180.dp).height(180.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            app.fadelux.ui.PlaylistEntryList(
                                tracks.mapIndexed { i, t -> app.fadelux.data.PlaylistEntry(i.toLong(), i, t) },
                                {}, {}, {}, { _, _ -> },
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun onboarding() {
        paparazzi.snapshot {
            AppTheme(AppColors.Emerald) { AppBackground { OnboardingScreen {} } }
        }
    }
}
