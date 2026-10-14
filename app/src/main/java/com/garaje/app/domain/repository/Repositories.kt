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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.time.LocalDate

/*
 * Contratos de datos. La UI y los ViewModels solo dependen de estas interfaces;
 * la implementación (falsa o remota) se elige en di/AppContainer.kt.
 * Los métodos `observe*` emiten cada vez que cambian los datos.
 */

/** Sesión iniciada: token de la API + usuario. */
@Serializable
data class Session(val token: String, val user: User)

interface AuthRepository {
    /** null = no hay sesión (se muestra el login). Persiste entre reinicios de la app. */
    val session: StateFlow<Session?>

    /** Crea la cuenta con correo y contraseña e inicia sesión. */
    suspend fun register(name: String, email: String, password: String): User

    /** Inicia sesión con correo y contraseña. */
    suspend fun login(email: String, password: String): User

    /** Login con el ID token de "Iniciar sesión con Google". */
    suspend fun loginGoogle(idToken: String): User

    fun logout()

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
    enum class Kind { NOT_FOUND, INVALID, FORBIDDEN, UNAUTHORIZED, NETWORK, UNKNOWN }
}
