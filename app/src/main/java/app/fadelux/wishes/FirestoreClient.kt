package app.fadelux.wishes

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** Ein Musikwunsch eines Gastes. */
data class Wish(
    val id: String,
    val text: String,
    val song: String,
    val createdAt: Instant,
    val done: Boolean,
)

/**
 * Sehr einfacher Zugriff auf Firebase (Anmeldung + Firestore) über die REST-Schnittstelle.
 * So braucht die App keine extra Firebase-Bibliothek und keine google-services.json.
 * Alle Funktionen blockieren – nur im Hintergrund (Dispatchers.IO) aufrufen!
 */
class FirestoreClient(
    private val apiKey: String,
    private val projectId: String,
    private val prefs: SharedPreferences,
) {
    private var idToken: String? = null
    private var tokenExpires = 0L
    val uid: String get() = prefs.getString("fb_uid", "") ?: ""

    private val base =
        "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    /** Anonym bei Firebase anmelden (bzw. Anmeldung erneuern). */
    private fun ensureToken(): String {
        val now = System.currentTimeMillis()
        idToken?.let { if (now < tokenExpires) return it }
        val refresh = prefs.getString("fb_refresh", null)
        if (refresh != null) {
            try {
                val res = http(
                    "POST", "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                    "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"),
                    form = true,
                )
                val j = JSONObject(res)
                saveToken(j.getString("id_token"), j.getString("refresh_token"),
                    j.getString("user_id"), j.getString("expires_in"))
                return idToken!!
            } catch (_: Exception) {
                // Erneuern fehlgeschlagen: neu anmelden
            }
        }
        val res = http(
            "POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey",
            """{"returnSecureToken":true}""",
        )
        val j = JSONObject(res)
        saveToken(j.getString("idToken"), j.getString("refreshToken"),
            j.getString("localId"), j.getString("expiresIn"))
        return idToken!!
    }

    private fun saveToken(token: String, refresh: String, uid: String, expiresIn: String) {
        idToken = token
        tokenExpires = System.currentTimeMillis() + (expiresIn.toLongOrNull() ?: 3600) * 1000 - 60_000
        prefs.edit().putString("fb_refresh", refresh).putString("fb_uid", uid).apply()
    }

    /** Neues Event anlegen (mit Songliste für die Suche der Gäste). */
    fun createEvent(eventId: String, name: String, songs: List<String>) {
        val token = ensureToken()
        val songValues = JSONArray()
        songs.take(5000).forEach { songValues.put(JSONObject().put("stringValue", it)) }
        val fields = JSONObject()
            .put("ownerUid", JSONObject().put("stringValue", uid))
            .put("name", JSONObject().put("stringValue", name))
            .put("active", JSONObject().put("booleanValue", true))
            .put("createdAt", JSONObject().put("timestampValue", Instant.now().toString()))
            // Ablaufzeit: danach sind keine Wünsche mehr möglich, Firestore löscht das Event automatisch (TTL)
            .put("expiresAt", JSONObject().put("timestampValue",
                Instant.now().plusSeconds(BackendConfig.EVENT_HOURS * 3600).toString()))
            .put("songs", JSONObject().put("arrayValue", JSONObject().put("values", songValues)))
        commit(token, JSONObject().put("update", doc("events/$eventId", fields)))
    }

    fun endEvent(eventId: String) {
        val token = ensureToken()
        val fields = JSONObject().put("active", JSONObject().put("booleanValue", false))
        commit(token, JSONObject().put("update", doc("events/$eventId", fields)).put("updateMask", mask("active")))
    }

    /** Alle Wünsche eines Events laden (neueste zuerst). */
    fun listWishes(eventId: String): List<Wish> {
        val token = ensureToken()
        val res = http("GET", "$base/events/$eventId/requests?pageSize=300", null, token = token)
        val docs = JSONObject(res).optJSONArray("documents") ?: return emptyList()
        val list = mutableListOf<Wish>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val f = d.optJSONObject("fields") ?: continue
            list += Wish(
                id = d.getString("name").substringAfterLast('/'),
                text = f.optJSONObject("text")?.optString("stringValue") ?: "",
                song = f.optJSONObject("song")?.optString("stringValue") ?: "",
                createdAt = f.optJSONObject("createdAt")?.optString("timestampValue")
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
                done = f.optJSONObject("done")?.optBoolean("booleanValue") ?: false,
            )
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun setDone(eventId: String, wishId: String, done: Boolean) {
        val token = ensureToken()
        val fields = JSONObject().put("done", JSONObject().put("booleanValue", done))
        commit(token, JSONObject().put("update", doc("events/$eventId/requests/$wishId", fields))
            .put("updateMask", mask("done")).put("currentDocument", JSONObject().put("exists", true)))
    }

    fun delete(eventId: String, wishId: String) {
        val token = ensureToken()
        http("DELETE", "$base/events/$eventId/requests/$wishId", null, token = token)
    }

    private val docPrefix = "projects/$projectId/databases/(default)/documents/"

    private fun doc(path: String, fields: JSONObject) =
        JSONObject().put("name", docPrefix + path).put("fields", fields)

    private fun mask(vararg paths: String) =
        JSONObject().put("fieldPaths", JSONArray().apply { paths.forEach { put(it) } })

    /** Schreibt Änderungen über den Firestore-Befehl "commit" (immer per POST). */
    private fun commit(token: String, vararg writes: JSONObject) {
        val arr = JSONArray()
        writes.forEach { arr.put(it) }
        http("POST", "$base:commit", JSONObject().put("writes", arr).toString(), token = token)
    }

    private fun http(
        method: String, url: String, body: String?, token: String? = null, form: Boolean = false,
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty(
                "Content-Type",
                if (form) "application/x-www-form-urlencoded" else "application/json; charset=utf-8",
            )
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("Firebase-Fehler $code: ${text.take(300)}")
        return text
    }
}
