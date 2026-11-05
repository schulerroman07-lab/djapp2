package app.fadelux.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import app.fadelux.DjViewModel
import app.fadelux.data.CoverRepository
import app.fadelux.data.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

/**
 * "Übersetzer" zwischen unserer DJ-Engine und Android (Media3).
 * Android sieht damit einen normalen Musik-Player: Benachrichtigung, Sperrbildschirm,
 * Bluetooth-Kopfhörer und Auto können Play/Pause, Zurück, Weiter und Spulen senden.
 * Die Musik selbst kommt weiterhin aus unserer Oboe-Engine.
 */
@OptIn(UnstableApi::class)
class DjPlayer(private val core: DjViewModel) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSignature = ""
    private var coverBytes: ByteArray? = null
    private var coverForId = -1L

    // Zweimal pro Sekunde prüfen, ob sich etwas Sichtbares geändert hat (Titel, Play-Zustand …)
    private val watcher = object : Runnable {
        override fun run() {
            val live = core.decks[core.autoMix.currentDeck]
            val sig = "${core.autoMix.current?.id}|${core.decks.any { it.playing }}|${core.autoMix.queue.firstOrNull()?.id}|" +
                "${core.autoMix.running}|${live.duration}|${core.autoMix.transitioning}"
            if (sig != lastSignature) {
                lastSignature = sig
                loadCover(core.autoMix.current)
                invalidateState()
            }
            handler.postDelayed(this, 500)
        }
    }

    init {
        handler.post(watcher)
    }

    private fun loadCover(t: Track?) {
        if (t == null || t.id == coverForId) return
        coverForId = t.id
        coverBytes = null
        core.scope.launch {
            CoverRepository.load(core.appContext, t.id, t.uri)
            coverBytes = CoverRepository.jpegBytes(core.appContext, t.id)
            invalidateState()
        }
    }

    private fun metadata(t: Track?, artwork: ByteArray?): MediaMetadata {
        val b = MediaMetadata.Builder()
            .setTitle(t?.title ?: core.appName)
            .setArtist(t?.artist ?: "")
            .setIsPlayable(true)
        if (artwork != null) b.setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        return b.build()
    }

    override fun getState(): State {
        val live = core.decks[core.autoMix.currentDeck]
        val t = core.autoMix.current
        val playing = core.decks.any { it.playing }
        val next = core.autoMix.nextTrack ?: core.autoMix.queue.firstOrNull()

        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .addIf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, !core.autoMix.transitioning)
            .build()

        // Playlist für Android: [aktueller Titel, nächster Titel] – damit "Weiter" aktiv ist
        val items = mutableListOf(
            MediaItemData.Builder("current-${t?.id ?: 0}")
                .setMediaItem(MediaItem.Builder().setMediaId("current").setMediaMetadata(metadata(t, coverBytes)).build())
                .setMediaMetadata(metadata(t, coverBytes))
                .setDurationUs((live.duration * 1_000_000).toLong().coerceAtLeast(1))
                .build(),
            MediaItemData.Builder("next-${next?.id ?: 0}")
                .setMediaItem(MediaItem.Builder().setMediaId("next").setMediaMetadata(metadata(next, null)).build())
                .setMediaMetadata(metadata(next, null))
                .build(),
        )

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (t == null) Player.STATE_IDLE else Player.STATE_READY)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs { (core.decks[core.autoMix.currentDeck].position * 1000).toLong() }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        core.remotePlayPause(playWhenReady)
        lastSignature = ""
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> core.remoteNext()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> core.remotePrevious()
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> core.seekTo(positionMs / 1000.0)
        }
        lastSignature = ""
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        core.remotePlayPause(false)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacks(watcher)
        return Futures.immediateVoidFuture()
    }

    /** Sofort neu melden (z.B. nach Auto-Mix an/aus). */
    fun refresh() {
        lastSignature = ""
    }
}
