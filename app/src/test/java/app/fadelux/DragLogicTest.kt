package app.fadelux

import app.fadelux.ui.DragLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rechen-Regeln fürs Ziehen in "Als Nächstes": Auto-Scroll in beide Richtungen, Ziel-Platz. */
class DragLogicTest {
    private val edge = 70f
    private val max = 900f

    @Test
    fun scrollsUpNearTopEdge_andDownNearBottomEdge() {
        // Sichtbarer Bereich 0..1000, Zeile 58 hoch
        assertEquals(0f, DragLogic.scrollSpeed(400f, 458f, 0f, 1000f, edge, max), 0f)             // Mitte: kein Scroll
        assertTrue(DragLogic.scrollSpeed(30f, 88f, 0f, 1000f, edge, max) < 0f)                     // oben: nach oben
        assertTrue(DragLogic.scrollSpeed(930f, 988f, 0f, 1000f, edge, max) > 0f)                   // unten: nach unten
        assertEquals(-max, DragLogic.scrollSpeed(-29f, 29f, 0f, 1000f, edge, max), 0f)             // ganz oben: volle Fahrt
        assertEquals(max, DragLogic.scrollSpeed(971f, 1029f, 0f, 1000f, edge, max), 0f)            // ganz unten: volle Fahrt
        // beide Richtungen gleich schnell bei gleichem Abstand zum Rand
        assertEquals(
            -DragLogic.scrollSpeed(20f, 78f, 0f, 1000f, edge, max),
            DragLogic.scrollSpeed(922f, 980f, 0f, 1000f, edge, max), 0.01f,
        )
    }

    @Test
    fun targetIsRowUnderTheDraggedCenter() {
        val visible = (10..20).map { Triple(it, (it - 10) * 58, 58) }       // Zeilen 10..20 sichtbar
        assertEquals(12, DragLogic.targetIndex(2 * 58 + 20f, visible, 15))
        assertEquals(10, DragLogic.targetIndex(-40f, visible, 15))          // über der ersten Zeile → deren Platz
        assertEquals(20, DragLogic.targetIndex(5000f, visible, 15))         // unter der letzten Zeile → deren Platz
        assertEquals(15, DragLogic.targetIndex(100f, emptyList(), 15))      // nichts sichtbar → bleibt
    }

    /** Ganze Liste simuliert: 40 Zeilen, letzte nach ganz oben und erste nach ganz unten ziehen. */
    @Test
    fun simulatedDragThroughWholeList_bothDirections() {
        val rows = 40
        val rowH = 58f
        val view = 9 * rowH
        fun run(startIndex: Int, fingerTop: Float): Int {
            var scroll = if (startIndex > 8) (rows * rowH - view) else 0f     // Scroll-Position (px)
            var index = startIndex
            repeat(4000) {
                val speed = DragLogic.scrollSpeed(fingerTop, fingerTop + rowH, 0f, view, edge, max)
                scroll = (scroll + speed * 0.016f).coerceIn(0f, rows * rowH - view)
                val first = (scroll / rowH).toInt()
                val visible = (first..minOf(rows - 1, first + 9)).map { Triple(it, (it * rowH - scroll).toInt(), rowH.toInt()) }
                index = DragLogic.targetIndex(fingerTop + rowH / 2, visible, index)
            }
            return index
        }
        assertEquals("letzter Song landet ganz oben", 0, run(39, -20f))
        assertEquals("erster Song landet ganz unten", 39, run(0, view - rowH + 20f))
    }
}
