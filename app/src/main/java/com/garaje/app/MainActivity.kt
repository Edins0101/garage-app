package com.garaje.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.navigation.compose.rememberNavController
import com.garaje.app.ui.navigation.AppNavHost
import com.garaje.app.ui.theme.GarajeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Íconos claros en las barras del sistema: el fondo de la app siempre es oscuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            GarajeTheme {
                val navController = rememberNavController()

                // Con launchMode=singleTask, los deep links que llegan con la app abierta
                // entran por onNewIntent: los reenviamos al NavController.
                DisposableEffect(navController) {
                    val listener = Consumer<android.content.Intent> { intent ->
                        navController.handleDeepLink(intent)
                    }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                AppNavHost(navController)
            }
        }
    }
}
