package com.arianna.app.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.domain.model.JoinResult
import com.arianna.app.domain.repository.SharingRepository
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.components.CircleAvatar
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.TopBar
import com.arianna.app.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface JoinState {
    data object Loading : JoinState
    data class Joined(val result: JoinResult) : JoinState
    data class Failed(val message: String) : JoinState
}

class JoinViewModel(code: String, sharing: SharingRepository) : BaseViewModel() {
    private val _state = MutableStateFlow<JoinState>(JoinState.Loading)
    val state: StateFlow<JoinState> = _state.asStateFlow()

    init {
        launchSafely {
            _state.value = try {
                JoinState.Joined(sharing.joinByCode(code))
            } catch (e: Exception) {
                JoinState.Failed(e.message ?: "No se pudo usar el código")
            }
        }
    }
}

/** Se abre con el deep link arianna://join/CODIGO. */
@Composable
fun JoinScreen(code: String, onJoined: (JoinResult) -> Unit, onBack: () -> Unit) {
    val viewModel = appViewModel(key = "join-$code") { JoinViewModel(code, it.sharing) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        (state as? JoinState.Joined)?.let { onJoined(it.result) }
    }

    CosmicBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(title = "Unirme", overline = "Código $code", onBack = onBack)
            Spacer(Modifier.height(40.dp))
            CircleAvatar(Icons.Outlined.VpnKey, size = 88.dp)
            when (val current = state) {
                is JoinState.Failed -> {
                    Text(current.message, color = Palette.OnDark, textAlign = TextAlign.Center)
                    PillButton("Volver", onClick = onBack, light = true)
                }
                else -> CircularProgressIndicator(color = Palette.VioletBright)
            }
        }
    }
}
