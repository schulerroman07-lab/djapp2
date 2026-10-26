package ch.hirschexpress.dj.wishes

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Erzeugt ein QR-Code-Bild für einen Text (z.B. die Wunsch-Webseite). */
fun makeQrBitmap(text: String, size: Int = 600): Bitmap {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
