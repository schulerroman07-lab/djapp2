package app.fadelux.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Liest Cover-Bilder aus den MP3-Tags (ID3/APIC) und speichert sie zwischen:
 * 1) im Arbeitsspeicher (schnell), 2) als kleine JPG-Datei im Cache-Ordner (übersteht Neustart).
 */
object CoverRepository {
    private const val SIZE = 256
    private val memory = LruCache<Long, ImageBitmap>(200)
    private val missing = HashSet<Long>()          // Titel ohne Cover (nicht immer wieder suchen)
    private val limit = Semaphore(2)               // max. 2 Dateien gleichzeitig lesen

    fun cached(id: Long): ImageBitmap? = memory.get(id)

    suspend fun load(context: Context, id: Long, uri: String): ImageBitmap? {
        memory.get(id)?.let { return it }
        synchronized(missing) { if (id in missing) return null }
        return limit.withPermit {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "covers").apply { mkdirs() }
                val file = File(dir, "$id.jpg")
                val none = File(dir, "$id.none")
                var bmp: Bitmap? = null
                if (file.exists()) {
                    bmp = BitmapFactory.decodeFile(file.path)
                } else if (!none.exists()) {
                    bmp = extract(context, uri)
                    if (bmp != null) file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    else none.createNewFile()
                }
                val img = bmp?.asImageBitmap()
                if (img != null) memory.put(id, img) else synchronized(missing) { missing += id }
                img
            }
        }
    }

    /** Cover als JPG-Bytes (für Benachrichtigung / Sperrbildschirm), falls vorhanden. */
    fun jpegBytes(context: Context, id: Long): ByteArray? {
        val f = File(File(context.cacheDir, "covers"), "$id.jpg")
        return if (f.exists()) runCatching { f.readBytes() }.getOrNull() else null
    }

    private fun extract(context: Context, uri: String): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, Uri.parse(uri))
            val bytes = r.embeddedPicture ?: return null
            // Zuerst nur Grösse lesen, dann verkleinert dekodieren (spart Speicher)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= SIZE && opts.outHeight / (sample * 2) >= SIZE) sample *= 2
            val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            Bitmap.createScaledBitmap(full, SIZE, SIZE, true)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}
