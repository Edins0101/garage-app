package com.arianna.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.theme.Palette

/** Se muestra en vez de la app mientras está bloqueada con huella o rostro. */
@Composable
fun LockScreen(userName: String?, onUnlock: () -> Unit, onLogout: () -> Unit) {
    // Al aparecer, pide la huella enseguida.
    LaunchedEffect(Unit) { onUnlock() }
    CosmicBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Palette.Violet),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = Palette.OnDark, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                userName?.let { "Hola, ${it.substringBefore(' ')}" } ?: "Arianna",
                style = MaterialTheme.typography.displaySmall,
                color = Palette.OnDark,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Arianna está bloqueada. Usa tu huella, tu rostro o el PIN del teléfono para entrar.",
                color = Palette.OnDarkSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            PillButton("Desbloquear", onClick = onUnlock, icon = Icons.Outlined.Fingerprint, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLogout) { Text("Entrar con otra cuenta", color = Palette.OnDarkSoft) }
        }
    }
}
