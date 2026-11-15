package app.kaze.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.kaze.browser.fx.Fx
import app.kaze.browser.fx.FxForzar
import app.kaze.browser.fx.BibliotecaFxPantalla
import app.kaze.browser.ui.IdsFxFyx
import app.kaze.browser.ui.catalogoFyx
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.theme.KazeTheme
import app.kaze.browser.ui.theme.LocalKaze
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Verificación de la biblioteca de animaciones: cada efecto pintado en su pieza real, en build/fx/<id>.png. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h700dp-xxhdpi")
class FxCapturasTest(private val id: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun ids(): List<Array<Any>> = (IdsFxFyx + "_biblioteca").map { arrayOf<Any>(it) }
    }
    @get:Rule val compose = createComposeRule()

    @Test fun captura() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val app = Kaze.get(ctx)
        Fx.init(ctx)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KazeTheme(true) {
                CompositionLocalProvider(LocalApp provides app) {
                    val k = LocalKaze.current
                    if (id == "_biblioteca") BibliotecaFxPantalla(catalogoFyx(), {})
                    else {
                        val item = catalogoFyx().first { it.id == id }
                        Box(Modifier.fillMaxSize().background(k.paper).padding(16.dp)) { FxForzar(id) { item.vista!!() } }
                    }
                }
            }
        }
        repeat(12) { compose.mainClock.advanceTimeBy(100) }
        compose.onRoot().captureRoboImage("build/fx/$id.png")
    }
}
