package app.fadelux.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fadelux.R
import app.fadelux.ui.components.AppLogo
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Start-Animation (ca. 1,8 s): Das Logo wächst aus einem Punkt (wie auf dem System-Startbildschirm),
 * schwingt wie eine Schallwelle und wird zum Ring, die Balken steigen, darunter erscheint der Name.
 * Danach ruft [onDone] – der Wechsel in die App wird weich überblendet.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val accent = LocalAccent.current
    val morph = remember { Animatable(0f) }
    val bars = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        IntroTiming.started = android.os.SystemClock.uptimeMillis()
        val smooth = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        // Gesamtdauer inkl. Überblendung höchstens 2 s – auch auf langsamen Geräten: Die Animation
        // dauert 1,2 s; spätestens nach 1,4 s (Uhrzeit, nicht Bilder) geht es weiter, notfalls mit
        // dem fertigen Logo (wenn das Gerät beim Start mit anderem beschäftigt ist).
        val anim = async {
            val a = async { morph.animateTo(1f, tween(800, easing = LinearEasing)) }
            val b = async { delay(250); bars.animateTo(1f, tween(600, easing = smooth)) }
            val c = async { delay(550); name.animateTo(1f, tween(550, easing = smooth)) }
            a.await(); b.await(); c.await()
            delay(100)
        }
        if (withTimeoutOrNull(1400) { anim.await() } == null) {
            anim.cancel()
            morph.snapTo(1f); bars.snapTo(1f); name.snapTo(1f)
        }
        IntroTiming.done = android.os.SystemClock.uptimeMillis()
        android.util.Log.i("Fadelux", "Start-Animation: ${IntroTiming.done - IntroTiming.started} ms (+ 350 ms Überblendung)")
        onDone()
    }
    IntroContent(morph.value, bars.value, name.value)
}

/** Zeitpunkte der Start-Animation (für den Gerätetest). */
object IntroTiming {
    @Volatile var started = 0L
    @Volatile var done = 0L
}

/** Ein Bild der Start-Animation (morph/bars/name je 0..1) – auch für den Screenshot-Test. */
@Composable
fun IntroContent(morph: Float, bars: Float, name: Float) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxSize().background(AppColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AppLogo(Modifier.size(150.dp), accent, morph = morph, bars = bars)
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.app_name),
                style = AppType.logo.copy(fontSize = 34.sp, letterSpacing = (10f * (1f - name) + 2f).sp),
                color = accent,
                modifier = Modifier.graphicsLayer {
                    alpha = name
                    translationY = (1f - name) * 24f
                },
            )
        }
    }
}
