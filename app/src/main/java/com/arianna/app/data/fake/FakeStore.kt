package com.arianna.app.data.fake

import com.arianna.app.domain.model.Group
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.GarageDay
import com.arianna.app.domain.model.GarageSettings
import com.arianna.app.domain.model.Payment
import com.arianna.app.domain.model.Product
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderLog
import com.arianna.app.domain.model.ReminderSpace
import com.arianna.app.domain.model.ShareInvite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** "Base de datos" del servidor simulado. */
@Serializable
data class FakeDb(
    /** Contadores por día (el garaje es uno más). */
    val counters: List<FakeCounter> = emptyList(),
    /** true cuando el garaje de versiones anteriores ya pasó a [counters]. */
    val countersMigrated: Boolean = false,
    // Garaje de versiones anteriores (solo se leen una vez para crear "Mi garaje").
    val garageDays: List<GarageDay> = emptyList(),
    val garageSettings: GarageSettings = GarageSettings(),
    /** Último día rellenado por el marcado automático. */
    val autoFilledUntil: String? = null,
    val payments: List<Payment> = emptyList(),
    val lists: List<Checklist> = emptyList(),
    /** Catálogo de productos (todos del usuario local). */
    val products: List<Product> = emptyList(),
    val invites: List<ShareInvite> = emptyList(),
    /** Recordatorios (Pagos, Mi hogar, Mi carro). */
    val spaces: List<ReminderSpace> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    /** Historial por recordatorio, el más reciente primero. */
    val reminderLogs: Map<String, List<ReminderLog>> = emptyMap(),
    /** Día del mes original de cada recordatorio (para los meses cortos). */
    val anchorDays: Map<String, Int> = emptyMap(),
    /** Grupos (sin otros usuarios: los integrantes quedan pendientes). */
    val groups: List<Group> = emptyList(),
)

/**
 * Persiste el servidor simulado en un archivo JSON dentro del almacenamiento
 * privado de la app, para que los datos sobrevivan al cerrarla mientras no
 * exista backend. La primera vez carga los datos de ejemplo de [FakeSeed].
 */
class FakeStore(
    private val file: File,
    private val latencyMs: Long = 200,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow<FakeDb?>(null)

    /** Emite null hasta que se carga el archivo. */
    val db: StateFlow<FakeDb?> = state.asStateFlow()

    suspend fun load(): FakeDb = mutex.withLock { loadLocked() }

    /** Aplica un cambio atómico, simula la latencia de red y persiste. */
    suspend fun <T> update(transform: (FakeDb) -> Pair<FakeDb, T>): T {
        delay(latencyMs)
        return mutex.withLock {
            val (next, result) = transform(loadLocked())
            state.value = next
            withContext(Dispatchers.IO) { file.writeText(json.encodeToString(FakeDb.serializer(), next)) }
            result
        }
    }

    suspend fun simulateLatency() = delay(latencyMs)

    private suspend fun loadLocked(): FakeDb {
        state.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (file.exists()) {
                runCatching { json.decodeFromString(FakeDb.serializer(), file.readText()) }.getOrNull()
            } else {
                null
            }
        } ?: FakeSeed.create().also { seed ->
            withContext(Dispatchers.IO) { file.writeText(json.encodeToString(FakeDb.serializer(), seed)) }
        }
        state.value = loaded
        return loaded
    }
}
