package app.fadelux.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Schriftstile der App. */
object AppType {
    val title = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.Text, letterSpacing = 0.5.sp)
    val logo = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    val body = TextStyle(fontSize = 15.sp, color = AppColors.Text)
    val bodyBold = TextStyle(fontSize = 15.sp, color = AppColors.Text, fontWeight = FontWeight.Bold)
    val small = TextStyle(fontSize = 12.sp, color = AppColors.TextDim)
    val tiny = TextStyle(fontSize = 10.sp, color = AppColors.TextDim, letterSpacing = 0.5.sp)
    val number = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    val button = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
}
