/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/** Una descarga del historial (lo que se ve en la lista de Descargas). */
class DownloadItem(
    val id: Long, val name: String, val mime: String, val total: Long, val time: Long,
    /** De dónde se bajó (para reintentar); null si no se puede repetir (PDF de la página, pestaña privada ya cerrada…). */
    val url: String? = null,
    val private: Boolean = false,
    val referrer: String? = null,
) {
    enum class State { RUNNING, DONE, FAILED }
    var state by mutableStateOf(State.RUNNING)
    var done by mutableLongStateOf(0L)
    var uri by mutableStateOf<String?>(null)
    /** Por qué falló, en palabras sencillas. */
    var error by mutableStateOf<String?>(null)
}

/**
 * Guarda descargas en la carpeta Descargas del teléfono, en segundo plano, y lleva su historial.
 * No necesita permisos de almacenamiento: en Android 10+ escribe con MediaStore; si eso falla (o en
 * Android 8–9) usa la carpeta propia de Fyx y presta el archivo con FileProvider al abrirlo.
 */
class Downloads(private val context: Context, private val ui: BrowserUi, private val runtime: () -> GeckoRuntime) {

    // Hasta 3 a la vez: una descarga grande ya no deja esperando a las demás (su conexión caducaba).
    private val io = Executors.newFixedThreadPool(3) { r -> Thread(r, "kaze-download") }
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    /** Historial, la más reciente primero (como en Chrome y Safari). */
    val items = mutableStateListOf<DownloadItem>().apply { addAll(load()) }

    private fun load(): List<DownloadItem> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DownloadItem(
                o.getLong("id"), o.getString("name"), o.optString("mime"), o.optLong("total", -1), o.getLong("time"),
                url = o.optString("url").ifEmpty { null }, referrer = o.optString("referrer").ifEmpty { null },
            ).apply {
                state = DownloadItem.State.valueOf(o.optString("state", "DONE")).let { if (it == DownloadItem.State.RUNNING) DownloadItem.State.FAILED else it }
                done = o.optLong("done")
                uri = o.optString("uri").ifEmpty { null }
                error = o.optString("error").ifEmpty { null }
            }
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val arr = JSONArray()
        items.take(200).forEach { d ->
            arr.put(JSONObject().put("id", d.id).put("name", d.name).put("mime", d.mime).put("total", d.total)
                .put("time", d.time).put("state", d.state.name).put("done", d.done).put("uri", d.uri ?: "")
                // Lo privado no deja rastro de dónde vino.
                .put("url", if (d.private) "" else d.url ?: "").put("referrer", if (d.private) "" else d.referrer ?: "")
                .put("error", d.error ?: ""))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun remove(d: DownloadItem) { items.remove(d); persist() }
    fun clear() { items.removeAll { it.state != DownloadItem.State.RUNNING }; persist() }

    private fun header(response: WebResponse, name: String) =
        response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value

    private fun extensionOf(mime: String) = MimeTypeMap.getSingleton()?.getExtensionFromMimeType(mime)
    private fun mimeOf(ext: String) = MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext)

    /** Guarda lo que el motor entrega como descarga (un enlace a un archivo, un blob de la página, etc.). */
    fun save(response: WebResponse, private: Boolean = false, referrer: String? = null, requested: Boolean = false) {
        val body = response.body ?: run { ui.show("La descarga está vacía"); return }
        // Pedida por nosotros (menú o reintento): un 403/404 no es el archivo, es la página de error del sitio.
        if (requested && response.statusCode >= 400) {
            runCatching { body.close() }
            ui.show("No se pudo descargar: el sitio respondió con el error ${response.statusCode}")
            return
        }
        // Por defecto el motor corta la lectura a los 30 s sin datos: en redes lentas la descarga fallaba.
        runCatching { response.setReadTimeoutMillis(120_000) }
        val mime = header(response, "Content-Type")?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
        val name = DownloadNames.guess(response.uri, header(response, "Content-Disposition"), mime, ::extensionOf)
        val total = header(response, "Content-Length")?.toLongOrNull() ?: -1
        val url = response.uri.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        start(body, name, mime, total, url, private, referrer)
    }

    /** Descarga [url] por petición del usuario (menú de un enlace o imagen, o reintento). */
    fun fetch(url: String, private: Boolean = false, referrer: String? = null) {
        when {
            url.startsWith("data:", true) -> io.execute {
                // Imagen incrustada en la propia página: se decodifica aquí, sin red.
                val decoded = runCatching { decodeData(url) }.getOrNull()
                if (decoded == null) { main.post { ui.show("No se pudo guardar este archivo") }; return@execute }
                val name = DownloadNames.guess(url, null, decoded.first, ::extensionOf)
                main.post { start(decoded.second.inputStream(), name, decoded.first, decoded.second.size.toLong(), null, private, null) }
            }
            url.startsWith("http://", true) || url.startsWith("https://", true) -> {
                ui.show("Preparando la descarga…")
                val builder = WebRequest.Builder(url)
                if (referrer != null && referrer.startsWith("http")) builder.referrer(referrer)
                val request = builder.build()
                val flags = if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
                runCatching {
                    GeckoWebExecutor(runtime()).fetch(request, flags).accept(
                        { r -> main.post { if (r != null) save(r, private, referrer, requested = true) else ui.show("No se pudo descargar") } },
                        { main.post { ui.show("No se pudo descargar: revisa tu conexión e inténtalo de nuevo") } },
                    )
                }.onFailure { ui.show("No se pudo descargar") }
            }
            // blob: y similares solo existen dentro de la página que los creó.
            else -> ui.show("Este archivo solo se puede guardar desde la propia página")
        }
    }

