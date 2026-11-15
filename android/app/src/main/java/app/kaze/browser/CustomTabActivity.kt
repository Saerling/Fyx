/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.kaze.browser.data.Urls
import app.kaze.browser.fx.Fx
import app.kaze.browser.engine.AndroidPermissions
import app.kaze.browser.engine.AppLinks
import app.kaze.browser.engine.BrowserUi
import app.kaze.browser.engine.Engine
import app.kaze.browser.engine.ErrorPages
import app.kaze.browser.engine.FilePicker
import app.kaze.browser.engine.PagePermissions
import app.kaze.browser.engine.PagePrompts
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.sheets.PermissionHost
import app.kaze.browser.ui.sheets.PromptHost
import app.kaze.browser.ui.theme.KazeTheme
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import app.kaze.browser.ui.theme.KazeType
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * Pestaña personalizada: una página abierta desde otra app (Gmail, WhatsApp…) en una ventana ligera de Fyx,
 * con cerrar, compartir y "Abrir en Fyx". Usa el mismo motor (y sesiones iniciadas) que el navegador.
 */
class CustomTabActivity : ComponentActivity() {
    private var session: GeckoSession? = null
    private var title by mutableStateOf("")
    private var url by mutableStateOf("")
    private var progress by mutableFloatStateOf(0f)
    private var secure by mutableStateOf(true)
    private var canGoBack by mutableStateOf(false)

