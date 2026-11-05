package app.fadelux.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.fadelux.DjViewModel
import app.fadelux.MainActivity
import app.fadelux.R

/**
 * Hintergrund-Dienst: hält die App am Leben, solange Musik läuft, und zeigt die
 * Medien-Benachrichtigung (auch auf dem Sperrbildschirm) mit Cover, Titel, Fortschritt,
 * Zurück, Play/Pause, Weiter und Fortschrittsbalken (Spulen) – auch über Bluetooth-Kopfhörer und Auto.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: DjPlayer
    private lateinit var core: DjViewModel
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        core = DjViewModel.get(application)
        player = DjPlayer(core)

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(Callback())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
    }

    /** Standard-Verbindung: Play/Pause, Zurück, Weiter, Spulen (der Auto-DJ läuft immer). */
    private inner class Callback : MediaSession.Callback

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

}
