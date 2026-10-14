package com.garaje.app.data.fake

import com.garaje.app.domain.model.Checklist
import com.garaje.app.domain.model.ChecklistItem
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.model.MemberRole
import com.garaje.app.domain.model.MemberStatus
import com.garaje.app.domain.model.ShareInvite
import com.garaje.app.domain.model.SharedMember
import com.garaje.app.domain.model.User
import com.garaje.app.domain.repository.AuthRepository
import com.garaje.app.domain.repository.DataException
import com.garaje.app.domain.repository.GarageRepository
import com.garaje.app.domain.repository.ListsRepository
import com.garaje.app.domain.repository.Session
import com.garaje.app.domain.repository.SharingRepository
import com.garaje.app.data.session.SessionStore
import com.garaje.app.util.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/*
 * Implementaciones falsas de los repositorios. Imitan las reglas del backend
 * descrito en docs/API.md sobre el archivo local de [FakeStore].
 */

private fun now() = Instant.now().toString()

/** Login simulado: acepta cualquier correo y guarda la sesión en el teléfono. */
class FakeAuthRepository(
    private val store: FakeStore,
    private val sessions: SessionStore,
) : AuthRepository {
    override val session: StateFlow<Session?> = sessions.session

    override suspend fun loginDev(email: String, name: String): User {
        store.simulateLatency()
        val user = FakeSeed.currentUser.copy(name = name.trim(), email = email.trim().lowercase())
        sessions.save(Session(token = "fake-token", user = user))
        return user
    }

    override suspend fun loginGoogle(idToken: String): User =
        throw DataException("El login con Google necesita el backend", DataException.Kind.INVALID)

    override fun logout() = sessions.clear()

    override suspend fun currentUser(): User {
        store.simulateLatency()
        return sessions.session.value?.user ?: FakeSeed.currentUser
    }
}

class FakeGarageRepository(private val store: FakeStore) : GarageRepository {

    override fun observeDays(): Flow<List<GarageDay>> =
        store.db.filterNotNull()
            .map { db -> db.garageDays.sortedBy { it.date } }
            .onStart { store.load() }

    override suspend fun refresh() {
        store.load()
    }

    override suspend fun setDay(date: LocalDate, note: String?): GarageDay = store.update { db ->
        val key = date.toString()
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        val existing = db.garageDays.find { it.date == key }
        val day = existing?.copy(note = cleanNote, updatedAt = now())
            ?: GarageDay(key, cleanNote, createdAt = now(), updatedAt = now())
        db.copy(garageDays = db.garageDays.filterNot { it.date == key } + day) to day
    }

    override suspend fun setDays(dates: List<LocalDate>) = store.update { db ->
        val existing = db.garageDays.map { it.date }.toSet()
        val added = dates.map { it.toString() }
            .filterNot { it in existing }
            .distinct()
            .map { GarageDay(it, null, createdAt = now(), updatedAt = now()) }
        db.copy(garageDays = db.garageDays + added) to Unit
    }

    override suspend fun removeDay(date: LocalDate) = store.update { db ->
        db.copy(garageDays = db.garageDays.filterNot { it.date == date.toString() }) to Unit
    }
}

/** Utilidades compartidas por los repositorios de listas y de compartir. */
private fun FakeDb.requireList(id: String): Checklist =
    lists.find { it.id == id } ?: throw DataException("La lista no existe", DataException.Kind.NOT_FOUND)

private fun FakeDb.replaceList(list: Checklist): FakeDb =
    copy(lists = lists.map { if (it.id == list.id) list.copy(updatedAt = now()) else it })

class FakeListsRepository(private val store: FakeStore) : ListsRepository {

    override fun observeLists(): Flow<List<Checklist>> =
        store.db.filterNotNull()
            .map { db -> db.lists.sortedByDescending { it.updatedAt } }
            .onStart { store.load() }

    override fun observeList(id: String): Flow<Checklist?> =
        store.db.filterNotNull()
            .map { db -> db.lists.find { it.id == id } }
            .onStart { store.load() }

    override suspend fun refresh() {
        store.load()
    }

    override suspend fun createList(title: String, kind: ChecklistKind): Checklist = store.update { db ->
        val clean = title.trim()
        if (clean.isEmpty()) throw DataException("El nombre es obligatorio", DataException.Kind.INVALID)
        val list = Checklist(
            id = Ids.create("list-"),
            title = clean,
            kind = kind,
            ownerId = FakeSeed.currentUser.id,
            createdAt = now(),
            updatedAt = now(),
        )
        db.copy(lists = db.lists + list) to list
    }

