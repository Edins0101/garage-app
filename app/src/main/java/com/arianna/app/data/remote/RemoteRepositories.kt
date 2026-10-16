package com.arianna.app.data.remote

import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistItem
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.model.GarageCharge
import com.arianna.app.domain.model.GarageDay
import com.arianna.app.domain.model.GarageSettings
import com.arianna.app.domain.model.GarageSummary
import com.arianna.app.domain.model.Counter
import com.arianna.app.domain.model.CounterDirection
import com.arianna.app.domain.model.CounterIcon
import com.arianna.app.domain.repository.CounterDraft
import com.arianna.app.domain.repository.CountersRepository
import com.arianna.app.domain.model.JoinResult
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.model.ReminderLog
import com.arianna.app.domain.model.ReminderSpace
import com.arianna.app.domain.repository.ReminderDraft
import com.arianna.app.domain.repository.RemindersRepository
import com.arianna.app.domain.model.Payment
import com.arianna.app.domain.model.Product
import com.arianna.app.domain.repository.PhotoInput
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.model.SharedMember
import com.arianna.app.domain.model.User
import com.arianna.app.domain.repository.AuthRepository
import com.arianna.app.domain.repository.CodePurpose
import com.arianna.app.domain.repository.GarageRepository
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.repository.Session
import com.arianna.app.domain.repository.SharingRepository
import com.arianna.app.util.Money
import com.arianna.app.data.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

@Serializable private data class CounterBody(
    val name: String,
    val icon: CounterIcon,
    val direction: CounterDirection,
    val dailyRate: String? = null,
    val autoMark: Boolean? = null,
)

class RemoteCountersRepository(private val api: ApiClient) : CountersRepository {
    private val counters = MutableStateFlow<List<Counter>>(emptyList())
    private val scoped = mutableMapOf<String, RemoteCounterRepository>()

    override fun observeCounters(): Flow<List<Counter>> = counters.onStart { refresh() }

    override suspend fun refresh() {
        counters.value = api.request("GET", "/trackers", null, ListSerializer(Counter.serializer()))!!
    }

    override suspend fun create(draft: CounterDraft): Counter {
        val body = CounterBody(draft.name.trim(), draft.icon, draft.direction, draft.dailyRate?.let(Money::toApi), draft.autoMark)
        val counter = api.request("POST", "/trackers", api.body(body), Counter.serializer())!!
        refresh()
        return counter
    }

    override suspend fun update(counter: Counter, draft: CounterDraft) {
        val body = CounterBody(draft.name.trim(), draft.icon, draft.direction, draft.dailyRate?.let(Money::toApi), draft.autoMark)
        api.request("PATCH", "/trackers/${enc(counter.id)}", api.body(body), Counter.serializer())
        scoped[counter.id]?.refresh() ?: refresh()
    }

    override suspend fun delete(counter: Counter) {
        api.request<Unit>("DELETE", "/trackers/${enc(counter.id)}", null, null)
        scoped.remove(counter.id)
        refresh()
    }

    @Synchronized
    override fun counter(id: String): GarageRepository =
        scoped.getOrPut(id) { RemoteCounterRepository(api, id, counters, onChanged = ::refresh) }
}

