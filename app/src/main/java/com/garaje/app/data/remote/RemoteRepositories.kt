package com.garaje.app.data.remote

import com.garaje.app.domain.model.Checklist
import com.garaje.app.domain.model.ChecklistItem
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.model.MemberRole
import com.garaje.app.domain.model.ShareInvite
import com.garaje.app.domain.model.SharedMember
import com.garaje.app.domain.model.User
import com.garaje.app.domain.repository.AuthRepository
import com.garaje.app.domain.repository.GarageRepository
import com.garaje.app.domain.repository.ListsRepository
import com.garaje.app.domain.repository.Session
import com.garaje.app.domain.repository.SharingRepository
import com.garaje.app.data.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import java.net.URLEncoder
import java.time.LocalDate

/*
 * Implementaciones remotas de los repositorios (endpoints en docs/API.md).
 * Guardan en memoria la última respuesta para que los `observe*` emitan
 * y la UI se actualice después de cada operación.
 */

private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

@Serializable private data class NoteBody(val note: String?)
@Serializable private data class NewListBody(val title: String, val kind: ChecklistKind)
@Serializable private data class TitleBody(val title: String)
@Serializable private data class NewItemBody(val text: String, val quantity: String?)
@Serializable private data class ItemPatchBody(val text: String?, val quantity: String?, val done: Boolean?)
@Serializable private data class InviteBody(val email: String, val role: MemberRole)
@Serializable private data class JoinBody(val code: String)

private inline fun <reified T> ApiClient.body(value: T): String = json.encodeToString(serializer<T>(), value)

@Serializable private data class DevLoginBody(val email: String, val name: String)
@Serializable private data class GoogleLoginBody(val idToken: String)

class RemoteAuthRepository(
    private val api: ApiClient,
    private val sessions: SessionStore,
) : AuthRepository {
    override val session: StateFlow<Session?> = sessions.session

    override suspend fun loginDev(email: String, name: String): User =
        login("/auth/dev", api.body(DevLoginBody(email.trim(), name.trim())))

    override suspend fun loginGoogle(idToken: String): User =
        login("/auth/google", api.body(GoogleLoginBody(idToken)))

    private suspend fun login(path: String, body: String): User {
        val session = api.request("POST", path, body, Session.serializer())!!
        sessions.save(session)
        return session.user
    }

    override fun logout() = sessions.clear()

    override suspend fun currentUser(): User = api.request("GET", "/me", null, User.serializer())!!
}

class RemoteGarageRepository(private val api: ApiClient) : GarageRepository {
    private val cache = MutableStateFlow<List<GarageDay>>(emptyList())

    override fun observeDays(): Flow<List<GarageDay>> = cache.onStart { refresh() }

    override suspend fun refresh() {
        cache.value = api.request("GET", "/garage-days", null, ListSerializer(GarageDay.serializer()))!!
    }

    override suspend fun setDay(date: LocalDate, note: String?): GarageDay {
        val day = api.request("PUT", "/garage-days/$date", api.body(NoteBody(note)), GarageDay.serializer())!!
        cache.value = cache.value.filterNot { it.date == day.date } + day
        return day
    }

    override suspend fun setDays(dates: List<LocalDate>) {
        dates.forEach { api.request("PUT", "/garage-days/$it", api.body(NoteBody(null)), GarageDay.serializer()) }
        refresh()
    }

    override suspend fun removeDay(date: LocalDate) {
        api.request<Unit>("DELETE", "/garage-days/$date", null, null)
        cache.value = cache.value.filterNot { it.date == date.toString() }
    }
}

class RemoteListsRepository(private val api: ApiClient) : ListsRepository {
    private val cache = MutableStateFlow<List<Checklist>>(emptyList())

    override fun observeLists(): Flow<List<Checklist>> = cache.onStart { refresh() }

    override fun observeList(id: String): Flow<Checklist?> =
        cache.map { lists -> lists.find { it.id == id } }.onStart { reload(id) }

    override suspend fun refresh() {
        cache.value = api.request("GET", "/lists", null, ListSerializer(Checklist.serializer()))!!
    }

    private suspend fun reload(id: String) {
        val list = api.request("GET", "/lists/${enc(id)}", null, Checklist.serializer())!!
        cache.value = cache.value.filterNot { it.id == id } + list
    }

    override suspend fun createList(title: String, kind: ChecklistKind): Checklist {
        val list = api.request("POST", "/lists", api.body(NewListBody(title, kind)), Checklist.serializer())!!
        cache.value = cache.value + list
        return list
    }

    override suspend fun renameList(id: String, title: String) {
        api.request("PATCH", "/lists/${enc(id)}", api.body(TitleBody(title)), Checklist.serializer())
        reload(id)
    }

    override suspend fun deleteList(id: String) {
        api.request<Unit>("DELETE", "/lists/${enc(id)}", null, null)
        cache.value = cache.value.filterNot { it.id == id }
    }

    override suspend fun addItem(listId: String, text: String, quantity: String?): ChecklistItem {
        val item = api.request(
            "POST", "/lists/${enc(listId)}/items", api.body(NewItemBody(text, quantity)), ChecklistItem.serializer(),
        )!!
        reload(listId)
        return item
    }

    override suspend fun updateItem(listId: String, itemId: String, text: String?, quantity: String?, done: Boolean?) {
        api.request(
            "PATCH",
            "/lists/${enc(listId)}/items/${enc(itemId)}",
            api.body(ItemPatchBody(text, quantity, done)),
            ChecklistItem.serializer(),
        )
        reload(listId)
    }

    override suspend fun removeItem(listId: String, itemId: String) {
        api.request<Unit>("DELETE", "/lists/${enc(listId)}/items/${enc(itemId)}", null, null)
        reload(listId)
    }

    override suspend fun clearCompleted(listId: String) {
        api.request("POST", "/lists/${enc(listId)}/items/clear-completed", null, Checklist.serializer())
        reload(listId)
    }
}

class RemoteSharingRepository(
    private val api: ApiClient,
    private val lists: ListsRepository,
) : SharingRepository {

    override suspend fun invite(listId: String, email: String, role: MemberRole): SharedMember {
        val member = api.request(
            "POST", "/lists/${enc(listId)}/members", api.body(InviteBody(email, role)), SharedMember.serializer(),
        )!!
        lists.refresh()
        return member
    }

    override suspend fun removeMember(listId: String, email: String) {
        api.request<Unit>("DELETE", "/lists/${enc(listId)}/members/${enc(email)}", null, null)
        lists.refresh()
    }

    override suspend fun createInvite(listId: String): ShareInvite =
        api.request("POST", "/lists/${enc(listId)}/invites", null, ShareInvite.serializer())!!

    override suspend fun joinByCode(code: String): Checklist {
        val list = api.request("POST", "/invites/join", api.body(JoinBody(code)), Checklist.serializer())!!
        lists.refresh()
        return list
    }
}
