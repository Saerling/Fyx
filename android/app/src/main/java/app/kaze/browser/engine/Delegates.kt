/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.os.Handler
import android.os.Looper
import app.kaze.browser.data.Urls
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate

// Delegados compartidos por las pestañas normales (SessionPool) y las pestañas personalizadas (CustomTabActivity).

/** Permisos que piden las páginas (cámara, micrófono, ubicación, notificaciones): pregunta con [ui]. */
class PagePermissions(
    private val ui: BrowserUi,
    private val androidPermissions: () -> ((Array<String>, PermissionDelegate.Callback) -> Unit)?,
) : PermissionDelegate {
    private val main = Handler(Looper.getMainLooper())

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: PermissionDelegate.ContentPermission,
    ): GeckoResult<Int> {
        // Reproducción automática y almacenamiento: decisiones silenciosas, sin molestar.
        when (perm.permission) {
            PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
            PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
            PermissionDelegate.PERMISSION_STORAGE_ACCESS,
            -> return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_ALLOW)
            PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
            PermissionDelegate.PERMISSION_TRACKING,
            PermissionDelegate.PERMISSION_XR,
            -> return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
        }
        val result = GeckoResult<Int>()
        if (perm.permission == PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION) {
            // Si el usuario acepta, también hace falta el permiso de notificaciones de Android (13 o más).
            val asked = GeckoResult<Int>()
            asked.accept({ v ->
                if (v == PermissionDelegate.ContentPermission.VALUE_ALLOW && android.os.Build.VERSION.SDK_INT >= 33) {
                    main.post {
                        androidPermissions()?.invoke(
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            object : PermissionDelegate.Callback {},
                        )
                    }
                }
                result.complete(v ?: PermissionDelegate.ContentPermission.VALUE_DENY)
            }, { result.complete(PermissionDelegate.ContentPermission.VALUE_DENY) })
            ui.permission = PermissionAsk.Content(Urls.displayHost(perm.uri), perm, asked)
            return result
        }
        ui.permission = PermissionAsk.Content(Urls.displayHost(perm.uri), perm, result)
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<PermissionDelegate.MediaSource>?,
        audio: Array<PermissionDelegate.MediaSource>?,
        callback: PermissionDelegate.MediaCallback,
    ) {
        ui.permission = PermissionAsk.Media(Urls.displayHost(uri), video, audio, callback)
    }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: PermissionDelegate.Callback,
    ) {
        val req = androidPermissions()
        if (permissions.isNullOrEmpty() || req == null) callback.grant() else req(permissions, callback)
    }
}

/** Diálogos de las páginas (alert, select, archivos, contraseñas…): los muestra la interfaz a través de [ui]. */
class PagePrompts(
    private val ui: BrowserUi,
    private val sharer: () -> ((String?, String?, String?) -> Boolean)?,
) : PromptDelegate {
    private fun <T : PromptDelegate.BasePrompt> ask(p: T): GeckoResult<PromptDelegate.PromptResponse> {
        val result = GeckoResult<PromptDelegate.PromptResponse>()
        ui.prompt?.dismiss()
        ui.prompt = PendingPrompt(p, result)
        return result
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) = ask(prompt)
    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) = ask(prompt)
    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) = ask(prompt)
    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt) = ask(prompt)
    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) = ask(prompt)
    override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt) = ask(prompt)
    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt) = ask(prompt)
    override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt) = ask(prompt)
    override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt) = ask(prompt)
    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) = ask(prompt)
    // navigator.share(): el botón "Compartir" de las páginas abre el menú de compartir de Android.
    override fun onSharePrompt(session: GeckoSession, prompt: PromptDelegate.SharePrompt): GeckoResult<PromptDelegate.PromptResponse> {
        val ok = runCatching { sharer()?.invoke(prompt.title, prompt.text, prompt.uri) ?: false }.getOrDefault(false)
        val r = if (ok) PromptDelegate.SharePrompt.Result.SUCCESS else PromptDelegate.SharePrompt.Result.FAILURE
        return GeckoResult.fromValue(prompt.confirm(r))
    }
    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt) = ask(prompt)
    // Contraseñas: preguntar si guardar; al rellenar con una sola cuenta, se usa directamente.
    override fun onLoginSave(session: GeckoSession, request: PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>) = ask(request)
    override fun onLoginSelect(session: GeckoSession, request: PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>) = ask(request)
}
