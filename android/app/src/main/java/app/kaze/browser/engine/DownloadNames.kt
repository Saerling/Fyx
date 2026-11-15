/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

/** Nombre y tipo de un archivo descargado (lógica pura: se prueba sin Android). */
object DownloadNames {
    /** Tipos que no dicen nada: se prefiere el que indica la extensión del nombre. */
    private val GENERIC = setOf(
        "application/octet-stream", "binary/octet-stream", "application/force-download", "application/x-download",
        "application/download", "application/unknown", "text/plain",
    )

    fun isGeneric(mime: String?): Boolean = mime.isNullOrBlank() || mime.lowercase() in GENERIC

    /** Nombre que indica la cabecera Content-Disposition (`filename*=UTF-8''…` gana a `filename="…"`). */
    fun fromDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) return null
        Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.let { m ->
            decode(m.groupValues[2].trim().trim('"'), m.groupValues[1].ifBlank { "UTF-8" })?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val plain = Regex("filename\\s*=\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|[^;]+)", RegexOption.IGNORE_CASE).find(disposition) ?: return null
        val raw = (if (plain.groupValues[1].startsWith("\"")) plain.groupValues[2].replace(Regex("\\\\(.)"), "$1") else plain.groupValues[1]).trim()
        if (raw.isEmpty()) return null
        // Muchos servidores mandan el nombre con %20 aunque no deberían.
        return if (Regex("%[0-9A-Fa-f]{2}").containsMatchIn(raw)) decode(raw, "UTF-8") ?: raw else raw
    }

    private fun decode(s: String, charset: String): String? =
        runCatching { java.net.URLDecoder.decode(s.replace("+", "%2B"), charset) }.getOrNull()

    /** Quita lo que no puede ir en un nombre de archivo (rutas, caracteres reservados) y lo acorta. */
    fun sanitize(name: String): String {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001f\\u007f:*?\"<>|]"), "_").trim().trim('.').trim()
        if (clean.length <= 120) return clean
        val ext = clean.substringAfterLast('.', "").takeIf { it.length in 1..10 }
        return if (ext != null) clean.take(119 - ext.length).trimEnd('.') + "." + ext else clean.take(120)
    }

    /**
     * Nombre final: Content-Disposition → último tramo de la dirección → "descarga". Si queda sin extensión
     * y el tipo es conocido, se le pone ([extensionOf] traduce tipo → extensión).
     */
    fun guess(url: String, disposition: String?, mime: String?, extensionOf: (String) -> String?): String {
        val fromUrl = {
            // Solo la ruta (sin "https://sitio"): una dirección sin archivo no debe llamarse como el dominio.
            val path = url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "").trimEnd('/')
            if (url.startsWith("data:", true) || url.startsWith("blob:", true)) "" else decode(path.substringAfterLast('/'), "UTF-8").orEmpty()
        }
        var name = sanitize(fromDisposition(disposition).orEmpty()).ifBlank { sanitize(fromUrl()) }.ifBlank { "descarga" }
        val ext = name.substringAfterLast('.', "")
        val hasExt = ext.isNotEmpty() && ext.length <= 10 && ext.all { it.isLetterOrDigit() }
        if (!hasExt && !isGeneric(mime)) extensionOf(mime!!.lowercase())?.takeIf { it.isNotBlank() }?.let { name = "$name.$it" }
        return name
    }

    /** Tipo con el que se guarda: el del servidor, salvo que sea genérico y la extensión diga algo mejor. */
    fun type(name: String, mime: String?, mimeOf: (String) -> String?): String {
        val byExt = name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }?.let(mimeOf)
        return (if (isGeneric(mime)) byExt ?: mime else mime)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
    }

    /** "foto.jpg" → "foto (1).jpg" si ya existe. */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val base = dot?.let { name.substring(0, it) } ?: name
        val ext = dot?.let { name.substring(it) }.orEmpty()
        for (i in 1..999) "$base ($i)$ext".let { if (!exists(it)) return it }
        return "$base (${System.currentTimeMillis()})$ext"
    }
}
