package app.fadelux

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fadelux.audio.NativeEngine
import app.fadelux.automix.AutoMix
import app.fadelux.automix.TransitionType
import app.fadelux.automix.Suggestion
import app.fadelux.automix.Suggestions
import app.fadelux.data.AppSettings
import app.fadelux.data.LibraryRepository
import app.fadelux.data.PlaylistRepository
import app.fadelux.io.ImportResult
import app.fadelux.io.PlaylistIO
import app.fadelux.data.Track
import app.fadelux.ui.UiText
import app.fadelux.playback.AudioFocusHelper
import app.fadelux.wishes.BackendConfig
import app.fadelux.wishes.FirestoreClient
import app.fadelux.wishes.LocalWishServer
import app.fadelux.wishes.Wish
import app.fadelux.wishes.makeQrBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ein Audio-Ausgabegerät (z.B. Lautsprecher, Kopfhörer, Bluetooth, USB). */
data class OutputDevice(val id: Int, val type: Int, val name: String)

/**
 * Der "Kern" der App: Decks, Mixer, Auto-Mix, Bibliothek, Wünsche.
 * Gibt es nur EINMAL pro App (nicht pro Fenster) – so läuft die Musik weiter,
 * auch wenn Android das Fenster im Hintergrund schliesst. Bildschirm und
 * Hintergrund-Dienst (PlaybackService) greifen beide auf denselben Kern zu.
 */
class DjViewModel private constructor(private val app: Application) {
    /** Eigener Ablauf-Bereich für Hintergrund-Aufgaben (lebt so lange wie die App). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val appContext: Context get() = app
    val appName: String get() = app.getString(R.string.app_name)

    /** Ist ein Fenster der App sichtbar? Im Hintergrund wird seltener aktualisiert (spart Akku). */
    @Volatile var uiVisible = true

    companion object {
        @Volatile private var instance: DjViewModel? = null
        fun get(app: Application): DjViewModel = instance ?: synchronized(this) {
            instance ?: DjViewModel(app).also { instance = it }
        }
    }

    val settings = AppSettings(app)
    val library = LibraryRepository(app)
    val tracks = library.tracks.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val unanalyzed = library.unanalyzedCount.stateIn(scope, SharingStarted.Eagerly, 0)
    var folders by mutableStateOf<List<String>>(emptyList())
    val playlists = PlaylistRepository(app)
    val playlistSummaries = playlists.summaries.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val decks = listOf(DeckModel(0), DeckModel(1))
    val mixer = MixerModel()
    var engineOk by mutableStateOf(false)
    var message by mutableStateOf<UiText?>(null)
    var engineInfo by mutableStateOf(DoubleArray(4))

    val autoMix = AutoMix(scope, decks, mixer, settings) { deck, t -> loadTrack(deck, t, force = true) }

    // Aufnahme
    var recording by mutableStateOf(false)
    var recordSeconds by mutableStateOf(0.0)
    private var recordUri: Uri? = null

    private var scanning = false
    private var analyzeJob: Job? = null

    // ---------------- Laufende Aktualisierung (ca. 60 x pro Sekunde) ----------------
    private fun startPolling() = scope.launch {
        val st = DoubleArray(9)
        val lastLoad = intArrayOf(-1, -1)
        var frame = 0
        var wasPlaying = false
        while (isActive) {
            for (d in decks) {
                NativeEngine.getDeckState(d.index, st)
                val ls = st[0].toInt()
                d.loadState = ls
                d.playing = st[1] > 0.5
                d.position = st[2]
                d.duration = st[3]
                d.bpm = st[4].toFloat()
                d.firstBeat = st[5].toFloat()
                d.cue = st[7]
                d.level = st[8].toFloat()
                if (ls != lastLoad[d.index]) {
                    lastLoad[d.index] = ls
                    if (ls == 2) {
                        d.waveform = NativeEngine.getWaveform(d.index, 600)
                        d.peaks = NativeEngine.getPeaks(d.index)
                        val t = d.track
                        if (t != null && d.bpm > 0 && (t.bpm == null || t.bpm == 0f)) library.setBpm(t.id, d.bpm)
                    }
                    if (ls == 3) message = UiText(R.string.msg_load_failed)
                }
            }
            val anyPlaying = decks.any { it.playing }
            if (anyPlaying && !wasPlaying) focus.request()
            if (!anyPlaying && wasPlaying && remotePaused.isEmpty() && !autoMix.running) focus.abandon()
            wasPlaying = anyPlaying
            mixer.masterLevel = NativeEngine.getMasterLevel()
            // Spektrum: neues Array, damit Compose neu zeichnet (Berechnung hier, nicht im Audio-Thread)
            val spec = FloatArray(28)
            NativeEngine.getSpectrum(spec)
            val old = mixer.spectrum
            for (i in spec.indices) spec[i] = maxOf(spec[i], (old.getOrNull(i) ?: 0f) * 0.86f) // sanft abfallen
            mixer.spectrum = spec
            if (recording && frame % 10 == 0) recordSeconds = NativeEngine.recordingSeconds()
            frame++
            delay(if (uiVisible) 16 else 100)
        }
    }

