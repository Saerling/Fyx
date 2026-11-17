package app.kaze.browser.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import app.kaze.browser.ui.mochy.AppMotion
import kotlin.math.roundToInt

/**
 * La página crece desde su tarjeta y se encoge de vuelta a ella (como Safari y Fir). Como en Fir, la
 * página real (GeckoView) nunca se mueve: se anima su foto encima y luego desaparece. Mover la vista
 * viva hace que Gecko recoloque lo fijo a mitad de camino y salte al terminar.
 */
object CardZoom {
    /** Dónde está cada tarjeta en pantalla (la última vez que se dibujó). */
    val bounds = HashMap<String, Rect>()
    internal var anim by mutableStateOf<Zoom?>(null)

    internal class Zoom(val bitmap: ImageBitmap, val card: Rect, val opening: Boolean)

    /** Lanza la animación si se conoce la tarjeta y hay foto; si no, no hace nada (queda la transición de siempre). */
    fun start(tabId: String, bitmap: android.graphics.Bitmap?, opening: Boolean) {
        val card = bounds[tabId] ?: return
        if (bitmap == null || !AppMotion.enabled || card.width < 1f) return
        anim = Zoom(bitmap.asImageBitmap(), card, opening)
    }
}

/** Anota dónde quedó la tarjeta de [tabId] para que la página crezca desde ahí. */
fun Modifier.zoomSource(tabId: String): Modifier = onGloballyPositioned { CardZoom.bounds[tabId] = it.boundsInRoot() }

/** Capa de la animación: va encima de todo en la raíz de la app. */
@Composable
fun CardZoomOverlay() {
    val a = CardZoom.anim ?: return
    val p = remember(a) { Animatable(0f) }
    LaunchedEffect(a) {
        p.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f, visibilityThreshold = 0.002f))
        if (CardZoom.anim === a) CardZoom.anim = null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // t = 0 en la tarjeta, 1 a pantalla completa.
        val t = if (a.opening) p.value else 1f - p.value
        val left = lerp(a.card.left, 0f, t)
        val top = lerp(a.card.top, 0f, t)
        val right = lerp(a.card.right, w, t)
        val bottom = lerp(a.card.bottom, h, t)
        Image(
            a.bitmap, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(d) { (right - left).toDp() }, with(d) { (bottom - top).toDp() })
                .clip(RoundedCornerShape(with(d) { lerp(22f, 0f, t).dp })),
        )
    }
}

private val Float.dp get() = androidx.compose.ui.unit.Dp(this)
