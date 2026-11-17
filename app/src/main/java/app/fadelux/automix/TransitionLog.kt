package app.fadelux.automix

import java.io.File

/**
 * Protokoll aller Übergänge mit Bewertung (Daumen hoch/runter) – zum Exportieren und Verbessern.
 * Datei mit einer Zeile pro Übergang (JSON). Bewertungen kommen als eigene kurze Zeile dazu
 * ("R <id> <wert>") und werden beim Export in den Eintrag geschrieben. So muss beim Bewerten nie
 * die ganze Datei neu geschrieben werden, und ein Absturz kann nichts zerstören.
 */
class TransitionLog(private val file: File, private val maxEntries: Int = 2000) {

    private val lock = Any()

    /** Neuen Übergang speichern. Gibt die ID zurück (für die Bewertung). */
    fun add(fields: Map<String, Any?>, now: Long = System.currentTimeMillis()): Long = synchronized(lock) {
        var id = now
        if (id <= lastId) id = lastId + 1
        lastId = id
        val sb = StringBuilder("{\"id\":").append(id).append(",\"rating\":0")
        for ((k, v) in fields) sb.append(',').append(quote(k)).append(':').append(value(v))
        sb.append('}')
        file.parentFile?.mkdirs()
        file.appendText(sb.toString() + "\n")
        trimIfNeeded()
        id
    }

    /** Bewertung: +1 = Daumen hoch, -1 = Daumen runter. */
    fun rate(id: Long, rating: Int) = synchronized(lock) {
        file.parentFile?.mkdirs()
        file.appendText("R $id ${rating.coerceIn(-1, 1)}\n")
    }

    /** Alle Einträge (älteste zuerst) als JSON-Texte, Bewertungen eingetragen. */
    fun entries(): List<String> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        val ratings = HashMap<Long, Int>()
        val out = ArrayList<Pair<Long, String>>()
        for (line in file.readLines()) {
            if (line.startsWith("R ")) {
                val p = line.split(' ')
                val id = p.getOrNull(1)?.toLongOrNull() ?: continue
                ratings[id] = p.getOrNull(2)?.toIntOrNull() ?: 0
            } else if (line.startsWith("{\"id\":")) {
                val id = line.substring(6).takeWhile { it.isDigit() }.toLongOrNull() ?: continue
                out += id to line
            }
        }
        out.map { (id, json) ->
            val r = ratings[id] ?: 0
            if (r == 0) json else json.replaceFirst("\"rating\":0", "\"rating\":$r")
        }
    }

    fun count(): Int = entries().size
    fun ratedCount(): Int = entries().count { !it.contains("\"rating\":0") }

    /**
     * Exportiert das Protokoll als eine JSON-Datei (Kopf mit App-Version/Gerät + Liste der Übergänge).
     * Gibt die Anzahl Einträge zurück.
     */
    fun export(target: File, header: Map<String, Any?> = emptyMap()): Int {
        val list = entries()
        val sb = StringBuilder("{")
        for ((k, v) in header) sb.append(quote(k)).append(':').append(value(v)).append(',')
        sb.append("\"count\":").append(list.size).append(",\n\"transitions\":[\n")
        list.forEachIndexed { i, e -> sb.append(e); if (i < list.size - 1) sb.append(','); sb.append('\n') }
        sb.append("]}\n")
        target.parentFile?.mkdirs()
        target.writeText(sb.toString())
        return list.size
    }

    fun clear() = synchronized(lock) { if (file.exists()) file.delete() }

    private var lastId = 0L

    private fun trimIfNeeded() {
        // Nur selten prüfen (Datei wächst pro Übergang um eine Zeile)
        if (file.length() < 1_500_000) return
        val all = entries()
        if (all.size <= maxEntries) return
        file.writeText(all.takeLast(maxEntries / 2).joinToString("\n", postfix = "\n"))
    }

    companion object {
        fun quote(s: String): String {
            val sb = StringBuilder("\"")
            for (c in s) when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
            return sb.append('"').toString()
        }

        fun value(v: Any?): String = when (v) {
            null -> "null"
            is Boolean -> v.toString()
            is Int, is Long -> v.toString()
            is Float -> if (v.isNaN() || v.isInfinite()) "null" else String.format(java.util.Locale.ROOT, "%.4f", v)
            is Double -> if (v.isNaN() || v.isInfinite()) "null" else String.format(java.util.Locale.ROOT, "%.4f", v)
            is Map<*, *> -> v.entries.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + value(it.value) }
            is Collection<*> -> v.joinToString(",", "[", "]") { value(it) }
            else -> quote(v.toString())
        }
    }
}
