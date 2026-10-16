package com.arianna.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

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

/** Un día en el que el carro está en el garaje. */
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
    val createdAt: String,
    val updatedAt: String,
) {
    val doneCount: Int get() = items.count { it.done }
    val progress: Float get() = if (items.isEmpty()) 0f else doneCount.toFloat() / items.size
}

/** Código y enlace para que otra persona se una a una lista. */
@Serializable
data class ShareInvite(
    val code: String,
    val url: String,
    val listId: String,
    val expiresAt: String,
)
