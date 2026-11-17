package app.fadelux

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.fadelux.automix.TransitionLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Übergangs-Protokoll als Datei exportieren und teilen (Mail, Messenger, Drive …). */
object LogExport {
    /** Schreibt die Export-Datei in den App-Cache und gibt sie zurück. */
    fun write(context: Context, log: TransitionLog): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }      // alte Exporte aufräumen
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        val name = context.getString(R.string.app_name).replace(Regex("[^A-Za-z0-9]+"), "")
        val file = File(dir, "$name-Protokoll-$stamp.json")
        log.export(file, mapOf(
            "app" to context.getString(R.string.app_name),
            "version" to BuildConfig.VERSION_NAME,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to Build.VERSION.RELEASE,
            "exported" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()),
        ))
        return file
    }

    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** "Teilen"-Auswahl für die Datei. */
    fun shareIntent(context: Context, file: File): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri(context, file))
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.log_export)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
