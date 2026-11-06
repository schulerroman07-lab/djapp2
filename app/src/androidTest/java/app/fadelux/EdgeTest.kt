package app.fadelux

import android.app.Application
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.WindowInsets
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bildschirmrand (Samsung-Gesten): liest die echten Positionen aller antippbaren Elemente aus der
 * Compose-Oberfläche und prüft, dass kein Knopf in den Rand-Zonen liegt – den Gesten-Zonen des Systems
 * bzw. mindestens 16 dp. Läuft im Emulator mit Bildschirmgrösse/Dichte eines Samsung-Handys
 * (Workflow: wm size 1080x2340, wm density 420). Hoch- und Querformat, Player und "Als Nächstes".
 */
@RunWith(AndroidJUnit4::class)
class EdgeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun log(s: String) = Log.i("EdgeTest", s)

    private val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    private fun settle(ms: Long) {
        // Die Oberfläche hat Endlos-Animationen (Hintergrund) → Test-Uhr von Hand weiterdrehen
        var left = ms
        while (left > 0) {
            rule.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
            left -= 100
        }
    }

    /** Gibt die Anzahl Knöpfe in Rand-Zonen zurück. */
    private fun check(label: String): Int {
        val act = rule.activity
        val root = act.window.decorView
        val ins = root.rootWindowInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())
        val minPx = (16 * act.resources.displayMetrics.density).toInt()
        val w = root.width
        val h = root.height
        val l = maxOf(ins?.left ?: 0, minPx)
        val r = maxOf(ins?.right ?: 0, minPx)
        val b = maxOf(ins?.bottom ?: 0, minPx)
        val t = minPx
        val nodes: List<SemanticsNode> = rule.onAllNodes(clickable, useUnmergedTree = false).fetchSemanticsNodes()
        val bad = mutableListOf<String>()
        var wide = 0
        for (n in nodes) {
            val rb = n.boundsInWindow
            if (rb.width <= 0f || rb.height <= 0f) continue
            val inZone = rb.left < l || rb.right > w - r || rb.bottom > h - b || rb.top < t
            if (!inZone) continue
            if (rb.width > w * 0.6f) { wide++; continue }     // Listenzeilen/Leisten über die ganze Breite
            val name = n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text ?: "Element"
            bad += "$name [${rb.left.toInt()},${rb.top.toInt()} – ${rb.right.toInt()},${rb.bottom.toInt()}]"
        }
        log("Bildschirmrand ($label): ${nodes.size} antippbare Elemente, Fenster ${w}x$h px, " +
            "Rand-Zonen links=$l rechts=$r oben=$t unten=$b px (System-Gesten ${ins?.left ?: 0}/${ins?.right ?: 0}/${ins?.bottom ?: 0}) → " +
            (if (bad.isEmpty()) "kein Knopf am Rand" else "KNOPF AM RAND: ${bad.joinToString()}") +
            (if (wide > 0) " · $wide breite Zeilen/Leisten (ganze Breite)" else ""))
        return bad.size
    }

    @Test
    fun nothingAtEdges() {
        rule.mainClock.autoAdvance = false
        settle(3000)                                  // Start-Animation
        val vm = DjViewModel.get(rule.activity.application as Application)
        // Bibliothek (vom Dauertest schon eingelesen) abspielen, damit "Als Nächstes" erscheint
        val t0 = System.currentTimeMillis()
        while (vm.tracks.value.isEmpty() && System.currentTimeMillis() - t0 < 30_000) settle(500)
        rule.runOnUiThread { vm.playLibrary() }
        val t1 = System.currentTimeMillis()
        while (!vm.isPlaying && System.currentTimeMillis() - t1 < 60_000) settle(500)
        settle(2000)

        var problems = check("Player, hoch")
        rule.onNode(hasText("Up next", substring = true)).performClick()
        settle(1500)
        problems += check("Als Nächstes, hoch")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle(1000)

        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Thread.sleep(2500)
        settle(2000)
        problems += check("Player, quer")
        runCatching {
            rule.onNode(hasText("Up next", substring = true)).performClick()
            settle(1500)
            problems += check("Als Nächstes, quer")
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        }.onFailure { log("Als Nächstes (quer) nicht geöffnet: ${it.message}") }
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        rule.runOnUiThread { vm.autoMix.stop() }
        log("ERGEBNIS Rand-Prüfung: ${if (problems == 0) "OK" else "$problems Knöpfe am Rand"}")
        assertTrue("Kein Knopf am Bildschirmrand", problems == 0)
    }
}
