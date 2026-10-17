package com.arianna.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * Modelos de dominio. Son los mismos JSON que esperamos del backend (docs/API.md),
 * así que pasar de los repositorios falsos a los remotos no toca la UI.
 * Las fechas de día viajan como texto "yyyy-MM-dd" y los timestamps en ISO-8601.
 */

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
)

/** Ícono de un contador por día. */
@Serializable
enum class CounterIcon {
    @SerialName("car") CAR,
    @SerialName("child") CHILD,
    @SerialName("bus") BUS,
    @SerialName("gym") GYM,
    @SerialName("work") WORK,
    @SerialName("pet") PET,
    @SerialName("home") HOME,
    @SerialName("other") OTHER,
}

/** PAY = yo pago (garaje, niñera); EARN = me pagan (días trabajados). */
@Serializable
enum class CounterDirection {
    @SerialName("pay") PAY,
    @SerialName("earn") EARN,
}

/** Mi permiso sobre un contador, una lista o una sección: dueño, puede editar o solo ver. */
@Serializable
enum class CounterRole {
    @SerialName("owner") OWNER,
    @SerialName("editor") EDITOR,
    @SerialName("viewer") VIEWER,
}

/** Contador por día (el garaje es uno más) con su cuenta hasta hoy. */
@Serializable
data class Counter(
    val id: String,
    val ownerId: String = "",
    val ownerName: String = "",
    val role: CounterRole = CounterRole.OWNER,
    /** Con quién lo comparto (solo lo llena el servidor; en el modo demo, el teléfono). */
    val members: List<SharedMember> = emptyList(),
    /** Grupos con los que lo comparto. */
    val groups: List<GroupShare> = emptyList(),
    val name: String,
    val icon: CounterIcon = CounterIcon.OTHER,
    val direction: CounterDirection = CounterDirection.PAY,
    val dailyRate: Double? = null,
    val autoMark: Boolean = false,
    /** Lista adjunta (ej. las tareas de la niñera). */
    val listId: String? = null,
    val today: String = LocalDate.now().toString(),
    val summary: GarageSummary = GarageSummary(),
    val createdAt: String,
) {
    val settings: GarageSettings get() = GarageSettings(dailyRate, autoMark, today)
    val mine: Boolean get() = role == CounterRole.OWNER
    val canEdit: Boolean get() = role != CounterRole.VIEWER
}

/** Un día marcado en un contador (ej. el carro en el garaje). */
@Serializable
data class GarageDay(
    val date: String,
    val note: String? = null,
    /** Tarifa de ESTE día (copia de la vigente al marcarlo). null = sin tarifa. */
    val rate: Double? = null,
    val charges: List<GarageCharge> = emptyList(),
    /** Tarifa + cargos del día. */
    val total: Double = 0.0,
    val createdAt: String,
    val updatedAt: String,
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
}

/** Cargo extra de un día (lavado, multa…). Puede haber varios en el mismo día. */
@Serializable
data class GarageCharge(
    val id: String,
    val description: String,
    val amount: Double,
    val createdAt: String,
)

@Serializable
data class Payment(
    val id: String,
    val date: String,
    val amount: Double,
    val note: String? = null,
    val createdAt: String,
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
}

@Serializable
data class GarageSettings(
    val dailyRate: Double? = null,
    /** Marcar cada día automáticamente con la tarifa por defecto. */
    val autoMark: Boolean = false,
    /** "Hoy" según el servidor (zona horaria de Ecuador). */
    val today: String = LocalDate.now().toString(),
)

/** Cuenta del garaje hasta `until`: días + cargos - pagos = pendiente. */
@Serializable
data class GarageSummary(
    val until: String = LocalDate.now().toString(),
    val dailyRate: Double? = null,
    val days: Int = 0,
    val daysWithoutRate: Int = 0,
    val daysTotal: Double = 0.0,
    val chargesTotal: Double = 0.0,
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val pending: Double = 0.0,
)

@Serializable
enum class ChecklistKind {
    @SerialName("shopping") SHOPPING,
    @SerialName("todo") TODO,
}

