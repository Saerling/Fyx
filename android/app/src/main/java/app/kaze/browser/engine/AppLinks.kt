/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.kaze.browser.data.Urls

/**
 * Enlaces que no son páginas web (tel:, mailto:, whatsapp:, market:, intent:…): se abren con la app que
 * corresponda. Sigue el patrón de AppLinksUseCases de Firefox para Android:
 *  - nunca se mandan a otra app los esquemas peligrosos ([DENY]);
 *  - un `intent:` se limpia (sin componente ni selector fijos, solo actividades "navegables");
 *  - si la app no está instalada: primero la dirección web de respaldo (`browser_fallback_url`) y,
 *    si no trae, la ficha de la app en la tienda.
 */
object AppLinks {
    /** Lo que nunca sale hacia otra app (lo maneja el motor o no se abre). */
    val DENY = setOf("jar", "file", "javascript", "data", "about", "content", "blob", "resource", "view-source", "moz-extension")

    sealed interface Result {
        /** Se abrió otra app. */
        data object Opened : Result
        /** No hay app: cargar esta dirección web en la pestaña. */
        data class Web(val url: String) : Result
        /** No hay nada que pueda abrirlo. */
        data object None : Result
    }

    class Target(val intent: Intent?, val fallbackUrl: String?, val market: Intent?)

    fun scheme(uri: String): String = uri.substringBefore(':', "").trim().lowercase()

    /** Arma (sin lanzar nada) lo que se intentará para [uri]; null si no se debe abrir. */
    fun target(uri: String, ownPackage: String): Target? {
        val scheme = scheme(uri)
        if (scheme.isEmpty() || scheme in DENY) return null
        if (scheme != "intent" && scheme != "android-app") {
            return Target(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).clean(), null, null)
        }
        val parsed = runCatching {
            Intent.parseUri(uri, if (scheme == "intent") Intent.URI_INTENT_SCHEME else Intent.URI_ANDROID_APP_SCHEME)
        }.getOrNull() ?: return null
        val data = parsed.dataString
        val dataScheme = parsed.data?.scheme?.lowercase()
        // Respaldo: el que trae el enlace o, si el propio enlace apunta a una web, esa web.
        val fallback = parsed.getStringExtra("browser_fallback_url")?.takeIf(Urls::isWeb)
            ?: data?.takeIf(Urls::isWeb)
        val pkg = parsed.`package`
        val market = pkg?.takeIf { it != ownPackage && Regex("[A-Za-z0-9_.]+").matches(it) }
            ?.let { Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$it")).clean() }
        // Nada de apuntar a Fyx mismo ni a esquemas peligrosos escondidos dentro del intent.
        val launchable = pkg != ownPackage && (dataScheme == null || dataScheme !in DENY)
        parsed.removeExtra("browser_fallback_url")
        return Target(if (launchable) parsed.clean() else null, fallback, market)
    }

    private fun Intent.clean(): Intent = apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        component = null
        selector?.let { it.addCategory(Intent.CATEGORY_BROWSABLE); it.component = null }
        // Solo "tarea nueva": se descartan permisos de lectura y demás banderas que traiga el enlace.
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    /** Intenta abrir [uri] fuera de Fyx. */
    fun open(context: Context, uri: String): Result {
        val t = target(uri, context.packageName) ?: return Result.None
        if (t.intent != null && start(context, t.intent)) return Result.Opened
        if (t.fallbackUrl != null) return Result.Web(t.fallbackUrl)
        if (t.market != null && start(context, t.market)) return Result.Opened
        return Result.None
    }

    private fun start(context: Context, intent: Intent): Boolean {
        // Si quien lo abriría es Fyx (por ser el navegador predeterminado), no se lanza: sería un bucle.
        val self = runCatching {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
        }.getOrDefault(false)
        if (self) return false
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
