package app.fadelux.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType

/**
 * Fenster, das von unten hereingleitet (z.B. "Als Nächstes", Playlist wählen).
 * Schliessen: am Griff nach unten wischen, ausserhalb tippen oder Zurück-Taste (im Aufrufer).
 */
@Composable
fun BottomSheet(
    visible: Boolean,
    title: String,
    onClose: () -> Unit,
    heightFraction: Float = 0.85f,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(remember { MutableInteractionSource() }, null) { onClose() },
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            var drag by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction)
                    .graphicsLayer { translationY = drag.coerceAtLeast(0f) }
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xF20B0E11))
                    .clickable(remember { MutableInteractionSource() }, null) { }   // Klicks nicht durchlassen
                    .safeEdges()
                    .padding(horizontal = 12.dp),
            ) {
                // Griff: nach unten wischen schliesst
                Box(
                    Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (drag > 120f) onClose(); drag = 0f },
                            onDragCancel = { drag = 0f },
                        ) { change, amount -> change.consume(); drag += amount }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.TextDim))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = AppType.title, modifier = Modifier.weight(1f))
                    NeonButton(Modifier.size(44.dp), icon = AppIcons.Close, color = AppColors.TextDim, corner = 22.dp) { onClose() }
                }
                content()
            }
        }
    }
}
