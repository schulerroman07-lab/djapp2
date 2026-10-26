package ch.hirschexpress.dj

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.hirschexpress.dj.audio.NativeEngine
import ch.hirschexpress.dj.data.LibraryRepository
import ch.hirschexpress.dj.data.Track
import ch.hirschexpress.dj.wishes.FirestoreClient
import ch.hirschexpress.dj.wishes.Wish
import ch.hirschexpress.dj.wishes.makeQrBitmap
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
import kotlin.math.abs

/** Zustand und Steuerung eines Decks (A oder B). */
class DeckModel(val index: Int) {
    val name = if (index == 0) "A" else "B"

    // Anzeige (wird laufend aus der Engine aktualisiert)
    var track by mutableStateOf<Track?>(null)
    var loadState by mutableIntStateOf(0)   // 0 leer, 1 lädt, 2 bereit, 3 Fehler
    var playing by mutableStateOf(false)
    var position by mutableStateOf(0.0)
    var duration by mutableStateOf(0.0)
    var bpm by mutableFloatStateOf(0f)
    var cue by mutableStateOf(0.0)
    var level by mutableFloatStateOf(0f)
    var waveform by mutableStateOf(FloatArray(0))

    // Bedienelemente
    var tempoRange by mutableIntStateOf(8)      // ±8 % oder ±16 %
    var tempoSlider by mutableFloatStateOf(0f)  // -1..1
    var keylock by mutableStateOf(true)
    var volume by mutableFloatStateOf(0.85f)
    val eq = mutableStateListOf(0f, 0f, 0f)     // Hoch, Mitte, Tief (-1..1)
    val kill = mutableStateListOf(false, false, false)
    var filter by mutableFloatStateOf(0f)
    var echoOn by mutableStateOf(false)
    var echoAmount by mutableFloatStateOf(0.4f)
    var pfl by mutableStateOf(false)

    val tempo: Float get() = 1f + tempoSlider * tempoRange / 100f
    val effectiveBpm: Float get() = bpm * tempo

    // Hinweis: Band-Index in der Engine: 0 = Tief, 1 = Mitte, 2 = Hoch
    fun applyAll() {
        NativeEngine.setTempo(index, tempo)
        NativeEngine.setKeylock(index, keylock)
        NativeEngine.setVolume(index, volume)
        for (i in 0..2) {
            NativeEngine.setEq(index, 2 - i, eq[i])
            NativeEngine.setKill(index, 2 - i, kill[i])
        }
        NativeEngine.setFilter(index, filter)
        NativeEngine.setEcho(index, echoOn, echoAmount)
        NativeEngine.setPfl(index, pfl)
    }

    fun changeTempo(slider: Float) {
        tempoSlider = slider.coerceIn(-1f, 1f)
        NativeEngine.setTempo(index, tempo)
    }

    fun changeRange() {
        val t = tempo
        tempoRange = if (tempoRange == 8) 16 else 8
        changeTempo((t - 1f) * 100f / tempoRange)
    }

    fun resetTempo() = changeTempo(0f)

    /** Kurz schneller/langsamer (zum Angleichen der Beats von Hand). */
    fun nudge(direction: Int) {
        NativeEngine.setTempo(index, tempo * (1f + 0.04f * direction))
    }

    fun sync() {
        val ratio = NativeEngine.sync(index)
        val needed = (ratio - 1f) * 100f
        if (abs(needed) > tempoRange) tempoRange = 16
        tempoSlider = (needed / tempoRange).coerceIn(-1f, 1f)
        NativeEngine.setTempo(index, tempo)
    }

    fun togglePlay() {
        if (loadState != 2) return
        NativeEngine.setPlaying(index, !playing)
    }

    fun cuePress() = NativeEngine.cuePress(index)
    fun seekFraction(f: Float) {
        if (duration > 0) NativeEngine.seek(index, duration * f.coerceIn(0f, 1f))
    }