    /** Vuelve a intentar una descarga fallida. */
    fun retry(d: DownloadItem): Boolean {
        val url = d.url ?: return false
        items.remove(d)
        fetch(url, d.private, d.referrer)
        return true
    }

    private fun decodeData(url: String): Pair<String?, ByteArray> {
        val meta = url.substring(5, url.indexOf(','))
        val payload = url.substring(url.indexOf(',') + 1)
        val mime = meta.substringBefore(';').trim().ifEmpty { null }
        val bytes = if (meta.endsWith(";base64", true)) android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        else java.net.URLDecoder.decode(payload.replace("+", "%2B"), "UTF-8").toByteArray()
        return mime to bytes
    }

    private fun start(body: InputStream, name: String, mime: String?, total: Long, url: String?, private: Boolean, referrer: String?) {
        ui.show("Descargando $name…")
        io.execute {
            val item = runCatching { write(body, name, mime, total, url, private, referrer) }.getOrNull()
            main.post {
                if (item != null && item.state == DownloadItem.State.DONE) ui.show("Descargado: $name", "Abrir", onAction = { open(item) })
                else if (url != null) ui.show("No se pudo descargar $name", "Reintentar", onAction = { items.firstOrNull { it.url == url && it.state == DownloadItem.State.FAILED }?.let(::retry) })
                else ui.show("No se pudo descargar $name")
            }
        }
    }

    /** Copia mostrando el progreso (se avisa a la interfaz cada ~256 KB, no en cada bloque: menos trabajo). */
    private fun InputStream.copyWithProgress(out: java.io.OutputStream, item: DownloadItem) {
        val buf = ByteArray(64 * 1024)
        var n: Int
        var copied = 0L
        var lastPost = 0L
        while (read(buf).also { n = it } >= 0) {
            out.write(buf, 0, n)
            copied += n
            if (copied - lastPost >= 256 * 1024) { lastPost = copied; val c = copied; main.post { item.done = c } }
        }
        main.post { item.done = copied }
        // El servidor cortó antes de tiempo: un archivo a medias no cuenta como descargado.
        if (item.total > 0 && copied < item.total) throw java.io.IOException("incompleta")
    }

    /** Escribe [input] en Descargas (bloquea: llamar fuera del hilo principal). Lanza si falla. */
    fun write(
        input: InputStream, name: String, mime: String?, total: Long = -1,
        url: String? = null, private: Boolean = false, referrer: String? = null,
    ): DownloadItem {
        val type = DownloadNames.type(name, mime, ::mimeOf)
        val item = DownloadItem(System.nanoTime(), name, type, total, System.currentTimeMillis(), url, private, referrer)
        main.post { items.add(0, item); DownloadService.start(context) }
        try {
            writeTo(input, name, type, item)
            main.post { item.state = DownloadItem.State.DONE; persist() }
            return item
        } catch (e: Exception) {
            android.util.Log.w("FyxDescargas", "Falló la descarga de $name", e)
            val why = reason(e)
            main.post { item.error = why; item.state = DownloadItem.State.FAILED; persist() }
            throw e
        }
    }

    private fun reason(e: Exception): String {
        val free = runCatching { android.os.StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        return when {
            free < 20L * 1024 * 1024 || e.message?.contains("ENOSPC") == true -> "No hay espacio en el teléfono"
            e is java.net.SocketTimeoutException || e.message == "incompleta" -> "Se cortó la conexión"
            e is java.io.IOException -> "Se interrumpió la descarga"
            else -> "No se pudo guardar el archivo"
        }
    }

    private fun writeTo(input: InputStream, name: String, type: String, item: DownloadItem) {
        input.use { src ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, type)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                // Si Descargas no acepta el archivo (pasa en algunos teléfonos), no se pierde: va a la carpeta de Fyx.
                val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull()
                if (uri != null) {
                    try {
                        (resolver.openOutputStream(uri) ?: error("sin acceso a Descargas")).use { src.copyWithProgress(it, item) }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        main.post { item.uri = uri.toString() }
                    } catch (e: Exception) {
                        runCatching { resolver.delete(uri, null, null) }
                        throw e
                    }
                    return
                }
            }
            // Android 8–9 (o sin acceso a Descargas): carpeta propia de la app, sin permisos.
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "descargas")
            dir.mkdirs()
            val file = File(dir, DownloadNames.unique(name) { File(dir, it).exists() })
            try {
                file.outputStream().use { src.copyWithProgress(it, item) }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            val shared = runCatching { FileProvider.getUriForFile(context, context.packageName + ".archivos", file) }.getOrNull()
            main.post { item.uri = shared?.toString() }
        }
    }

    /** Abre una descarga terminada con la app que corresponda. */
    fun open(d: DownloadItem): Boolean {
        val uri = d.uri ?: run { ui.show("El archivo ya no está disponible"); return false }
        val ok = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), d.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!ok) ui.show("No tienes una app para abrir este tipo de archivo. Está guardado en Descargas.")
        return ok
    }
}
