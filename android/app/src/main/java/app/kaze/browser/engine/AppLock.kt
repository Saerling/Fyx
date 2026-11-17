package app.kaze.browser.engine

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bloqueo de Fyx con huella, rostro o PIN (como el de Fir). Se cierra al salir de la app y pide
 * desbloqueo al volver. Usa el BiometricPrompt del sistema (Android 10+): sin librerías extra.
 */
object AppLock {
    /** La pantalla de bloqueo está puesta (tapa todo, páginas incluidas). */
    var locked by mutableStateOf(false)
    private var asking = false

    /** El teléfono tiene PIN, patrón o huella: sin eso no hay con qué bloquear. */
    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Pide huella/rostro/PIN; [onOk] solo si el dueño se identifica. */
    fun authenticate(context: Context, title: String, onOk: () -> Unit) {
        val activity = context.activity() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !available(activity) || asking) return
        asking = true
        prompt(activity, title, onOk)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun prompt(activity: Activity, title: String, onOk: () -> Unit) {
        val b = BiometricPrompt.Builder(activity).setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        }
        b.build().authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { asking = false; onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { asking = false }
        })
    }

    /** Quita el bloqueo tras identificarse. */
    fun unlock(context: Context) = authenticate(context, "Desbloquear Fyx") { locked = false }

    private fun Context.activity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }
}
