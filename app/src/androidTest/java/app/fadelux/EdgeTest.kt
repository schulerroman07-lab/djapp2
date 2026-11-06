package app.fadelux

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bildschirmrand (Samsung-Gesten): liest die echten Positionen aller antippbaren Elemente direkt aus
 * dem Compose-Aufbau der App und prüft, dass kein Knopf in den Rand-Zonen liegt – den Gesten-Zonen des
 * Systems bzw. mindestens 16 dp. Läuft im Emulator mit Bildschirmgrösse/Dichte eines Samsung-Handys
 * (Workflow: wm size 1080x2340, wm density 420). Hoch- und Querformat, Player und "Als Nächstes".
 * (Ohne Compose-Testregel: die App hat Endlos-Animationen, darauf kann Espresso nicht warten.)
 */
@RunWith(AndroidJUnit4::class)
class EdgeTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun log(s: String) = Log.i("EdgeTest", s)

    private fun <T> main(block: () -> T): T {
        var r: Any? = null
        inst.runOnMainSync { r = block() }
        @Suppress("UNCHECKED_CAST")
        return r as T
    }

    private fun activity(): Activity? =
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()

    private fun composeRoots(v: View, out: MutableList<ViewRootForTest>) {
        if (v is ViewRootForTest) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) composeRoots(v.getChildAt(i), out)
    }

    private fun allNodes(): List<SemanticsNode> = main {
        val roots = mutableListOf<ViewRootForTest>()
        activity()?.window?.decorView?.let { composeRoots(it, roots) }
        val list = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { list += n; n.children.forEach { walk(it) } }
        roots.forEach { walk(it.semanticsOwner.rootSemanticsNode) }
        list
    }

    private fun label(n: SemanticsNode): String =
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: "Element"

    /** Gibt die Anzahl Knöpfe in Rand-Zonen zurück. */
    private fun check(label: String): Int {
        data class Win(val w: Int, val h: Int, val l: Int, val r: Int, val b: Int, val gl: Int, val gr: Int, val gb: Int, val min: Int)
        val win = main {
            val root = activity()!!.window.decorView
            val ins = root.rootWindowInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())
            val minPx = (16 * ctx.resources.displayMetrics.density).toInt()
            Win(root.width, root.height, maxOf(ins?.left ?: 0, minPx), maxOf(ins?.right ?: 0, minPx), maxOf(ins?.bottom ?: 0, minPx),
                ins?.left ?: 0, ins?.right ?: 0, ins?.bottom ?: 0, minPx)
        }
        val clickables = allNodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
        val bad = mutableListOf<String>()
        var wide = 0
        for (n in clickables) {
            val rb = main { n.boundsInWindow }
            if (rb.width <= 0f || rb.height <= 0f) continue
            val inZone = rb.left < win.l || rb.right > win.w - win.r || rb.bottom > win.h - win.b || rb.top < win.min
            if (!inZone) continue
            if (rb.width > win.w * 0.6f) { wide++; continue }   // Listenzeilen/Leisten über die ganze Breite
            bad += "${label(n)} [${rb.left.toInt()},${rb.top.toInt()} – ${rb.right.toInt()},${rb.bottom.toInt()}]"
        }
        log("Bildschirmrand ($label): ${clickables.size} antippbare Elemente, Fenster ${win.w}x${win.h} px, " +
            "Rand-Zonen links=${win.l} rechts=${win.r} oben=${win.min} unten=${win.b} px " +
            "(System-Gesten ${win.gl}/${win.gr}/${win.gb}, mind. 16 dp = ${win.min} px) → " +
            (if (bad.isEmpty()) "kein Knopf am Rand" else "KNOPF AM RAND: ${bad.joinToString()}") +
            (if (wide > 0) " · $wide breite Zeilen/Leisten (ganze Breite)" else ""))
        return bad.size
    }

    /** Element mit diesem Text antippen (über seine Klick-Aktion). */
    private fun click(text: String): Boolean {
        val n = allNodes().firstOrNull { label(it).contains(text) && it.config.getOrNull(SemanticsActions.OnClick) != null }
            ?: allNodes().firstOrNull { label(it).contains(text) }?.let { t ->
                generateSequence(t.parent) { it.parent }.firstOrNull { it.config.getOrNull(SemanticsActions.OnClick) != null }
            }
            ?: return false
        return main { n.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() ?: false }
    }

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(250) }
        return false
    }

    @Test
    fun nothingAtEdges() {
        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("App geöffnet", waitFor(30_000) { main { activity() } != null })
        Thread.sleep(4000)                                         // Start-Animation
        val vm = main { DjViewModel.get(ctx.applicationContext as Application) }
        waitFor(30_000) { main { vm.tracks.value.isNotEmpty() } }
        main { vm.playLibrary() }
        waitFor(60_000) { main { vm.isPlaying } }
        Thread.sleep(3000)

        var problems = check("Player, hoch")
        if (click("Up next")) {
            Thread.sleep(2500)
            problems += check("Als Nächstes, hoch")
            main { (activity() as? MainActivity)?.onBackPressedDispatcher?.onBackPressed() }
            Thread.sleep(1500)
        } else log("'Up next' nicht gefunden")

        main { activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Thread.sleep(4000)
        problems += check("Player, quer")
        if (click("Up next")) {
            Thread.sleep(2500)
            problems += check("Als Nächstes, quer")
            main { (activity() as? MainActivity)?.onBackPressedDispatcher?.onBackPressed() }
            Thread.sleep(1500)
        }
        main { activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        main { vm.autoMix.stop() }
        log("ERGEBNIS Rand-Prüfung: ${if (problems == 0) "OK" else "$problems Knöpfe am Rand"}")
        assertTrue("Kein Knopf am Bildschirmrand", problems == 0)
    }
}
