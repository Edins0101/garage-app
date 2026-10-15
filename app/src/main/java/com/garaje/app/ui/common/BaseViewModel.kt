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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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

    private val retries = MutableStateFlow(0)

    /** Vuelve a cargar los datos de la pantalla (botón "Reintentar"). */
    fun retry() {
        retries.value++
    }

    /**
     * Estado de pantalla con carga: empieza en LOADING, pasa a READY con los primeros datos
     * y, si la carga falla, a ERROR (con mensaje) en vez de quedarse cargando para siempre.
     * [setLoad] copia el estado con otro [Load]; [source] arma el flujo de datos de la pantalla.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun <T> loadingState(initial: T, setLoad: T.(Load) -> T, source: () -> Flow<T>): StateFlow<T> {
        var last = initial
        return retries.flatMapLatest { attempt ->
            source()
                .map { it.setLoad(Load.READY) }
                .onStart { if (attempt > 0) emit(last.setLoad(Load.LOADING)) }
                .onEach { last = it }
                .catch { e ->
                    notify(e.message ?: "No se pudo cargar")
                    emit(last.setLoad(Load.ERROR))
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
    }
}

/** Estado de carga de una pantalla. */
enum class Load { LOADING, READY, ERROR }

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
