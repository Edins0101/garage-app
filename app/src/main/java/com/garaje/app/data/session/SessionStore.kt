package com.garaje.app.data.session

import android.content.Context
import androidx.core.content.edit
import com.garaje.app.domain.repository.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Guarda la sesión (token + usuario) en el almacenamiento privado de la app,
 * para no pedir login cada vez que se abre.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    val token: String? get() = _session.value?.token

    fun save(session: Session) {
        prefs.edit { putString(KEY, json.encodeToString(Session.serializer(), session)) }
        _session.value = session
    }

    fun clear() {
        prefs.edit { remove(KEY) }
        _session.value = null
    }

    private fun load(): Session? =
        prefs.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString(Session.serializer(), raw) }.getOrNull()
        }

    private companion object {
        const val KEY = "session"
    }
}