@Serializable
data class ChecklistItem(
    val id: String,
    val text: String,
    /** Solo en listas de compras: "2 kg", "1 caja"... */
    val quantity: String? = null,
    val done: Boolean = false,
    /** Quién lo marcó como hecho (útil en listas compartidas). */
    val doneBy: String? = null,
    /** Producto del catálogo del dueño de la lista. */
    val productId: String? = null,
    /** Foto que se muestra: la propia del elemento o, si no tiene, la del producto. */
    val image: ImageRef? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
enum class ImageSource {
    @SerialName("item") ITEM,
    @SerialName("product") PRODUCT,
}

/** Foto lista para mostrar. `key` es estable (sirve de clave de caché); `url` puede ser temporal. */
@Serializable
data class ImageRef(
    val url: String,
    val key: String,
    val source: ImageSource,
)

/** Producto del catálogo ("Leche") con su foto opcional. */
@Serializable
data class Product(
    val id: String,
    val name: String,
    val image: ImageRef? = null,
)

@Serializable
enum class MemberRole {
    @SerialName("editor") EDITOR,
    @SerialName("viewer") VIEWER,
}

@Serializable
enum class MemberStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED,
}

@Serializable
data class SharedMember(
    val email: String,
    val name: String? = null,
    val userId: String? = null,
    val role: MemberRole,
    val status: MemberStatus,
    val invitedAt: String,
)

@Serializable
data class Checklist(
    val id: String,
    val title: String,
    val kind: ChecklistKind,
    val ownerId: String,
    val items: List<ChecklistItem> = emptyList(),
    val sharedWith: List<SharedMember> = emptyList(),
    val groups: List<GroupShare> = emptyList(),
    /** Mi permiso (directo o por un grupo); lo calcula el servidor. */
    val role: CounterRole = CounterRole.OWNER,
    val createdAt: String,
    val updatedAt: String,
) {
    val doneCount: Int get() = items.count { it.done }
    val pendingCount: Int get() = items.size - doneCount
    val progress: Float get() = if (items.isEmpty()) 0f else doneCount.toFloat() / items.size
}

/** Código y enlace para que otra persona se una a una lista o a una sección de recordatorios. */
@Serializable
data class ShareInvite(
    val code: String,
    val url: String,
    val listId: String? = null,
    val spaceId: String? = null,
    val trackerId: String? = null,
    val groupId: String? = null,
    val expiresAt: String,
)

/** A qué se unió alguien con un código (POST /join). */
@Serializable
data class JoinResult(
    val type: String,
    val listId: String? = null,
    val spaceId: String? = null,
    val trackerId: String? = null,
    val groupId: String? = null,
    val kind: ReminderKind? = null,
)

// ---------------------------------------------------------------- recordatorios (docs/PLAN-RECORDATORIOS.md)

/** Categoría de un recordatorio. Cada una es una sección que se comparte por separado. */
@Serializable
enum class ReminderKind {
    @SerialName("payments") PAYMENTS,
    @SerialName("home") HOME,
    @SerialName("car") CAR,
    @SerialName("other") OTHER;

    /** Como va en la API y en los deep links (arianna://reminders/payments). */
    val apiName: String get() = name.lowercase()

    companion object {
        /** null si no es una categoría (ej. "all" = todas). */
        fun fromApi(value: String?): ReminderKind? = entries.find { it.apiName == value }
    }
}

/** Desde dónde se cuenta el siguiente vencimiento al marcarlo. */
@Serializable
enum class CountFrom {
    /** La fecha que vencía (la luz vence siempre el 15). */
    @SerialName("due") DUE,

    /** El día que se hizo (limpiar el aire cada 3 meses desde la última limpieza). */
    @SerialName("done") DONE;

    companion object {
        /** Pagos y Otros (cumpleaños, cédula) tienen fecha fija; los mantenimientos, desde que se hicieron. */
        fun defaultFor(kind: ReminderKind) = if (kind == ReminderKind.HOME || kind == ReminderKind.CAR) DONE else DUE
    }
}

@Serializable
enum class RepeatUnit {
    @SerialName("day") DAY,
    @SerialName("week") WEEK,
    @SerialName("month") MONTH,
    @SerialName("year") YEAR,
}

