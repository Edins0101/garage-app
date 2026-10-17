package com.arianna.app.ui.reminders

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.ui.graphics.vector.ImageVector
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.Group
import com.arianna.app.domain.repository.GroupsRepository
import com.arianna.app.domain.repository.ShareTarget
import com.arianna.app.domain.model.CountFrom
import com.arianna.app.domain.model.CounterRole
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.MemberStatus
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.model.ReminderLog
import com.arianna.app.domain.model.ReminderSpace
import com.arianna.app.domain.model.RepeatUnit
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.repository.ReminderDraft
import com.arianna.app.domain.repository.RemindersRepository
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.Load
import com.arianna.app.util.Dates
import com.arianna.app.util.Recurrence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

/** Atajo para crear un recordatorio común con un toque. every null = una sola vez. */
data class Suggestion(
    val title: String,
    val every: Int?,
    val unit: RepeatUnit?,
    val countFrom: CountFrom? = null,
    val everyKm: Int? = null,
)

private fun monthly(vararg titles: String) = titles.map { Suggestion(it, 1, RepeatUnit.MONTH) }

fun kindIcon(kind: ReminderKind): ImageVector = when (kind) {
    ReminderKind.PAYMENTS -> Icons.Outlined.Payments
    ReminderKind.HOME -> Icons.Outlined.House
    ReminderKind.CAR -> Icons.Outlined.DirectionsCar
    ReminderKind.OTHER -> Icons.Outlined.Event
}

/** Textos que cambian según la categoría. */
data class KindCopy(
    /** Nombre corto de la categoría (filtros, etiquetas). */
    val title: String,
    val doneLabel: String,
    val doneTitle: String,
    val donePast: String,
    val namePlaceholder: String,
    val shareIntro: String,
    val suggestions: List<Suggestion> = emptyList(),
    /** Etiqueta del monto: lo que se paga o lo que cuesta. */
    val amountLabel: String = "Costo",
) {
    companion object {
        fun of(kind: ReminderKind): KindCopy = when (kind) {
            ReminderKind.PAYMENTS -> KindCopy(
                title = "Pagos",
                doneLabel = "Ya pagué",
                doneTitle = "Marcar como pagado",
                donePast = "pagado",
                namePlaceholder = "Luz, internet, tarjeta…",
                shareIntro = "Quien invites verá todos tus pagos y podrá marcarlos como pagados. Ideal para la casa.",
                amountLabel = "Monto",
                suggestions = monthly("Luz", "Agua", "Internet", "Plan del celular", "Tarjeta de crédito", "Arriendo"),
            )
            ReminderKind.HOME -> KindCopy(
                title = "Mi hogar",
                doneLabel = "Hecho",
                doneTitle = "Marcar como hecho",
                donePast = "hecho",
                namePlaceholder = "Limpiar el aire, filtro de agua…",
                shareIntro = "Quien invites verá y podrá marcar los mantenimientos de la casa.",
                suggestions = listOf(
                    Suggestion("Limpiar el aire acondicionado", 3, RepeatUnit.MONTH),
                    Suggestion("Filtro de la lavadora", 1, RepeatUnit.MONTH),
                    Suggestion("Cambiar el filtro de agua", 6, RepeatUnit.MONTH),
                    Suggestion("Fumigación", 6, RepeatUnit.MONTH),
                    Suggestion("Limpiar la refrigeradora", 3, RepeatUnit.MONTH),
                    Suggestion("Revisar el calefón", 1, RepeatUnit.YEAR),
                ),
            )
            ReminderKind.CAR -> KindCopy(
                title = "Mi carro",
                doneLabel = "Hecho",
                doneTitle = "Marcar como hecho",
                donePast = "hecho",
                namePlaceholder = "Cambio de aceite, SOAT…",
                shareIntro = "Quien invites verá y podrá marcar los recordatorios del carro.",
                suggestions = listOf(
                    Suggestion("Cambio de aceite", 3, RepeatUnit.MONTH, everyKm = 5000),
                    Suggestion("Rotación de llantas", 6, RepeatUnit.MONTH, everyKm = 10000),
                    Suggestion("Revisión de frenos", 1, RepeatUnit.YEAR, everyKm = 20000),
                    Suggestion("SOAT", 1, RepeatUnit.YEAR, CountFrom.DUE),
                    Suggestion("Seguro del carro", 1, RepeatUnit.YEAR, CountFrom.DUE),
                    Suggestion("Revisión técnica vehicular", 1, RepeatUnit.YEAR, CountFrom.DUE),
                ),
            )
            ReminderKind.OTHER -> KindCopy(
                title = "Otros",
                doneLabel = "Hecho",
                doneTitle = "Marcar como hecho",
                donePast = "hecho",
                namePlaceholder = "Cumpleaños, cita médica…",
                shareIntro = "Quien invites verá y podrá marcar estos recordatorios.",
                suggestions = listOf(
                    Suggestion("Cumpleaños de…", 1, RepeatUnit.YEAR),
                    Suggestion("Aniversario", 1, RepeatUnit.YEAR),
                    Suggestion("Cita médica", null, null),
                    Suggestion("Renovar la licencia de conducir", 5, RepeatUnit.YEAR),
                    Suggestion("Vacuna de la mascota", 1, RepeatUnit.YEAR, CountFrom.DONE),
                    Suggestion("Desparasitar a la mascota", 3, RepeatUnit.MONTH, CountFrom.DONE),
                ),
            )
        }
    }
}

