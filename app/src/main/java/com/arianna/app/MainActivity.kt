package com.arianna.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.arianna.app.data.push.Notifications
import com.arianna.app.data.reminders.ReminderWorker
import com.arianna.app.ui.login.LoginScreen
import com.arianna.app.ui.navigation.AppNavHost
import com.arianna.app.ui.theme.AriannaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Íconos claros en las barras del sistema: el fondo de la app siempre es oscuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val auth = (application as AriannaApp).container.auth
        setContent {
            AriannaTheme {
                val session by auth.session.collectAsStateWithLifecycle()
                val userId = session?.user?.id
                if (userId == null) {
                    LoginScreen()
                } else {
                    // key: al cambiar de usuario se reinicia la navegación y sus ViewModels.
                    key(userId) { MainContent(this) }
                }
            }
        }
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
    AppNavHost(navController)
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
