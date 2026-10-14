package com.garaje.app.ui.login

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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

class LoginViewModel(private val auth: AuthRepository) : BaseViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Al tener éxito, la sesión cambia y MainActivity muestra la app sola. */
    fun loginDev(email: String, name: String) {
        if (_loading.value) return
        if (!email.contains("@") || name.isBlank()) {
            notify("Escribe tu nombre y un correo válido")
            return
        }
        _loading.value = true
        launchSafely {
            try {
                auth.loginDev(email, name)
            } finally {
                _loading.value = false
            }
        }
    }
}

@Composable
fun LoginScreen() {
    val viewModel = appViewModel { LoginViewModel(it.auth) }
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    val submit = { viewModel.loginDev(email, name) }

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
                Spacer(Modifier.height(72.dp))
                CircleAvatar(Icons.Outlined.DirectionsCar, size = 96.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    "GARAJE",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, letterSpacing = 4.sp),
                    color = Palette.OnDarkSoft,
                )
                Text("Bienvenido", style = MaterialTheme.typography.displaySmall, color = Palette.OnDark)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tu calendario del garaje y listas compartidas, sincronizados en la nube.",
                    color = Palette.OnDarkSoft,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(36.dp))

                GlassCard {
                    Text("INICIAR SESIÓN", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = Palette.OnDark)
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        GlassField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "correo@ejemplo.com",
                            leadingIcon = Icons.Outlined.AlternateEmail,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done,
                            ),
                            onDone = submit,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                        PillButton(
                            "Entrar",
                            onClick = submit,
                            enabled = name.isNotBlank() && email.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Usa el mismo correo con el que te invitan a las listas compartidas.",
                    color = Palette.OnDarkSoft,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
