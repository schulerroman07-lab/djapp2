package app.fadelux

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.fadelux.audio.NativeEngine
import app.fadelux.playback.PlaybackService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
        openApp()
        val start = System.currentTimeMillis()
        for ((i, at) in listOf(250L, 600L, 1000L, 1500L, 2600L).withIndex()) {
            Thread.sleep((start + at - System.currentTimeMillis()).coerceAtLeast(0))
            shot("1_intro_${i}_${at}ms")
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

        // ---- Weiter / Zurück: kurzer Übergang im Takt ----
        assertTrue("nächster Song bereit", waitFor(60_000, "nächster Song geladen") { main { vm.autoMix.nextPlan != null } })
        var before = main { vm.autoMix.transitionsDone }
        val titleBefore = main { vm.autoMix.current?.title }
        main { vm.remoteNext() }
        val nextOk = waitFor(40_000, "Weiter") { main { vm.autoMix.transitionsDone } > before }
        log("Weiter: ${if (nextOk) "OK" else "FEHLER"} ($titleBefore → ${main { vm.autoMix.current?.title }}), Typ ${main { vm.autoMix.logLines.firstOrNull { it.contains("Übergang startet") } }}")
        assertTrue("Weiter macht einen Übergang", nextOk)
        Thread.sleep(3000)
        before = main { vm.autoMix.transitionsDone }
        main { vm.remotePrevious() }
        val prevOk = waitFor(60_000, "Zurück") { main { vm.autoMix.transitionsDone } > before }
        log("Zurück: ${if (prevOk) "OK" else "FEHLER"} → ${main { vm.autoMix.current?.title }} (erwartet: $titleBefore)")
        assertTrue("Zurück macht einen Übergang", prevOk)

        // ---- Gästewunsch über den lokalen Webserver (wie ein Gast mit Handy) ----
        main { vm.startEvent("Test", emptyMap()) }
        waitFor(10_000, "Wunsch-Server") { main { vm.eventActive } }
        val url = main { vm.wishUrl }
        val wishTrack = main { vm.tracks.value.lastOrNull { t -> t.id != vm.autoMix.current?.id && vm.autoMix.queue.take(6).none { it.id == t.id } } }
        if (url.isNotEmpty() && wishTrack != null) {
            val wishUrl = URL(url.replace(Regex("https?://[^/:]+"), "http://127.0.0.1").trimEnd('/') + "/wish")
            val code = runCatching {
                (wishUrl.openConnection() as HttpURLConnection).run {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    outputStream.write(("song=" + URLEncoder.encode("${wishTrack.artist} – ${wishTrack.title}", "UTF-8") + "&text=").toByteArray())
                    responseCode
                }
            }.getOrElse { -1 }
            val queued = waitFor(15_000, "Wunsch eingereiht") { main { vm.queuedWishIds.isNotEmpty() } }
            val pos = main { vm.autoMix.queue.indexOfFirst { it.id == wishTrack.id } }
            log("Gästewunsch '${wishTrack.title}': HTTP $code, eingereiht=$queued an Platz ${pos + 1} (von ${main { vm.autoMix.queue.size }})")
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