    fun refreshEngineInfo() {
        val info = DoubleArray(4)
        NativeEngine.getEngineInfo(info)
        engineInfo = info
    }

    // ---------------- Audio-Fokus & Fernsteuerung (Benachrichtigung, Bluetooth, Auto) ----------------
    private var duckRestore = -1f
    private var remotePaused = emptyList<Int>()   // Decks, die per Pause angehalten wurden

    private val focus =
        AudioFocusHelper(
            app,
            onPause = { _ -> pauseAll() },
            onResume = { resumePaused() },
            onDuck = { duck ->
                if (duck && duckRestore < 0f) {
                    duckRestore = mixer.masterVolume
                    NativeEngine.setMasterVolume(mixer.masterVolume * 0.3f)
                } else if (!duck && duckRestore >= 0f) {
                    NativeEngine.setMasterVolume(mixer.masterVolume)
                    duckRestore = -1f
                }
            },
            onNoisy = { if (settings.outputMode == 0) pauseAll() },
        )

    private fun pauseAll() {
        remotePaused = decks.filter { it.playing }.map { it.index }
        remotePaused.forEach { NativeEngine.setPlaying(it, false) }
    }

    private fun resumePaused() {
        remotePaused.forEach { decks[it].play(true) }
        remotePaused = emptyList()
    }

    /** Play/Pause von aussen (Benachrichtigung, Kopfhörer-Taste, Auto). */
    fun remotePlayPause(play: Boolean) {
        if (!play) {
            pauseAll()
            return
        }
        if (remotePaused.isNotEmpty()) resumePaused()
        else (liveDeck() ?: decks.firstOrNull { it.loadState == 2 })?.play(true)
    }

    /** "Nächster Song" von aussen: sauberer Übergang statt hartem Schnitt. */
    fun remoteNext() {
        when {
            autoMix.running -> autoMix.mixNow()
            autoMix.queue.isNotEmpty() -> { autoMix.start(); autoMix.mixNow() }
            else -> message = UiText(R.string.msg_queue_empty_next)
        }
    }

    /** Übergangstyp an/aus (mindestens einer bleibt an). Plant den nächsten Übergang neu. */
    fun toggleTransitionType(t: TransitionType) {
        val off = settings.disabledTransitions.split(',').filter { it.isNotBlank() }.toMutableSet()
        if (t.name in off) off.remove(t.name)
        else if (TransitionType.entries.size - off.size > 1) off.add(t.name)
        settings.disabledTransitions = off.joinToString(",")
        autoMix.changeType(autoMix.forcedType)
    }

    fun toggleAutoMix() {
        if (autoMix.running) autoMix.stop() else autoMix.start()
    }

    /** Läuft die App ohne Akku-Einschränkung? (sonst kann Android den Dienst stoppen) */
    fun batteryUnrestricted(): Boolean {
        val pm = app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(app.packageName)
    }

    // ---------------- Bibliothek ----------------
    fun scanLibrary() {
        if (scanning) return
        scanning = true
        scope.launch {
            try {
                val (n, f) = library.scan(settings.libraryFolder)
                folders = f
                message = UiText(R.string.msg_tracks_found, n)
            } catch (e: Exception) {
                message = UiText(R.string.msg_scan_error)
            }
            scanning = false
            startAnalysis()
        }
    }

    private fun startAnalysis() {
        if (analyzeJob?.isActive == true) return
        analyzeJob = scope.launch {
            while (isActive && library.analyzeNext()) delay(50)
        }
    }

