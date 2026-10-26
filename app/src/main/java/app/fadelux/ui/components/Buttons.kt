package app.fadelux.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.Dimens
import app.fadelux.ui.theme.LocalAccent

/**
 * Der Standard-Knopf der App: Glas, leuchtet wenn aktiv, federt beim Drücken
 * und gibt ein kurzes Vibrations-Feedback.
 *
 * instant = true: reagiert schon beim Berühren (für Play, Cue usw. – wichtig beim Mixen).
 */
@Composable
fun NeonButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    color: Color = LocalAccent.current,
    active: Boolean = false,
    instant: Boolean = false,
    fontSize: Int = 15,
    iconSize: Dp = 22.dp,
    corner: Dp = Dimens.radiusSmall,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "scale")
    val bg by animateColorAsState(if (active) color.copy(alpha = 0.85f) else Color(0x14FFFFFF), label = "bg")
    val fg = if (active) Color.Black else color
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glow(color, radius = 12.dp, corner = corner, alpha = 0.6f, enabled = active)
            .clip(shape)
            .background(bg)
            .border(1.dp, Brush.verticalGradient(listOf(color.copy(alpha = if (active) 0.9f else 0.55f), color.copy(alpha = 0.15f))), shape)
            .pointerInput(instant) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (instant) click()
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { if (!instant) click() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) Icon(icon, contentDescription = text, tint = fg, modifier = Modifier.size(iconSize))
            if (icon != null && text != null) Spacer(Modifier.width(6.dp))
            if (text != null) Text(
                text, style = AppType.button.copy(fontSize = fontSize.sp), color = fg,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Knopf, der nur wirkt, solange er gedrückt gehalten wird (Tempo-Schubs). */
@Composable
fun HoldButton(icon: ImageVector, modifier: Modifier = Modifier, onDown: () -> Unit, onUp: () -> Unit) {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    Box(
        modifier
            .clip(RoundedCornerShape(Dimens.radiusSmall))
            .background(if (pressed) accent.copy(alpha = 0.4f) else Color(0x14FFFFFF))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    down()
                    tryAwaitRelease()
                    up()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = AppColors.Text, modifier = Modifier.size(18.dp)) }
}

/** Kleines Text-Label in Grossbuchstaben (über Reglern). */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = AppColors.TextDim) {
    Text(text.uppercase(), style = AppType.tiny, color = color, modifier = modifier, maxLines = 1)
}