sealed interface ReminderDialog {
    /** reminder null = nuevo (en [kind]). */
    data class Edit(val reminder: Reminder?, val kind: ReminderKind) : ReminderDialog
    data class Done(val reminder: Reminder) : ReminderDialog
    data class Detail(val reminderId: String) : ReminderDialog
    data class Share(val kind: ReminderKind) : ReminderDialog
    data class Delete(val reminder: Reminder) : ReminderDialog
}

data class RemindersUiState(
    /** null = todas las categorías. */
    val filter: ReminderKind? = null,
    /** Todos los recordatorios (sin filtrar). */
    val all: List<Reminder> = emptyList(),
    val spaces: List<ReminderSpace> = emptyList(),
    /** Listas que puedo ver (para adjuntarlas y mostrar su avance). */
    val lists: List<Checklist> = emptyList(),
    /** Mis grupos (para compartir una categoría). */
    val groups: List<Group> = emptyList(),
    val myId: String = "",
    val dialog: ReminderDialog? = null,
    val history: List<ReminderLog>? = null,
    val invite: ShareInvite? = null,
    val busy: Boolean = false,
    val load: Load = Load.LOADING,
    val today: LocalDate = LocalDate.now(),
) {
    val reminders: List<Reminder> = all.filter { filter == null || it.kind == filter }
    private val active = reminders.filterNot { it.finished }
    val overdue: List<Reminder> = active.filter { it.dueDate < today }
    val soon: List<Reminder> = active.filter { it.dueDate >= today && it.dueDate <= today.plusDays(7) }
    val later: List<Reminder> = active.filter { it.dueDate > today.plusDays(7) }
    val finished: List<Reminder> = reminders.filter { it.finished }

    /** Lo que vence este mes (incluye lo vencido) y aún no se marca. */
    private val dueThisMonth: List<Reminder> = active.filter { YearMonth.from(it.dueDate) <= YearMonth.from(today) }
    val monthTotal: Double = dueThisMonth.sumOf { it.amount ?: 0.0 }
    val monthCount: Int = dueThisMonth.size
    val overdueTotal: Double = overdue.sumOf { it.amount ?: 0.0 }

    /** Categoría por defecto al crear o compartir desde "Todos". */
    val defaultKind: ReminderKind = filter ?: ReminderKind.PAYMENTS

    fun ownSpace(kind: ReminderKind): ReminderSpace? = spaces.find { it.ownerId == myId && it.kind == kind }
    fun sharedWithMe(kind: ReminderKind): List<ReminderSpace> = spaces.filter { it.ownerId != myId && it.kind == kind }

    /** Secciones donde puedo crear: la mía y las compartidas conmigo (directo o por un grupo) como editor. */
    fun writableSpaces(kind: ReminderKind): List<ReminderSpace> = spaces.filter { space ->
        space.kind == kind && (space.ownerId == myId || space.role != CounterRole.VIEWER)
    }

    fun linkedList(reminder: Reminder): Checklist? = lists.find { it.id == reminder.listId }

    fun canEdit(reminder: Reminder): Boolean = writableSpaces(reminder.kind).any { it.id == reminder.spaceId }

    /** "De Ana" si el recordatorio es de una sección compartida conmigo. */
    fun ownerLabel(reminder: Reminder): String? =
        spaces.find { it.id == reminder.spaceId }?.takeIf { it.ownerId != myId }?.let { "De ${it.ownerName}" }

    val detail: Reminder? = (dialog as? ReminderDialog.Detail)?.let { d -> all.find { it.id == d.reminderId } }

    /** Con cuántas personas comparto (alguna de) mis categorías visibles. */
    val sharedCount: Int = spaces
        .filter { it.ownerId == myId && (filter == null || it.kind == filter) }
        .flatMap { space -> space.members.filter { it.status == MemberStatus.ACCEPTED }.map { it.email } }
        .distinct().size

    /** Mi carro: sugerir la matrícula si todavía no existe. */
    val suggestPlate: Boolean = filter == ReminderKind.CAR && load == Load.READY &&
        all.none { it.kind == ReminderKind.CAR && it.title.startsWith("Matrícula") }
}