    // Lo que la página puede pedir (diálogos, listas desplegables, archivos, permisos, pantalla completa):
    // antes la pestaña personalizada no respondía a nada de esto y media web "no hacía nada" al tocarla.
    private val ui = BrowserUi()
    private val filePicker = FilePicker(this)
    private val androidPermissions = AndroidPermissions(this)
    private var crashes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fx.init(this)
        enableEdgeToEdge()
        val start = intent.dataString?.takeIf(Urls::isWeb) ?: return finish()
        url = start
        val app = Kaze.get(this)
        val s = GeckoSession(GeckoSessionSettings.Builder().useTrackingProtection(true).build())
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, u: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                // La página de error propia no reemplaza la dirección real.
                if (u != null && !u.startsWith(ErrorPages.PREFIX)) url = u
            }
            override fun onCanGoBack(session: GeckoSession, b: Boolean) { canGoBack = b }

            // Enlaces a otras apps (tel:, mailto:, whatsapp:, intent:, market:…), igual que en las pestañas normales.
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                if (AppLinks.scheme(request.uri) in WEB_SCHEMES) return null
                when (val r = AppLinks.open(this@CustomTabActivity, request.uri)) {
                    is AppLinks.Result.Web -> window.decorView.post { session.loadUri(r.url) }
                    AppLinks.Result.None -> toast("No hay una app para abrir este enlace")
                    AppLinks.Result.Opened -> Unit
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            // Enlaces que piden ventana nueva (target="_blank", window.open): se abren aquí mismo en vez de no hacer nada.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (Urls.isWeb(uri)) window.decorView.post { session.loadUri(uri) }
                return null
            }

            // Sin esto la pantalla se queda en blanco cuando la página no carga.
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? =
                GeckoResult.fromValue(ErrorPages.dataUri(uri ?: url, error))
        }
        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, t: String?) { title = t.orEmpty() }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { ui.fullscreen = fullScreen }
            override fun onCloseRequest(session: GeckoSession) = finish()
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                app.downloads.save(response, referrer = url.takeIf(Urls::isWeb))
                toast("Descargando… (mira el progreso en las notificaciones)")
            }
            // La página se cerró: se vuelve a cargar una vez; si repite, se muestra el error en vez de un bucle.
            override fun onCrash(session: GeckoSession) = recover(session)
            override fun onKill(session: GeckoSession) = recover(session)
            private fun recover(session: GeckoSession) {
                if (isFinishing || isDestroyed) return
                runCatching {
                    if (!session.isOpen) session.open(app.runtime)
                    if (crashes++ == 0) session.loadUri(url)
                    else session.loadUri(ErrorPages.dataUri(url, WebRequestError.ERROR_CONTENT_CRASHED, WebRequestError.ERROR_CATEGORY_CONTENT))
                }
            }
        }
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, p: Int) { progress = if (p >= 100) 0f else p / 100f }
            override fun onSecurityChange(session: GeckoSession, info: GeckoSession.ProgressDelegate.SecurityInformation) { secure = info.isSecure }
        }
        s.promptDelegate = PagePrompts(ui) {
            { t, text, uri ->
                val body = listOfNotNull(text?.takeIf { it.isNotBlank() }, uri?.takeIf { it.isNotBlank() }).joinToString("\n")
                if (body.isBlank()) false else {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body).putExtra(Intent.EXTRA_SUBJECT, t), null))
                    true
                }
            }
        }
        s.permissionDelegate = PagePermissions(ui) { androidPermissions::request }
        s.open(app.runtime)
        s.loadUri(start)
        session = s
        // Color de la barra que pide la app (si lo pide), con texto legible encima.
        val asked = intent.getIntExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0)
        setContent {
          val st = app.settings
          KazeTheme(
              Engine.isDarkTheme(this, st), st.uiStyle, st.accent, st.glassTransparency,
              cornerRadius = st.cornerRadius, smoothCorners = st.smoothCorners, glassOn = st.glassOn,
          ) {
           androidx.compose.runtime.CompositionLocalProvider(LocalApp provides app) {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            // Pantalla completa (videos): sin barra propia ni barras del sistema.
            val full = ui.fullscreen
            androidx.compose.runtime.LaunchedEffect(full) {
                val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (full) {
                    c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } else c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            val bar = if (asked != 0) Color(asked) else if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F8)
            val ink = if (bar.luminance() > 0.5f) Color(0xFF1D1D1F) else Color.White
            BackHandler { if (full) session?.exitFullScreen() else if (canGoBack) session?.goBack() else finish() }
            Column(Modifier.fillMaxSize().background(bar)) {
                if (!full) Row(Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn(Icons.Outlined.Close, "Cerrar", ink) { finish() }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(title.ifBlank { Urls.displayHost(url) }, style = KazeType.subhead.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (secure) Icon(Icons.Outlined.Lock, null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(11.dp).padding(end = 2.dp))
                            Text(Urls.displayHost(url), style = KazeType.footnote, color = ink.copy(alpha = 0.6f), maxLines = 1)
                        }
                    }
                    Btn(Icons.Outlined.Share, "Compartir", ink) {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null))
                    }
                    Btn(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir en Fyx", ink) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(this@CustomTabActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    }
                }
                if (!full) Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (progress > 0f) Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Color(0xFF0A84FF)))
                }
                AndroidView(
                    factory = { GeckoView(it).apply { setSession(s) } },
                    modifier = Modifier.weight(1f).fillMaxWidth().then(if (full) Modifier else Modifier.navigationBarsPadding().imePadding()),
                )
            }
            ui.prompt?.let { p -> PromptHost(p, onDone = { ui.prompt = null }, onFile = filePicker::open) }
            ui.permission?.let { p -> PermissionHost(p) { ui.permission = null } }
           }
          }
        }
    }

    private fun toast(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()

    override fun onStart() {
        super.onStart()
        session?.setActive(true)
    }

    // Fuera de pantalla, la página se pausa (no gasta batería ni sigue sonando por detrás).
    override fun onStop() {
        super.onStop()
        session?.setActive(false)
    }

    override fun onDestroy() {
        ui.prompt?.dismiss()
        session?.close()
        session = null
        super.onDestroy()
    }
}

private val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "file", "javascript", "moz-extension", "resource", "view-source", "ws", "wss", "content", "jar")

@androidx.compose.runtime.Composable
private fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
