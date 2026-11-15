package app.kaze.browser.ui

// Biblioteca de animaciones y funciones de Fyx: qué hace cada interruptor, dónde actúa y su vista previa con las
// piezas reales del navegador (cada previa fuerza su efecto con FxForzar, sin tocar el ajuste guardado).

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaze.browser.fx.*
import app.kaze.browser.ui.components.*
import app.kaze.browser.ui.screens.TabCard
import app.kaze.browser.ui.screens.TabsButton
import app.kaze.browser.ui.sheets.Empty
import app.kaze.browser.ui.sheets.KazeSheet
import app.kaze.browser.ui.sheets.KazeSwitch
import app.kaze.browser.ui.sheets.MenuActions
import app.kaze.browser.ui.sheets.SheetTitle
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze

/** Los 12 estilos de diálogo (blocks.so Dialogs 01–12) aplicados a los diálogos reales de las páginas. */
fun Modifier.estiloDialogoFx(): Modifier = composed {
    if (!fxOn("dialogs")) return@composed Modifier
    val k = LocalKaze.current
    when (Fx.variante("dialogs")) {
        0 -> Modifier.hazBorde(28.dp, k.accent)
        1 -> Modifier.bordeBrillante(28.dp)
        2 -> Modifier.noise(.06f)
        3 -> Modifier.foco(k.ink)
        4 -> Modifier.hazBorde(28.dp, k.accent).noise(.05f)
        5 -> Modifier.aparecerDifuso()
        6 -> Modifier.halo(listOf(k.accent, k.ink, k.accent))
        7 -> Modifier.bordeBrillante(28.dp, listOf(k.accent, k.ink, k.accent))
        8 -> Modifier.patron(Patron.Puntos, k.ink.copy(alpha = .10f))
        9 -> Modifier.patron(Patron.Cuadricula, k.ink.copy(alpha = .06f))
        10 -> Modifier.hazBorde(28.dp, k.accent, ms = 1400).aparecerDifuso()
        else -> Modifier.foco(k.accent).noise(.04f)
    }
}

/** Fondo animado de la página de inicio (16 fondos de opensourceui + Light Rays de Magic UI). */
@Composable
fun FondoInicioFx(modifier: Modifier = Modifier) {
    val k = LocalKaze.current
    if (fxOn("bgs")) { val ks = FondosGradiente.keys.toList(); FondoGradiente(FondosGradiente.getValue(ks[Fx.variante("bgs") % ks.size]), modifier.then(Modifier.background(k.paper))) }
    if (fxOn("lightrays")) RayosDeLuz(modifier, k.ink)
}

/** Lo que va encima del saludo en la página de inicio: «hola» a mano y reloj grande. */
@Composable
fun CabeceraInicioFx() {
    val k = LocalKaze.current
    if (fxOn("hello")) HolaApple(Modifier.size(150.dp, 58.dp).padding(bottom = 6.dp), k.ink)
    if (fxOn("ioslock")) {
        var hora by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()); while (true) { hora = f.format(java.util.Date()); kotlinx.coroutines.delay(20_000) } }
        Text(hora, style = KazeType.largeTitle.copy(fontSize = 64.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Thin), color = k.ink)
    }
}

/** Bienvenida (Onboarding) la primera vez tras activarla. */
@Composable
fun FxCapasFyx(@Suppress("UNUSED_PARAMETER") dark: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("fx", android.content.Context.MODE_PRIVATE) }
    var vista by remember { mutableStateOf(prefs.getBoolean("onboarding_visto", false)) }
    if (fxOn("onboarding") && !vista) BienvenidaFyx { vista = true; prefs.edit().putBoolean("onboarding_visto", true).apply() }
}

@Composable
fun BienvenidaFyx(onTerminar: () -> Unit) {
    val k = LocalKaze.current
    Box(Modifier.fillMaxSize().background(k.paper)) {
        CompositionLocalProvider(LocalContentColor provides k.ink) {
            Bienvenida(listOf(
                Triple("🦊", "Bienvenido a Fyx", "Un navegador rápido y ligero, con el motor de Firefox."),
                Triple("🗂", "Espacios y pestañas", "Ordena tus pestañas en espacios, como en Zen, y sincronízalas con el escritorio."),
                Triple("✨", "Hazlo tuyo", "En Ajustes → Personalización tienes colores, vidrio y esta biblioteca de animaciones."),
            ), onTerminar, color = k.accent)
        }
    }
}

