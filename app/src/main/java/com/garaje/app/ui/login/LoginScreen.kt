package com.garaje.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garaje.app.domain.repository.AuthRepository
import com.garaje.app.ui.common.BaseViewModel
import com.garaje.app.ui.common.CollectMessages
import com.garaje.app.ui.common.appViewModel
import com.garaje.app.ui.components.CircleAvatar
import com.garaje.app.ui.components.CosmicBackground
import com.garaje.app.ui.components.GlassCard
import com.garaje.app.ui.components.GlassField
import com.garaje.app.ui.components.PillButton
import com.garaje.app.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MIN_PASSWORD = 8
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

class LoginViewModel(private val auth: AuthRepository) : BaseViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Al tener éxito la sesión cambia y MainActivity muestra la app sola. */
    fun login(email: String, password: String) {
        val error = when {
            !EMAIL_REGEX.matches(email.trim()) -> "Escribe un correo válido"
            password.isEmpty() -> "Escribe tu contraseña"
            else -> null
        }
        submit(error) { auth.login(email, password) }
    }

    fun register(name: String, email: String, password: String, confirm: String) {
        val error = when {
            name.isBlank() -> "Escribe tu nombre"
            !EMAIL_REGEX.matches(email.trim()) -> "Escribe un correo válido"
            password.length < MIN_PASSWORD -> "La contraseña debe tener al menos $MIN_PASSWORD caracteres"
            password != confirm -> "Las contraseñas no coinciden"
            else -> null
        }
        submit(error) { auth.register(name, email, password) }
    }

    private fun submit(validationError: String?, action: suspend () -> Unit) {
        if (_loading.value) return
        if (validationError != null) {
            notify(validationError)
            return
        }
        _loading.value = true
        launchSafely {
            try {
                action()
            } finally {
                _loading.value = false
            }
        }
    }
}

private enum class Mode { LOGIN, REGISTER }

@Composable
fun LoginScreen() {
    val viewModel = appViewModel { LoginViewModel(it.auth) }
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)

    var mode by rememberSaveable { mutableStateOf(Mode.LOGIN) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val isRegister = mode == Mode.REGISTER
    val submit = {
        if (isRegister) viewModel.register(name, email, password, confirm) else viewModel.login(email, password)
    }
    val passwordTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()

    CosmicBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding().imePadding()) },
        ) { _ ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                CircleAvatar(Icons.Outlined.DirectionsCar, size = 88.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "GARAJE",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, letterSpacing = 4.sp),
                    color = Palette.OnDarkSoft,
                )
                Text(
                    if (isRegister) "Crea tu cuenta" else "Bienvenido",
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.OnDark,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tu calendario del garaje y listas compartidas, sincronizados en la nube.",
                    color = Palette.OnDarkSoft,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                GlassCard {
                    ModeTabs(mode = mode, onChange = { mode = it })
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isRegister) {
                            GlassField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = "Tu nombre",
                                leadingIcon = Icons.Outlined.Person,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        GlassField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "correo@ejemplo.com",
                            leadingIcon = Icons.Outlined.AlternateEmail,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        GlassField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = if (isRegister) "Contraseña (mín. $MIN_PASSWORD caracteres)" else "Contraseña",
                            leadingIcon = Icons.Outlined.Lock,
                            visualTransformation = passwordTransformation,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (isRegister) ImeAction.Next else ImeAction.Done,
                            ),
                            onDone = submit,
                            trailing = {
                                Icon(
                                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                    tint = Palette.OnDarkSoft,
                                    modifier = Modifier
                                        .size(20.dp)
                                        // Sin foco: "Siguiente" del teclado debe saltar al campo de confirmación.
                                        .focusProperties { canFocus = false }
                                        .clickable { showPassword = !showPassword },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (isRegister) {
                            GlassField(
                                value = confirm,
                                onValueChange = { confirm = it },
                                placeholder = "Repite la contraseña",
                                leadingIcon = Icons.Outlined.Lock,
                                visualTransformation = passwordTransformation,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                onDone = submit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(color = Palette.VioletBright, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Conectando… si el servidor estaba dormido puede tardar hasta un minuto.",
                                color = Palette.OnDarkSoft,
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        val ready = email.isNotBlank() && password.isNotEmpty() &&
                            (!isRegister || (name.isNotBlank() && confirm.isNotEmpty()))
                        PillButton(
                            if (isRegister) "Crear cuenta" else "Entrar",
                            onClick = submit,
                            enabled = ready,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (isRegister) {
                        "Usa el correo con el que te invitan a las listas: al crear la cuenta verás las que ya te compartieron."
                    } else {
                        "¿Primera vez? Toca \"Crear cuenta\"."
                    },
                    color = Palette.OnDarkSoft,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Selector "Entrar | Crear cuenta". */
@Composable
private fun ModeTabs(mode: Mode, onChange: (Mode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Glass)
            .padding(4.dp),
    ) {
        listOf(Mode.LOGIN to "Entrar", Mode.REGISTER to "Crear cuenta").forEach { (value, label) ->
            val selected = value == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Palette.Violet else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Palette.OnDark else Palette.OnDarkSoft,
                )
            }
        }
    }
}
