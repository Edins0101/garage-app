package com.garaje.app.domain.repository

import com.garaje.app.domain.model.Checklist
import com.garaje.app.domain.model.ChecklistItem
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.model.MemberRole
import com.garaje.app.domain.model.ShareInvite
import com.garaje.app.domain.model.SharedMember
import com.garaje.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/*
 * Contratos de datos. La UI y los ViewModels solo dependen de estas interfaces;
 * la implementación (falsa o remota) se elige en di/AppContainer.kt.
 * Los métodos `observe*` emiten cada vez que cambian los datos.
 */

interface AuthRepository {
    suspend fun currentUser(): User
}

interface GarageRepository {
    fun observeDays(): Flow<List<GarageDay>>
    suspend fun refresh()
    /** Crea o actualiza el día (nota opcional). */
    suspend fun setDay(date: LocalDate, note: String? = null): GarageDay
    suspend fun setDays(dates: List<LocalDate>)
    suspend fun removeDay(date: LocalDate)
}

interface ListsRepository {
    fun observeLists(): Flow<List<Checklist>>
    fun observeList(id: String): Flow<Checklist?>
    suspend fun refresh()

    suspend fun createList(title: String, kind: ChecklistKind): Checklist
    suspend fun renameList(id: String, title: String)
    suspend fun deleteList(id: String)

    suspend fun addItem(listId: String, text: String, quantity: String? = null): ChecklistItem
    suspend fun updateItem(
        listId: String,
        itemId: String,
        text: String? = null,
        quantity: String? = null,
        done: Boolean? = null,
    )
    suspend fun removeItem(listId: String, itemId: String)
    suspend fun clearCompleted(listId: String)
}

interface SharingRepository {
    suspend fun invite(listId: String, email: String, role: MemberRole): SharedMember
    suspend fun removeMember(listId: String, email: String)
    /** Reutiliza una invitación vigente si existe. */
    suspend fun createInvite(listId: String): ShareInvite
    /** Une al usuario actual a la lista del código y la devuelve. */
    suspend fun joinByCode(code: String): Checklist
}

/** Error de datos con mensaje listo para mostrar al usuario. */
class DataException(
    message: String,
    val kind: Kind = Kind.UNKNOWN,
) : Exception(message) {
    enum class Kind { NOT_FOUND, INVALID, FORBIDDEN, NETWORK, UNKNOWN }
}
