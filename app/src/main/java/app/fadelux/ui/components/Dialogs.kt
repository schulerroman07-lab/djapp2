package app.fadelux.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.fadelux.R
import app.fadelux.ui.theme.AppColors
import app.fadelux.ui.theme.AppType
import app.fadelux.ui.theme.LocalAccent

/** Dialog-Rahmen im Glas-Design. */
@Composable
fun GlassDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(420.dp)
                .glass(glowColor = LocalAccent.current, strong = true)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = AppType.title)
            content()
        }
    }
}

/** Name eingeben (Playlist erstellen / umbenennen). */
@Composable
fun NameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    GlassDialog(title, onDismiss) {
        OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.pl_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            NeonButton(Modifier.width(120.dp).height(44.dp), text = stringResource(R.string.pl_cancel), color = AppColors.TextDim, fontSize = 13) { onDismiss() }
            NeonButton(Modifier.width(140.dp).height(44.dp), text = confirmLabel, active = name.isNotBlank(), fontSize = 13) {
                if (name.isNotBlank()) onConfirm(name.trim())
            }
        }
    }
}

/** Sicherheitsfrage (z.B. vor dem Löschen). */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, danger: Boolean = true, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(title, onDismiss) {
        Text(text, style = AppType.body)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Spacer(Modifier.weight(1f))
            NeonButton(Modifier.width(120.dp).height(44.dp), text = stringResource(R.string.pl_cancel), color = AppColors.TextDim, fontSize = 13) { onDismiss() }
            NeonButton(
                Modifier.width(140.dp).height(44.dp), text = confirmLabel,
                color = if (danger) AppColors.Red else LocalAccent.current, active = true, fontSize = 13,
            ) { onConfirm() }
        }
    }
}

/** Transparent (für Platzhalter). */
val NoColor = Color.Transparent