    override suspend fun renameList(id: String, title: String) = store.update { db ->
        val list = db.requireList(id)
        db.replaceList(list.copy(title = title.trim().ifEmpty { list.title })) to Unit
    }

    override suspend fun deleteList(id: String) = store.update { db ->
        db.copy(
            lists = db.lists.filterNot { it.id == id },
            invites = db.invites.filterNot { it.listId == id },
        ) to Unit
    }

    override suspend fun addItem(listId: String, text: String, quantity: String?): ChecklistItem =
        store.update { db ->
            val clean = text.trim()
            if (clean.isEmpty()) throw DataException("Escribe algo para agregar", DataException.Kind.INVALID)
            val list = db.requireList(listId)
            val item = ChecklistItem(
                id = Ids.create("item-"),
                text = clean,
                quantity = quantity?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now(),
                updatedAt = now(),
            )
            db.replaceList(list.copy(items = list.items + item)) to item
        }

    override suspend fun updateItem(
        listId: String,
        itemId: String,
        text: String?,
        quantity: String?,
        done: Boolean?,
    ) = store.update { db ->
        val list = db.requireList(listId)
        if (list.items.none { it.id == itemId }) {
            throw DataException("El elemento no existe", DataException.Kind.NOT_FOUND)
        }
        val items = list.items.map { item ->
            if (item.id != itemId) return@map item
            item.copy(
                text = text?.trim()?.takeIf { it.isNotEmpty() } ?: item.text,
                quantity = quantity ?: item.quantity,
                done = done ?: item.done,
                doneBy = when (done) {
                    true -> FakeSeed.currentUser.id
                    false -> null
                    null -> item.doneBy
                },
                updatedAt = now(),
            )
        }
        db.replaceList(list.copy(items = items)) to Unit
    }

    override suspend fun removeItem(listId: String, itemId: String) = store.update { db ->
        val list = db.requireList(listId)
        db.replaceList(list.copy(items = list.items.filterNot { it.id == itemId })) to Unit
    }

    override suspend fun clearCompleted(listId: String) = store.update { db ->
        val list = db.requireList(listId)
        db.replaceList(list.copy(items = list.items.filterNot { it.done })) to Unit
    }
}

class FakeSharingRepository(private val store: FakeStore) : SharingRepository {

    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    override suspend fun invite(listId: String, email: String, role: MemberRole): SharedMember =
        store.update { db ->
            val normalized = email.trim().lowercase()
            if (!emailRegex.matches(normalized)) {
                throw DataException("Correo no válido", DataException.Kind.INVALID)
            }
            if (normalized == FakeSeed.currentUser.email) {
                throw DataException("No puedes invitarte a ti mismo", DataException.Kind.INVALID)
            }
            val list = db.requireList(listId)
            val member = list.sharedWith.find { it.email == normalized }?.copy(role = role)
                ?: SharedMember(
                    email = normalized,
                    role = role,
                    status = MemberStatus.PENDING,
                    invitedAt = now(),
                )
            val members = list.sharedWith.filterNot { it.email == normalized } + member
            db.replaceList(list.copy(sharedWith = members)) to member
        }

    override suspend fun removeMember(listId: String, email: String) = store.update { db ->
        val list = db.requireList(listId)
        db.replaceList(list.copy(sharedWith = list.sharedWith.filterNot { it.email == email })) to Unit
    }

    override suspend fun createInvite(listId: String): ShareInvite = store.update { db ->
        db.requireList(listId)
        val nowInstant = Instant.now()
        val valid = db.invites.filter { Instant.parse(it.expiresAt).isAfter(nowInstant) }
        val reusable = valid.find { it.listId == listId }
        if (reusable != null) return@update db.copy(invites = valid) to reusable

        val code = Ids.shareCode()
        val invite = ShareInvite(
            code = code,
            url = "garajeapp://join/$code",
            listId = listId,
            expiresAt = nowInstant.plus(Duration.ofDays(7)).toString(),
        )
        db.copy(invites = valid + invite) to invite
    }

    override suspend fun joinByCode(code: String): Checklist {
        store.simulateLatency()
        val db = store.load()
        val clean = code.trim().uppercase()
        val invite = db.invites.find {
            it.code == clean && Instant.parse(it.expiresAt).isAfter(Instant.now())
        } ?: throw DataException("Código inválido o vencido", DataException.Kind.NOT_FOUND)
        // Sin backend no existe otro usuario real: en el mock "unirse" solo abre la lista.
        return db.requireList(invite.listId)
    }
}
