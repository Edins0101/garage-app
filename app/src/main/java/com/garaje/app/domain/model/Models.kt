package com.garaje.app.domain.model

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
    val createdAt: String,
    val updatedAt: String,
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
}

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
    val createdAt: String,
    val updatedAt: String,
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
