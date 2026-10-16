package com.arianna.app.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.arianna.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.domain.repository.CodePurpose
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.components.CircleAvatar
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.GlassCard
import com.arianna.app.ui.components.GlassField
import com.arianna.app.ui.components.OtpInput
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.theme.Palette

private enum class Tab { LOGIN, REGISTER }

@Composable
fun LoginScreen() {
    val viewModel = appViewModel { LoginViewModel(it.auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)

    // Los campos viven aquí para conservarse al pasar de un paso a otro.
    var tab by rememberSaveable { mutableStateOf(Tab.LOGIN) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val goBack = {
        code = ""
        viewModel.goTo(if (state.step == AuthStep.RESET_CODE) AuthStep.RESET_EMAIL else AuthStep.FORM)
    }
    BackHandler(enabled = state.step != AuthStep.FORM, onBack = goBack)
    LaunchedEffect(state.codeRejections) { if (state.codeRejections > 0) code = "" }

    val passwordField: @Composable (String, String, (String) -> Unit, ImeAction, () -> Unit) -> Unit =
        { placeholder, value, onChange, ime, onDone ->
            GlassField(
                value = value,
                onValueChange = onChange,
                placeholder = placeholder,
                leadingIcon = Icons.Outlined.Lock,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
                onDone = onDone,
                trailing = {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = Palette.OnDarkSoft,
                        modifier = Modifier
                            .size(20.dp)
                            // Sin foco: "Siguiente" del teclado debe saltar al próximo campo.
                            .focusProperties { canFocus = false }
                            .clickable { showPassword = !showPassword },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

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
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    if (state.step != AuthStep.FORM) {
                        IconButton(onClick = goBack, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Palette.OnDark)
                        }
                    }
                }

                when (state.step) {
                    AuthStep.FORM -> {
                        Header(
                            icon = ImageVector.vectorResource(R.drawable.ic_brand),
                            title = if (tab == Tab.REGISTER) "Crea tu cuenta" else "Bienvenido",
                            subtitle = "Hola, soy Arianna. Te ayudo con tus pagos, listas y pendientes, y a compartirlos con los tuyos.",
                        )
                        GlassCard {
                            ModeTabs(tab) { tab = it }
                            Spacer(Modifier.height(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (tab == Tab.REGISTER) {
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
                                EmailField(email, { email = it }, ImeAction.Next)
                                if (tab == Tab.REGISTER) {
                                    passwordField("Contraseña (mín. $MIN_PASSWORD caracteres)", password, { password = it }, ImeAction.Next) {}
                                    passwordField("Repite la contraseña", confirm, { confirm = it }, ImeAction.Done) {
                                        viewModel.startRegister(name, email, password, confirm)
                                    }
                                } else {
                                    passwordField("Contraseña", password, { password = it }, ImeAction.Done) {
                                        viewModel.login(email, password)
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            if (tab == Tab.REGISTER) {
                                Action(
                                    loading = state.loading,
                                    text = "Continuar",
                                    enabled = name.isNotBlank() && email.isNotBlank() && password.isNotEmpty() && confirm.isNotEmpty(),
                                ) { viewModel.startRegister(name, email, password, confirm) }
                            } else {
                                Action(state.loading, "Entrar", email.isNotBlank() && password.isNotEmpty()) {
                                    viewModel.login(email, password)
                                }
                                Spacer(Modifier.height(12.dp))
                                LinkText("¿Olvidaste tu contraseña?") {
                                    code = ""
                                    viewModel.goTo(AuthStep.RESET_EMAIL)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Hint(
                            if (tab == Tab.REGISTER) {
                                "Te enviaremos un código para confirmar tu correo. Usa el correo con el que te invitan a las listas."
                            } else {
                                "¿Primera vez? Toca \"Crear cuenta\"."
                            },
                        )
                    }

                    AuthStep.VERIFY_REGISTER -> {
                        Header(
                            icon = Icons.Outlined.MarkEmailRead,
                            title = "Verifica tu correo",
                            subtitle = "Escribe el código de $CODE_LENGTH dígitos que enviamos a ${state.codeEmail}.",
                        )
                        GlassCard {
                            OtpInput(
                                value = code,
                                onValueChange = { code = it },
                                onComplete = { viewModel.confirmRegister(name, email, password, code) },
                            )
                            Spacer(Modifier.height(18.dp))
                            Action(state.loading, "Crear cuenta", code.length == CODE_LENGTH) {
                                viewModel.confirmRegister(name, email, password, code)
                            }
                            Spacer(Modifier.height(12.dp))
                            ResendRow(state.resendIn) { viewModel.resend(email, CodePurpose.REGISTER) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Hint("¿No llega? Revisa la carpeta de spam. El código vence en 10 minutos.")
                    }

                    AuthStep.RESET_EMAIL -> {
                        Header(
                            icon = Icons.Outlined.LockReset,
                            title = "Recupera tu cuenta",
                            subtitle = "Te enviaremos un código para crear una contraseña nueva.",
                        )
                        GlassCard {
                            EmailField(email, { email = it }, ImeAction.Done) { viewModel.startReset(email) }
                            Spacer(Modifier.height(18.dp))
                            Action(state.loading, "Enviar código", email.isNotBlank()) { viewModel.startReset(email) }
                        }
                    }

                    AuthStep.RESET_CODE -> {
                        Header(
                            icon = Icons.Outlined.LockReset,
                            title = "Nueva contraseña",
                            subtitle = "Escribe el código que enviamos a ${state.codeEmail} y tu nueva contraseña.",
                        )
                        GlassCard {
                            OtpInput(value = code, onValueChange = { code = it })
                            Spacer(Modifier.height(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                passwordField("Nueva contraseña (mín. $MIN_PASSWORD)", password, { password = it }, ImeAction.Next) {}
                                passwordField("Repite la contraseña", confirm, { confirm = it }, ImeAction.Done) {
                                    viewModel.confirmReset(email, code, password, confirm)
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Action(
                                state.loading,
                                "Cambiar contraseña",
                                code.length == CODE_LENGTH && password.isNotEmpty() && confirm.isNotEmpty(),
                            ) { viewModel.confirmReset(email, code, password, confirm) }
                            Spacer(Modifier.height(12.dp))
                            ResendRow(state.resendIn) { viewModel.resend(email, CodePurpose.RESET) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Hint("Si el correo tiene una cuenta, el código llega en unos segundos. Revisa también spam.")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(icon: ImageVector, title: String, subtitle: String) {
    CircleAvatar(icon, size = 84.dp)
    Spacer(Modifier.height(16.dp))
    Text(
        "ARIANNA",
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, letterSpacing = 4.sp),
        color = Palette.OnDarkSoft,
    )
    Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.OnDark, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = Palette.OnDarkSoft, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit, ime: ImeAction, onDone: () -> Unit = {}) {
    GlassField(
        value = value,
        onValueChange = onChange,
        placeholder = "correo@ejemplo.com",
        leadingIcon = Icons.Outlined.AlternateEmail,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ime),
        onDone = onDone,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.Action(loading: Boolean, text: String, enabled: Boolean, onClick: () -> Unit) {
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
        PillButton(text, onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ColumnScope.ResendRow(resendIn: Int, onResend: () -> Unit) {
    if (resendIn > 0) {
        Text(
            "Puedes reenviar el código en $resendIn s",
            color = Palette.OnDarkSoft,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    } else {
        LinkText("Reenviar código", onResend)
    }
}

@Composable
private fun ColumnScope.LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Palette.VioletBright,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Palette.OnDarkSoft, fontSize = 12.sp, textAlign = TextAlign.Center)
}

/** Selector "Entrar | Crear cuenta". */
@Composable
private fun ModeTabs(tab: Tab, onChange: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Glass)
            .padding(4.dp),
    ) {
        listOf(Tab.LOGIN to "Entrar", Tab.REGISTER to "Crear cuenta").forEach { (value, label) ->
            val selected = value == tab
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Palette.Violet else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Una sola línea: si no cabe (pantalla pequeña o letra grande), se achica.
                BasicText(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = if (selected) Palette.OnDark else Palette.OnDarkSoft,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}
