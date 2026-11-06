package app.fadelux

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.fadelux.audio.NativeEngine
import app.fadelux.playback.PlaybackService
import app.fadelux.ui.IntroTiming
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Gerätetest (läuft auf GitHub im Android-Emulator, echtes Android mit echter Audio-Ausgabe über Oboe):
 * Start-Animation, Playlist + Play, Hintergrund-Betrieb mit Vordergrund-Dienst, Weiter/Zurück,
 * Gästewunsch über den lokalen Webserver, Früh-Übergang, Ordnung nach Stimmung – und Dauerbetrieb
 * (Dauer über das Argument "minutes", Standard 10). Ergebnis: Bericht + Bildschirmfotos in
 * /sdcard/Android/data/app.fadelux/files/soak/.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSoakTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val outDir = File(ctx.getExternalFilesDir(null), "soak").apply { deleteRecursively(); mkdirs() }
    private val report = StringBuilder()
    private val t0 = System.currentTimeMillis()

    private fun log(s: String) {
        val line = String.format("%7.1f min  %s", (System.currentTimeMillis() - t0) / 60000.0, s)
        android.util.Log.i("SoakTest", line)
        report.appendLine(line)
        File(outDir, "report.txt").writeText(report.toString())
    }

    private fun <T> main(block: () -> T): T {
        var r: T? = null
        inst.runOnMainSync { r = block() }
        @Suppress("UNCHECKED_CAST")
        return r as T
    }

    private fun shot(name: String) {
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        // kleine Vorschau (wird vom Workflow als Text ins Protokoll geschrieben)
        val small = Bitmap.createScaledBitmap(bmp, 270, (270f * bmp.height / bmp.width).toInt(), true)
        File(outDir, "small_$name.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 60, it) }
    }

    /**
     * Bildschirmrand: echte Gesten-Zonen des Systems (Wischgesten Zurück/Home) auslesen und prüfen,
     * dass kein Bedienelement darin liegt. Breite Flächen (Listenzeilen, Leisten über die ganze Breite)
     * werden nur gemeldet – Wischgesten am Rand lösen dort nichts aus, Antippen geht trotzdem.
     */
    private fun edgeCheck(label: String): Int {
        // Gesten-Zonen auch dann, wenn die Systemleisten ausgeblendet sind (Vollbild)
        val ins = main {
            val act = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            act?.window?.decorView?.rootWindowInsets?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemGestures())
        }
        val density = ctx.resources.displayMetrics.density
        val minPx = (16 * density).toInt()       // mind. 16 dp Abstand zum Rand (typische Samsung-Gestenbreite)
        val w = device.displayWidth
        val h = device.displayHeight
        val l = maxOf(ins?.left ?: 0, minPx); val r = maxOf(ins?.right ?: 0, minPx); val b = maxOf(ins?.bottom ?: 0, minPx)
        // alle antippbaren Elemente aus dem Bedienungshilfen-Baum (Compose meldet Knöpfe dort)
        val nodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo?) {
            if (n == null) return
            if (n.isClickable || n.isLongClickable) nodes += n
            for (k in 0 until n.childCount) walk(n.getChild(k))
        }
        walk(inst.uiAutomation.rootInActiveWindow)
        val bad = mutableListOf<String>()
        val wide = mutableListOf<String>()
        for (n in nodes) {
            val rb = android.graphics.Rect().also { n.getBoundsInScreen(it) }
            // auf den sichtbaren Bereich scrollbarer Listen zuschneiden (Zeilen, die unter den Listenrand
            // gescrollt sind, melden sonst ihre volle Grösse)
            var p = n.parent
            while (p != null) {
                if (p.isScrollable) {
                    val pb = android.graphics.Rect().also { p.getBoundsInScreen(it) }
                    if (!rb.intersect(pb)) rb.setEmpty()
                }
                p = p.parent
            }
            if (rb.isEmpty) continue
            val inZone = rb.left < l || rb.right > w - r || rb.bottom > h - b
            if (!inZone) continue
            val desc = "${n.contentDescription ?: n.text ?: n.className} $rb"
            if (rb.width() > w * 0.6f) wide += desc else bad += desc
        }
        log("Bildschirmrand ($label): ${nodes.size} antippbare Elemente, Rand-Zonen links=$l rechts=$r unten=$b px " +
            "(System-Gesten ${ins?.left ?: 0}/${ins?.right ?: 0}/${ins?.bottom ?: 0} px, mind. 16 dp; Bildschirm ${w}x$h) → " +
            (if (bad.isEmpty()) "kein Knopf in einer Rand-Zone" else "KNOPF AM RAND: ${bad.joinToString()}") +
            (if (wide.isNotEmpty()) " · breite Flächen bis zum Rand: ${wide.joinToString()}" else ""))
        return bad.size
    }

    private fun openApp() {
        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun waitFor(timeoutMs: Long, what: String, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return true
            Thread.sleep(250)
        }
        log("ZEITÜBERSCHREITUNG: $what")
        return false
    }

    @Test
    fun autoDjSoak() {
        val minutes = (InstrumentationRegistry.getArguments().getString("minutes") ?: "10").toDouble()
        log("Gerätetest startet, Dauerbetrieb $minutes min, Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}")

        // Testsongs (vom Workflow nach /sdcard/Music/Soak kopiert) dem Medienspeicher bekannt machen
        val files = File("/sdcard/Music/Soak").listFiles()?.map { it.absolutePath } ?: emptyList()
        if (files.isNotEmpty()) {
            val latch = CountDownLatch(files.size)
            MediaScannerConnection.scanFile(ctx, files.toTypedArray(), null) { _, _ -> latch.countDown() }
            latch.await(120, TimeUnit.SECONDS)
        }
        log("Testsongs: ${files.size}")

        // ---- Start-Animation: Bildschirmfotos während des Logo-Morphs ----
        // 1. Start ohne Bildschirmfotos: Dauer messen (Fotos bremsen den Emulator beim Zeichnen)
        val launch = android.os.SystemClock.uptimeMillis()
        openApp()
        if (waitFor(60_000, "Start-Animation beginnt") { IntroTiming.started > 0 }) {
            val start = IntroTiming.started
            log("Start-Animation beginnt ${start - launch} ms nach dem Öffnen")
            waitFor(30_000, "Start-Animation fertig") { IntroTiming.done > 0 }
            log("Start-Animation dauerte ${IntroTiming.done - start} ms (+ 250 ms Überblendung)")
        }
        Thread.sleep(2000)
        // 2. Start nur für die Fotos des Logo-Morphs
        main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { it.finish() } }
        Thread.sleep(2000)
        IntroTiming.started = 0
        IntroTiming.done = 0
        openApp()
        if (waitFor(60_000, "Start-Animation beginnt (Fotos)") { IntroTiming.started > 0 }) {
            val start = IntroTiming.started
            for ((i, at) in listOf(120L, 380L, 650L, 950L).withIndex()) {
                Thread.sleep((start + at - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0))
                shot("1_intro_${i}_${at}ms")
            }
            waitFor(30_000, "Start-Animation fertig (Fotos)") { IntroTiming.done > 0 }
            log("Start-Animation mit Fotos dauerte ${IntroTiming.done - start} ms (Fotos bremsen den Emulator)")
            Thread.sleep(800)
            shot("1_intro_5_danach")
        }

        val app = ctx.applicationContext as Application
        val vm = main { DjViewModel.get(app) }
        assertTrue("Audio-Engine startet", main { vm.engineOk })
        assertTrue("Bibliothek eingelesen", waitFor(120_000, "Bibliothek") { main { vm.tracks.value.size } >= files.size.coerceAtMost(5) })
        log("Bibliothek: ${main { vm.tracks.value.size }} Songs")
        shot("2_player_idle")

        // ---- Playlist + Play (Ablauf "Steigernd", Früh-Übergänge: fragen) ----
        main {
            vm.settings.flowCurve = 0
            vm.settings.genreBlocks = false
            vm.settings.earlyMode = 0
            vm.settings.bgAnimation = 2
            vm.playLibrary()
        }
        val playAt = System.currentTimeMillis()
        val started = waitFor(180_000, "erster Song") { main { vm.isPlaying } }
        val firstStartSec = (System.currentTimeMillis() - playAt) / 1000.0
        log("Erster Song läuft nach $firstStartSec s ab Play: ${main { vm.autoMix.current?.title }} (Ladezustand Deck A: ${main { vm.decks[0].loadState }}, Status: ${main { vm.autoMix.status?.toString() }})")
        main { vm.autoMix.logLines.take(10).forEach { log("  Protokoll: $it") } }
        assertTrue("Erster Song startet", started)
        Thread.sleep(4000)
        shot("3_player_playing")

        // ---- Flüssigkeit: Bildraten des Hauptbildschirms, Hintergrund "Voll" vs. "Aus" (Musik läuft) ----
        // (Der Emulator rechnet die Grafik ohne Grafikkarte – absolute Werte sind viel schlechter als auf
        // einem Handy; der Vergleich zeigt, was der animierte Hintergrund kostet.)
        fun frameStats(label: String) {
            device.executeShellCommand("dumpsys gfxinfo app.fadelux reset")
            Thread.sleep(15_000)
            val gfx = device.executeShellCommand("dumpsys gfxinfo app.fadelux")
            val keys = listOf("Total frames rendered", "Janky frames:", "50th percentile", "90th percentile")
            log("Bildraten ($label): " + gfx.lines().map { it.trim() }.filter { l -> keys.any { l.startsWith(it) } }.distinct().joinToString(" | "))
        }
        frameStats("Hintergrund Voll")
        shot("4_player_15s")

        // ---- Hintergrund im Beat: Puls, der den Hintergrund antreibt, gegen das Song-Tempo ----
        run {
            // erster Song: Puls, sobald sein Beat-Raster bekannt ist (gespeicherte Analyse oder fertig geladen)
            waitFor(120_000, "Beat-Raster des laufenden Songs") { main { vm.decks[vm.autoMix.currentDeck].let { it.bpm > 0f || it.hintBpm > 0f } } }
            val bpm = main { vm.decks[vm.autoMix.currentDeck].let { if (it.bpm > 0f) it.bpm else it.hintBpm } }
            val samples = ArrayList<Pair<Long, Float>>()
            val tStart = android.os.SystemClock.uptimeMillis()
            while (android.os.SystemClock.uptimeMillis() - tStart < 8000) {
                // direkt lesen (wie der Hintergrund beim Zeichnen) – über den Haupt-Thread wäre es zu langsam
                samples += android.os.SystemClock.uptimeMillis() to vm.beatPulse()
                Thread.sleep(5)
            }
            var peaks = 0
            var armed = true
            for ((_, v) in samples) { if (armed && v > 0.5f) { peaks++; armed = false } else if (v < 0.25f) armed = true }
            val secs = (samples.last().first - samples.first().first) / 1000.0
            val expected = secs * bpm / 60.0
            log("Hintergrund-Beat: $peaks Pulse in ${"%.1f".format(secs)} s gemessen, erwartet ${"%.1f".format(expected)} bei $bpm BPM " +
                "(Puls min ${"%.2f".format(samples.minOf { it.second })}, max ${"%.2f".format(samples.maxOf { it.second })}, ${samples.size} Messungen)")
            assertTrue("Hintergrund pulsiert im Beat", bpm > 0 && peaks >= expected * 0.6 && peaks <= expected * 1.4)
        }

        // ---- Bildschirmrand: Player und "Als Nächstes" ----
        var edgeProblems = edgeCheck("Player")
        device.findObject(By.textContains("Up next"))?.click() ?: device.findObject(By.textContains("Als Nächstes"))?.click()
        Thread.sleep(2500)
        shot("5_als_naechstes")
        edgeProblems += edgeCheck("Als Nächstes")
        device.pressBack()
        Thread.sleep(1500)
        assertTrue("Kein Knopf in den Gesten-Zonen am Rand", edgeProblems == 0)
        main { vm.settings.bgAnimation = 0 }
        frameStats("Hintergrund Aus")
        main { vm.settings.bgAnimation = 2 }

        // ---- "Weiter", während der nächste Song noch lädt (meist noch nicht analysiert) ----
        run {
            val prepared = main { vm.autoMix.nextPlan != null }
            val before0 = main { vm.autoMix.transitionsDone }
            main { vm.remoteNext() }
            val t0 = System.currentTimeMillis()
            val heard = waitFor(120_000, "Weiter (früh): Übergang beginnt") { main { vm.autoMix.transitioning } }
            log("Weiter (früh, nächster Song ${if (prepared) "schon bereit" else "lädt noch"}): Übergang hörbar nach ${(System.currentTimeMillis() - t0) / 1000.0} s – " +
                "${main { vm.autoMix.logLines.firstOrNull { it.contains("Plan ") }?.substringAfter("Plan ") }}")
            assertTrue("Weiter (früh) macht einen Übergang", heard && waitFor(60_000, "Weiter (früh) fertig") { main { vm.autoMix.transitionsDone } > before0 })
            Thread.sleep(2000)
        }

        // ---- Weiter / Zurück: kurzer Übergang im Takt ----
        val tPrep = System.currentTimeMillis()
        assertTrue("nächster Song bereit", waitFor(150_000, "nächster Song geladen") { main { vm.autoMix.nextPlan != null } })
        log("Nächster Song vorbereitet nach ${(System.currentTimeMillis() - tPrep) / 1000.0} s (ab Start des ersten Songs)")
        var before = main { vm.autoMix.transitionsDone }
        val titleBefore = main { vm.autoMix.current?.title }
        main { vm.remoteNext() }
        val tNext = System.currentTimeMillis()
        if (waitFor(120_000, "Weiter: Übergang beginnt") { main { vm.autoMix.transitioning } })
            log("Weiter: Übergang hörbar nach ${(System.currentTimeMillis() - tNext) / 1000.0} s")
        val nextOk = waitFor(120_000, "Weiter") { main { vm.autoMix.transitionsDone } > before }
        log("Weiter: ${if (nextOk) "OK" else "FEHLER"} nach ${(System.currentTimeMillis() - tNext) / 1000.0} s ($titleBefore → ${main { vm.autoMix.current?.title }}), Typ ${main { vm.autoMix.logLines.firstOrNull { it.contains("Übergang startet") } }}")
        assertTrue("Weiter macht einen Übergang", nextOk)
        Thread.sleep(3000)
        before = main { vm.autoMix.transitionsDone }
        main { vm.remotePrevious() }
        val tPrev = System.currentTimeMillis()
        if (waitFor(150_000, "Zurück: Übergang beginnt") { main { vm.autoMix.transitioning } })
            log("Zurück: Übergang hörbar nach ${(System.currentTimeMillis() - tPrev) / 1000.0} s")
        val prevOk = waitFor(150_000, "Zurück") { main { vm.autoMix.transitionsDone } > before }
        log("Zurück: ${if (prevOk) "OK" else "FEHLER"} nach ${(System.currentTimeMillis() - tPrev) / 1000.0} s → ${main { vm.autoMix.current?.title }} (erwartet: $titleBefore)")
        assertTrue("Zurück macht einen Übergang", prevOk)

        // ---- Gästewunsch über den lokalen Webserver (wie ein Gast mit Handy) ----
        main { vm.startEvent("Test", emptyMap()) }
        waitFor(10_000, "Wunsch-Server") { main { vm.eventActive } }
        val url = main { vm.wishUrl }
        val wishTrack = main { vm.tracks.value.lastOrNull { t -> t.id != vm.autoMix.current?.id && vm.autoMix.queue.take(6).none { it.id == t.id } } }
        if (url.isNotEmpty() && wishTrack != null) {
            // Wie ein Gast-Handy: HTTP-POST direkt über einen Socket (die App selbst darf kein
            // unverschlüsseltes HTTP benutzen – der Browser des Gastes schon)
            val port = Regex(":(\\d+)").findAll(url).lastOrNull()?.groupValues?.get(1)?.toInt() ?: 8080
            val body = "song=" + URLEncoder.encode("${wishTrack.artist} – ${wishTrack.title}", "UTF-8") + "&text="
            val code = runCatching {
                java.net.Socket("127.0.0.1", port).use { sock ->
                    sock.soTimeout = 10_000
                    val req = "POST /wish HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/x-www-form-urlencoded\r\n" +
                        "Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
                    sock.getOutputStream().apply { write(req.toByteArray()); flush() }
                    sock.getInputStream().bufferedReader().readLine() ?: "keine Antwort"
                }
            }.getOrElse { "Fehler: ${it.message}" }
            val queued = waitFor(15_000, "Wunsch eingereiht") { main { vm.queuedWishIds.isNotEmpty() } }
            val pos = main { vm.autoMix.queue.indexOfFirst { it.id == wishTrack.id } }
            log("Gästewunsch '${wishTrack.title}': Antwort '$code', eingereiht=$queued an Platz ${pos + 1} (von ${main { vm.autoMix.queue.size }})")
            assertTrue("Wunsch passend unter den nächsten Songs", queued && pos in 0..5)
        } else log("Gästewunsch: übersprungen (url='$url')")

        // ---- Hintergrund: App verlassen, Dauerbetrieb ----
        device.pressHome()
        Thread.sleep(3000)
        val fg = isServiceForeground()
        log("Im Hintergrund: Vordergrund-Dienst aktiv = $fg, spielt = ${main { vm.isPlaying }}")
        assertTrue("Musik läuft im Hintergrund weiter", main { vm.isPlaying })

        val endAt = System.currentTimeMillis() + (minutes * 60_000).toLong()
        val startTransitions = main { vm.autoMix.transitionsDone }
        val xrunStart = NativeEngine.getXRuns()
        var earlyAccepted = 0
        var silentChecks = 0
        var lastLog = 0L
        var lastFgCheck = System.currentTimeMillis()
        while (System.currentTimeMillis() < endAt) {
            // Früh-Übergang-Frage: im Test immer mit "Ja" beantworten
            if (main { vm.autoMix.earlyOffer } != null) {
                main { vm.autoMix.answerEarly(true) }
                earlyAccepted++
                log("Früh-Übergang angeboten und angenommen")
            }
            if (!main { vm.isPlaying }) silentChecks++
            if (main { vm.autoMix.queue.size } < 3) main { vm.autoMix.addAll(vm.tracks.value.shuffled()) }   // Party geht weiter
            val now = System.currentTimeMillis()
            if (now - lastLog > 60_000) {
                lastLog = now
                log("läuft=${main { vm.autoMix.running }} spielt=${main { vm.isPlaying }} Übergänge=${main { vm.autoMix.transitionsDone }} " +
                    "Aussetzer(xruns)=${NativeEngine.getXRuns() - xrunStart} Song='${main { vm.autoMix.current?.title }}'")
            }
            if (now - lastFgCheck > 10 * 60_000) { lastFgCheck = now; log("Vordergrund-Dienst aktiv = ${isServiceForeground()}") }
            Thread.sleep(1000)
        }
        val transitions = main { vm.autoMix.transitionsDone } - startTransitions
        val xruns = NativeEngine.getXRuns() - xrunStart
        log("Dauerbetrieb fertig: $transitions Übergänge in $minutes min, Aussetzer(xruns)=$xruns, Sekunden ohne Wiedergabe=$silentChecks, Früh-Übergänge=$earlyAccepted (Fragen gesamt ${main { vm.autoMix.earlyOffersShown }})")

        // ---- Ordnung nach Stimmung (nach der Analyse) ----
        main { vm.autoMix.arrangeQueue() }
        val energies = main { vm.autoMix.queue.filter { it.id.toString() !in vm.autoMix.pinned.map { p -> p.toString() } }.map { t -> vm.tracks.value.firstOrNull { it.id == t.id }?.energy ?: -1f } }
        val rising = energies.zipWithNext().count { (a, b) -> b >= a - 0.02f }
        log("Ordnung 'Steigernd': ${energies.joinToString { "%.2f".format(it) }} → $rising von ${(energies.size - 1).coerceAtLeast(0)} Schritten steigend")
        log("Übergangs-Typen: " + main { vm.autoMix.logLines.filter { it.contains("Übergang startet") }.map { it.substringAfter("startet: ").substringBefore(" ") }.groupingBy { it }.eachCount() })

        // ---- zurück in die App, letztes Bild ----
        openApp()
        Thread.sleep(3000)
        shot("4_player_after_soak")

        // Ergebnis
        assertTrue("Auto-DJ läuft noch", main { vm.autoMix.running })
        assertTrue("spielt noch", main { vm.isPlaying })
        val expected = (minutes * 60 / 260).toInt()      // Testsongs ca. 3 min → mindestens 1 Übergang pro 4,3 min
        assertTrue("genug Übergänge ($transitions, erwartet ≥ $expected)", transitions >= expected)
        assertTrue("keine Pausen in der Wiedergabe ($silentChecks s)", silentChecks <= 5)
        assertTrue("erster Song startet schnell ($firstStartSec s)", firstStartSec <= 8.0)
        log("ERGEBNIS: OK")
    }

    @Suppress("DEPRECATION")
    private fun isServiceForeground(): Boolean {
        val am = ctx.getSystemService(ActivityManager::class.java)
        return am.getRunningServices(50).any { it.service.className == PlaybackService::class.java.name && it.foreground }
    }
}
