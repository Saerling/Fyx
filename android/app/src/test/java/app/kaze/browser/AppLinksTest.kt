package app.kaze.browser

import android.content.Intent
import app.kaze.browser.engine.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLinksTest {
    private val yo = "app.kaze.browser"

    @Test fun esquemasPeligrososNoSalen() {
        listOf("file:///data/x", "javascript:alert(1)", "content://x/y", "data:text/html,hola", "about:config", "jar:file:///x!/y").forEach {
            assertNull(it, AppLinks.target(it, yo))
        }
    }

    @Test fun esquemasDeApps() {
        val t = AppLinks.target("whatsapp://send?text=hola", yo)!!
        assertEquals(Intent.ACTION_VIEW, t.intent!!.action)
        assertEquals("whatsapp", t.intent!!.data!!.scheme)
        assertTrue(t.intent!!.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, t.intent!!.flags)
        assertNotNull(AppLinks.target("tel:+571234567", yo)!!.intent)
        assertNotNull(AppLinks.target("mailto:a@b.co", yo)!!.intent)
        assertNotNull(AppLinks.target("market://details?id=org.mozilla.firefox", yo)!!.intent)
    }

    @Test fun intentConRespaldo() {
        val t = AppLinks.target(
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;S.browser_fallback_url=https%3A%2F%2Fzxing.org;end", yo,
        )!!
        assertEquals("zxing", t.intent!!.data!!.scheme)
        assertNull(t.intent!!.component)
        assertEquals("https://zxing.org", t.fallbackUrl)
        assertEquals("market://details?id=com.google.zxing.client.android", t.market!!.dataString)
        // el respaldo no viaja a la otra app
        assertFalse(t.intent!!.hasExtra("browser_fallback_url"))
    }

    @Test fun intentSinRespaldoVaALaTienda() {
        val t = AppLinks.target("intent://abrir#Intent;scheme=miapp;package=com.ejemplo.app;end", yo)!!
        assertNull(t.fallbackUrl)
        assertEquals("market://details?id=com.ejemplo.app", t.market!!.dataString)
    }

    @Test fun intentQueApuntaAUnaWeb() {
        val t = AppLinks.target("intent://www.youtube.com/watch?v=1#Intent;scheme=https;package=com.google.android.youtube;end", yo)!!
        assertEquals("https://www.youtube.com/watch?v=1", t.fallbackUrl)
    }

    @Test fun intentMalicioso() {
        // componente fijo: se quita; respaldo que no es web: se ignora; esquema peligroso dentro: no se lanza
        val a = AppLinks.target("intent://x#Intent;scheme=miapp;component=com.victima/.Privada;S.browser_fallback_url=javascript%3Aalert(1);end", yo)!!
        assertNull(a.intent!!.component)
        assertNull(a.fallbackUrl)
        val b = AppLinks.target("intent:///data/data/app/secreto#Intent;scheme=file;end", yo)!!
        assertNull(b.intent)
        // apuntar a Fyx mismo tampoco
        val c = AppLinks.target("intent://x#Intent;scheme=miapp;package=$yo;end", yo)!!
        assertNull(c.intent)
        assertNull(c.market)
        assertNull(AppLinks.target("intent://esto no es un intent", yo)?.intent?.component)
    }
}
