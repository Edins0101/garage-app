package com.arianna.app.domain.repository

import com.arianna.app.domain.model.AccountPreferences
import com.arianna.app.domain.model.Group
import com.arianna.app.domain.model.GroupMember
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistItem
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.model.CountFrom
import com.arianna.app.domain.model.Counter
import com.arianna.app.domain.model.CounterDirection
import com.arianna.app.domain.model.CounterIcon
import com.arianna.app.domain.model.GarageDay
import com.arianna.app.domain.model.JoinResult
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.model.ReminderLog
import com.arianna.app.domain.model.ReminderSpace
import com.arianna.app.domain.model.RepeatUnit
import com.arianna.app.domain.model.GarageSettings
import com.arianna.app.domain.model.GarageSummary
import com.arianna.app.domain.model.Payment
import com.arianna.app.domain.model.Product
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.model.SharedMember
import com.arianna.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/*
 * Contratos de datos. La UI y los ViewModels solo dependen de estas interfaces;
 * la implementación (falsa o remota) se elige en di/AppContainer.kt.
 * Los métodos `observe*` emiten cada vez que cambian los datos.
 */

/** Para qué se pide el código por correo. */
@Serializable
enum class CodePurpose {
    @SerialName("register") REGISTER,
    @SerialName("reset") RESET,
}

/** Sesión iniciada: token de la API + usuario. */
@Serializable
data class Session(val token: String, val user: User)

interface AuthRepository {
    /** null = no hay sesión (se muestra el login). Persiste entre reinicios de la app. */
    val session: StateFlow<Session?>

    /** Envía al correo un código de 6 dígitos. Devuelve el mensaje para mostrar. */
    suspend fun requestCode(email: String, purpose: CodePurpose): String

    /** Crea la cuenta (con el código recibido por correo) e inicia sesión. */
    suspend fun register(name: String, email: String, password: String, code: String): User

    /** Cambia la contraseña con el código recibido por correo e inicia sesión. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): User

    /** Inicia sesión con correo y contraseña. */
    suspend fun login(email: String, password: String): User

    /** Login con el ID token de "Iniciar sesión con Google". */
    suspend fun loginGoogle(idToken: String): User

    fun logout()

    suspend fun currentUser(): User
}

/** Lo que el usuario llena al crear o editar un contador. */
data class CounterDraft(
    val name: String,
    val icon: CounterIcon,
    val direction: CounterDirection,
    val dailyRate: Double?,
    val autoMark: Boolean,
    /** Lista adjunta (null = ninguna). */
    val listId: String? = null,
)

/**
 * Contadores por día (garaje, niñera, expreso, días trabajados…). Cada uno tiene su calendario,
 * tarifa, cargos y pagos: se manejan con el [GarageRepository] que devuelve [counter].
 */
interface CountersRepository {
    /** Mis contadores con la cuenta hasta hoy, en el orden en que se crearon. */
    fun observeCounters(): Flow<List<Counter>>
    suspend fun refresh()

    suspend fun create(draft: CounterDraft): Counter
    suspend fun update(counter: Counter, draft: CounterDraft)
    suspend fun delete(counter: Counter)

    /** Días, cargos, pagos y tarifa de un contador. */
    fun counter(id: String): GarageRepository

    // ---- compartir (solo el dueño invita; un miembro puede salirse con su correo)
    suspend fun invite(counter: Counter, email: String, role: MemberRole): SharedMember
    suspend fun removeMember(counter: Counter, email: String)
    /** Código para ver el contador: quien lo usa entra como "solo ver". */
    suspend fun createInvite(counter: Counter): ShareInvite
}

/** Días, tarifa, cargos y pagos de UN contador (ver [CountersRepository.counter]). */
interface GarageRepository {
    fun observeDays(): Flow<List<GarageDay>>
    fun observeSettings(): Flow<GarageSettings>
    fun observeSummary(): Flow<GarageSummary>
    fun observePayments(): Flow<List<Payment>>
    suspend fun refresh()