    fun setEq(i: Int, v: Float) { eq[i] = v; NativeEngine.setEq(index, 2 - i, v) }
    fun toggleKill(i: Int) { kill[i] = !kill[i]; NativeEngine.setKill(index, 2 - i, kill[i]) }
    fun changeVolume(v: Float) { volume = v.coerceIn(0f, 1f); NativeEngine.setVolume(index, volume) }
    fun changeFilter(v: Float) { filter = v; NativeEngine.setFilter(index, v) }
    fun toggleEcho() { echoOn = !echoOn; NativeEngine.setEcho(index, echoOn, echoAmount) }
    fun changeEchoAmount(v: Float) { echoAmount = v; NativeEngine.setEcho(index, echoOn, v) }
    fun togglePfl() { pfl = !pfl; NativeEngine.setPfl(index, pfl) }
    fun toggleKeylock() { keylock = !keylock; NativeEngine.setKeylock(index, keylock) }
}

/** Ein Audio-Ausgabegerät (z.B. Lautsprecher, Kopfhörer, Bluetooth, USB). */
data class OutputDevice(val id: Int, val label: String)

class DjViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val library = LibraryRepository(app)
    val tracks = library.tracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val unanalyzed = library.unanalyzedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val decks = listOf(DeckModel(0), DeckModel(1))
    var engineOk by mutableStateOf(false)
    var crossfader by mutableFloatStateOf(0f)
    var masterVolume by mutableFloatStateOf(0.9f)
    var headphoneVolume by mutableFloatStateOf(0.8f)
    var cueMix by mutableFloatStateOf(0f)
    var masterLevel by mutableFloatStateOf(0f)
    var message by mutableStateOf<String?>(null)

    // Einstellungen
    var outputMode by mutableIntStateOf(prefs.getInt("outputMode", 0))
    var masterDevice by mutableIntStateOf(prefs.getInt("masterDevice", 0))
    var cueDevice by mutableIntStateOf(prefs.getInt("cueDevice", 0))
    var cueDelayMs by mutableFloatStateOf(prefs.getFloat("cueDelayMs", 0f))
    var fbApiKey by mutableStateOf(prefs.getString("fbApiKey", "") ?: "")
    var fbProjectId by mutableStateOf(prefs.getString("fbProjectId", "") ?: "")
    var engineInfo by mutableStateOf("")

    // Aufnahme
    var recording by mutableStateOf(false)
    var recordSeconds by mutableStateOf(0.0)
    private var recordUri: Uri? = null

    private var scanning = false
    private var analyzeJob: Job? = null

    init {
        engineOk = NativeEngine.create()
        applyOutput()
        NativeEngine.setCueDelayMs(cueDelayMs)
        NativeEngine.setCrossfader(crossfader)
        NativeEngine.setMasterVolume(masterVolume)
        NativeEngine.setHeadphoneVolume(headphoneVolume)
        NativeEngine.setCueMix(cueMix)
        decks.forEach { it.applyAll() }
        startPolling()
        restoreEvent()
    }

    // ---------------- Laufende Aktualisierung ----------------
    private fun startPolling() = viewModelScope.launch {
        val st = DoubleArray(9)
        val info = DoubleArray(4)
        var lastLoad = intArrayOf(-1, -1)
        while (isActive) {
            for (d in decks) {
                NativeEngine.getDeckState(d.index, st)
                val ls = st[0].toInt()
                d.loadState = ls
                d.playing = st[1] > 0.5
                d.position = st[2]
                d.duration = st[3]
                d.bpm = st[4].toFloat()
                d.cue = st[7]
                d.level = st[8].toFloat()
                if (ls != lastLoad[d.index]) {
                    lastLoad[d.index] = ls
                    if (ls == 2) {
                        d.waveform = NativeEngine.getWaveform(d.index, 600)
                        // BPM aus dem Deck auch in der Bibliothek speichern
                        val t = d.track
                        if (t != null && d.bpm > 0 && (t.bpm == null || t.bpm == 0f)) {
                            library.setBpm(t.id, d.bpm)
                        }
                    }
                    if (ls == 3) message = "Titel konnte nicht geladen werden"
                }
            }
            NativeEngine.getEngineInfo(info)
            masterLevel = info[3].toFloat()
            if (recording) recordSeconds = NativeEngine.recordingSeconds()
            delay(40)
        }
    }

    fun refreshEngineInfo() {
        val info = DoubleArray(4)
        NativeEngine.getEngineInfo(info)
        engineInfo = "Abtastrate ${info[0].toInt()} Hz · Latenz ca. ${info[1].toInt()} ms · ${info[2].toInt()} Kanäle"
    }

    // ---------------- Bibliothek ----------------
    fun scanLibrary() {
        if (scanning) return
        scanning = true
        viewModelScope.launch {
            try {
                val n = library.scan()
                message = "$n Titel gefunden"
            } catch (e: Exception) {
                message = "Fehler beim Einlesen: ${e.message}"
            }
            scanning = false
            startAnalysis()
        }
    }

    private fun startAnalysis() {
        if (analyzeJob?.isActive == true) return
        analyzeJob = viewModelScope.launch {
            while (isActive && library.analyzeNext()) {
                delay(50)
            }
        }
    }

    fun loadTrack(deck: Int, t: Track) {
        val d = decks[deck]
        if (d.playing) {
            message = "Deck ${d.name} spielt gerade – erst anhalten"
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
                    }
                    NativeEngine.loadTrack(deck, fd, len)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message = "Datei nicht lesbar: ${e.message}" }
            }
        }
    }

    // ---------------- Mixer ----------------
    fun changeCrossfader(x: Float) { crossfader = x.coerceIn(-1f, 1f); NativeEngine.setCrossfader(crossfader) }
    fun changeMasterVolume(v: Float) { masterVolume = v.coerceIn(0f, 1f); NativeEngine.setMasterVolume(masterVolume) }
    fun changeHeadphoneVolume(v: Float) { headphoneVolume = v.coerceIn(0f, 1f); NativeEngine.setHeadphoneVolume(headphoneVolume) }
    fun changeCueMix(v: Float) { cueMix = v.coerceIn(0f, 1f); NativeEngine.setCueMix(cueMix) }

    // ---------------- Ausgabe / Einstellungen ----------------
    fun outputDevices(): List<OutputDevice> {
        val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY && it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            .map { OutputDevice(it.id, "${typeName(it.type)} – ${it.productName}") }
    }

    private fun typeName(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Handy-Lautsprecher"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Kopfhörer (Kabel)"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headset (Kabel)"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (Telefon)"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-Audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-Headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB-Zubehör"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        else -> "Gerät $type"
    }

    fun setOutput(mode: Int, master: Int, cue: Int) {
        outputMode = mode
        masterDevice = master
        cueDevice = cue
        prefs.edit().putInt("outputMode", mode).putInt("masterDevice", master).putInt("cueDevice", cue).apply()
        applyOutput()
    }

    private fun applyOutput() {
        viewModelScope.launch(Dispatchers.Default) {
            NativeEngine.setOutput(outputMode, masterDevice, cueDevice)
            withContext(Dispatchers.Main) { refreshEngineInfo() }
        }
    }

    fun changeCueDelay(ms: Float) {
        cueDelayMs = ms
        NativeEngine.setCueDelayMs(ms)
        prefs.edit().putFloat("cueDelayMs", ms).apply()
    }

    fun saveFirebase(apiKey: String, projectId: String) {
        fbApiKey = apiKey.trim()
        fbProjectId = projectId.trim()
        prefs.edit().putString("fbApiKey", fbApiKey).putString("fbProjectId", fbProjectId).apply()
        firestore = null
    }

    // ---------------- Aufnahme ----------------
    fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val resolver = getApplication<Application>().contentResolver
        val name = "Mix_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.GERMANY).format(Date()) + ".wav"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/HirschExpressDJ")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        try {
            val uri = resolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
            ) ?: throw IllegalStateException("Datei konnte nicht angelegt werden")
            val pfd = resolver.openFileDescriptor(uri, "rw") ?: throw IllegalStateException("Datei nicht beschreibbar")
            val fd = pfd.detachFd()
            pfd.close()
            if (NativeEngine.startRecording(fd)) {
                recordUri = uri
                recording = true
                recordSeconds = 0.0
                message = "Aufnahme läuft: Musik/HirschExpressDJ/$name"
            }
        } catch (e: Exception) {
            message = "Aufnahme nicht möglich: ${e.message}"
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
        message = "Aufnahme gespeichert (Ordner Musik/HirschExpressDJ)"
    }

    // ---------------- Gästewünsche ----------------
    var eventId by mutableStateOf<String?>(null)
    val wishes = mutableStateListOf<Wish>()
    var wishError by mutableStateOf<String?>(null)
    var newWishes by mutableIntStateOf(0)
    var qrBitmap by mutableStateOf<Bitmap?>(null)
    var eventBusy by mutableStateOf(false)
    private var firestore: FirestoreClient? = null
    private var pollJob: Job? = null
    private val seenWishes = mutableSetOf<String>()

    val firebaseConfigured: Boolean get() = fbApiKey.isNotBlank() && fbProjectId.isNotBlank()
    val wishUrl: String get() = "https://$fbProjectId.web.app/?e=${eventId ?: ""}"

    private fun client(): FirestoreClient {
        firestore?.let { return it }
        return FirestoreClient(fbApiKey, fbProjectId, prefs).also { firestore = it }
    }

    private fun restoreEvent() {
        val id = prefs.getString("eventId", null) ?: return
        if (!firebaseConfigured) return
        eventId = id
        qrBitmap = makeQrBitmap(wishUrl)
        startWishPolling()
    }

    fun startEvent(name: String) {
        if (!firebaseConfigured) {
            wishError = "Zuerst Firebase in den Einstellungen eintragen"
            return
        }
        eventBusy = true
        val id = (1..10).map { "abcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")
        val songs = tracks.value.map { "${it.artist} – ${it.title}" }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client().createEvent(id, name.ifBlank { "Hirsch Express" }, songs) }
                eventId = id
                prefs.edit().putString("eventId", id).apply()
                wishes.clear()
                seenWishes.clear()
                newWishes = 0
                qrBitmap = makeQrBitmap(wishUrl)
                wishError = null
                startWishPolling()
            } catch (e: Exception) {
                Log.e("HirschDJ", "Event starten", e)
                wishError = "Event konnte nicht gestartet werden: ${e.message}"
            }
            eventBusy = false
        }
    }

    fun endEvent() {
        val id = eventId ?: return
        pollJob?.cancel()
        eventId = null
        qrBitmap = null
        wishes.clear()
        prefs.edit().remove("eventId").apply()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().endEvent(id) }
        }
    }

    private fun startWishPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val id = eventId ?: break
                try {
                    val list = withContext(Dispatchers.IO) { client().listWishes(id) }
                    val fresh = list.count { it.id !in seenWishes }
                    if (seenWishes.isNotEmpty() || wishes.isNotEmpty()) newWishes += fresh
                    seenWishes += list.map { it.id }
                    wishes.clear()
                    wishes.addAll(list)
                    wishError = null
                } catch (e: Exception) {
                    wishError = "Keine Verbindung: ${e.message?.take(120)}"
                }
                delay(5000)
            }
        }
    }

    fun markWish(w: Wish) {
        val id = eventId ?: return
        val i = wishes.indexOfFirst { it.id == w.id }
        if (i >= 0) wishes[i] = w.copy(done = !w.done)
        viewModelScope.launch(Dispatchers.IO) { runCatching { client().setDone(id, w.id, !w.done) } }
    }

    fun deleteWish(w: Wish) {
        val id = eventId ?: return
        wishes.removeAll { it.id == w.id }
        viewModelScope.launch(Dispatchers.IO) { runCatching { client().delete(id, w.id) } }
    }

    fun clearNewWishes() { newWishes = 0 }

    override fun onCleared() {
        if (recording) stopRecording()
        super.onCleared()
    }
}