/** Días, cargos y pagos de un contador (/trackers/{id}/…). La tarifa sale de la lista de contadores. */
private class RemoteCounterRepository(
    private val api: ApiClient,
    id: String,
    private val counters: MutableStateFlow<List<Counter>>,
    /** Recarga la lista de contadores (tarifa y cuenta de cada uno). */
    private val onChanged: suspend () -> Unit,
) : GarageRepository {
    private val base = "/trackers/${enc(id)}"
    private val counterId = id
    private val days = MutableStateFlow<List<GarageDay>>(emptyList())
    private val summary = MutableStateFlow(GarageSummary())
    private val payments = MutableStateFlow<List<Payment>>(emptyList())

    override fun observeDays(): Flow<List<GarageDay>> = days.onStart { refresh() }
    override fun observeSettings(): Flow<GarageSettings> =
        counters.map { list -> list.find { it.id == counterId }?.settings ?: GarageSettings() }
    override fun observeSummary(): Flow<GarageSummary> = summary
    override fun observePayments(): Flow<List<Payment>> = payments

    /** Recarga todo: el servidor rellena los días automáticos y recalcula la cuenta. */
    override suspend fun refresh() {
        // El resumen va primero: rellena los días automáticos antes de listarlos.
        summary.value = api.request("GET", "$base/summary", null, GarageSummary.serializer())!!
        days.value = api.request("GET", "$base/days", null, ListSerializer(GarageDay.serializer()))!!
        payments.value = api.request("GET", "$base/payments", null, ListSerializer(Payment.serializer()))!!
        onChanged()
    }

    override suspend fun setDay(date: LocalDate, note: String?): GarageDay {
        val day = api.request("PUT", "$base/days/$date", api.body(NoteBody(note)), GarageDay.serializer())!!
        refresh()
        return day
    }

    override suspend fun setDays(dates: List<LocalDate>) {
        dates.forEach { api.request("PUT", "$base/days/$it", api.body(NoteBody(null)), GarageDay.serializer()) }
        refresh()
    }

    override suspend fun removeDay(date: LocalDate) {
        api.request<Unit>("DELETE", "$base/days/$date", null, null)
        refresh()
    }

    override suspend fun setDayRate(date: LocalDate, rate: Double) {
        api.request("PUT", "$base/days/$date", api.body(RateBody(Money.toApi(rate))), GarageDay.serializer())
        refresh()
    }

    override suspend fun updateSettings(dailyRate: Double?, autoMark: Boolean?) {
        val body = SettingsBody(dailyRate?.let(Money::toApi), autoMark)
        api.request("PATCH", base, api.body(body), Counter.serializer())
        refresh()
    }

    override suspend fun addCharge(date: LocalDate, description: String, amount: Double) {
        val body = ChargeBody(description.trim(), Money.toApi(amount))
        api.request("POST", "$base/days/$date/charges", api.body(body), GarageCharge.serializer())
        refresh()
    }

    override suspend fun removeCharge(chargeId: String) {
        api.request<Unit>("DELETE", "$base/charges/${enc(chargeId)}", null, null)
        refresh()
    }

    override suspend fun addPayment(date: LocalDate, amount: Double, note: String?) {
        val body = PaymentBody(date.toString(), Money.toApi(amount), note?.trim()?.takeIf { it.isNotEmpty() })
        api.request("POST", "$base/payments", api.body(body), Payment.serializer())
        refresh()
    }

    override suspend fun removePayment(paymentId: String) {
        api.request<Unit>("DELETE", "$base/payments/${enc(paymentId)}", null, null)
        refresh()
    }
}

@Serializable private data class UploadBody(val listId: String, val contentType: String = "image/jpeg")
@Serializable private data class UploadResponse(val uploadUrl: String, val key: String, val contentType: String)
@Serializable private data class ImageBody(val key: String? = null, val url: String? = null)

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

    override suspend fun searchProducts(listId: String, query: String): List<Product> =
        api.request(
            "GET",
            "/lists/${enc(listId)}/products?q=${enc(query)}",
            null,
            ListSerializer(Product.serializer()),
        )!!

    /** Pide un enlace firmado, sube la foto directo al almacenamiento y devuelve su clave. */
    private suspend fun upload(listId: String, jpeg: ByteArray): String {
        val upload = api.request("POST", "/uploads", api.body(UploadBody(listId)), UploadResponse.serializer())!!
        api.uploadBytes(upload.uploadUrl, jpeg, upload.contentType)
        return upload.key
    }

    override suspend fun setProductImage(listId: String, productId: String, photo: PhotoInput) {
        val body = when (photo) {
            is PhotoInput.Jpeg -> ImageBody(key = upload(listId, photo.bytes))
            is PhotoInput.Url -> ImageBody(url = photo.url.trim())
        }
        api.request("PUT", "/lists/${enc(listId)}/products/${enc(productId)}/image", api.body(body), Product.serializer())
        reload(listId)
    }

    override suspend fun removeProductImage(listId: String, productId: String) {
        api.request("DELETE", "/lists/${enc(listId)}/products/${enc(productId)}/image", null, Product.serializer())
        reload(listId)
    }

    override suspend fun setItemImage(listId: String, itemId: String, jpeg: ByteArray) {
        val body = ImageBody(key = upload(listId, jpeg))
        api.request("PUT", "/lists/${enc(listId)}/items/${enc(itemId)}/image", api.body(body), ChecklistItem.serializer())
        reload(listId)
    }

    override suspend fun removeItemImage(listId: String, itemId: String) {
        api.request("DELETE", "/lists/${enc(listId)}/items/${enc(itemId)}/image", null, ChecklistItem.serializer())
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

    override suspend fun joinByCode(code: String): JoinResult {
        val result = api.request("POST", "/join", api.body(JoinBody(code)), JoinResult.serializer())!!
        if (result.listId != null) lists.refresh()
        return result
    }
}

