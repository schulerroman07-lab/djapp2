package app.fadelux.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fadelux.R
import app.fadelux.ui.components.AppIcons
import app.fadelux.ui.components.NeonButton
import app.fadelux.ui.components.glass
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent

/** Kurze Einführung beim ersten Start (4 Seiten). */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val accent = LocalAccent.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        Triple(AppIcons.Library, R.string.onb1_title, R.string.onb1_text),
        Triple(AppIcons.Sync, R.string.onb2_title, R.string.onb2_text),
        Triple(AppIcons.Auto, R.string.onb3_title, R.string.onb3_text),
        Triple(AppIcons.Star, R.string.onb4_title, R.string.onb4_text),
    )
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(520.dp).glass(glowColor = accent).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
                label = "onboarding",
            ) { p ->
                val (icon, title, text) = pages[p]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = accent, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(title), style = AppType.title, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(text), style = AppType.body.copy(color = AppColors.TextDim), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(Modifier.size(if (i == page) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == page) accent else AppColors.TextFaint))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton(Modifier.width(140.dp).height(48.dp), text = stringResource(R.string.onb_skip), color = AppColors.TextDim, fontSize = 14) { onDone() }
                NeonButton(
                    Modifier.width(180.dp).height(48.dp),
                    text = stringResource(if (page == pages.size - 1) R.string.onb_start else R.string.onb_next),
                    active = true, fontSize = 15,
                ) { if (page < pages.size - 1) page++ else onDone() }
            }
        }
    }
}
