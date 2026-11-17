package app.fadelux.ui

/** Rechen-Regeln fürs Ziehen in der Liste (ohne Oberfläche testbar). */
object DragLogic {
    /**
     * Auto-Scroll-Geschwindigkeit in Pixel pro Sekunde: negativ = nach oben, positiv = nach unten,
     * 0 = gezogene Zeile ist nicht nahe am Rand. Wächst linear, je weiter die Zeile in die Randzone ragt.
     */
    fun scrollSpeed(top: Float, bottom: Float, viewStart: Float, viewEnd: Float, edge: Float, max: Float): Float = when {
        top < viewStart + edge -> -((viewStart + edge - top) / edge).coerceIn(0f, 1f) * max
        bottom > viewEnd - edge -> ((bottom - (viewEnd - edge)) / edge).coerceIn(0f, 1f) * max
        else -> 0f
    }

    /**
     * Ziel-Platz für die gezogene Zeile: der Platz der sichtbaren Zeile, in der [center] liegt.
     * Oberhalb der ersten / unterhalb der letzten sichtbaren Zeile: deren Platz.
     * @param visible sichtbare Zeilen als (Index, Oberkante, Höhe)
     */
    fun targetIndex(center: Float, visible: List<Triple<Int, Int, Int>>, from: Int): Int {
        if (visible.isEmpty()) return from
        visible.firstOrNull { center >= it.second && center < it.second + it.third }?.let { return it.first }
        return if (center < visible.first().second) visible.first().first else visible.last().first
    }
}
