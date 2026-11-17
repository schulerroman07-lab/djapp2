package app.fadelux

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fadelux.automix.TransitionPlan
import app.fadelux.automix.TransitionType
import app.fadelux.data.Track
import app.fadelux.ui.ExportLogButton
import app.fadelux.ui.RatingCard
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Daumen-Bewertung nach einem Übergang und Protokoll-Export – mit dem echten Auto-DJ, der echten
 * Oberfläche (Bewertungs-Karte, Export-Knopf) und echten Berührungen:
 *  1. Übergang wird protokolliert → Karte erscheint → Daumen hoch antippen → Bewertung gespeichert
 *  2. zweiter Übergang → nicht antippen → Karte verschwindet nach 15 s von selbst
 *  3. Export-Knopf antippen → Datei mit beiden Übergängen, über den FileProvider lesbar, Teilen-Auswahl
 */
@RunWith(AndroidJUnit4::class)
class RatingExportTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun log(s: String) = Log.i("RatingTest", s)

    private fun <T> main(block: () -> T): T {
        var r: Any? = null
        inst.runOnMainSync { r = block() }
        @Suppress("UNCHECKED_CAST")
        return r as T
    }

    private fun roots(v: View, out: MutableList<ViewRootForTest>) {
        if (v is ViewRootForTest) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) roots(v.getChildAt(i), out)
    }

    /** Bildschirm-Rechteck des Elements mit diesem Test-Namen (oder null). */
    private fun find(act: ComponentActivity, tag: String): Rect? = main {
        val rs = mutableListOf<ViewRootForTest>()
        roots(act.window.decorView, rs)
        val nodes = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { nodes += n; n.children.forEach { walk(it) } }
        rs.forEach { walk(it.semanticsOwner.unmergedRootSemanticsNode) }
        val loc = IntArray(2)
        act.window.decorView.getLocationOnScreen(loc)
        nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            ?.boundsInWindow?.translate(loc[0].toFloat(), loc[1].toFloat())
            ?.takeIf { it.width > 4f && it.height > 4f }
    }

    private fun tap(r: Rect) {
        val down = SystemClock.uptimeMillis()
        fun ev(action: Int) {
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, r.center.x, r.center.y, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            inst.sendPointerSync(e)
            e.recycle()
        }
        ev(MotionEvent.ACTION_DOWN)
        Thread.sleep(60)
        ev(MotionEvent.ACTION_UP)
    }

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { if (cond()) return true; Thread.sleep(100) }
        return false
    }

    @Test
    fun rateTransitionAndExportLog() {
        val vm = main { DjViewModel.get(ctx.applicationContext as Application) }
        val am = vm.autoMix
        val tlog = am.transitionLog!!
        tlog.clear()
        val a = Track(1, "", "Song A", "Künstler A", 200_000, 124f, 0.6f, genre = "Pop")
        val b = Track(2, "", "Song B", "Künstler B", 210_000, 126f, 0.7f, genre = "House")
        val c = Track(3, "", "Song C", "Künstler C", 190_000, 128f, 0.8f, genre = "Techno")
        val plan = TransitionPlan(TransitionType.FILTER, 80, 160.0, 12.0, 8, 0.98f, true, "Test")

        var exported: File? = null
        var share: Intent? = null
        val act = inst.startActivitySync(
            Intent(ctx, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ComponentActivity
        main {
            act.setContent {
                AppTheme(AppColors.Emerald) {
                    Column(
                        Modifier.fillMaxSize().background(AppColors.Background).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        ExportLogButton(tlog.count(), tlog.ratedCount()) {
                            exported = LogExport.write(act, tlog)
                            share = LogExport.shareIntent(act, exported!!)
                        }
                        RatingCard(am.rating, onRate = { am.rate(it) })
                    }
                }
            }
        }

        // ---- 1) Übergang A → B: Karte erscheint, Daumen hoch ----
        main { am.recordTransition(a, b, null, null, plan, 158.5, quick = false, early = false) }
        assertNotNull("Bewertung wird angeboten", main { am.rating })
        assertTrue("Daumen-hoch-Knopf sichtbar", waitFor(5000) { find(act, "rate_up") != null })
        tap(find(act, "rate_up")!!)
        assertTrue("Karte verschwindet nach dem Antippen", waitFor(3000) { main { am.rating } == null })
        val e1 = tlog.entries()
        assertEquals(1, e1.size)
        assertTrue("Bewertung +1 gespeichert: ${e1[0].take(200)}", e1[0].contains("\"rating\":1"))
        log("Daumen hoch gespeichert: ${e1[0].take(160)}")

        // ---- 2) Übergang B → C: nicht bewerten → nach 15 s weg ----
        main { am.recordTransition(b, c, null, null, plan.copy(type = TransitionType.CUT), 170.0, quick = true, early = false) }
        assertTrue(waitFor(5000) { find(act, "rate_down") != null })
        val t0 = SystemClock.uptimeMillis()
        assertTrue("Karte verschwindet von selbst", waitFor(20_000) { main { am.rating } == null })
        val secs = (SystemClock.uptimeMillis() - t0) / 1000.0
        log("Karte ohne Antwort verschwunden nach $secs s")
        assertTrue("nach ca. 15 s (war $secs s)", secs in 12.0..17.0)
        assertTrue("ohne Bewertung bleibt 0", tlog.entries()[1].contains("\"rating\":0"))

        // ---- 3) Export antippen ----
        Thread.sleep(800)
        tap(find(act, "log_export")!!)
        assertTrue("Export-Datei geschrieben", waitFor(5000) { exported != null && share != null })
        val json = JSONObject(exported!!.readText())
        assertEquals(2, json.getInt("count"))
        val list = json.getJSONArray("transitions")
        assertEquals(1, list.getJSONObject(0).getInt("rating"))
        assertEquals("Song A", list.getJSONObject(0).getJSONObject("a").getString("title"))
        assertEquals("FILTER", list.getJSONObject(0).getString("type"))
        assertEquals("CUT", list.getJSONObject(1).getString("type"))
        assertEquals(0, list.getJSONObject(1).getInt("rating"))
        // Teilen: Auswahl mit einer Datei, die über den FileProvider lesbar ist
        val inner = share!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        val uri = inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        val viaProvider = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertEquals(exported!!.readText(), viaProvider)
        log("Export: ${exported!!.name}, ${list.length()} Übergänge, über $uri lesbar")
        log("ERGEBNIS Bewertung + Export: OK")
        main { act.finish() }
        tlog.clear()
    }
}
