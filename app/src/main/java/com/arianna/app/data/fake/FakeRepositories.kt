package com.arianna.app.data.fake

import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistItem
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.model.GarageCharge
import com.arianna.app.domain.model.GarageDay
import com.arianna.app.domain.model.GarageSettings
import com.arianna.app.domain.model.GarageSummary
import com.arianna.app.domain.model.ImageRef
import com.arianna.app.domain.model.ImageSource
import com.arianna.app.domain.model.JoinResult
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.model.ReminderLog
import com.arianna.app.domain.model.ReminderSpace
import com.arianna.app.domain.repository.ReminderDraft
import com.arianna.app.domain.repository.RemindersRepository
import com.arianna.app.util.Recurrence
import com.arianna.app.domain.model.Payment
import com.arianna.app.domain.model.Product
import com.arianna.app.domain.repository.PhotoInput
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.MemberStatus
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.model.SharedMember
import com.arianna.app.domain.model.User
import com.arianna.app.domain.repository.AuthRepository
import com.arianna.app.domain.repository.CodePurpose
import com.arianna.app.domain.repository.DataException
import com.arianna.app.domain.repository.GarageRepository
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.repository.Session
import com.arianna.app.domain.repository.SharingRepository
import com.arianna.app.data.session.SessionStore
import com.arianna.app.util.Dates
import com.arianna.app.util.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.File
import java.text.Normalizer
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

    // Sin backend no se envían correos: cualquier código de 6 dígitos sirve.
    override suspend fun requestCode(email: String, purpose: CodePurpose): String {
        store.simulateLatency()
        return "Modo demo: usa cualquier código de 6 dígitos"
    }

    override suspend fun register(name: String, email: String, password: String, code: String): User =
        start(name.trim(), email)

    override suspend fun resetPassword(email: String, code: String, newPassword: String): User =
        start(email.substringBefore("@"), email)

    // Sin backend no hay cuentas reales: cualquier correo y contraseña entran.
    override suspend fun login(email: String, password: String): User =
        start(email.substringBefore("@"), email)

    private suspend fun start(name: String, email: String): User {
        store.simulateLatency()
        val user = FakeSeed.currentUser.copy(name = name, email = email.trim().lowercase())
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
            .onStart { refresh() }

    override fun observeSettings(): Flow<GarageSettings> = store.db.filterNotNull().map { it.garageSettings }

    override fun observeSummary(): Flow<GarageSummary> = store.db.filterNotNull().map { summaryOf(it) }

    override fun observePayments(): Flow<List<Payment>> =
        store.db.filterNotNull().map { db -> db.payments.sortedByDescending { it.date } }

    /** Igual que el servidor: rellena los días automáticos pendientes. */
    override suspend fun refresh() {
        store.load()
        store.update { db -> backfill(db) to Unit }
    }

    private fun backfill(db: FakeDb): FakeDb {
        if (!db.garageSettings.autoMark) return db
        val today = LocalDate.now()
        val start = (db.autoFilledUntil?.let(LocalDate::parse) ?: today.minusDays(1)).plusDays(1)
        if (start.isAfter(today)) return db
        val existing = db.garageDays.map { it.date }.toSet()
        val added = Dates.between(start, today)
            .map { it.toString() }
            .filterNot { it in existing }
            .map { newDay(it, db.garageSettings.dailyRate) }
        return db.copy(garageDays = db.garageDays + added, autoFilledUntil = today.toString())
    }

    private fun newDay(date: String, rate: Double?) =
        GarageDay(date, rate = rate, total = rate ?: 0.0, createdAt = now(), updatedAt = now())

    private fun GarageDay.recalculated() = copy(total = (rate ?: 0.0) + charges.sumOf { it.amount }, updatedAt = now())

    private fun FakeDb.withDay(date: LocalDate, change: (GarageDay) -> GarageDay): FakeDb {
        val key = date.toString()
        val day = garageDays.find { it.date == key } ?: newDay(key, garageSettings.dailyRate)
        return copy(garageDays = garageDays.filterNot { it.date == key } + change(day).recalculated())
    }

    private fun summaryOf(db: FakeDb): GarageSummary {
        val until = LocalDate.now().toString()
        val days = db.garageDays.filter { it.date <= until }
        val daysTotal = days.sumOf { it.rate ?: 0.0 }
        val chargesTotal = days.sumOf { day -> day.charges.sumOf { it.amount } }
        val paid = db.payments.filter { it.date <= until }.sumOf { it.amount }
        return GarageSummary(
            until = until,
            dailyRate = db.garageSettings.dailyRate,
            days = days.size,
            daysWithoutRate = days.count { it.rate == null },
            daysTotal = daysTotal,
            chargesTotal = chargesTotal,
            total = daysTotal + chargesTotal,
            paid = paid,
            pending = daysTotal + chargesTotal - paid,
        )
    }

    override suspend fun setDay(date: LocalDate, note: String?): GarageDay = store.update { db ->
        val next = db.withDay(date) { day -> if (note != null) day.copy(note = note.trim().ifEmpty { null }) else day }
        next to next.garageDays.first { it.date == date.toString() }
    }

    override suspend fun setDays(dates: List<LocalDate>) = store.update { db ->
        dates.fold(db) { acc, date -> acc.withDay(date) { it } } to Unit
    }

    override suspend fun removeDay(date: LocalDate) = store.update { db ->
        db.copy(garageDays = db.garageDays.filterNot { it.date == date.toString() }) to Unit
    }

    override suspend fun setDayRate(date: LocalDate, rate: Double) = store.update { db ->
        db.withDay(date) { it.copy(rate = rate) } to Unit
    }

    override suspend fun updateSettings(dailyRate: Double?, autoMark: Boolean?) = store.update { db ->
        var next = backfill(db)
        if (dailyRate != null) {
            next = next.copy(
                garageSettings = next.garageSettings.copy(dailyRate = dailyRate),
                garageDays = next.garageDays.map { if (it.rate == null) it.copy(rate = dailyRate).recalculated() else it },
            )
        }
        if (autoMark != null && autoMark != next.garageSettings.autoMark) {
            next = next.copy(
                garageSettings = next.garageSettings.copy(autoMark = autoMark),
                autoFilledUntil = if (autoMark) LocalDate.now().minusDays(1).toString() else next.autoFilledUntil,
            )
        }
        backfill(next) to Unit
    }

    override suspend fun addCharge(date: LocalDate, description: String, amount: Double) = store.update { db ->
        val charge = GarageCharge(Ids.create("charge-"), description.trim(), amount, createdAt = now())
        db.withDay(date) { it.copy(charges = it.charges + charge) } to Unit
    }

    override suspend fun removeCharge(chargeId: String) = store.update { db ->
        db.copy(
            garageDays = db.garageDays.map { day ->
                if (day.charges.none { it.id == chargeId }) day
                else day.copy(charges = day.charges.filterNot { it.id == chargeId }).recalculated()
            },
        ) to Unit
    }

    override suspend fun addPayment(date: LocalDate, amount: Double, note: String?) = store.update { db ->
        val payment = Payment(Ids.create("pay-"), date.toString(), amount, note?.trim()?.ifEmpty { null }, now())
        db.copy(payments = db.payments + payment) to Unit
    }

    override suspend fun removePayment(paymentId: String) = store.update { db ->
        db.copy(payments = db.payments.filterNot { it.id == paymentId }) to Unit
    }
}

