package com.arianna.app.ui.reminders

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
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.Load
import com.arianna.app.util.Dates
import com.arianna.app.util.Recurrence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

/** Atajo para crear un recordatorio común con un toque. */
data class Suggestion(val title: String, val every: Int, val unit: RepeatUnit)

private fun monthly(vararg titles: String) = titles.map { Suggestion(it, 1, RepeatUnit.MONTH) }

/** Textos que cambian según la sección. */
data class KindCopy(
    val title: String,
    val overline: String,
    val newLabel: String,
    val doneLabel: String,
    val doneTitle: String,
    val donePast: String,
    val namePlaceholder: String,
    val empty: String,
    val shareIntro: String,
    /** "pago"/"pagos", "recordatorio"/"recordatorios". */
    val one: String = "recordatorio",
    val many: String = "recordatorios",
    val suggestions: List<Suggestion> = emptyList(),
    /** Etiqueta del monto: lo que se paga o lo que cuesta el mantenimiento. */
    val amountLabel: String = "Costo",
) {
    companion object {
        fun of(kind: ReminderKind): KindCopy = when (kind) {
            ReminderKind.PAYMENTS -> KindCopy(
                title = "Pagos",
                overline = "Recordatorios",
                newLabel = "Nuevo pago",
                doneLabel = "Ya pagué",
                doneTitle = "Marcar como pagado",
                donePast = "pagado",
                namePlaceholder = "Luz, internet, tarjeta…",
                empty = "Agrega la luz, el internet o la tarjeta y te aviso antes de que venzan.",
                shareIntro = "Quien invites verá todos tus pagos y podrá marcarlos como pagados. Ideal para la casa.",
                one = "pago",
                amountLabel = "Monto",
                many = "pagos",
                suggestions = monthly("Luz", "Agua", "Internet", "Plan del celular", "Tarjeta de crédito", "Arriendo"),
            )
            ReminderKind.HOME -> KindCopy(
                title = "Mi hogar",
                overline = "Mantenimientos",
                newLabel = "Nuevo recordatorio",
                doneLabel = "Hecho",
                doneTitle = "Marcar como hecho",
                donePast = "hecho",
                namePlaceholder = "Limpiar el aire, filtro de agua…",
                empty = "Agrega el mantenimiento del aire, la lavadora o la fumigación y te aviso.",
                shareIntro = "Quien invites verá y podrá marcar los mantenimientos de la casa.",
                one = "mantenimiento",
                many = "mantenimientos",
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
                overline = "Mantenimientos",
                newLabel = "Nuevo recordatorio",
                doneLabel = "Hecho",
                doneTitle = "Marcar como hecho",
                donePast = "hecho",
                namePlaceholder = "Matrícula, cambio de aceite…",
                empty = "Agrega la matrícula, el SOAT o el cambio de aceite y te aviso.",
                shareIntro = "Quien invites verá y podrá marcar los recordatorios del carro.",
            )
        }
    }
}

sealed interface ReminderDialog {
    /** reminder null = nuevo. */
    data class Edit(val reminder: Reminder?) : ReminderDialog
    data class Done(val reminder: Reminder) : ReminderDialog
    data class Detail(val reminderId: String) : ReminderDialog
    data object Share : ReminderDialog
    data class Delete(val reminder: Reminder) : ReminderDialog
}

