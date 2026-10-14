package com.garaje.app.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.garaje.app.GarajeApp
import com.garaje.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** ViewModel con canal de mensajes para mostrar errores/avisos en un Snackbar. */
abstract class BaseViewModel : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    protected fun notify(message: String) {
        _messages.tryEmit(message)
    }

    /** Lanza una corrutina y convierte cualquier error en un mensaje para el usuario. */
    protected fun launchSafely(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notify(e.message ?: "Ocurrió un error inesperado")
        }
    }
}

/** Crea un ViewModel con acceso al [AppContainer] de la aplicación. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: CreationExtras.(AppContainer) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as GarajeApp
            create(app.container)
        }
    },
)

/** Muestra en el Snackbar los mensajes emitidos por el ViewModel. */
@Composable
fun CollectMessages(viewModel: BaseViewModel, snackbar: SnackbarHostState) {
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
}
