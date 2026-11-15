/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent

/** Recibe los enlaces de otras apps: si piden una pestaña personalizada se abre aquí mismo; si no, en Fyx. */
class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val custom = intent.hasExtra(CustomTabsIntent.EXTRA_SESSION)
        // Intent nuevo y limpio: solo la dirección y los extras (color de la barra, etc.). Las banderas de quien
        // llama (varias tareas, documentos nuevos, permisos de lectura…) no se copian: podían abrir Fyx duplicado.
        val forward = Intent(Intent.ACTION_VIEW, intent.data).setClass(this, if (custom) CustomTabActivity::class.java else MainActivity::class.java)
        runCatching { intent.extras?.let(forward::putExtras) }
        if (!custom) forward.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(forward) }
        finish()
    }
}
