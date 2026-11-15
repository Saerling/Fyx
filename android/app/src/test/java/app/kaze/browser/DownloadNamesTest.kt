package app.kaze.browser

import app.kaze.browser.engine.DownloadNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadNamesTest {
    private val ext: (String) -> String? = { mapOf("application/pdf" to "pdf", "image/jpeg" to "jpg", "text/html" to "html")[it] }
    private val mime: (String) -> String? = { mapOf("pdf" to "application/pdf", "apk" to "application/vnd.android.package-archive", "jpg" to "image/jpeg")[it] }

    @Test fun cabecera() {
        assertEquals("informe final.pdf", DownloadNames.fromDisposition("attachment; filename=\"informe final.pdf\""))
        assertEquals("informe.pdf", DownloadNames.fromDisposition("attachment; filename=informe.pdf"))
        // filename* (RFC 5987) gana y trae tildes
        assertEquals("canción ñ.mp3", DownloadNames.fromDisposition("attachment; filename=\"cancion.mp3\"; filename*=UTF-8''canci%C3%B3n%20%C3%B1.mp3"))
        // nombre con %20 aunque no debería
        assertEquals("mi archivo.zip", DownloadNames.fromDisposition("attachment; filename=\"mi%20archivo.zip\""))
        assertNull(DownloadNames.fromDisposition("inline"))
        assertNull(DownloadNames.fromDisposition(null))
    }

    @Test fun nombre() {
        assertEquals("a b.pdf", DownloadNames.guess("https://x.com/dir/a%20b.pdf?token=1#p", null, "application/pdf", ext))
        // sin extensión: se pone la del tipo
        assertEquals("descargar.pdf", DownloadNames.guess("https://x.com/descargar?id=5", null, "application/pdf", ext))
        // tipo genérico: no se inventa extensión
        assertEquals("descargar", DownloadNames.guess("https://x.com/descargar", null, "application/octet-stream", ext))
        assertEquals("descarga", DownloadNames.guess("https://x.com/", null, null, ext))
        assertEquals("descarga.jpg", DownloadNames.guess("data:image/jpeg;base64,AAAA", null, "image/jpeg", ext))
        // la cabecera manda sobre la dirección, y nunca sale de la carpeta
        assertEquals("passwd", DownloadNames.guess("https://x.com/a.bin", "attachment; filename=\"../../etc/passwd\"", null, ext))
        assertEquals("a_b_.txt", DownloadNames.sanitize("a:b?.txt"))
        assertEquals(120, DownloadNames.sanitize("x".repeat(300) + ".pdf").length)
    }

    @Test fun tipo() {
        // el servidor dice "binario" pero el nombre dice APK
        assertEquals("application/vnd.android.package-archive", DownloadNames.type("app.apk", "application/octet-stream", mime))
        assertEquals("image/jpeg", DownloadNames.type("foto.jpg", null, mime))
        assertEquals("text/html", DownloadNames.type("pagina.pdf", "text/html", mime))
        assertEquals("application/octet-stream", DownloadNames.type("cosa", null, mime))
    }

    @Test fun repetidos() {
        val hay = setOf("foto.jpg", "foto (1).jpg")
        assertEquals("foto (2).jpg", DownloadNames.unique("foto.jpg") { it in hay })
        assertEquals("nuevo.jpg", DownloadNames.unique("nuevo.jpg") { it in hay })
    }
}
