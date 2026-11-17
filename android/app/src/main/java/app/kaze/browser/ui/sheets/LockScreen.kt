package app.kaze.browser.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.kaze.browser.engine.AppLock
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze

/** Tapa todo Fyx hasta que el dueño se identifica; pide la huella sola al aparecer. */
@Composable
fun LockScreen() {
    val k = LocalKaze.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppLock.unlock(context) }
    BackHandler { (context as? android.app.Activity)?.moveTaskToBack(true) }
    Column(
        Modifier.fillMaxSize().background(k.paper)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, null, tint = k.ink, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text("Fyx está bloqueado", style = KazeType.title2, color = k.ink)
        Spacer(Modifier.height(20.dp))
        TextPill("Desbloquear", k.accent) { AppLock.unlock(context) }
    }
}