/** Utilidades compartidas por los repositorios de listas y de compartir. */
private fun FakeDb.requireList(id: String): Checklist =
    lists.find { it.id == id } ?: throw DataException("La lista no existe", DataException.Kind.NOT_FOUND)

private fun FakeDb.replaceList(list: Checklist): FakeDb =
    copy(lists = lists.map { if (it.id == list.id) list.copy(updatedAt = now()) else it })

/** Normaliza nombres de productos igual que el backend: minúsculas, sin tildes, espacios simples. */
private fun normalizeName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

/**
 * Listas falsas. Las fotos se guardan como archivos en [imagesDir] y se muestran con su ruta local.
 * Igual que el backend: la foto propia del elemento tiene prioridad sobre la del producto.
 */
class FakeListsRepository(private val store: FakeStore, private val imagesDir: File) : ListsRepository {

    /** Completa la foto de cada elemento con la de su producto si no tiene una propia. */
    private fun FakeDb.resolved(list: Checklist): Checklist = list.copy(
        items = list.items.map { item ->
            val own = item.image?.takeIf { it.source == ImageSource.ITEM }
            item.copy(image = own ?: products.find { it.id == item.productId }?.image)
        },
    )

    override fun observeLists(): Flow<List<Checklist>> =
        store.db.filterNotNull()
            .map { db -> db.lists.sortedByDescending { it.updatedAt }.map { db.resolved(it) } }
            .onStart { store.load() }

