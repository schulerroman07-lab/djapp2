package ch.hirschexpress.dj

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ch.hirschexpress.dj.ui.DjTheme
import ch.hirschexpress.dj.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val vm: DjViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.scanLibrary()
            else vm.message = "Ohne Erlaubnis kann die App deine Musik nicht lesen"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bildschirm bleibt an, solange die App offen ist
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Vollbild: Statusleiste und Navigationsleiste ausblenden
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Vordergrund-Dienst starten (Musik läuft auch im Hintergrund weiter)
        try {
            startForegroundService(Intent(this, DjService::class.java))
        } catch (e: Exception) {
            android.util.Log.e("HirschDJ", "Dienst konnte nicht starten", e)
        }

        setContent {
            DjTheme { MainScreen(vm) }
        }

        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            vm.scanLibrary()
        } else {
            permissionLauncher.launch(perm)
        }
    }
}