    /** Crea o actualiza el día (nota opcional). Al crearlo toma la tarifa vigente. */
    suspend fun setDay(date: LocalDate, note: String? = null): GarageDay
    suspend fun setDays(dates: List<LocalDate>)
    suspend fun removeDay(date: LocalDate)
    /** Cambia la tarifa de un día puntual. */
    suspend fun setDayRate(date: LocalDate, rate: Double)

    /** Cambia la tarifa por defecto (y la aplica a los días sin tarifa) y/o el marcado automático. */
    suspend fun updateSettings(dailyRate: Double? = null, autoMark: Boolean? = null)

    suspend fun addCharge(date: LocalDate, description: String, amount: Double)
    suspend fun removeCharge(chargeId: String)

    suspend fun addPayment(date: LocalDate, amount: Double, note: String?)
    suspend fun removePayment(paymentId: String)
}

/**
 * Borrados que se pueden deshacer: lo borrado se oculta enseguida y se borra de verdad
 * pasados unos segundos ([UNDO_WINDOW_MS]), salvo que se llame a [undo] con la clave devuelta.
 */
interface UndoableDeletes {
    fun deleteListLater(id: String, onError: (String) -> Unit = {}): String
    fun removeItemLater(listId: String, itemId: String, onError: (String) -> Unit = {}): String
    fun undo(key: String)

    companion object {
        /** Un poco más que el Snackbar largo (10 s), para que el botón nunca llegue tarde. */
        const val UNDO_WINDOW_MS = 11_000L
    }
}

interface ListsRepository {
    fun observeLists(): Flow<List<Checklist>>
    fun observeList(id: String): Flow<Checklist?>
    suspend fun refresh()

    suspend fun createList(title: String, kind: ChecklistKind): Checklist
    suspend fun renameList(id: String, title: String)
    suspend fun deleteList(id: String)

    suspend fun addItem(listId: String, text: String, quantity: String? = null, section: String? = null): ChecklistItem
    /** Mueve el elemento a otra sección; null lo deja sin sección. */
    suspend fun setItemSection(listId: String, itemId: String, section: String?)
    suspend fun updateItem(
        listId: String,
        itemId: String,
        text: String? = null,
        quantity: String? = null,
        done: Boolean? = null,
    )
    suspend fun removeItem(listId: String, itemId: String)
    suspend fun clearCompleted(listId: String)

    // ---- Catálogo y fotos (docs/PLAN-FOTOS.md). El catálogo es el del dueño de la lista.

    /** Autocompletar: productos del catálogo que coinciden con lo escrito. */
    suspend fun searchProducts(listId: String, query: String): List<Product>

    /** Foto del producto (se ve en todas las listas): JPEG ya comprimido o URL https. */
    suspend fun setProductImage(listId: String, productId: String, photo: PhotoInput)
    suspend fun removeProductImage(listId: String, productId: String)

    /** Foto propia de un elemento (reemplaza la del producto solo ahí). */
    suspend fun setItemImage(listId: String, itemId: String, jpeg: ByteArray)
    suspend fun removeItemImage(listId: String, itemId: String)
}

/** De dónde viene una foto nueva. */
sealed interface PhotoInput {
    /** Foto de cámara o galería, ya comprimida en JPEG. */
    class Jpeg(val bytes: ByteArray) : PhotoInput

    /** Enlace a una imagen en internet (https). */
    data class Url(val url: String) : PhotoInput
}

interface SharingRepository {
    suspend fun invite(listId: String, email: String, role: MemberRole): SharedMember
    suspend fun removeMember(listId: String, email: String)
    /** Reutiliza una invitación vigente si existe. */
    suspend fun createInvite(listId: String): ShareInvite
    /** Une al usuario actual a la lista o sección del código; dice cuál para abrirla. */
    suspend fun joinByCode(code: String): JoinResult
}

