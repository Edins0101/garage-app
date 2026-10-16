package com.arianna.app.ui.login

import androidx.lifecycle.viewModelScope
import com.arianna.app.domain.repository.AuthRepository
import com.arianna.app.domain.repository.CodePurpose
import com.arianna.app.ui.common.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MIN_PASSWORD = 8
const val CODE_LENGTH = 6
private const val RESEND_SECONDS = 60
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Pasos de la pantalla de acceso. */
enum class AuthStep {
    /** Pestañas "Entrar" / "Crear cuenta". */
    FORM,
    /** Código enviado para crear la cuenta. */
    VERIFY_REGISTER,
    /** Pedir el correo para recuperar la contraseña. */
    RESET_EMAIL,
    /** Código + nueva contraseña. */
    RESET_CODE,
}

data class LoginUiState(
    val step: AuthStep = AuthStep.FORM,
    val loading: Boolean = false,
    /** Segundos que faltan para poder reenviar el código (0 = ya se puede). */
    val resendIn: Int = 0,
    /** Correo al que se envió el último código. */
    val codeEmail: String = "",
    /** Aumenta cada vez que el servidor rechaza un código (la pantalla vacía las casillas). */
    val codeRejections: Int = 0,
)

class LoginViewModel(private val auth: AuthRepository) : BaseViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var countdown: Job? = null

    fun goTo(step: AuthStep) = _state.update { it.copy(step = step) }

    /** Al tener éxito la sesión cambia y MainActivity muestra la app sola. */
    fun login(email: String, password: String) = submit(
        when {
            !EMAIL_REGEX.matches(email.trim()) -> "Escribe un correo válido"
            password.isEmpty() -> "Escribe tu contraseña"
            else -> null
        },
    ) { auth.login(email, password) }

    /** Paso 1 de crear cuenta: valida el formulario y envía el código. */
    fun startRegister(name: String, email: String, password: String, confirm: String) = submit(
        when {
            name.isBlank() -> "Escribe tu nombre"
            !EMAIL_REGEX.matches(email.trim()) -> "Escribe un correo válido"
            password.length < MIN_PASSWORD -> "La contraseña debe tener al menos $MIN_PASSWORD caracteres"
            password != confirm -> "Las contraseñas no coinciden"
            else -> null
        },
    ) { sendCode(email, CodePurpose.REGISTER, AuthStep.VERIFY_REGISTER) }

    /** Paso 2 de crear cuenta. */
    fun confirmRegister(name: String, email: String, password: String, code: String) = submit(
        if (code.length != CODE_LENGTH) "Escribe los $CODE_LENGTH dígitos del código" else null,
        onFailure = ::codeRejected,
    ) { auth.register(name, email, password, code) }

    /** Paso 1 de recuperar contraseña. */
    fun startReset(email: String) = submit(
        if (!EMAIL_REGEX.matches(email.trim())) "Escribe un correo válido" else null,
    ) { sendCode(email, CodePurpose.RESET, AuthStep.RESET_CODE) }

    /** Paso 2 de recuperar contraseña. */
    fun confirmReset(email: String, code: String, password: String, confirm: String) = submit(
        when {
            code.length != CODE_LENGTH -> "Escribe los $CODE_LENGTH dígitos del código"
            password.length < MIN_PASSWORD -> "La contraseña debe tener al menos $MIN_PASSWORD caracteres"
            password != confirm -> "Las contraseñas no coinciden"
            else -> null
        },
        onFailure = ::codeRejected,
    ) { auth.resetPassword(email, code, password) }

    fun resend(email: String, purpose: CodePurpose) = submit(
        if (_state.value.resendIn > 0) "Espera ${_state.value.resendIn} s para reenviar" else null,
    ) { sendCode(email, purpose, _state.value.step) }

    private suspend fun sendCode(email: String, purpose: CodePurpose, next: AuthStep) {
        val message = auth.requestCode(email, purpose)
        _state.update { it.copy(step = next, codeEmail = email.trim().lowercase()) }
        notify(message)
        startCountdown()
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (seconds in RESEND_SECONDS downTo 0) {
                _state.update { it.copy(resendIn = seconds) }
                if (seconds > 0) delay(1_000)
            }
        }
    }

    private fun codeRejected() = _state.update { it.copy(codeRejections = it.codeRejections + 1) }

    private fun submit(validationError: String?, onFailure: () -> Unit = {}, action: suspend () -> Unit) {
        if (_state.value.loading) return
        if (validationError != null) {
            notify(validationError)
            return
        }
        _state.update { it.copy(loading = true) }
        launchSafely {
            try {
                action()
            } catch (e: Exception) {
                if (e !is CancellationException) onFailure()
                throw e
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
