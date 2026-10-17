package com.arianna.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.ui.theme.Palette
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.arianna.app.data.push.Notifications
import com.arianna.app.data.reminders.ReminderWorker
import com.arianna.app.ui.login.LockScreen
import com.arianna.app.ui.login.LoginScreen
import com.arianna.app.ui.navigation.AppNavHost
import com.arianna.app.ui.theme.AriannaTheme

// FragmentActivity: el diálogo de huella/rostro (BiometricPrompt) la necesita.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Íconos claros en las barras del sistema: el fondo de la app siempre es oscuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as AriannaApp).container
        val auth = container.auth
        val appLock = container.appLock
        setContent {
            AriannaTheme {
                val session by auth.session.collectAsStateWithLifecycle()
                val locked by appLock.locked.collectAsStateWithLifecycle()
                val userId = session?.user?.id
                when {
                    userId == null -> LoginScreen()
                    locked -> LockScreen(
                        userName = session?.user?.name,
                        onUnlock = { appLock.authenticate(this, "Desbloquear Arianna") },
                        onLogout = auth::logout,
                    )
                    // key: al cambiar de usuario se reinicia la navegación y sus ViewModels.
                    else -> key(userId) { MainContent(this) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as AriannaApp).container.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        (application as AriannaApp).container.appLock.onBackground()
    }
}

@Composable
private fun MainContent(activity: ComponentActivity) {
    val navController = rememberNavController()

    // Con launchMode=singleTask, los deep links que llegan con la app abierta
    // entran por onNewIntent: los reenviamos al NavController.
    DisposableEffect(navController) {
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    NotificationSetup(activity)
    val container = (activity.application as AriannaApp).container
    val userId = container.auth.session.collectAsStateWithLifecycle().value?.user?.id
    LaunchedEffect(userId) { userId?.let(container.preferencesSync::start) }
    val offline by container.offline.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        if (offline) OfflineBanner()
        // Con el aviso arriba, las pantallas ya no dejan el espacio de la barra de estado.
        Box(
            Modifier
                .weight(1f)
                .then(if (offline) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
        ) {
            AppNavHost(navController)
        }
    }
}

/** Franja arriba mientras se ven los datos guardados en el teléfono (no tapa los avisos de abajo). */
@Composable
private fun OfflineBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Palette.OnDark, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Sin conexión · ves lo último guardado", color = Palette.OnDark, fontSize = 13.sp)
    }
}

/**
 * Pide el permiso de notificaciones una vez (Android 13+), registra el teléfono en el backend
 * (push de listas compartidas) y programa la revisión diaria de recordatorios (avisos locales).
 */
@Composable
private fun NotificationSetup(activity: ComponentActivity) {
    val push = (activity.application as AriannaApp).container.push
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        push.sync()
        ReminderWorker.schedule(activity)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canNotify(activity)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            push.sync()
            ReminderWorker.schedule(activity)
        }
    }
}