class RemindersViewModel(
    filter: ReminderKind?,
    private val repository: RemindersRepository,
    lists: ListsRepository,
    private val groups: GroupsRepository,
    myId: String,
    private val myEmail: String,
    val demoMode: Boolean,
) : BaseViewModel() {
    private val ui = MutableStateFlow(RemindersUiState(filter = filter, myId = myId))

    val state: StateFlow<RemindersUiState> = loadingState(ui.value, { copy(load = it) }) {
        combine(
            ui,
            repository.observeReminders(),
            repository.observeSpaces(),
            lists.observeLists().catch { emit(emptyList()) },
            groups.observeGroups().catch { emit(emptyList()) },
        ) { current, reminders, spaces, allLists, myGroups ->
            current.copy(all = reminders, spaces = spaces, lists = allLists, groups = myGroups, today = LocalDate.now())
        }
    }

    fun setFilter(filter: ReminderKind?) {
        ui.value = ui.value.copy(filter = filter)
    }

    fun open(dialog: ReminderDialog?) {
        // El código de invitación es de una categoría: se pide de nuevo al abrir o cambiar la hoja.
        ui.value = ui.value.copy(dialog = dialog, history = null, invite = null)
        if (dialog is ReminderDialog.Detail) loadHistory(dialog.reminderId)
    }

    fun close() = open(null)

    private fun loadHistory(reminderId: String) = launchSafely {
        val reminder = state.value.all.find { it.id == reminderId } ?: return@launchSafely
        val history = repository.history(reminder)
        if ((ui.value.dialog as? ReminderDialog.Detail)?.reminderId == reminderId) {
            ui.value = ui.value.copy(history = history)
        }
    }

    private fun withBusy(block: suspend () -> Unit) = launchSafely {
        ui.value = ui.value.copy(busy = true)
        try {
            block()
        } finally {
            ui.value = ui.value.copy(busy = false)
        }
    }

    fun save(existing: Reminder?, kind: ReminderKind, draft: ReminderDraft, spaceId: String?) = withBusy {
        if (existing == null) {
            val created = repository.create(kind, draft, spaceId)
            val notice = created.time?.let { "${Recurrence.alertLabel(created.alertMinutes ?: 0).lowercase()} (${Dates.time(it)})" }
                ?: Recurrence.noticeLabel(created.notifyDaysBefore).lowercase()
            notify("Listo: te aviso $notice de «${created.title}»")
        } else {
            repository.update(existing, draft)
            notify("Cambios guardados")
        }
        close()
    }

    fun markDone(reminder: Reminder, amount: Double?, note: String?, odometer: Int?) = withBusy {
        val updated = repository.markDone(reminder, amount, note, odometer)
        val next = updated.takeIf { it.repeats && !it.finished }?.let { " · Próximo: ${Dates.shortDay(it.dueDate).lowercase()}" }
        notify("«${reminder.title}» ${KindCopy.of(reminder.kind).donePast}${next.orEmpty()}")
        close()
    }

    fun undo(reminder: Reminder) = withBusy {
        repository.undo(reminder)
        notify("Se deshizo la última marca de «${reminder.title}»")
        loadHistory(reminder.id)
    }

    fun delete(reminder: Reminder) = withBusy {
        repository.delete(reminder)
        notify("«${reminder.title}» eliminado")
        close()
    }

    /** Matrícula del carro según el último dígito de la placa (calendario nacional de la ANT). */
    fun createPlateReminder(digit: Int) = withBusy {
        val due = Plates.nextRegistration(digit, LocalDate.now())
        val draft = ReminderDraft(
            title = "Matrícula del carro",
            amount = null,
            every = 1,
            unit = RepeatUnit.YEAR,
            nextDue = due,
            notifyDaysBefore = 7,
            note = "Placa terminada en $digit: te toca en ${Dates.monthName(YearMonth.from(due)).lowercase()}. " +
                "Confirma las fechas en la ANT o en la agencia de tránsito de tu ciudad.",
            countFrom = CountFrom.DUE,
        )
        repository.create(ReminderKind.CAR, draft)
        notify("Listo: te aviso una semana antes de ${Dates.monthName(YearMonth.from(due)).lowercase()}")
        close()
    }

    // ---- compartir una categoría propia

    fun createInvite(kind: ReminderKind) = withBusy {
        val space = state.value.ownSpace(kind) ?: return@withBusy
        ui.value = ui.value.copy(invite = repository.createSpaceInvite(space))
    }

    fun invite(kind: ReminderKind, email: String, role: MemberRole, onDone: () -> Unit) = withBusy {
        val space = state.value.ownSpace(kind) ?: return@withBusy
        val member = repository.inviteToSpace(space, email, role)
        notify(
            if (member.status == MemberStatus.ACCEPTED) "${member.name ?: member.email} ya ve tu sección ${KindCopy.of(kind).title}"
            else "Invitación guardada: la verá cuando entre con ese correo",
        )
        onDone()
    }

    fun removeMember(kind: ReminderKind, email: String) = launchSafely {
        state.value.ownSpace(kind)?.let { repository.removeSpaceMember(it, email) }
    }

    /** Compartir una categoría propia con un grupo (o dejar de hacerlo con role null). */
    fun shareWithGroup(kind: ReminderKind, group: Group, role: MemberRole?) = launchSafely {
        val space = state.value.ownSpace(kind) ?: return@launchSafely
        groups.share(ShareTarget.SpaceTarget(space.id), group, role)
        notify(
            if (role == null) "${KindCopy.of(kind).title} ya no se comparte con «${group.name}»"
            else "${KindCopy.of(kind).title} compartido con «${group.name}»",
        )
    }

    /** Dejar de ver la sección que otra persona compartió conmigo. */
    fun leave(space: ReminderSpace) = launchSafely {
        repository.removeSpaceMember(space, myEmail)
        notify("Ya no ves la sección ${KindCopy.of(space.kind).title} de ${space.ownerName}")
    }
}

/** Calendario de matriculación por último dígito de la placa (ANT, Ecuador). */
object Plates {
    /** 1 → febrero … 9 → octubre, 0 → noviembre. */
    fun month(digit: Int): Int = if (digit == 0) 11 else digit + 1

    /** Primer día del próximo mes de matriculación (este año si aún no termina ese mes). */
    fun nextRegistration(digit: Int, today: LocalDate): LocalDate {
        val thisYear = LocalDate.of(today.year, month(digit), 1)
        return if (YearMonth.from(thisYear) >= YearMonth.from(today)) {
            if (thisYear < today) today else thisYear
        } else {
            thisYear.plusYears(1)
        }
    }
}
