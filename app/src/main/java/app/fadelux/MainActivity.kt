package app.fadelux

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.fadelux.ui.IntroScreen
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.fadelux.data.AppSettings
import app.fadelux.ui.MainScreen
import app.fadelux.ui.UiText
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    // Der App-Kern lebt unabhängig vom Fenster (Musik läuft im Hintergrund weiter)
    private val vm: DjViewModel by lazy { DjViewModel.get(application) }
    private var controller: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.scanLibrary()
            else vm.message = UiText(R.string.msg_permission)
        }

    /** Gewählte Sprache anwenden (leer = Sprache des Handys). */
    override fun attachBaseContext(base: Context) {
        val lang = AppSettings.language(base)
        if (lang.isEmpty()) {
            super.attachBaseContext(base)
            return
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(lang))
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System-Startbildschirm (Punkt auf Schwarz) → danach die eigene Start-Animation
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Lautstärke-Tasten des Handys regeln immer die Musik
        volumeControlStream = AudioManager.STREAM_MUSIC
        applyRefreshRate()
        // Bildschirm bleibt an, solange die App offen ist
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Vollbild
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Mit dem Hintergrund-Dienst verbinden (Benachrichtigung, Sperrbildschirm, Bluetooth-Tasten)
        val token = androidx.media3.session.SessionToken(this, android.content.ComponentName(this, app.fadelux.playback.PlaybackService::class.java))
        controller = androidx.media3.session.MediaController.Builder(this, token).buildAsync()

        val firstStart = savedInstanceState == null
        setContent {
            val accent = AppColors.accents.getOrElse(vm.settings.accentIndex) { AppColors.Emerald }
            AppTheme(accent) {
                // Start-Animation nur beim echten Start (nicht nach Sprachwechsel/Drehen)
                var intro by rememberSaveable { mutableStateOf(firstStart) }
                Crossfade(intro, animationSpec = tween(350), label = "intro") { showIntro ->
                    if (showIntro) IntroScreen { intro = false }
                    else MainScreen(vm, onLanguageChanged = { recreate() })
                }
            }
        }
        handlePermission()
    }

    override fun onStart() {
        super.onStart()
        vm.uiVisible = true
    }

    override fun onStop() {
        vm.uiVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        // Verbindung zum Dienst lösen – der Dienst selbst läuft weiter, solange Musik spielt
        controller?.let { androidx.media3.session.MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    /**
     * Höchste Bildwiederholrate des Displays nutzen (z.B. 120 Hz bei Samsung) – Animationen laufen
     * dann besonders flüssig. Im Energiesparmodus (Einstellung "Hintergrund: Energiesparen/Aus")
     * entscheidet Android selbst (meist 60 Hz).
     */
    private fun applyRefreshRate() {
        val disp = (if (Build.VERSION.SDK_INT >= 30) this.display else @Suppress("DEPRECATION") windowManager.defaultDisplay) ?: return
        val current = disp.mode
        val params = window.attributes
        if (vm.settings.bgAnimation == 2) {
            val best = disp.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            params.preferredDisplayModeId = best.modeId
        } else {
            params.preferredDisplayModeId = 0
        }
        window.attributes = params
    }

    override fun onResume() {
        super.onResume()
        applyRefreshRate()        // Einstellung könnte sich geändert haben
    }

    private fun handlePermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            vm.scanLibrary()
        } else {
            permissionLauncher.launch(perm)
        }
    }
}
