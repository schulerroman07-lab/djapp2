package app.fadelux.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.Dimens

/** Gemeinsamer Rahmen für Vollbild-Fenster (Bibliothek, Auto-Mix, Wünsche, Einstellungen). */
@Composable
fun OverlayScaffold(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6050608))
            .safeEdges()
            .padding(Dimens.gapLarge),
    ) {
        Row(Modifier.fillMaxWidth().height(Dimens.button), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            Text(title, style = AppType.title)
            Spacer(Modifier.weight(1f))
            actions()
            NeonButton(Modifier.size(Dimens.button), icon = AppIcons.Close, color = AppColors.Red) { onClose() }
        }
        Spacer(Modifier.height(Dimens.gap))
        content()
    }
}

/** Abschnitts-Titel in Listen. */
@Composable
fun SectionTitle(text: String, color: Color = AppColors.TextDim) {
    Text(text.uppercase(), style = AppType.tiny, color = color, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

/** Leerer Platzhalter-Abstand. */
@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))

/**
 * Abstand zum Bildschirmrand: unten so viel, wie die Wischgeste "Startbildschirm" braucht,
 * seitlich mindestens 10 dp (Zurück-Geste) bzw. die Kamera-Aussparung. So verschiebt ein
 * Wischen zum Verlassen der App keine Regler mehr.
 */
@Composable
fun Modifier.safeEdges(): Modifier = this
    .windowInsetsPadding(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom))
    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    .padding(start = 10.dp, end = 10.dp, bottom = 6.dp)
