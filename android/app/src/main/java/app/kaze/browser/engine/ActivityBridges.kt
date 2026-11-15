/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import java.io.File

/**
 * Selector de archivos para `<input type="file">`. Lo usan la actividad principal y las pestañas
 * personalizadas. Debe crearse como propiedad de la actividad (se registra antes de que arranque).
 */
class FilePicker(private val activity: ComponentActivity) {
    private var pending: Pair<PromptDelegate.FilePrompt, PendingPrompt>? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val (prompt, waiting) = pending ?: return@registerForActivityResult
        pending = null
        val data = res.data
        val uris = buildList {
            data?.clipData?.let { c -> for (i in 0 until c.itemCount) add(c.getItemAt(i).uri) }
            if (isEmpty()) data?.data?.let(::add)
        }
        if (uris.isEmpty()) { waiting.dismiss(); return@registerForActivityResult }
        // GeckoView no puede leer los enlaces content:// que devuelve el selector de Android: como Firefox
        // para Android, se copian a una carpeta temporal y se le pasa el archivo. En segundo plano (archivos grandes).
        Thread {
            val files = uris.mapNotNull { runCatching { toUploadFile(it) }.getOrNull() }
            activity.runOnUiThread {
                runCatching {
                    when {
                        files.isEmpty() -> waiting.dismiss()
                        prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE -> waiting.finish(prompt.confirm(activity, files.toTypedArray()))
                        else -> waiting.finish(prompt.confirm(activity, files.first()))
                    }
                }.onFailure { waiting.dismiss() }
            }
        }.start()
    }

    /** Copia lo elegido a cache/subidas con su nombre original (las copias de hace más de una hora se borran). */
    private fun toUploadFile(uri: Uri): Uri {
        if (uri.scheme == "file") return uri
        val cache = activity.cacheDir
        val dir = File(cache, "subidas/${System.nanoTime()}").apply { mkdirs() }
        File(cache, "subidas").listFiles()?.filter { it != dir && it.lastModified() < System.currentTimeMillis() - 3_600_000 }?.forEach { it.deleteRecursively() }
        val name = activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.replace('/', '_')?.takeIf { it.isNotBlank() } ?: "archivo"
        val out = File(dir, name)
        activity.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it, 64 * 1024) } }
        return Uri.fromFile(out)
    }

    fun open(prompt: PromptDelegate.FilePrompt, waiting: PendingPrompt) {
        // Si quedó uno a medias, se cancela: la página no se queda esperando para siempre.
        pending?.second?.dismiss()
        pending = prompt to waiting
        val mimes = prompt.mimeTypes?.filter { it.contains('/') }.orEmpty()
        val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).apply {
            type = if (mimes.size == 1) mimes[0] else "*/*"
            if (mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE)
        }
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pending = null
            waiting.dismiss()
        }
    }
}

/** Pide a Android los permisos que una página necesita (cámara, micrófono, ubicación, notificaciones). */
class AndroidPermissions(private val activity: ComponentActivity) {
    private var pending: PermissionDelegate.Callback? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val cb = pending ?: return@registerForActivityResult
        pending = null
        if (r.isNotEmpty() && r.values.all { it }) cb.grant() else cb.reject()
    }

    fun request(perms: Array<String>, cb: PermissionDelegate.Callback) {
        val missing = perms.filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { cb.grant(); return }
        // Una petición anterior sin responder se rechaza: nunca queda una página colgada.
        pending?.reject()
        pending = cb
        runCatching { launcher.launch(missing.toTypedArray()) }.onFailure { pending = null; cb.reject() }
    }

    /** Pide un permiso sin que nadie espere la respuesta (notificaciones de descargas). */
    fun ask(permission: String) {
        if (pending != null) return
        runCatching { launcher.launch(arrayOf(permission)) }
    }
}
