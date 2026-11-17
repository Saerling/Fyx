package app.kaze.browser.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.data.Urls
import app.kaze.browser.engine.GlanceRequest
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * Vista rápida (el "Glance" de Fir): la página de un enlace, en vivo en una hoja. Se puede leer y
 * navegar sin abrir pestaña; "Abrir" la convierte en pestaña. Su sesión de Gecko vive solo mientras
 * la hoja está abierta, así que no deja RAM ocupada al cerrarse.
 */
@Composable
fun GlanceSheet(req: GlanceRequest, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    var url by remember(req) { mutableStateOf(req.url) }
    var title by remember(req) { mutableStateOf("") }
    var progress by remember(req) { mutableIntStateOf(0) }
    val session = remember(req) {
        val b = GeckoSessionSettings.Builder()
            .usePrivateMode(req.from.isPrivate)
            .allowJavascript(app.settings.javascript)
        // Mismo contenedor de cookies que la pestaña de origen: la vista rápida ve la sesión iniciada.
        app.store.spaceById(req.from.spaceId)?.let { sp -> sp.contextId(app.store.profileById(sp.profileId))?.let(b::contextId) }
        GeckoSession(b.build()).apply {
            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLocationChange(
                    session: GeckoSession, u: String?,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean,
                ) { if (u != null) url = u }
            }
            contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, t: String?) { title = t.orEmpty() }
            }
            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onProgressChange(session: GeckoSession, p: Int) { progress = p }
            }
            open(app.runtime)
            loadUri(req.url)
        }
    }
    DisposableEffect(session) { onDispose { session.close() } }
    val height = (LocalConfiguration.current.screenHeightDp * 0.78f).dp

    KazeSheet(onDismiss, full = true) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title.ifBlank { Urls.displayHost(url) }, style = KazeType.headline, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Urls.displayHost(url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
            }
            TextPill("Abrir", k.accent) { onOpen(url); onDismiss() }
            Spacer(Modifier.width(6.dp))
            TextPill("Cerrar", k.ink55) { onDismiss() }
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, color = k.accent, modifier = Modifier.fillMaxWidth().height(2.dp))
        else Spacer(Modifier.height(2.dp))
        AndroidView(
            factory = { GeckoView(it).apply { setSession(session) } },
            onRelease = { it.releaseSession() },
            modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(8.dp))
    }
}