data class RemindersUiState(
    val kind: ReminderKind,
    val reminders: List<Reminder> = emptyList(),
    val spaces: List<ReminderSpace> = emptyList(),
    val myId: String = "",
    val dialog: ReminderDialog? = null,
    val history: List<ReminderLog>? = null,
    val invite: ShareInvite? = null,
    val busy: Boolean = false,
    val load: Load = Load.LOADING,
    val today: LocalDate = LocalDate.now(),
) {
    val copy: KindCopy = KindCopy.of(kind)
    private val active = reminders.filterNot { it.finished }
    val overdue: List<Reminder> = active.filter { it.dueDate < today }
    val soon: List<Reminder> = active.filter { it.dueDate >= today && it.dueDate <= today.plusDays(7) }
    val later: List<Reminder> = active.filter { it.dueDate > today.plusDays(7) }
    val finished: List<Reminder> = reminders.filter { it.finished }

    /** Lo que vence este mes (incluye lo vencido) y aún no se marca. */
    val monthTotal: Double = active
        .filter { YearMonth.from(it.dueDate) <= YearMonth.from(today) }
        .sumOf { it.amount ?: 0.0 }
    val overdueTotal: Double = overdue.sumOf { it.amount ?: 0.0 }

    val ownSpace: ReminderSpace? = spaces.find { it.ownerId == myId }
    val sharedWithMe: List<ReminderSpace> = spaces.filter { it.ownerId != myId }

    /** Secciones donde puedo crear: la mía y las compartidas conmigo como editor. */
    val writableSpaces: List<ReminderSpace> = spaces.filter { space ->
        space.ownerId == myId || space.members.any { it.userId == myId && it.role == MemberRole.EDITOR }
    }

    fun canEdit(reminder: Reminder): Boolean = writableSpaces.any { it.id == reminder.spaceId }

    /** "De Ana" si el recordatorio es de una sección compartida conmigo. */
    fun ownerLabel(reminder: Reminder): String? =
        spaces.find { it.id == reminder.spaceId }?.takeIf { it.ownerId != myId }?.let { "De ${it.ownerName}" }

    val detail: Reminder? = (dialog as? ReminderDialog.Detail)?.let { d -> reminders.find { it.id == d.reminderId } }

    /** Personas que comparten conmigo mi sección (aceptadas o pendientes). */
    val acceptedMembers: Int = ownSpace?.members?.count { it.status == MemberStatus.ACCEPTED } ?: 0
}

class RemindersViewModel(
    kind: ReminderKind,
    private val repository: RemindersRepository,
    myId: String,
    private val myEmail: String,
    val demoMode: Boolean,
) : BaseViewModel() {
    private val ui = MutableStateFlow(RemindersUiState(kind = kind, myId = myId))

    val state: StateFlow<RemindersUiState> = loadingState(ui.value, { copy(load = it) }) {
        combine(ui, repository.observeReminders(kind), repository.observeSpaces(kind)) { current, reminders, spaces ->
            current.copy(reminders = reminders, spaces = spaces, today = LocalDate.now())
        }
    }

    private val kind get() = ui.value.kind

    fun open(dialog: ReminderDialog?) {
        ui.value = ui.value.copy(dialog = dialog, history = null)
        if (dialog is ReminderDialog.Detail) loadHistory(dialog.reminderId)
    }

    fun close() = open(null)

    private fun loadHistory(reminderId: String) = launchSafely {
        val reminder = state.value.reminders.find { it.id == reminderId } ?: return@launchSafely
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

    fun save(existing: Reminder?, draft: ReminderDraft, spaceId: String?) = withBusy {
        if (existing == null) {
            val created = repository.create(kind, draft, spaceId)
            notify("Listo: te aviso ${Recurrence.noticeLabel(created.notifyDaysBefore).lowercase()} de «${created.title}»")
        } else {
            repository.update(existing, draft)
            notify("Cambios guardados")
        }
        close()
    }

    fun markDone(reminder: Reminder, amount: Double?, note: String?) = withBusy {
        val updated = repository.markDone(reminder, amount, note)
        val next = updated.takeIf { it.repeats && !it.finished }?.let { " · Próximo: ${Dates.shortDay(it.dueDate).lowercase()}" }
        notify("«${reminder.title}» ${state.value.copy.donePast}${next.orEmpty()}")
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

    // ---- compartir la sección propia

    fun createInvite() = withBusy {
        val space = state.value.ownSpace ?: return@withBusy
        ui.value = ui.value.copy(invite = repository.createSpaceInvite(space))
    }

    fun invite(email: String, role: MemberRole, onDone: () -> Unit) = withBusy {
        val space = state.value.ownSpace ?: return@withBusy
        val member = repository.inviteToSpace(space, email, role)
        notify(
            if (member.status == MemberStatus.ACCEPTED) "${member.name ?: member.email} ya ve tus ${state.value.copy.title.lowercase()}"
            else "Invitación guardada: la verá cuando entre con ese correo",
        )
        onDone()
    }

    fun removeMember(email: String) = launchSafely {
        state.value.ownSpace?.let { repository.removeSpaceMember(it, email) }
    }

    /** Dejar de ver la sección que otra persona compartió conmigo. */
    fun leave(space: ReminderSpace) = launchSafely {
        repository.removeSpaceMember(space, myEmail)
        notify("Ya no ves los ${state.value.copy.title.lowercase()} de ${space.ownerName}")
    }
}
