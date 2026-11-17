package app.kaze.browser.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Una pestaña que salió del mazo por llevar días sin verse; se puede volver a abrir. */
data class ArchivedTab(val url: String, val title: String, val spaceId: String, val archivedAt: Long)

/**
 * Archivo de pestañas (idea de Fir): las pestañas normales que nadie abre en X días se cierran solas y
 * quedan aquí. Menos pestañas = menos RAM y un mazo más limpio. Las fijadas, Essentials y privadas
 * nunca se archivan. Se guarda como JSON en un archivo (pueden ser cientos).
 */
class TabArchive(context: Context) {
    private val file = File(context.filesDir, "archivadas.json")
    val items = mutableStateListOf<ArchivedTab>().apply { addAll(read()) }

    /** Archiva las pestañas normales sin ver en [days] días (menos la abierta). Devuelve cuántas. */
    fun archiveStale(store: Store, days: Int, onClosed: (Tab) -> Unit = {}): Int {
        if (days <= 0) return 0
        val limit = System.currentTimeMillis() - days * 86_400_000L
        val stale = store.tabs.filter {
            it.kind == TabKind.NORMAL && !it.isPrivate && !it.isStart && it.id != store.selectedTabId &&
                it.lastUsed < limit && it.url.startsWith("http")
        }
        if (stale.isEmpty()) return 0
        val now = System.currentTimeMillis()
        stale.forEach { t ->
            items.removeAll { it.url == t.url }
            items.add(0, ArchivedTab(t.url, t.displayTitle, t.spaceId, now))
            store.close(t)
            onClosed(t)
        }
        while (items.size > MAX) items.removeAt(items.lastIndex)
        save()
        return stale.size
    }

    fun remove(item: ArchivedTab) { items.remove(item); save() }
    fun clear() { items.clear(); save() }

    private fun read(): List<ArchivedTab> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            ArchivedTab(o.getString("url"), o.optString("title"), o.optString("space"), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    /** Escritura atómica: primero a un temporal y luego se renombra (no se pierde el archivo a medias). */
    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("url", it.url).put("title", it.title).put("space", it.spaceId).put("at", it.archivedAt)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching { tmp.writeText(a.toString()); tmp.renameTo(file) }
    }

    private companion object { const val MAX = 500 }
}
