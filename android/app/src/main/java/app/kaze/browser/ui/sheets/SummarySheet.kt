package app.kaze.browser.ui.sheets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.kaze.browser.data.Tab
import app.kaze.browser.engine.Summarizer
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.theme.liftCard
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Resumen con IA de la página abierta. Sin clave, explica cómo conseguir una gratis. */
@Composable
fun SummarySheet(tab: Tab, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    var key by remember { mutableStateOf(app.settings.geminiKey) }
    var draft by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    if (key.isNotBlank()) LaunchedEffect(key, attempt) {
        result = null; error = null
        val text = suspendCancellableCoroutine { c -> app.pool.pageText(tab) { c.resume(it) } }
        if (text.isNullOrBlank()) { error = "Esta página no tiene texto que resumir (o aún está cargando)"; return@LaunchedEffect }
        runCatching { withContext(Dispatchers.IO) { Summarizer.summarize(key, tab.displayTitle, tab.url, text) } }
            .onSuccess { result = it }.onFailure { error = it.message ?: "No se pudo resumir" }
    }

    KazeSheet(onDismiss) {
        SheetTitle("Resumen", "OK", onDismiss)
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            when {
                key.isBlank() -> {
                    Text(
                        "El resumen usa Gemini con tu propia clave (gratis). El texto de la página se envía a Google para resumirlo. " +
                            "Crea la clave en Google AI Studio y pégala aquí.",
                        style = KazeType.subhead, color = k.ink,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextPill("Conseguir una clave", k.accent) { onDismiss(); onOpenUrl("https://aistudio.google.com/apikey") }
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = draft, onValueChange = { draft = it.trim() }, singleLine = true,
                        textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent),
                        modifier = Modifier.fillMaxWidth().liftCard(RoundedCornerShape(10.dp)).padding(10.dp),
                        decorationBox = { inner -> if (draft.isEmpty()) Text("Pega la clave (AIza…)", style = KazeType.body, color = k.ink55); inner() },
                    )
                    Spacer(Modifier.height(10.dp))
                    TextPill("Guardar y resumir", k.accent) { if (draft.isNotBlank()) { app.settings.geminiKey = draft; key = draft } }
                }
                error != null -> {
                    Text(error!!, style = KazeType.subhead, color = k.ink)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        TextPill("Reintentar", k.accent) { attempt++ }
                        Spacer(Modifier.padding(4.dp))
                        TextPill("Cambiar clave", k.ink55) { app.settings.geminiKey = ""; key = "" }
                    }
                }
                result == null -> {
                    Text("Leyendo la página…", style = KazeType.subhead, color = k.ink55)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(color = k.accent, modifier = Modifier.fillMaxWidth())
                }
                else -> Text(result!!.replace("**", ""), style = KazeType.body, color = k.ink)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
