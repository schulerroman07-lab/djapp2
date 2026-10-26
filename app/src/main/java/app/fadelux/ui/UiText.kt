package app.fadelux.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Text aus strings.xml, der erst in der Oberfläche übersetzt wird (passend zur gewählten Sprache). */
class UiText(@StringRes val id: Int, vararg val args: Any) {
    @Composable
    fun resolve(): String = stringResource(id, *args)
}
