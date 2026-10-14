package com.garaje.app.data.remote

import com.garaje.app.domain.model.Checklist
import com.garaje.app.domain.model.ChecklistItem
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.domain.model.GarageCharge
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.model.GarageSettings
import com.garaje.app.domain.model.GarageSummary
import com.garaje.app.domain.model.Payment
import com.garaje.app.domain.model.MemberRole
import com.garaje.app.domain.model.ShareInvite
import com.garaje.app.domain.model.SharedMember
import com.garaje.app.domain.model.User
import com.garaje.app.domain.repository.AuthRepository
import com.garaje.app.domain.repository.CodePurpose
import com.garaje.app.domain.repository.GarageRepository
import com.garaje.app.domain.repository.ListsRepository
import com.garaje.app.domain.repository.Session
import com.garaje.app.domain.repository.SharingRepository
import com.garaje.app.util.Money
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

@Serializable private data class RegisterBody(val name: String, val email: String, val password: String, val code: String)
@Serializable private data class CodeBody(val email: String, val purpose: CodePurpose)
@Serializable private data class ResetBody(val email: String, val code: String, val password: String)
@Serializable private data class MessageResponse(val message: String)
@Serializable private data class LoginBody(val email: String, val password: String)
@Serializable private data class GoogleLoginBody(val idToken: String)

class RemoteAuthRepository(
    private val api: ApiClient,
    private val sessions: SessionStore,
) : AuthRepository {
    override val session: StateFlow<Session?> = sessions.session

    override suspend fun requestCode(email: String, purpose: CodePurpose): String =
        api.request("POST", "/auth/codes", api.body(CodeBody(email.trim(), purpose)), MessageResponse.serializer())!!
            .message

    override suspend fun register(name: String, email: String, password: String, code: String): User =
        start("/auth/register", api.body(RegisterBody(name.trim(), email.trim(), password, code)))

    override suspend fun resetPassword(email: String, code: String, newPassword: String): User =
        start("/auth/password/reset", api.body(ResetBody(email.trim(), code, newPassword)))

    override suspend fun login(email: String, password: String): User =
        start("/auth/login", api.body(LoginBody(email.trim(), password)))

    override suspend fun loginGoogle(idToken: String): User =
        start("/auth/google", api.body(GoogleLoginBody(idToken)))

    private suspend fun start(path: String, body: String): User {
        val session = api.request("POST", path, body, Session.serializer())!!
        sessions.save(session)
        return session.user
    }

    override fun logout() = sessions.clear()

    override suspend fun currentUser(): User = api.request("GET", "/me", null, User.serializer())!!
}

@Serializable private data class RateBody(val rate: String)
@Serializable private data class SettingsBody(val dailyRate: String? = null, val autoMark: Boolean? = null)
@Serializable private data class ChargeBody(val description: String, val amount: String)
@Serializable private data class PaymentBody(val date: String, val amount: String, val note: String?)

class RemoteGarageRepository(private val api: ApiClient) : GarageRepository {
    private val days = MutableStateFlow<List<GarageDay>>(emptyList())
    private val settings = MutableStateFlow(GarageSettings())
    private val summary = MutableStateFlow(GarageSummary())
    private val payments = MutableStateFlow<List<Payment>>(emptyList())

    override fun observeDays(): Flow<List<GarageDay>> = days.onStart { refresh() }
    override fun observeSettings(): Flow<GarageSettings> = settings
    override fun observeSummary(): Flow<GarageSummary> = summary
    override fun observePayments(): Flow<List<Payment>> = payments

    /** Recarga todo: el servidor rellena los días automáticos y recalcula la cuenta. */
    override suspend fun refresh() {
        // El resumen va primero: rellena los días automáticos antes de listarlos.
        summary.value = api.request("GET", "/garage/summary", null, GarageSummary.serializer())!!
        days.value = api.request("GET", "/garage-days", null, ListSerializer(GarageDay.serializer()))!!
        settings.value = api.request("GET", "/garage/settings", null, GarageSettings.serializer())!!
        payments.value = api.request("GET", "/payments", null, ListSerializer(Payment.serializer()))!!
    }

    override suspend fun setDay(date: LocalDate, note: String?): GarageDay {
        val day = api.request("PUT", "/garage-days/$date", api.body(NoteBody(note)), GarageDay.serializer())!!
        refresh()
        return day
    }

    override suspend fun setDays(dates: List<LocalDate>) {
        dates.forEach { api.request("PUT", "/garage-days/$it", api.body(NoteBody(null)), GarageDay.serializer()) }
        refresh()
    }

    override suspend fun removeDay(date: LocalDate) {
        api.request<Unit>("DELETE", "/garage-days/$date", null, null)
        refresh()
    }

    override suspend fun setDayRate(date: LocalDate, rate: Double) {
        api.request("PUT", "/garage-days/$date", api.body(RateBody(Money.toApi(rate))), GarageDay.serializer())
        refresh()
    }

    override suspend fun updateSettings(dailyRate: Double?, autoMark: Boolean?) {
        val body = SettingsBody(dailyRate?.let(Money::toApi), autoMark)
        settings.value = api.request("PUT", "/garage/settings", api.body(body), GarageSettings.serializer())!!
        refresh()
    }

    override suspend fun addCharge(date: LocalDate, description: String, amount: Double) {
        val body = ChargeBody(description.trim(), Money.toApi(amount))
        api.request("POST", "/garage-days/$date/charges", api.body(body), GarageCharge.serializer())
        refresh()
    }

    override suspend fun removeCharge(chargeId: String) {
        api.request<Unit>("DELETE", "/garage-charges/${enc(chargeId)}", null, null)
        refresh()
    }

    override suspend fun addPayment(date: LocalDate, amount: Double, note: String?) {
        val body = PaymentBody(date.toString(), Money.toApi(amount), note?.trim()?.takeIf { it.isNotEmpty() })
        api.request("POST", "/payments", api.body(body), Payment.serializer())
        refresh()
    }

    override suspend fun removePayment(paymentId: String) {
        api.request<Unit>("DELETE", "/payments/${enc(paymentId)}", null, null)
        refresh()
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