/** Command Menu: las acciones del menú del navegador, escribiendo. */
fun comandosFyx(a: MenuActions): List<Pair<String, () -> Unit>> = listOf(
    "Nueva pestaña" to a.onNewTab, "Nueva pestaña privada" to a.onNewPrivate, "Favoritos y lista de lectura" to a.onLibrary,
    "Pestañas cerradas recientemente" to a.onRecent, "Descargas" to a.onDownloads, "Buscar en la página" to a.onFind,
    "Compartir" to a.onShare, "Guardar como PDF" to a.onSavePdf, "Mover a otro espacio" to a.onMove,
    "Personalizar inicio" to a.onCustomizeHome, "Borrar datos de navegación" to a.onClearData, "Ajustes" to a.onSettings,
)

@Composable private fun Variantes(id: String, nombres: List<String>) {
    val k = LocalKaze.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        nombres.forEachIndexed { n, t -> val sel = Fx.variante(id) == n
            Text(t, Modifier.clip(RoundedCornerShape(50)).background(if (sel) k.accent else k.lift).tap { Fx.elegirVariante(id, n) }.padding(horizontal = 12.dp, vertical = 7.dp), color = if (sel) k.paper else k.ink, style = KazeType.footnote) }
    }
}

val IdsFxFyx = listOf("blurfade", "noise", "ripple", "switch", "segmented", "empty", "dialogs", "wordrotate", "borderbeam", "nf_tabs",
    "tilt", "magiccard", "glare", "bgs", "lightrays", "ioslock", "hello", "hypertext", "cmd", "onboarding")

