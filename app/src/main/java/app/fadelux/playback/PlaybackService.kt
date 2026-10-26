package app.fadelux.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.fadelux.DjViewModel
import app.fadelux.MainActivity
import app.fadelux.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Hintergrund-Dienst: hält die App am Leben, solange Musik läuft, und zeigt die
 * Medien-Benachrichtigung (auch auf dem Sperrbildschirm) mit Cover, Titel, Fortschritt,
 * Play/Pause, Nächster Song und Auto-Mix an/aus.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: DjPlayer
    private lateinit var core: DjViewModel
    private var lastAutoMix: Boolean? = null
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
        updateLayout()
    }

    /** Auto-Mix-Knopf in der Benachrichtigung (Symbol zeigt an/aus). */
    private fun updateLayout() {
        val on = core.autoMix.running
        if (on != lastAutoMix) {
            lastAutoMix = on
            val button = CommandButton.Builder()
                .setDisplayName(getString(R.string.nav_automix))
                .setIconResId(if (on) R.drawable.ic_automix_on else R.drawable.ic_automix_off)
                .setSessionCommand(SessionCommand(CMD_AUTOMIX, Bundle.EMPTY))
                .build()
            session?.setCustomLayout(listOf(button))
            player.refresh()
        }
        handler.postDelayed({ updateLayout() }, 700)
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_AUTOMIX, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_AUTOMIX) {
                core.toggleAutoMix()
                lastAutoMix = null
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

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

    companion object {
        const val CMD_AUTOMIX = "app.fadelux.AUTOMIX_TOGGLE"
    }
}