    override fun observeList(id: String): Flow<Checklist?> =
        store.db.filterNotNull()
            .map { db -> db.lists.find { it.id == id }?.let { db.resolved(it) } }
            .onStart { store.load() }

    private fun FakeDb.withProduct(name: String): Pair<FakeDb, Product> {
        val normalized = normalizeName(name)
        products.find { normalizeName(it.name) == normalized }?.let { return this to it }
        val product = Product(Ids.create("prod-"), name.trim())
        return copy(products = products + product) to product
    }

    private fun saveImage(bytes: ByteArray): ImageRef {
        imagesDir.mkdirs()
        val file = File(imagesDir, "${Ids.create()}.jpg").apply { writeBytes(bytes) }
        return ImageRef(url = file.absolutePath, key = file.absolutePath, source = ImageSource.PRODUCT)
    }

    private fun deleteImage(image: ImageRef?) {
        image?.url?.takeIf { it.startsWith(imagesDir.absolutePath) }?.let { File(it).delete() }
    }

    override suspend fun searchProducts(listId: String, query: String): List<Product> {
        val q = normalizeName(query)
        val all = store.load().products
        val starts = all.filter { normalizeName(it.name).startsWith(q) }.sortedBy { it.name }
        val contains = all.filter { it !in starts && normalizeName(it.name).contains(q) }.sortedBy { it.name }
        return (starts + contains).take(8)
    }

    override suspend fun setProductImage(listId: String, productId: String, photo: PhotoInput) = store.update { db ->
        val product = db.products.find { it.id == productId }
            ?: throw DataException("El producto no existe", DataException.Kind.NOT_FOUND)
        deleteImage(product.image)
        val image = when (photo) {
            is PhotoInput.Jpeg -> saveImage(photo.bytes)
            is PhotoInput.Url -> ImageRef(photo.url.trim(), photo.url.trim(), ImageSource.PRODUCT)
        }
        db.copy(products = db.products.map { if (it.id == productId) it.copy(image = image) else it }) to Unit
    }

    override suspend fun removeProductImage(listId: String, productId: String) = store.update { db ->
        deleteImage(db.products.find { it.id == productId }?.image)
        db.copy(products = db.products.map { if (it.id == productId) it.copy(image = null) else it }) to Unit
    }

    override suspend fun setItemImage(listId: String, itemId: String, jpeg: ByteArray) = store.update { db ->
        val list = db.requireList(listId)
        val image = saveImage(jpeg).copy(source = ImageSource.ITEM)
        val items = list.items.map {
            if (it.id != itemId) it else {
                deleteImage(it.image?.takeIf { old -> old.source == ImageSource.ITEM })
                it.copy(image = image)
            }
        }
        db.replaceList(list.copy(items = items)) to Unit
    }

