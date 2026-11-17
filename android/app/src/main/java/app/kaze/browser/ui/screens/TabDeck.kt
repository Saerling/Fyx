package app.kaze.browser.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.kaze.browser.data.Tab
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mazo de pestañas (de Fir, como el selector de apps clásico de iOS): tarjetas superpuestas que se
 * deslizan bajo el dedo. Arriba cierra, abajo o un toque abre. Todo se puede cancelar: mientras el
 * dedo está puesto nada se cierra ni se abre; solo al soltar pasado el punto de no retorno.
 */
@Composable
internal fun TabDeck(
    tabs: List<Tab>, selectedId: String?, onOpen: (Tab) -> Unit, onClose: (Tab) -> Unit, onMenu: (Tab) -> Unit,
    height: Dp,
) {
    if (tabs.isEmpty()) return
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val start = tabs.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: tabs.lastIndex
    // Índice (fraccionario) de la tarjeta al frente.
    val pos = remember { Animatable(start.toFloat()) }
    LaunchedEffect(tabs.size) {
        if (pos.value > tabs.lastIndex) pos.animateTo(tabs.lastIndex.toFloat(), spring(0.86f, 380f))
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val cardW = maxWidth * 0.74f
        val cardH = height * 0.86f
        val stepPx = with(LocalDensity.current) { (cardW * 0.62f).toPx() }
        Box(
            Modifier.fillMaxSize().pointerInput(tabs.size) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x / stepPx
                        val target = (pos.value - v * 0.25f).roundToInt().coerceIn(0, tabs.lastIndex)
                        scope.launch { pos.animateTo(target.toFloat(), spring(0.86f, 380f), -v) }
                    },
                    onDragCancel = { scope.launch { pos.animateTo(pos.value.roundToInt().toFloat().coerceIn(0f, tabs.lastIndex.toFloat())) } },
                ) { change, dx ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // Resiste al pasar de la primera o la última tarjeta (como goma).
                    val over = pos.value < 0f || pos.value > tabs.lastIndex
                    val d = -dx / stepPx * (if (over) 0.35f else 1f)
                    scope.launch { pos.snapTo(pos.value + d) }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            tabs.forEachIndexed { i, tab ->
                val rel = i - pos.value
                // Visible solo cerca del frente: las lejanas no se dibujan (ahorra trabajo con muchas pestañas).
                if (rel < -4f || rel > 2.2f) return@forEachIndexed
                DeckCard(
                    tab = tab, rel = rel, stepPx = stepPx, width = cardW, height = cardH,
                    onOpen = { onOpen(tab) }, onMenu = { onMenu(tab) },
                    onClose = { haptic.performHapticFeedback(HapticFeedbackType.Confirm); onClose(tab) },
                    modifier = Modifier.zIndex(i.toFloat()),
                )
            }
        }
    }
}

@Composable
private fun DeckCard(
    tab: Tab, rel: Float, stepPx: Float, width: Dp, height: Dp,
    onOpen: () -> Unit, onMenu: () -> Unit, onClose: () -> Unit, modifier: Modifier,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val scope = rememberCoroutineScope()
    val dy = remember(tab.id) { Animatable(0f) }
    var thumb by remember(tab.id) { mutableStateOf<Bitmap?>(app.thumbnails.cached(tab.id)) }
    val version = app.thumbnails.version.intValue
    LaunchedEffect(tab.id, version) { thumb = withContext(Dispatchers.IO) { app.thumbnails.load(tab.id) } }
    val density = LocalDensity.current
    val closeAt = with(density) { 160.dp.toPx() }
    val openAt = with(density) { 110.dp.toPx() }
    // Las de atrás se esconden a la izquierda, juntas y un poco más pequeñas; las de después entran por la derecha.
    val x = if (rel < 0f) rel * stepPx * 0.28f else rel * stepPx
    val scale = if (rel < 0f) 1f + rel * 0.05f else 1f
    val lift = (-dy.value / closeAt).coerceIn(0f, 1f)

    Column(
        modifier
            .graphicsLayer {
                translationX = x
                translationY = dy.value
                scaleX = scale * (1f - lift * 0.08f)
                scaleY = scale * (1f - lift * 0.08f)
                alpha = 1f - lift * 0.6f
            }
            .width(width)
            .pointerInput(tab.id) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().y
                        when {
                            // Arriba con fuerza o pasada la marca: se va y se cierra (solo al soltar).
                            dy.value < -closeAt || v < -2200f -> scope.launch {
                                dy.animateTo(-closeAt * 3f, tween(180)); onClose(); dy.snapTo(0f)
                            }
                            // Abajo: abre la pestaña (como tirar de la tarjeta en Fir).
                            dy.value > openAt || v > 2200f -> scope.launch { onOpen(); dy.animateTo(0f, spring(0.86f, 420f)) }
                            // Si no, vuelve exactamente a su sitio: el gesto quedó cancelado.
                            else -> scope.launch { dy.animateTo(0f, spring(0.8f, 420f), v) }
                        }
                    },
                    onDragCancel = { scope.launch { dy.animateTo(0f) } },
                ) { change, d ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // Hacia abajo resiste (abrir pide menos recorrido y no debe salirse del mazo).
                    val step = if (dy.value + d > 0f) d * 0.45f else d
                    scope.launch { dy.snapTo(dy.value + step) }
                }
            }
            .pointerInput(tab.id) { detectTapGestures(onTap = { onOpen() }, onLongPress = { onMenu() }) },
    ) {
        Text(
            tab.displayTitle, style = KazeType.footnote, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp).graphicsLayer { alpha = (1f - abs(rel).coerceAtMost(1f)) * (1f - lift * 2f).coerceAtLeast(0f) },
        )
        Box(
            Modifier.fillMaxWidth().height(height - 28.dp).zoomSource(tab.id)
                .shadow(14.dp, RoundedCornerShape(22.dp), clip = false)
                .clip(RoundedCornerShape(22.dp)).background(k.card),
        ) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) }
        }
    }
}