    fun loadTrack(deck: Int, t: Track, force: Boolean = false) {
        val d = decks[deck]
        if (d.playing && !force) {
            message = UiText(R.string.msg_deck_playing, d.name)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openFileDescriptor(Uri.parse(t.uri), "r")?.use { pfd ->
                    val len = pfd.statSize
                    val fd = pfd.detachFd()
                    withContext(Dispatchers.Main) {
                        d.track = t
                        d.waveform = FloatArray(0)
                        d.peaks = FloatArray(0)
                        d.loadState = 1
                        d.applyGain(1f)      // Lautheits-Angleichung gilt nur für den vorherigen Titel
                    }
                    NativeEngine.loadTrack(deck, fd, len)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message = UiText(R.string.msg_load_failed) }
            }
        }
    }

    /** Das Deck, das gerade "on air" ist (spielt und ist am Crossfader offen). */
    fun liveDeck(): DeckModel? {
        val playing = decks.filter { it.playing }
        if (playing.size <= 1) return playing.firstOrNull()
        return if (mixer.crossfader <= 0f) decks[0] else decks[1]
    }

    /** Vorschläge "Passt als nächstes". */
    fun suggestions(): List<Suggestion> {
        val live = liveDeck() ?: decks.firstOrNull { it.track != null }
        val exclude = decks.mapNotNull { it.track?.id }.toSet() + autoMix.queue.map { it.id }
        val wishTexts = wishes.filter { !it.done }.map { if (it.song.isNotBlank()) it.song else it.text }
        return Suggestions.suggest(live?.track, live?.effectiveBpm ?: 0f, tracks.value, wishTexts, exclude)
    }

    /** Vorschläge für die Auto-Mix-Warteschlange: passend zum letzten Titel der Warteschlange. */
    fun queueSuggestions(): List<Track> {
        val ref = autoMix.queue.lastOrNull() ?: liveDeck()?.track
        val refBpm = ref?.bpm ?: 0f
        val exclude = decks.mapNotNull { it.track?.id }.toSet() + autoMix.queue.map { it.id }
        val wishTexts = wishes.filter { !it.done }.map { if (it.song.isNotBlank()) it.song else it.text }
        return Suggestions.suggest(ref, refBpm, tracks.value, wishTexts, exclude).take(6).map { it.track }
    }

    // ---------------- Playlists ----------------
    fun createPlaylist(name: String, initial: List<Track> = emptyList(), onCreated: (Long) -> Unit = {}) {
        scope.launch {
            val id = playlists.create(name)
            if (initial.isNotEmpty()) playlists.addTracks(id, initial.map { it.id })
            if (initial.isNotEmpty()) message = UiText(R.string.pl_added, name)
            onCreated(id)
        }
    }

    fun addToPlaylist(playlistId: Long, playlistName: String, tracks: List<Track>) {
        scope.launch {
            playlists.addTracks(playlistId, tracks.map { it.id })
            message = UiText(R.string.pl_added, playlistName)
        }
    }

    fun renamePlaylist(id: Long, name: String) { scope.launch { playlists.rename(id, name) } }
    fun deletePlaylist(id: Long) { scope.launch { playlists.delete(id) } }
    fun removeFromPlaylist(entryId: Long) { scope.launch { playlists.removeEntry(entryId) } }
    fun reorderPlaylist(entryIds: List<Long>) { scope.launch { playlists.reorder(entryIds) } }

    /** Ganze Playlist in die Auto-Mix-Warteschlange; optional Auto-Mix gleich starten. */
    fun queuePlaylist(id: Long, start: Boolean) {
        scope.launch {
            val list = playlists.tracks(id)
            val before = autoMix.queue.size
            autoMix.addAll(list)
            message = UiText(R.string.msg_queue_added, autoMix.queue.size - before)
            if (start && !autoMix.running) {
                autoMix.start()
                if (!batteryUnrestricted()) message = UiText(R.string.bg_hint_short)
            }
        }
    }

    // ---------------- Import / Export ----------------
    /** Fortschritt (fertig, total) einer laufenden Kopie, oder null. */
    var ioProgress by mutableStateOf<Pair<Int, Int>?>(null)
    var importResult by mutableStateOf<ImportResult?>(null)
    private var ioJob: Job? = null

    fun exportM3u(playlistId: Long, target: Uri) {
        scope.launch {
            try {
                PlaylistIO.exportM3u(app, target, playlists.tracks(playlistId))
                message = UiText(R.string.io_export_done)
            } catch (e: Exception) {
                Log.e("Fadelux", "M3U-Export", e)
                message = UiText(R.string.io_failed)
            }
        }
    }

    fun exportFolder(playlistId: Long, name: String, tree: Uri, numbered: Boolean) {
        ioJob?.cancel()
        ioJob = scope.launch {
            try {
                ioProgress = 0 to 1
                val n = PlaylistIO.exportFolder(app, tree, name, playlists.tracks(playlistId), numbered) { done, total ->
                    scope.launch { ioProgress = done to total }
                }
                message = UiText(R.string.io_folder_done, n)
            } catch (e: kotlinx.coroutines.CancellationException) {
                message = UiText(R.string.io_cancelled)
            } catch (e: Exception) {
                Log.e("Fadelux", "Ordner-Export", e)
                message = UiText(R.string.io_failed)
            } finally {
                ioProgress = null
            }
        }
    }

    fun cancelIo() { ioJob?.cancel() }

    /** .m3u/.m3u8 oder Ordner importieren → neue Playlist. */
    fun importPlaylist(source: Uri, isFolder: Boolean) {
        scope.launch {
            try {
                val (name, result) = if (isFolder) PlaylistIO.readFolder(app, source, tracks.value)
                else PlaylistIO.readM3u(app, source, tracks.value)
                if (result.trackIds.isNotEmpty()) {
                    val id = playlists.create(name)
                    playlists.addTracks(id, result.trackIds)
                }
                importResult = ImportResult(
                    name, result.trackIds.size,
                    result.missing.map { m -> listOfNotNull(m.artist, m.title).joinToString(" – ").ifBlank { m.path } },
                )
            } catch (e: Exception) {
                Log.e("Fadelux", "Import", e)
                message = UiText(R.string.io_failed)
            }
        }
    }

    // ---------------- Ausgabe / Einstellungen ----------------
    fun outputDevices(): List<OutputDevice> {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY && it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            .map { OutputDevice(it.id, it.type, it.productName.toString()) }
    }

    fun setOutput(mode: Int, master: Int, cue: Int) {
        settings.outputMode = mode
        settings.masterDevice = master
        settings.cueDevice = cue
        applyOutput()
    }

    private fun applyOutput() {
        scope.launch(Dispatchers.Default) {
            NativeEngine.setOutput(settings.outputMode, settings.masterDevice, settings.cueDevice)
            withContext(Dispatchers.Main) { refreshEngineInfo() }
        }
    }

    fun changeCueDelay(ms: Float) {
        settings.cueDelayMs = ms
        NativeEngine.setCueDelayMs(ms)
    }

    fun changeBuffer(bursts: Int) {
        settings.bufferBursts = bursts
        NativeEngine.setBufferBursts(bursts)
        scope.launch { delay(200); refreshEngineInfo() }
    }

    fun setLibraryFolder(folder: String) {
        settings.libraryFolder = folder
        scanLibrary()
    }

    // ---------------- Aufnahme ----------------
    fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private val appFolder: String
        get() = app.getString(R.string.app_name).replace(Regex("[^A-Za-z0-9 _-]"), "")

    private fun startRecording() {
        val resolver = app.contentResolver
        val name = "Mix_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".wav"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + appFolder)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        try {
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw IllegalStateException("insert")
            val pfd = resolver.openFileDescriptor(uri, "rw") ?: throw IllegalStateException("open")
            val fd = pfd.detachFd()
            pfd.close()
            if (NativeEngine.startRecording(fd)) {
                recordUri = uri
                recording = true
                recordSeconds = 0.0
                message = UiText(R.string.msg_rec_started, "Music/$appFolder")
            }
        } catch (e: Exception) {
            message = UiText(R.string.msg_rec_failed)
        }
    }

    private fun stopRecording() {
        NativeEngine.stopRecording()
        recording = false
        recordUri?.let { uri ->
            val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            app.contentResolver.update(uri, values, null, null)
        }
        recordUri = null
        message = UiText(R.string.msg_rec_saved, "Music/$appFolder")
    }

    // ---------------- Gästewünsche ----------------
    var eventActive by mutableStateOf(false)
    val wishes = mutableStateListOf<Wish>()
    var wishError by mutableStateOf<UiText?>(null)
    var newWishes by mutableIntStateOf(0)
    var qrBitmap by mutableStateOf<Bitmap?>(null)
    var wishUrl by mutableStateOf("")
    var eventBusy by mutableStateOf(false)
    private val firestore by lazy {
        FirestoreClient(BackendConfig.API_KEY, BackendConfig.PROJECT_ID,
            app.getSharedPreferences("backend", Context.MODE_PRIVATE))
    }
    private var localServer: LocalWishServer? = null
    private var pollJob: Job? = null
    private val seenWishes = mutableSetOf<String>()

    /** Online nur möglich, wenn der Entwickler das zentrale Backend eingetragen hat. */
    val onlineAvailable: Boolean get() = BackendConfig.configured

    private fun songList() = tracks.value.map { "${it.artist} – ${it.title}" }

    private fun restoreEvent() {
        val id = settings.eventId
        if (id.isEmpty() || settings.wishMode != 0 || !onlineAvailable) return
        eventActive = true
        wishUrl = BackendConfig.guestUrlBase + "?e=" + id
        qrBitmap = makeQrBitmap(wishUrl)
        startWishPolling(id)
    }

    /** Event starten. texts = übersetzte Texte für die lokale Gästeseite. */
    fun startEvent(name: String, localTexts: Map<String, String>) {
        val local = settings.wishMode == 1 || !onlineAvailable
        wishes.clear()
        seenWishes.clear()
        newWishes = 0
        wishError = null
        if (local) {
            val server = LocalWishServer(::songList, localTexts) { w ->
                scope.launch {
                    wishes.add(0, w)
                    newWishes++
                }
            }
            if (!server.start()) { wishError = UiText(R.string.wish_err_server); return }
            val url = server.url()
            if (url == null) { server.stop(); wishError = UiText(R.string.wish_err_no_wifi); return }
            localServer = server
            settings.wishMode = 1
            wishUrl = url
            qrBitmap = makeQrBitmap(url)
            eventActive = true
            return
        }
        eventBusy = true
        val id = (1..12).map { "abcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")
        scope.launch {
            try {
                withContext(Dispatchers.IO) { firestore.createEvent(id, name.take(80), songList()) }
                settings.eventId = id
                wishUrl = BackendConfig.guestUrlBase + "?e=" + id
                qrBitmap = makeQrBitmap(wishUrl)
                eventActive = true
                startWishPolling(id)
            } catch (e: Exception) {
                Log.e("Fadelux", "Event starten", e)
                wishError = UiText(R.string.wish_err_online)
            }
            eventBusy = false
        }
    }

    fun endEvent() {
        pollJob?.cancel()
        localServer?.stop()
        localServer = null
        val id = settings.eventId
        settings.eventId = ""
        eventActive = false
        qrBitmap = null
        if (id.isNotEmpty() && onlineAvailable) {
            scope.launch(Dispatchers.IO) { runCatching { firestore.endEvent(id) } }
        }
    }

    private fun startWishPolling(id: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val list = withContext(Dispatchers.IO) { firestore.listWishes(id) }
                    val fresh = list.count { it.id !in seenWishes }
                    if (seenWishes.isNotEmpty() || wishes.isNotEmpty()) newWishes += fresh
                    seenWishes += list.map { it.id }
                    wishes.clear()
                    wishes.addAll(list)
                    wishError = null
                } catch (e: Exception) {
                    wishError = UiText(R.string.wish_err_connection)
                }
                delay(5000)
            }
        }
    }

    fun markWish(w: Wish) {
        val i = wishes.indexOfFirst { it.id == w.id }
        if (i >= 0) wishes[i] = w.copy(done = !w.done)
        val id = settings.eventId
        if (localServer == null && id.isNotEmpty()) scope.launch(Dispatchers.IO) { runCatching { firestore.setDone(id, w.id, !w.done) } }
    }

    fun deleteWish(w: Wish) {
        wishes.removeAll { it.id == w.id }
        val id = settings.eventId
        if (localServer == null && id.isNotEmpty()) scope.launch(Dispatchers.IO) { runCatching { firestore.delete(id, w.id) } }
    }

    fun clearNewWishes() { newWishes = 0 }

    /** Titel aus der Bibliothek, der zu einem Wunsch passt (oder null). */
    fun trackForWish(w: Wish): Track? =
        Suggestions.matchWish(if (w.song.isNotBlank()) w.song else w.text, tracks.value)

    // Muss am Ende stehen: alle Variablen oben sind dann schon angelegt
    init {
        engineOk = NativeEngine.create()
        applyOutput()
        NativeEngine.setCueDelayMs(settings.cueDelayMs)
        NativeEngine.setBufferBursts(settings.bufferBursts)
        mixer.applyAll()
        decks.forEach { it.applyAll() }
        startPolling()
        restoreEvent()
    }

    /** App wird ganz beendet (z.B. "Beenden" in der Benachrichtigung). */
    fun shutdown() {
        if (recording) stopRecording()
        localServer?.stop()
        autoMix.stop()
        decks.forEach { NativeEngine.setPlaying(it.index, false) }
    }
}
