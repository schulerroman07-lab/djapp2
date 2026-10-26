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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fadelux.audio.NativeEngine
import app.fadelux.automix.AutoMix
import app.fadelux.automix.Suggestion
import app.fadelux.automix.Suggestions
import app.fadelux.data.AppSettings
import app.fadelux.data.LibraryRepository
import app.fadelux.data.Track
import app.fadelux.ui.UiText
import app.fadelux.wishes.BackendConfig
import app.fadelux.wishes.FirestoreClient
import app.fadelux.wishes.LocalWishServer
import app.fadelux.wishes.Wish
import app.fadelux.wishes.makeQrBitmap
import kotlinx.coroutines.Dispatchers
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

class DjViewModel(app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)
    val library = LibraryRepository(app)
    val tracks = library.tracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val unanalyzed = library.unanalyzedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    var folders by mutableStateOf<List<String>>(emptyList())

    val decks = listOf(DeckModel(0), DeckModel(1))
    val mixer = MixerModel()
    var engineOk by mutableStateOf(false)
    var message by mutableStateOf<UiText?>(null)
    var engineInfo by mutableStateOf(DoubleArray(4))

    val autoMix = AutoMix(viewModelScope, decks, mixer, settings) { deck, t -> loadTrack(deck, t, force = true) }

    // Aufnahme
    var recording by mutableStateOf(false)
    var recordSeconds by mutableStateOf(0.0)
    private var recordUri: Uri? = null

    private var scanning = false
    private var analyzeJob: Job? = null

    // ---------------- Laufende Aktualisierung (ca. 60 x pro Sekunde) ----------------
    private fun startPolling() = viewModelScope.launch {
        val st = DoubleArray(9)
        val lastLoad = intArrayOf(-1, -1)
        var frame = 0
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
            mixer.masterLevel = NativeEngine.getMasterLevel()
            // Spektrum: neues Array, damit Compose neu zeichnet (Berechnung hier, nicht im Audio-Thread)
            val spec = FloatArray(28)
            NativeEngine.getSpectrum(spec)
            val old = mixer.spectrum
            for (i in spec.indices) spec[i] = maxOf(spec[i], (old.getOrNull(i) ?: 0f) * 0.86f) // sanft abfallen
            mixer.spectrum = spec
            if (recording && frame % 10 == 0) recordSeconds = NativeEngine.recordingSeconds()
            frame++
            delay(16)
        }
    }

    fun refreshEngineInfo() {
        val info = DoubleArray(4)
        NativeEngine.getEngineInfo(info)
        engineInfo = info
    }

    // ---------------- Bibliothek ----------------
    fun scanLibrary() {
        if (scanning) return
        scanning = true
        viewModelScope.launch {
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
        analyzeJob = viewModelScope.launch {
            while (isActive && library.analyzeNext()) delay(50)
        }
    }

    fun loadTrack(deck: Int, t: Track, force: Boolean = false) {
        val d = decks[deck]
        if (d.playing && !force) {
            message = UiText(R.string.msg_deck_playing, d.name)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openFileDescriptor(Uri.parse(t.uri), "r")?.use { pfd ->
                    val len = pfd.statSize
                    val fd = pfd.detachFd()
                    withContext(Dispatchers.Main) {
                        d.track = t
                        d.waveform = FloatArray(0)
                        d.peaks = FloatArray(0)
                        d.loadState = 1
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

    // ---------------- Ausgabe / Einstellungen ----------------
    fun outputDevices(): List<OutputDevice> {
        val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
        viewModelScope.launch(Dispatchers.Default) {
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
        viewModelScope.launch { delay(200); refreshEngineInfo() }
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
        get() = getApplication<Application>().getString(R.string.app_name).replace(Regex("[^A-Za-z0-9 _-]"), "")

    private fun startRecording() {
        val resolver = getApplication<Application>().contentResolver
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
            getApplication<Application>().contentResolver.update(uri, values, null, null)
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
            getApplication<Application>().getSharedPreferences("backend", Context.MODE_PRIVATE))
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
                viewModelScope.launch {
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
        viewModelScope.launch {
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
            viewModelScope.launch(Dispatchers.IO) { runCatching { firestore.endEvent(id) } }
        }
    }

    private fun startWishPolling(id: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
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
        if (localServer == null && id.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { runCatching { firestore.setDone(id, w.id, !w.done) } }
    }

    fun deleteWish(w: Wish) {
        wishes.removeAll { it.id == w.id }
        val id = settings.eventId
        if (localServer == null && id.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { runCatching { firestore.delete(id, w.id) } }
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

    override fun onCleared() {
        if (recording) stopRecording()
        localServer?.stop()
        autoMix.stop()
        decks.forEach { NativeEngine.setPlaying(it.index, false) }
        super.onCleared()
    }
}
