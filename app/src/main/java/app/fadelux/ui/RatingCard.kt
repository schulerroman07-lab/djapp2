package app.fadelux.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fadelux.R
import app.fadelux.automix.RatingPrompt
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent

/**
 * Nach einem Übergang: kleine Frage "Wie war der Übergang?" mit Daumen hoch / runter.
 * Verschwindet nach 15 s von selbst (dann ohne Bewertung). Bewusst klein, damit sie nicht stört.
 */
@Composable
fun RatingCard(prompt: RatingPrompt?, onRate: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    AnimatedVisibility(prompt != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val p = prompt ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .background(Color(0xF00B0E11), RoundedCornerShape(18.dp))
                .glass(corner = 18.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("rating"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rate_question), style = AppType.bodyBold)
                Text("${p.from} → ${p.to}", style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton(Modifier.size(48.dp).testTag("rate_down"), icon = AppIcons.ThumbDown, description = stringResource(R.string.rate_down_desc),
                    color = AppColors.Red, iconSize = 22.dp, corner = 24.dp) { onRate(false) }
                NeonButton(Modifier.size(48.dp).testTag("rate_up"), icon = AppIcons.ThumbUp, description = stringResource(R.string.rate_up_desc),
                    color = accent, iconSize = 22.dp, corner = 24.dp) { onRate(true) }
            }
        }
    }
}
