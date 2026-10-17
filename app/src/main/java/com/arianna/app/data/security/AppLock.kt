package com.arianna.app.data.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bloqueo con huella, rostro o el PIN del teléfono. La sesión ya queda guardada; esto solo pide
 * confirmar que es el dueño del teléfono al abrir la app o al volver después de [RELOCK_AFTER_MS].
 * Es una opción de este teléfono (no de la cuenta).
 */
class AppLock(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _locked = MutableStateFlow(_enabled.value && isAvailable())
    /** true = mostrar la pantalla de bloqueo en vez de la app. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundSince: Long? = null

    /** Ya se le ofreció activarlo después de iniciar sesión (solo se pregunta una vez). */
    var offered: Boolean
        get() = prefs.getBoolean(KEY_OFFERED, false)
        set(value) = prefs.edit { putBoolean(KEY_OFFERED, value) }

    /** El teléfono tiene huella, rostro o PIN configurado. */
    fun isAvailable(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
        if (!value) _locked.value = false
    }

    fun onBackground() {
        backgroundSince = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (_enabled.value && SystemClock.elapsedRealtime() - since >= RELOCK_AFTER_MS && isAvailable()) {
            _locked.value = true
        }
    }

    /** Al cerrar sesión no hay nada que proteger. */
    fun unlockForLogout() {
        _locked.value = false
    }

    /**
     * Muestra el diálogo del sistema. [onError] recibe el mensaje si falla de verdad (no cuando
     * la persona lo cancela).
     */
    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _locked.value = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!cancelled) onError(errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Usa tu huella, tu rostro o el PIN del teléfono")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_OFFERED = "offered"
        /** Huella/rostro o, si falla, el PIN/patrón del teléfono. */
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        /** Volver antes de esto (ej. después de tomar una foto) no vuelve a pedirla. */
        const val RELOCK_AFTER_MS = 2 * 60 * 1000L
    }
}

/** La actividad (FragmentActivity) detrás de un contexto de Compose. */
tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
