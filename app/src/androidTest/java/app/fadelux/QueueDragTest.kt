package app.fadelux

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fadelux.data.Track
import app.fadelux.ui.QueueList
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Als Nächstes": Ziehen mit Auto-Scroll in beide Richtungen. 40 Songs – den letzten Song mit dem
 * Finger ganz nach oben ziehen (die Liste muss dabei nach oben mitlaufen), danach den ersten ganz
 * nach unten. Echte Berührungs-Ereignisse auf der echten Listen-Komponente der App.
 */
@RunWith(AndroidJUnit4::class)
class QueueDragTest {
    private val inst = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("DragTest", s)

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

    /** Griffe (zum Ziehen) der sichtbaren Zeilen: Rechteck auf dem Bildschirm, von oben nach unten. */
    private fun handles(act: ComponentActivity): List<Rect> = main {
        val rs = mutableListOf<ViewRootForTest>()
        roots(act.window.decorView, rs)
        val nodes = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { nodes += n; n.children.forEach { walk(it) } }
        rs.forEach { walk(it.semanticsOwner.rootSemanticsNode) }
        val loc = IntArray(2)
        act.window.decorView.getLocationOnScreen(loc)
        nodes.filter { it.config.getOrNull(SemanticsProperties.TestTag) == "touch:Griff" }
            .map { it.boundsInWindow.translate(loc[0].toFloat(), loc[1].toFloat()) }
            .filter { it.height > 4f }
            .sortedBy { it.top }
    }

    private fun touch(action: Int, down: Long, x: Float, y: Float) {
        val ev = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        inst.sendPointerSync(ev)
        ev.recycle()
    }

    /**
     * Finger auf den Griff [from] setzen, nach [toY] ziehen und dort halten, bis [done] gilt.
     * Gibt die Dauer in Sekunden zurück, oder -1 bei Zeitüberschreitung.
     */
    private fun drag(from: Rect, toY: Float, done: () -> Boolean): Double {
        val x = from.center.x
        val down = SystemClock.uptimeMillis()
        var y = from.center.y
        touch(MotionEvent.ACTION_DOWN, down, x, y)
        Thread.sleep(120)
        val steps = 40
        val dy = (toY - y) / steps
        repeat(steps) {
            y += dy
            touch(MotionEvent.ACTION_MOVE, down, x, y)
            Thread.sleep(12)
        }
        val start = SystemClock.uptimeMillis()
        var ok = false
        while (SystemClock.uptimeMillis() - start < 40_000) {
            if (main(done)) { ok = true; break }
            touch(MotionEvent.ACTION_MOVE, down, x, y)      // Finger bleibt liegen
            Thread.sleep(30)
        }
        Thread.sleep(300)
        touch(MotionEvent.ACTION_UP, down, x, y)
        Thread.sleep(500)
        return if (ok) (SystemClock.uptimeMillis() - down) / 1000.0 else -1.0
    }

    @Test
    fun dragLastToTopAndFirstToBottom() {
        val songs = (1..40).map { Track(1000L + it, "", "Song %02d".format(it), "Test", 200_000, 120f, 0.5f) }
        val queue = mutableStateListOf<Track>().apply { addAll(songs) }
        val state = LazyListState()
        val act = inst.startActivitySync(
            Intent(inst.targetContext, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ComponentActivity
        main {
            act.setContent {
                AppTheme(AppColors.Emerald) {
                    Box(Modifier.fillMaxSize().background(AppColors.Background).padding(top = 60.dp, bottom = 60.dp, start = 12.dp, end = 12.dp)) {
                        QueueList(queue, onRemove = {}, onMove = { f, t -> queue.add(t, queue.removeAt(f)) }, state = state)
                    }
                }
            }
        }
        Thread.sleep(2000)
        val visibleRows = handles(act).size
        log("Liste: ${queue.size} Songs, ${visibleRows} Zeilen sichtbar")
        assertTrue("Liste braucht Scrollen (sichtbar $visibleRows von 40)", visibleRows in 3..30)

        // ---- 1) Letzten Song ganz nach oben ziehen (Liste muss nach OBEN mitlaufen) ----
        main { state.requestScrollToItem(39) }
        Thread.sleep(1200)
        var hs = handles(act)
        val last = queue.last()
        val topY = hs.first().top - hs.first().height * 0.3f
        val t1 = drag(hs.last(), topY) { queue.first().id == last.id }
        log("Nach oben: '${last.title}' von Platz 40 → Platz ${queue.indexOf(last) + 1} in $t1 s, erste sichtbare Zeile ${state.firstVisibleItemIndex + 1}")
        assertTrue("Letzter Song kommt beim Ziehen nach oben ganz oben an (Platz ${queue.indexOf(last) + 1})", t1 > 0)
        assertEquals("Reihenfolge der übrigen Songs bleibt", songs - last, queue.toList() - last)
        assertEquals("Liste ist bis ganz oben mitgelaufen", 0, main { state.firstVisibleItemIndex })

        // ---- 2) Ersten Song ganz nach unten ziehen (Liste muss nach UNTEN mitlaufen) ----
        Thread.sleep(800)
        hs = handles(act)
        val first = queue.first()
        val before = queue.toList()
        val bottomY = hs.last().bottom + hs.last().height * 0.3f
        val t2 = drag(hs.first(), bottomY) { queue.last().id == first.id }
        log("Nach unten: '${first.title}' von Platz 1 → Platz ${queue.indexOf(first) + 1} in $t2 s")
        assertTrue("Erster Song kommt beim Ziehen nach unten ganz unten an (Platz ${queue.indexOf(first) + 1})", t2 > 0)
        assertEquals("Reihenfolge der übrigen Songs bleibt", before - first, queue.toList() - first)
        log("ERGEBNIS Drag-Test: OK (nach oben $t1 s, nach unten $t2 s)")
        main { act.finish() }
    }
}