    override suspend fun removeItemImage(listId: String, itemId: String) = store.update { db ->
        val list = db.requireList(listId)
        val items = list.items.map {
            if (it.id != itemId || it.image?.source != ImageSource.ITEM) it else {
                deleteImage(it.image)
                it.copy(image = null)
            }
        }
        db.replaceList(list.copy(items = items)) to Unit
    }

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
            val (withProduct, product) = db.withProduct(clean)
            val item = ChecklistItem(
                id = Ids.create("item-"),
                text = clean,
                quantity = quantity?.trim()?.takeIf { it.isNotEmpty() },
                productId = product.id,
                createdAt = now(),
                updatedAt = now(),
            )
            withProduct.replaceList(list.copy(items = list.items + item)) to item
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
            url = "arianna://join/$code",
            listId = listId,
            expiresAt = nowInstant.plus(Duration.ofDays(7)).toString(),
        )
        db.copy(invites = valid + invite) to invite
    }

    override suspend fun joinByCode(code: String): JoinResult {
        store.simulateLatency()
        val db = store.load()
        val clean = code.trim().uppercase()
        val invite = db.invites.find {
            it.code == clean && Instant.parse(it.expiresAt).isAfter(Instant.now())
        } ?: throw DataException("Código inválido o vencido", DataException.Kind.NOT_FOUND)
        // Sin backend no existe otro usuario real: en el mock "unirse" solo abre la lista o la sección.
        invite.spaceId?.let { spaceId ->
            val space = db.spaces.find { it.id == spaceId }
                ?: throw DataException("Código inválido o vencido", DataException.Kind.NOT_FOUND)
            return JoinResult(type = "space", spaceId = spaceId, kind = space.kind)
        }
        return JoinResult(type = "list", listId = db.requireList(invite.listId!!).id)
    }
}

/** Recordatorios en el teléfono: una sola sección por tipo (no hay otros usuarios en el modo demo). */
class FakeRemindersRepository(private val store: FakeStore) : RemindersRepository {
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    private fun FakeDb.withSpace(kind: ReminderKind): Pair<FakeDb, ReminderSpace> {
        spaces.find { it.kind == kind }?.let { return this to it }
        val user = FakeSeed.currentUser
        val space = ReminderSpace("space-${kind.apiName}", kind, user.id, user.name)
        return copy(spaces = spaces + space) to space
    }

    private fun FakeDb.requireReminder(id: String): Reminder =
        reminders.find { it.id == id } ?: throw DataException("El recordatorio no existe", DataException.Kind.NOT_FOUND)

    private fun FakeDb.replaceReminder(reminder: Reminder): FakeDb =
        copy(reminders = reminders.map { if (it.id == reminder.id) reminder.copy(updatedAt = now()) else it })

    override fun observeReminders(kind: ReminderKind): Flow<List<Reminder>> = store.db
        .filterNotNull()
        .map { db -> db.reminders.filter { it.kind == kind }.sortedWith(compareBy({ it.nextDue }, { it.title })) }
        .onStart { store.load() }

    override fun observeSpaces(kind: ReminderKind): Flow<List<ReminderSpace>> = store.db
        .filterNotNull()
        .map { db -> db.withSpace(kind).first.spaces.filter { it.kind == kind } }

    override suspend fun refresh(kind: ReminderKind) {
        store.update { db -> db.withSpace(kind).first to Unit }
    }

    private fun validate(draft: ReminderDraft) {
        if (draft.title.isBlank()) throw DataException("Ponle un nombre", DataException.Kind.INVALID)
        if ((draft.every == null) != (draft.unit == null)) {
            throw DataException("Indica cada cuánto se repite", DataException.Kind.INVALID)
        }
    }

    override suspend fun create(kind: ReminderKind, draft: ReminderDraft, spaceId: String?): Reminder {
        validate(draft)
        return store.update { db ->
            val (withSpace, space) = db.withSpace(kind)
            val reminder = Reminder(
                id = Ids.create("r-"),
                spaceId = space.id,
                kind = kind,
                title = draft.title.trim(),
                amount = draft.amount,
                every = draft.every,
                unit = draft.unit,
                nextDue = draft.nextDue.toString(),
                notifyDaysBefore = draft.notifyDaysBefore,
                note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now(),
                updatedAt = now(),
            )
            withSpace.copy(
                reminders = withSpace.reminders + reminder,
                anchorDays = withSpace.anchorDays + (reminder.id to draft.nextDue.dayOfMonth),
            ) to reminder
        }
    }

    override suspend fun update(reminder: Reminder, draft: ReminderDraft) {
        validate(draft)
        store.update { db ->
            val current = db.requireReminder(reminder.id)
            val dateChanged = current.nextDue != draft.nextDue.toString()
            val next = current.copy(
                title = draft.title.trim(),
                amount = draft.amount,
                every = draft.every,
                unit = draft.unit,
                nextDue = draft.nextDue.toString(),
                notifyDaysBefore = draft.notifyDaysBefore,
                note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                finished = if (dateChanged) false else current.finished,
            )
            val anchors = if (dateChanged) db.anchorDays + (reminder.id to draft.nextDue.dayOfMonth) else db.anchorDays
            db.replaceReminder(next).copy(anchorDays = anchors) to Unit
        }
    }

