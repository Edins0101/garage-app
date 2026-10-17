package com.arianna.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.data.security.AppLock
import com.arianna.app.data.security.findFragmentActivity
import com.arianna.app.ui.theme.Palette

/** Activar o quitar el bloqueo con huella o rostro. */
@Composable
fun SecurityDialog(appLock: AppLock, onMessage: (String) -> Unit, onDismiss: () -> Unit) {
    val enabled by appLock.enabled.collectAsStateWithLifecycle()
    val enable = rememberEnableLock(appLock, onMessage)
    val available = appLock.isAvailable()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text("Seguridad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.toggleable(
                        value = enabled,
                        enabled = available,
                        role = Role.Switch,
                        onValueChange = { on -> if (on) enable() else appLock.setEnabled(false) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = Palette.Violet)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Entrar con huella o rostro", color = Palette.Ink)
                        Text("Se pide al abrir Arianna. También sirve el PIN del teléfono.", fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = null,
                        enabled = available,
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Violet),
                    )
                }
                if (!available) {
                    Text(
                        "Primero configura una huella, tu rostro o un PIN en los ajustes de seguridad del teléfono.",
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo", color = Palette.Violet) }
        },
    )
}

/** Después de iniciar sesión, se ofrece una vez activarlo (si el teléfono lo permite). */
@Composable
fun OfferLockDialog(appLock: AppLock, onMessage: (String) -> Unit, onDismiss: () -> Unit) {
    val enable = rememberEnableLock(appLock, onMessage)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        icon = { Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = Palette.Violet) },
        title = { Text("¿Entrar con tu huella o rostro?") },
        text = { Text("Tu sesión ya queda guardada. Con esto, solo tú podrás abrir Arianna en este teléfono. Puedes cambiarlo cuando quieras en Seguridad.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                enable()
            }) { Text("Activar", color = Palette.Violet) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ahora no", color = Palette.InkSoft) }
        },
    )
}

/** Confirma con la huella antes de activar (así se sabe que funciona). */
@Composable
private fun rememberEnableLock(appLock: AppLock, onMessage: (String) -> Unit): () -> Unit {
    val activity = LocalContext.current.findFragmentActivity()
    return {
        if (activity != null) {
            appLock.authenticate(
                activity,
                title = "Activar huella o rostro",
                onSuccess = {
                    appLock.setEnabled(true)
                    onMessage("Listo: Arianna pedirá tu huella o rostro al abrirse")
                },
                onError = onMessage,
            )
        }
    }
}