/** Cada vez que se marcó como pagado/hecho. */
@Serializable
data class ReminderLog(
    val id: String,
    /** Vencimiento que se cubrió. */
    val dueDate: String,
    val amount: Double? = null,
    val note: String? = null,
    /** Kilometraje cuando se hizo (Mi carro). */
    val odometer: Int? = null,
    val doneBy: String? = null,
    val doneByName: String? = null,
    val createdAt: String,
) {
    val localDueDate: LocalDate get() = LocalDate.parse(dueDate)

    /** Día (en este teléfono) en que se marcó. */
    val doneDate: LocalDate get() = Instant.parse(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

    /** La fecha que importa mostrar: la que vencía (pagos) o la que se hizo (mantenimientos). */
    fun shownDate(countFrom: CountFrom): LocalDate = if (countFrom == CountFrom.DONE) doneDate else localDueDate
}

@Serializable
data class Reminder(
    val id: String,
    val spaceId: String,
    val kind: ReminderKind,
    val title: String,
    /** Monto estimado (el real se guarda al marcarlo). */
    val amount: Double? = null,
    /** every=1, unit=MONTH → cada mes. Ambos null = una sola vez. */
    val every: Int? = null,
    val unit: RepeatUnit? = null,
    val nextDue: String,
    val notifyDaysBefore: Int = 2,
    val countFrom: CountFrom = CountFrom.DUE,
    /** Mi carro: también cada tantos km (solo informativo; los avisos son por fecha). */
    val everyKm: Int? = null,
    /** Hora a la que vence ("20:00" o "20:00:00"). Sin hora: aviso de la mañana. */
    val dueTime: String? = null,
    /** Con hora: cuántos minutos antes avisar (0 = a la hora, 60 = 1 hora antes, 1440 = el día anterior). */
    val alertMinutes: Int? = null,
    /** Lista adjunta (ej. "Compras del mes" con la lista del súper). */
    val listId: String? = null,
    val note: String? = null,
    /** Una sola vez y ya hecho. */
    val finished: Boolean = false,
    val lastDone: ReminderLog? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    val dueDate: LocalDate get() = LocalDate.parse(nextDue)
    val repeats: Boolean get() = every != null && unit != null
    val time: LocalTime? get() = dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    /** A qué kilometraje toca, según el último registrado. */
    val nextKm: Int? get() = everyKm?.let { km -> lastDone?.odometer?.plus(km) }
}

/** Sección (propia o de otra persona) con quienes la comparten. */
@Serializable
data class ReminderSpace(
    val id: String,
    val kind: ReminderKind,
    val ownerId: String,
    val ownerName: String,
    val members: List<SharedMember> = emptyList(),
    val groups: List<GroupShare> = emptyList(),
    /** Mi permiso (directo o por un grupo); lo calcula el servidor. */
    val role: CounterRole = CounterRole.OWNER,
)

// ---------------------------------------------------------------- grupos

/** Con qué grupo se compartió algo y con qué permiso. */
@Serializable
data class GroupShare(
    val groupId: String,
    val groupName: String,
    val role: MemberRole,
)

@Serializable
data class GroupMember(
    val email: String,
    val name: String? = null,
    val userId: String? = null,
    val status: MemberStatus,
    val invitedAt: String,
)

@Serializable
enum class GroupItemType {
    @SerialName("list") LIST,
    @SerialName("space") SPACE,
    @SerialName("tracker") TRACKER,
}

/** Algo compartido con un grupo (para la pantalla del grupo). */
@Serializable
data class GroupItem(
    val type: GroupItemType,
    val id: String,
    val title: String,
    /** shopping/todo (lista), payments/home/car/other (sección) o el ícono del contador. */
    val kind: String,
    val ownerId: String,
    val ownerName: String,
    val role: MemberRole,
)

@Serializable
enum class GroupRole {
    @SerialName("owner") OWNER,
    @SerialName("member") MEMBER,
}

/** Grupo de personas (Familia, Roomies…): lo que se comparte con él lo ven todos sus integrantes. */
@Serializable
data class Group(
    val id: String,
    val name: String,
    val ownerId: String,
    val ownerName: String,
    val role: GroupRole = GroupRole.OWNER,
    val members: List<GroupMember> = emptyList(),
    val shared: List<GroupItem> = emptyList(),
    val createdAt: String,
) {
    val mine: Boolean get() = role == GroupRole.OWNER
    /** Personas en el grupo, contando a quien lo creó. */
    val size: Int get() = members.size + 1
}
