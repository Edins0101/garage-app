package com.garaje.app.data.prefs

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Secciones que puede mostrar el inicio. */
@Serializable
enum class HomeSection { GARAGE, SHOPPING, TASKS }

/**
 * Cómo quiere el usuario su pantalla de inicio.
 * `configured = false` → todavía no eligió (se le pregunta al entrar).
 */
@Serializable
data class HomePrefs(
    val order: List<HomeSection> = HomeSection.entries,
    val hidden: Set<HomeSection> = emptySet(),
    val configured: Boolean = false,
) {
    val visible: List<HomeSection> get() = order.filterNot { it in hidden }
    val primary: HomeSection get() = visible.firstOrNull() ?: HomeSection.GARAGE

    /** Pone [section] primero y deja las demás en su orden. */
    fun withPrimary(section: HomeSection) = copy(
        order = listOf(section) + order.filterNot { it == section },
        hidden = hidden - section,
        configured = true,
    )

    fun moved(section: HomeSection, delta: Int): HomePrefs {
        val index = order.indexOf(section)
        val target = (index + delta).coerceIn(0, order.lastIndex)
        if (index == target) return this
        val next = order.toMutableList().apply { add(target, removeAt(index)) }
        return copy(order = next, configured = true)
    }

    /** Oculta o muestra la sección; siempre queda al menos una visible. */
    fun toggled(section: HomeSection): HomePrefs {
        val next = if (section in hidden) hidden - section else hidden + section
        return if (next.size >= HomeSection.entries.size) this else copy(hidden = next, configured = true)
    }
}

/** Guarda las preferencias del inicio en el teléfono, separadas por usuario. */
class HomePrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("home_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val flows = mutableMapOf<String, MutableStateFlow<HomePrefs>>()

    fun observe(userId: String): StateFlow<HomePrefs> = flow(userId).asStateFlow()

    fun save(userId: String, value: HomePrefs) {
        prefs.edit { putString(userId, json.encodeToString(HomePrefs.serializer(), value)) }
        flow(userId).value = value
    }

    @Synchronized
    private fun flow(userId: String): MutableStateFlow<HomePrefs> = flows.getOrPut(userId) {
        val stored = prefs.getString(userId, null)?.let {
            runCatching { json.decodeFromString(HomePrefs.serializer(), it) }.getOrNull()
        }
        MutableStateFlow(stored ?: HomePrefs())
    }
}
