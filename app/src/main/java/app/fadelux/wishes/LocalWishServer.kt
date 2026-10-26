package app.fadelux.wishes

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Kleiner Webserver direkt auf dem Handy – für Gästewünsche OHNE Internet.
 * Gäste im gleichen WLAN bzw. Handy-Hotspot scannen den QR-Code und landen hier.
 * Spam-Schutz: pro Gerät (IP-Adresse) nur alle 5 Minuten ein Wunsch.
 */
class LocalWishServer(
    private val songs: () -> List<String>,
    private val texts: Map<String, String>,     // übersetzte Texte für die Gästeseite
    private val onWish: (Wish) -> Unit,
) {
    private var server: ServerSocket? = null
    private val lastWish = ConcurrentHashMap<String, Long>()
    var port = 0
        private set

    val running: Boolean get() = server?.isClosed == false

    fun start(): Boolean {
        stop()
        for (p in 8080..8090) {
            try {
                server = ServerSocket(p)
                port = p
                break
            } catch (_: Exception) { }
        }
        val s = server ?: return false
        thread(name = "wish-server", isDaemon = true) {
            while (!s.isClosed) {
                try {
                    val c = s.accept()
                    thread(isDaemon = true) { handle(c) }
                } catch (_: Exception) { }
            }
        }
        return true
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    /** Adresse für den QR-Code, z.B. http://192.168.43.1:8080/ */
    fun url(): String? = localIp()?.let { "http://$it:$port/" }

    private fun handle(c: Socket) {
        c.use { sock ->
            sock.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: ""
            val path = parts.getOrNull(1)?.substringBefore('?') ?: "/"
            val ip = sock.inetAddress?.hostAddress ?: "?"
            val out = sock.getOutputStream()
            fun send(code: String, type: String, body: String) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                out.write("HTTP/1.1 $code\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
                out.write(bytes)
                out.flush()
            }
            when {
                method == "GET" && path == "/songs" -> send("200 OK", "application/json", JSONArray(songs()).toString())
                method == "POST" && path == "/wish" -> {
                    val buf = CharArray(contentLength.coerceIn(0, 4000))
                    var read = 0
                    while (read < buf.size) { val n = reader.read(buf, read, buf.size - read); if (n < 0) break; read += n }
                    val form = String(buf, 0, read).split("&").associate {
                        val k = it.substringBefore('='); val v = it.substringAfter('=', "")
                        k to URLDecoder.decode(v, "UTF-8")
                    }
                    val now = System.currentTimeMillis()
                    val last = lastWish[ip] ?: 0L
                    val song = (form["song"] ?: "").take(200)
                    val text = (form["text"] ?: "").take(200)
                    when {
                        now - last < 5 * 60_000 -> send("429 Too Many Requests", "application/json", """{"ok":false,"wait":true}""")
                        song.isBlank() && text.isBlank() -> send("400 Bad Request", "application/json", """{"ok":false}""")
                        else -> {
                            lastWish[ip] = now
                            onWish(Wish(UUID.randomUUID().toString(), text, song, Instant.now(), false))
                            send("200 OK", "application/json", """{"ok":true}""")
                        }
                    }
                }
                else -> send("200 OK", "text/html", page())
            }
        }
    }

    private fun page(): String {
        val t = JSONObject(texts as Map<*, *>).toString()
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>♫</title><style>
body{margin:0;background:#050608;color:#f2f5f7;font-family:system-ui,sans-serif}main{max-width:520px;margin:0 auto;padding:16px}
h1{color:#10b981;text-shadow:0 0 12px #10b98188}input,textarea{width:100%;box-sizing:border-box;font-size:1.05rem;padding:12px;border-radius:12px;border:1px solid #ffffff33;background:#ffffff12;color:#f2f5f7;margin-top:10px}
li{list-style:none;padding:10px;margin:4px 0;border-radius:10px;background:#ffffff10;cursor:pointer}li.sel{outline:2px solid #10b981}ul{padding:0;max-height:280px;overflow:auto}
button{width:100%;margin-top:14px;padding:14px;border:0;border-radius:14px;background:#10b981;color:#000;font-weight:700;font-size:1.1rem;box-shadow:0 0 18px #10b98166}
#msg{margin-top:12px}</style></head><body><main><h1 id="h"></h1>
<input id="q" type="search"><ul id="r"></ul><textarea id="t" maxlength="200"></textarea><button id="b"></button><div id="msg"></div></main>
<script>const T=$t;h.textContent=T.title;q.placeholder=T.search;t.placeholder=T.free;b.textContent=T.send;let S=[],c="";
fetch('/songs').then(r=>r.json()).then(j=>S=j);
q.oninput=()=>{const v=q.value.toLowerCase();r.innerHTML="";if(v.length<2)return;S.filter(s=>s.toLowerCase().includes(v)).slice(0,40).forEach(s=>{const li=document.createElement('li');li.textContent=s;if(s==c)li.className='sel';li.onclick=()=>{c=s;q.oninput()};r.appendChild(li)})};
b.onclick=async()=>{if(!c&&!t.value.trim()){msg.textContent=T.empty;return}const body='song='+encodeURIComponent(c)+'&text='+encodeURIComponent(t.value);
const res=await fetch('/wish',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body});msg.textContent=res.ok?T.thanks:(res.status==429?T.wait:T.empty);if(res.ok){c="";t.value="";q.value="";r.innerHTML=""}}
</script></body></html>"""
    }

    companion object {
        /** Eigene IP-Adresse im WLAN/Hotspot finden (bevorzugt 192.168.x.x). */
        fun localIp(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress ?: "" }
                .sortedBy { if (it.startsWith("192.168.")) 0 else if (it.startsWith("10.")) 1 else 2 }
                .firstOrNull { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("Fadelux", "IP nicht gefunden", e)
            null
        }
    }
}