/** Lo que el usuario llena al crear o editar un recordatorio. */
data class ReminderDraft(
    val title: String,
    val amount: Double?,
    /** null + null = una sola vez. */
    val every: Int?,
    val unit: RepeatUnit?,
    val nextDue: LocalDate,
    val notifyDaysBefore: Int,
    val note: String?,
    val countFrom: CountFrom,
    /** Mi carro: cada tantos km (opcional). */
    val everyKm: Int? = null,
    /** Rango: se repite hasta este día (incluido). null = sin fin. */
    val endsOn: LocalDate? = null,
    /** Hora a la que vence; null = sin hora (aviso de la mañana). */
    val dueTime: LocalTime? = null,
    /** Con hora: minutos de anticipación del aviso. */
    val alertMinutes: Int? = null,
    /** Lista adjunta (null = ninguna). */
    val listId: String? = null,
)

/**
 * Recordatorios (Pagos, Mi hogar, Mi carro) agrupados en secciones que se comparten completas.
 * Ver docs/PLAN-RECORDATORIOS.md.
 */
interface RemindersRepository {
    /** Los de mis secciones y de las compartidas conmigo, por fecha. [kind] null = todas las categorías. */
    fun observeReminders(kind: ReminderKind? = null): Flow<List<Reminder>>
    fun observeSpaces(kind: ReminderKind? = null): Flow<List<ReminderSpace>>
    suspend fun refresh()

    /** Lo crea en [spaceId] o, si es null, en la sección propia. */
    suspend fun create(kind: ReminderKind, draft: ReminderDraft, spaceId: String? = null): Reminder
    suspend fun update(reminder: Reminder, draft: ReminderDraft)
    suspend fun delete(reminder: Reminder)

    /** Ya pagué / hecho: guarda el historial y pasa al siguiente vencimiento. Devuelve cómo quedó. */
    suspend fun markDone(reminder: Reminder, amount: Double?, note: String?, odometer: Int? = null): Reminder
    /** Deshace la última marca. */
    suspend fun undo(reminder: Reminder)
    suspend fun history(reminder: Reminder): List<ReminderLog>

    suspend fun inviteToSpace(space: ReminderSpace, email: String, role: MemberRole): SharedMember
    suspend fun removeSpaceMember(space: ReminderSpace, email: String)
    suspend fun createSpaceInvite(space: ReminderSpace): ShareInvite
}

/** Error de datos con mensaje listo para mostrar al usuario. */
class DataException(
    message: String,
    val kind: Kind = Kind.UNKNOWN,
) : Exception(message) {
    enum class Kind { NOT_FOUND, INVALID, FORBIDDEN, UNAUTHORIZED, NETWORK, UNKNOWN }
}

/** Qué se comparte con un grupo. */
sealed interface ShareTarget {
    val id: String

    data class ListTarget(override val id: String) : ShareTarget
    data class SpaceTarget(override val id: String) : ShareTarget
    data class CounterTarget(override val id: String) : ShareTarget
}

/** Grupos (Familia, Roomies…): se comparte una lista, sección o contador con todo el grupo. */
interface GroupsRepository {
    /** Mis grupos (primero los que creé) con sus integrantes y lo compartido. */
    fun observeGroups(): Flow<List<Group>>
    suspend fun refresh()

    suspend fun create(name: String): Group
    suspend fun rename(group: Group, name: String)
    suspend fun delete(group: Group)

    /** Agrega por correo (solo quien creó el grupo). Si ya tiene cuenta, entra de inmediato. */
    suspend fun addMember(group: Group, email: String): GroupMember
    /** Quien creó el grupo quita a cualquiera; un integrante puede salirse con su correo. */
    suspend fun removeMember(group: Group, email: String)
    suspend fun createInvite(group: Group): ShareInvite

    /** Comparte con el grupo con ese permiso; [role] null deja de compartir. */
    suspend fun share(target: ShareTarget, group: Group, role: MemberRole?)
}

/** Inicio y color guardados en la cuenta (GET/PUT /me/preferences). */
interface PreferencesRepository {
    suspend fun load(): AccountPreferences

    /** Lo que venga en null queda como estaba en el servidor. */
    suspend fun save(preferences: AccountPreferences)
}