@Serializable private data class DoneBody(val amount: String? = null, val note: String? = null, val odometer: Int? = null)

class RemoteRemindersRepository(private val api: ApiClient) : RemindersRepository {
    // Una sola consulta trae todas las categorías; cada pantalla filtra la suya.
    private val reminders = MutableStateFlow<List<Reminder>>(emptyList())
    private val spaces = MutableStateFlow<List<ReminderSpace>>(emptyList())

    override fun observeReminders(kind: ReminderKind?): Flow<List<Reminder>> =
        reminders.map { all -> all.filter { kind == null || it.kind == kind } }.onStart { refresh() }

    override fun observeSpaces(kind: ReminderKind?): Flow<List<ReminderSpace>> =
        spaces.map { all -> all.filter { kind == null || it.kind == kind } }

    override suspend fun refresh() {
        spaces.value = api.request("GET", "/spaces", null, ListSerializer(ReminderSpace.serializer()))!!
        reminders.value = api.request("GET", "/reminders", null, ListSerializer(Reminder.serializer()))!!
    }

    /** Todos los campos, con null explícito: así al editar se puede borrar el monto o la repetición. */
    private fun draftJson(draft: ReminderDraft): JsonObject = buildJsonObject {
        put("title", draft.title.trim())
        put("amount", draft.amount?.let(Money::toApi))
        put("every", draft.every)
        put("unit", draft.unit?.name?.lowercase())
        put("nextDue", draft.nextDue.toString())
        put("notifyDaysBefore", draft.notifyDaysBefore)
        put("countFrom", draft.countFrom.name.lowercase())
        put("everyKm", draft.everyKm)
        put("note", draft.note?.trim()?.takeIf { it.isNotEmpty() })
    }

    override suspend fun create(kind: ReminderKind, draft: ReminderDraft, spaceId: String?): Reminder {
        val body = JsonObject(draftJson(draft) + buildJsonObject {
            put("kind", kind.apiName)
            if (spaceId != null) put("spaceId", spaceId)
        })
        val reminder = api.request("POST", "/reminders", body.toString(), Reminder.serializer())!!
        refresh()
        return reminder
    }

    override suspend fun update(reminder: Reminder, draft: ReminderDraft) {
        api.request("PATCH", "/reminders/${enc(reminder.id)}", draftJson(draft).toString(), Reminder.serializer())
        refresh()
    }

    override suspend fun delete(reminder: Reminder) {
        api.request<Unit>("DELETE", "/reminders/${enc(reminder.id)}", null, null)
        refresh()
    }

    override suspend fun markDone(reminder: Reminder, amount: Double?, note: String?, odometer: Int?): Reminder {
        val body = DoneBody(amount?.let(Money::toApi), note?.trim()?.takeIf { it.isNotEmpty() }, odometer)
        val updated = api.request("POST", "/reminders/${enc(reminder.id)}/done", api.body(body), Reminder.serializer())!!
        refresh()
        return updated
    }

    override suspend fun undo(reminder: Reminder) {
        api.request("POST", "/reminders/${enc(reminder.id)}/undo", null, Reminder.serializer())
        refresh()
    }

    override suspend fun history(reminder: Reminder): List<ReminderLog> =
        api.request("GET", "/reminders/${enc(reminder.id)}/history", null, ListSerializer(ReminderLog.serializer()))!!

    override suspend fun inviteToSpace(space: ReminderSpace, email: String, role: MemberRole): SharedMember {
        val member = api.request(
            "POST", "/spaces/${enc(space.id)}/members", api.body(InviteBody(email, role)), SharedMember.serializer(),
        )!!
        refresh()
        return member
    }

    override suspend fun removeSpaceMember(space: ReminderSpace, email: String) {
        api.request<Unit>("DELETE", "/spaces/${enc(space.id)}/members/${enc(email)}", null, null)
        refresh()
    }

    override suspend fun createSpaceInvite(space: ReminderSpace): ShareInvite =
        api.request("POST", "/spaces/${enc(space.id)}/invites", null, ShareInvite.serializer())!!
}
