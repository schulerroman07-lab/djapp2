package app.fadelux.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Audio-Fokus: Android sagt uns, wenn eine andere App (z.B. ein Anruf) den Ton braucht.
 * - Anruf / kurze Unterbrechung → Pause, danach automatisch weiter
 * - Navigation-Ansage → leiser ("Ducking"), danach wieder normal
 * - andere Musik-App startet → Pause
 * - Kopfhörer ausgesteckt ("becoming noisy") → Pause (nur im normalen Ausgabe-Modus)
 */
class AudioFocusHelper(
    private val context: Context,
    private val onPause: (transient: Boolean) -> Unit,
    private val onResume: () -> Unit,
    private val onDuck: (Boolean) -> Unit,
    private val onNoisy: () -> Unit,
) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasFocus = false
    private var pausedByFocus = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                pausedByFocus = false
                onDuck(false)
                onPause(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocus = true
                onPause(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(true)
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                onDuck(false)
                if (pausedByFocus) {
                    pausedByFocus = false
                    onResume()
                }
            }
        }
    }

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onNoisy()
        }
    }
    private var noisyRegistered = false

    /** Beim Start der Wiedergabe aufrufen. */
    fun request() {
        if (!hasFocus) hasFocus = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!noisyRegistered) {
            androidx.core.content.ContextCompat.registerReceiver(
                context, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
    }

    /** Wenn alles gestoppt ist (vom Benutzer). */
    fun abandon() {
        if (pausedByFocus) return   // während eines Anrufs Fokus behalten, damit es danach weitergeht
        if (hasFocus) am.abandonAudioFocusRequest(request)
        hasFocus = false
        if (noisyRegistered) {
            runCatching { context.unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
    }
}