    override suspend fun delete(reminder: Reminder) {
        store.update { db ->
            db.copy(
                reminders = db.reminders.filterNot { it.id == reminder.id },
                reminderLogs = db.reminderLogs - reminder.id,
                anchorDays = db.anchorDays - reminder.id,
            ) to Unit
        }
    }

    override suspend fun markDone(reminder: Reminder, amount: Double?, note: String?): Reminder =
        store.update { db ->
            val current = db.requireReminder(reminder.id)
            if (current.finished) throw DataException("Este recordatorio ya está hecho", DataException.Kind.INVALID)
            val user = FakeSeed.currentUser
            val log = ReminderLog(
                id = Ids.create("log-"),
                dueDate = current.nextDue,
                amount = amount ?: current.amount,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                doneBy = user.id,
                doneByName = user.name,
                createdAt = now(),
            )
            val every = current.every
            val unit = current.unit
            val next = if (every != null && unit != null) {
                val due = Recurrence.advance(current.dueDate, every, unit, db.anchorDays[current.id])
                current.copy(nextDue = due.toString(), lastDone = log)
            } else {
                current.copy(finished = true, lastDone = log)
            }
            val logs = listOf(log) + db.reminderLogs[current.id].orEmpty()
            db.replaceReminder(next).copy(reminderLogs = db.reminderLogs + (current.id to logs)) to next
        }

    override suspend fun undo(reminder: Reminder) {
        store.update { db ->
            val current = db.requireReminder(reminder.id)
            val logs = db.reminderLogs[current.id].orEmpty()
            val last = logs.firstOrNull() ?: throw DataException("No hay nada que deshacer", DataException.Kind.INVALID)
            val rest = logs.drop(1)
            val next = current.copy(nextDue = last.dueDate, finished = false, lastDone = rest.firstOrNull())
            db.replaceReminder(next).copy(reminderLogs = db.reminderLogs + (current.id to rest)) to Unit
        }
    }

    override suspend fun history(reminder: Reminder): List<ReminderLog> {
        store.simulateLatency()
        return store.load().reminderLogs[reminder.id].orEmpty()
    }

    private suspend fun changeMembers(space: ReminderSpace, change: (List<SharedMember>) -> List<SharedMember>) {
        store.update { db ->
            val (withSpace, current) = db.withSpace(space.kind)
            val updated = current.copy(members = change(current.members))
            withSpace.copy(spaces = withSpace.spaces.map { if (it.id == updated.id) updated else it }) to Unit
        }
    }

    override suspend fun inviteToSpace(space: ReminderSpace, email: String, role: MemberRole): SharedMember {
        val normalized = email.trim().lowercase()
        if (!emailRegex.matches(normalized)) throw DataException("Correo no válido", DataException.Kind.INVALID)
        if (normalized == FakeSeed.currentUser.email) {
            throw DataException("No puedes invitarte a ti mismo", DataException.Kind.INVALID)
        }
        val member = SharedMember(email = normalized, role = role, status = MemberStatus.PENDING, invitedAt = now())
        changeMembers(space) { members -> members.filterNot { it.email == normalized } + member }
        return member
    }

    override suspend fun removeSpaceMember(space: ReminderSpace, email: String) =
        changeMembers(space) { members -> members.filterNot { it.email == email } }

    override suspend fun createSpaceInvite(space: ReminderSpace): ShareInvite = store.update { db ->
        val nowInstant = Instant.now()
        val valid = db.invites.filter { Instant.parse(it.expiresAt).isAfter(nowInstant) }
        valid.find { it.spaceId == space.id }?.let { return@update db.copy(invites = valid) to it }
        val code = Ids.shareCode()
        val invite = ShareInvite(
            code = code,
            url = "arianna://join/$code",
            spaceId = space.id,
            expiresAt = nowInstant.plus(Duration.ofDays(7)).toString(),
        )
        db.copy(invites = valid + invite) to invite
    }
}