/** El catálogo de Fyx. Cada vista usa las piezas reales del navegador. */
fun catalogoFyx(): List<FxItem> {
    fun i(id: String, n: String, o: String, u: String, v: @Composable () -> Unit) = FxItem(id, n, o, u, vista = v)
    @Composable fun Capsula(content: @Composable BoxScope.() -> Unit) { val k = LocalKaze.current; Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(k.lift), contentAlignment = Alignment.Center, content = content) }
    return listOf(
        i("blurfade", "Blur Fade", "Magic UI", "Los grupos de Ajustes, Favoritos e historial aparecen subiendo con desenfoque") {
            var k by remember { mutableIntStateOf(0) }
            Column { key(k) { IosGroup { IosRow("Buscador", Icons.Outlined.Search); IosRow("Privacidad", Icons.Outlined.Lock, divider = false) } }; TextButton({ k++ }) { Text("Repetir") } }
        },
        i("noise", "Noise Texture", "Magic UI", "Grano fino en todas las hojas (Ajustes, menú, pestañas cerradas…)") { var v by remember { mutableStateOf(false) }; TextButton({ v = true }) { Text("Abrir una hoja") }
            if (v) KazeSheet({ v = false }) { SheetTitle("Hoja de ejemplo", "OK") { v = false }; IosGroup { IosRow("Descargas", Icons.Outlined.Download, divider = false) } } },
        i("ripple", "Ripple Button", "Magic UI", "Onda desde el dedo en los botones de la barra y los accesos") { GlassCircle(Icons.Outlined.Refresh, "Recargar", onClick = {}) },
        i("switch", "Switch", "coss ui", "Los interruptores de Ajustes con el estilo de coss") { var a by remember { mutableStateOf(true) }; IosGroup { IosRow("Bloquear rastreadores", Icons.Outlined.Shield, divider = false, trailing = { KazeSwitch(a) { a = it } }) } },
        i("segmented", "Segmented Control + Animated Background", "coss ui", "Los selectores de Personalización con indicador que se desliza") {
            val k = LocalKaze.current; var s by remember { mutableIntStateOf(0) }
            app.kaze.browser.ui.mochy.MiniSegmented(listOf("Redondas", "Continuas"), s, { s = it }, app.kaze.browser.ui.mochy.CustomiseColors(k.card, k.lift, k.ink, k.ink55, k.accent.copy(alpha = .35f), k.accent))
        },
        i("empty", "Empty", "coss ui", "El historial y las listas vacías con ícono y animación") { Empty("Aún no hay historial.") },
        i("dialogs", "Dialogs 01–12", "blocks.so", "Los avisos y preguntas de las páginas web con el estilo que elijas (12)") {
            var v by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Variantes("dialogs", (1..12).map { "Estilo %02d".format(it) }); TextButton({ v = true }) { Text("Abrir un aviso de página") } }
            if (v) AlertDialog({ v = false }, { TextButton({ v = false }) { Text("Aceptar") } }, Modifier.estiloDialogoFx(), { TextButton({ v = false }) { Text("Cancelar") } },
                title = { Text("ejemplo.com dice") }, text = { Text("¿Quieres salir de esta página? Los cambios no guardados se perderán.") })
        },
        i("wordrotate", "Word Rotate / Text Loop", "Magic UI", "El texto «Buscar o escribir dirección» de la barra va rotando ideas") { val k = LocalKaze.current; Capsula { TextoRotando(listOf("Buscar o escribir dirección", "Buscar noticias", "Buscar recetas", "Buscar videos", "Buscar manga"), KazeType.body, color = k.ink55) } },
        i("borderbeam", "Border Beam / Border Trail", "Magic UI", "Un haz de luz recorre la barra de dirección mientras carga la página") { val k = LocalKaze.current; Capsula { Box(Modifier.matchParentSize().hazBorde(23.dp, k.accent)); Text("fyx.app", style = KazeType.body, color = k.ink) } },
        i("nf_tabs", "Contador de pestañas", "NumberFlow", "El número del botón de pestañas rueda al abrir o cerrar") { var n by remember { mutableIntStateOf(3) }; Row(verticalAlignment = Alignment.CenterVertically) { TabsButton(n, {}, {}); TextButton({ n++ }) { Text("Abrir pestaña") }; TextButton({ n = (n - 1).coerceAtLeast(1) }) { Text("Cerrar") } } },
        i("tilt", "Tilt", "Motion Primitives", "Las tarjetas de la vista de pestañas se inclinan bajo el dedo") { TarjetaPestana() },
        i("magiccard", "Magic Card", "Magic UI", "Un foco de luz sigue al dedo sobre las tarjetas de pestañas") { TarjetaPestana() },
        i("glare", "Glare Hover", "Magic UI", "Destello que cruza la tarjeta de pestaña al tocarla") { TarjetaPestana() },
        i("bgs", "Fondos de la página de inicio", "opensourceui", "16 fondos animados para la página de inicio (Aurora, Midnight Mesh, Mint Lagoon…)") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Variantes("bgs", FondosGradiente.keys.toList()); Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))) { FondoInicioFx(Modifier.matchParentSize()) } }
        },
        i("lightrays", "Light Rays", "Magic UI", "Rayos de luz suaves sobre la página de inicio") { Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))) { FondoInicioFx(Modifier.matchParentSize()) } },
        i("ioslock", "iOS Digital Clock", "opensourceui", "Reloj grande sobre el saludo de la página de inicio") { CabeceraInicioFx() },
        i("hello", "Apple Hello Loader", "opensourceui", "«hola» escrito a mano al abrir la página de inicio") { var k by remember { mutableIntStateOf(0) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { key(k) { CabeceraInicioFx() }; TextButton({ k++ }) { Text("Repetir") } } },
        i("hypertext", "Hyper Text", "Magic UI", "El saludo de la página de inicio se forma barajando letras") { val k = LocalKaze.current; var n by remember { mutableIntStateOf(0) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { key(n) { TextoBarajado("Buenas noches", KazeType.largeTitle, color = k.ink) }; TextButton({ n++ }) { Text("Repetir") } } },
        i("cmd", "Command Menu 01–04", "blocks.so", "En el menú ⋯ aparece «Comandos»: escribe y ejecuta cualquier acción") { var v by remember { mutableStateOf(false) }; TextButton({ v = true }) { Text("Abrir comandos") }
            MenuComandos(v, listOf("Nueva pestaña", "Nueva pestaña privada", "Descargas", "Buscar en la página", "Guardar como PDF", "Ajustes").map { it to {} }, { v = false }, "Buscar una acción…") },
        i("onboarding", "Onboarding 01–07", "blocks.so", "Bienvenida de 3 pasos la próxima vez que abras Fyx") { var v by remember { mutableStateOf(false) }; TextButton({ v = true }) { Text("Ver la bienvenida") }
            if (v) androidx.compose.ui.window.Dialog({ v = false }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) { BienvenidaFyx { v = false } } },
    )
}

/** Una tarjeta real de la vista de pestañas (con la pestaña actual). */
@Composable
private fun TarjetaPestana() {
    val app = LocalApp.current
    val tab = app.store.tabs.firstOrNull()
    if (tab == null) { Text("Abre una pestaña para ver aquí su tarjeta.", color = LocalKaze.current.ink55); return }
    Box(Modifier.width(170.dp)) { TabCard(tab = tab, selected = true, onOpen = {}, onMenu = {}, onClose = {}) }
}
