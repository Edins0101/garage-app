package com.arianna.app.ui.garage

import androidx.lifecycle.viewModelScope
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.GarageDay
import com.arianna.app.domain.model.Group
import com.arianna.app.domain.repository.GroupsRepository
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.repository.ShareTarget
import com.arianna.app.domain.model.GarageSettings
import com.arianna.app.domain.model.GarageSummary
import com.arianna.app.domain.model.Payment
import com.arianna.app.domain.model.Counter
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.MemberStatus
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.model.CounterDirection
import com.arianna.app.domain.repository.CounterDraft
import com.arianna.app.domain.repository.CountersRepository
import com.arianna.app.ui.counters.CounterCopy
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.Load
import com.arianna.app.util.Dates
import com.arianna.app.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** Qué hace un toque sobre el calendario: un día (marca o abre su detalle) o un rango. */
enum class GarageMode { DAY, RANGE }

/** Diálogo abierto en la pantalla. */
sealed interface GarageDialog {
    data class Day(val date: LocalDate) : GarageDialog
    data object Settings : GarageDialog
    data object Payment : GarageDialog
    /** Cambiar nombre, ícono o "yo pago / me pagan" del contador. */
    data object EditCounter : GarageDialog
    data object DeleteCounter : GarageDialog
    data object Share : GarageDialog
    /** Dejar de ver un contador que otra persona compartió conmigo. */
    data object Leave : GarageDialog
}

data class GarageUiState(
    val month: YearMonth = YearMonth.now(),
    val mode: GarageMode = GarageMode.DAY,
    val rangeStart: LocalDate? = null,
    val days: List<GarageDay> = emptyList(),
    val settings: GarageSettings = GarageSettings(),
    val summary: GarageSummary = GarageSummary(),
    val payments: List<Payment> = emptyList(),
    val dialog: GarageDialog? = null,
    /** El contador que se está viendo (nombre, ícono, dirección). */
    val counter: Counter? = null,
    /** Listas que puedo ver (lista adjunta) y mis grupos (para compartir). */
    val lists: List<Checklist> = emptyList(),
    val groups: List<Group> = emptyList(),
    val invite: ShareInvite? = null,
    val busy: Boolean = false,
    val load: Load = Load.LOADING,
) {
    val canEdit: Boolean = counter?.canEdit ?: true
    val linkedList: Checklist? = counter?.listId?.let { id -> lists.find { it.id == id } }
    val mine: Boolean = counter?.mine ?: true
    val copy: CounterCopy = CounterCopy.of(counter?.direction ?: CounterDirection.PAY)
    val markedDates: Set<LocalDate> = days.map { it.localDate }.toSet()
    /** Hasta esta fecha todo está pagado y cerrado. */
    val paidUntil: LocalDate? = summary.paidUntilDate
    val paidDates: Set<LocalDate> = paidUntil?.let { limit -> markedDates.filter { it <= limit }.toSet() }.orEmpty()
    val paymentDates: Set<LocalDate> = payments.map { it.localDate }.toSet()
    fun isClosed(date: LocalDate): Boolean = paidUntil?.let { date <= it } ?: false
    val dialogPayments: List<Payment> = (dialog as? GarageDialog.Day)?.let { d -> payments.filter { it.localDate == d.date } }.orEmpty()
    /** Días con nota o cargos extra (llevan un punto en el calendario). */
    val noteDates: Set<LocalDate> =
        days.filter { it.note != null || it.charges.isNotEmpty() }.map { it.localDate }.toSet()
    val monthDays: List<GarageDay> = days.filter { YearMonth.from(it.localDate) == month }
    val monthTotal: Double = monthDays.sumOf { it.total }
    val dialogDay: GarageDay? = (dialog as? GarageDialog.Day)?.let { d -> days.find { it.localDate == d.date } }

    val hint: String = when (mode) {
        GarageMode.DAY -> "Toca un día libre para marcarlo. Toca un día marcado para ver su detalle, cambiarlo o quitarlo."
        GarageMode.RANGE -> rangeStart?.let { "Inicio: ${Dates.longDay(it)}. Ahora toca el último día." }
            ?: "Toca el primer día y luego el último para marcar todo el rango."
    }
}

private data class LocalState(
    val month: YearMonth = YearMonth.now(),
    val mode: GarageMode = GarageMode.DAY,
    val rangeStart: LocalDate? = null,
    val dialog: GarageDialog? = null,
    val invite: ShareInvite? = null,
    val busy: Boolean = false,
)

private data class Related(val ui: LocalState, val counter: Counter?, val lists: List<Checklist>, val groups: List<Group>)

/** Calendario, tarifa, cargos y pagos de un contador (el garaje es uno más). */
class GarageViewModel(
    private val counterId: String,
    private val counters: CountersRepository,
    lists: ListsRepository,
    private val groups: GroupsRepository,
    private val myEmail: String,
    val demoMode: Boolean,
) : BaseViewModel() {

    private val garage = counters.counter(counterId)
    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<GarageUiState> = loadingState(GarageUiState(), { copy(load = it) }) {
        combine(
            combine(
                local,
                counters.observeCounters(),
                lists.observeLists().catch { emit(emptyList()) },
                groups.observeGroups().catch { emit(emptyList()) },
            ) { ui, all, allLists, myGroups -> Related(ui, all.find { it.id == counterId }, allLists, myGroups) },
            garage.observeDays(),
            garage.observeSettings(),
            garage.observeSummary(),
            garage.observePayments(),
        ) { (ui, counter, allLists, myGroups), days, settings, summary, payments ->
            GarageUiState(
                ui.month, ui.mode, ui.rangeStart, days, settings, summary, payments, ui.dialog, counter, allLists, myGroups,
                ui.invite, ui.busy,
            )
        }
    }

    /** "Solo ver": avisa en vez de cambiar nada. Devuelve true si se puede editar. */
    private fun editable(): Boolean {
        if (!state.value.canEdit) notify("Solo puedes ver esta cuenta")
        return state.value.canEdit
    }

    fun openShare() = local.update { it.copy(dialog = GarageDialog.Share, invite = null) }
    fun openLeave() = local.update { it.copy(dialog = GarageDialog.Leave) }

    private fun withBusy(block: suspend () -> Unit) = launchSafely {
        local.update { it.copy(busy = true) }
        try {
            block()
        } finally {
            local.update { it.copy(busy = false) }
        }
    }

    fun createInvite() = withBusy {
        val counter = state.value.counter ?: return@withBusy
        val invite = counters.createInvite(counter)
        local.update { it.copy(invite = invite) }
    }

    fun invite(email: String, role: MemberRole, onDone: () -> Unit) = withBusy {
        val counter = state.value.counter ?: return@withBusy
        val member = counters.invite(counter, email, role)
        notify(
            if (member.status == MemberStatus.ACCEPTED) "${member.name ?: member.email} ya ve «${counter.name}»"
            else "Invitación guardada: la verá cuando entre con ese correo",
        )
        onDone()
    }

    fun removeMember(email: String) = launchSafely {
        state.value.counter?.let { counters.removeMember(it, email) }
    }

    /** Compartir con un grupo (o dejar de hacerlo con role null). */
    fun shareWithGroup(group: Group, role: MemberRole?) = launchSafely {
        groups.share(ShareTarget.CounterTarget(counterId), group, role)
        notify(if (role == null) "Ya no se comparte con «${group.name}»" else "Compartido con «${group.name}»")
    }

    fun leave(onLeft: () -> Unit) = launchSafely {
        val counter = state.value.counter ?: return@launchSafely
        counters.removeMember(counter, myEmail)
        notify("Ya no ves «${counter.name}»")
        onLeft()
    }

    fun openEditCounter() = local.update { it.copy(dialog = GarageDialog.EditCounter) }
    fun openDeleteCounter() = local.update { it.copy(dialog = GarageDialog.DeleteCounter) }

    fun saveCounter(draft: CounterDraft) = launchSafely {
        val counter = state.value.counter ?: return@launchSafely
        counters.update(counter, draft)
        notify("Cuenta guardada")
        closeDialog()
    }

    fun deleteCounter(onDeleted: () -> Unit) = launchSafely {
        val counter = state.value.counter ?: return@launchSafely
        counters.delete(counter)
        notify("«${counter.name}» eliminado")
        onDeleted()
    }

    fun previousMonth() = local.update { it.copy(month = it.month.minusMonths(1)) }
    fun nextMonth() = local.update { it.copy(month = it.month.plusMonths(1)) }
    fun setMode(mode: GarageMode) = local.update { it.copy(mode = mode, rangeStart = null) }

    fun openDay(date: LocalDate) = local.update { it.copy(dialog = GarageDialog.Day(date)) }
    fun openSettings() {
        if (!state.value.mine) return notify("Solo el dueño de la cuenta cambia la tarifa")
        local.update { it.copy(dialog = GarageDialog.Settings) }
    }

    fun openPayment() {
        if (editable()) local.update { it.copy(dialog = GarageDialog.Payment) }
    }
    fun closeDialog() = local.update { it.copy(dialog = null) }

    /**
     * Un día marcado (o con un pago) abre su detalle; uno libre se marca. Lo pagado no se cambia:
     * solo se puede ver. Quien solo ve el contador también abre el detalle sin poder editar.
     */
    fun onDayClick(date: LocalDate) {
        val current = state.value
        val opensDetail = date in current.markedDates || date in current.paymentDates
        if (opensDetail && (current.mode == GarageMode.DAY || !current.canEdit)) return openDay(date)
        if (current.isClosed(date)) return notify(closedMessage())
        if (!editable()) return
        when (current.mode) {
            GarageMode.DAY -> launchSafely { garage.setDay(date) }
            GarageMode.RANGE -> {
                val start = local.value.rangeStart
                if (start == null) {
                    local.update { it.copy(rangeStart = date) }
                } else {
                    local.update { it.copy(rangeStart = null) }
                    launchSafely {
                        // Los días ya pagados se saltan.
                        val range = Dates.between(minOf(start, date), maxOf(start, date)).filterNot(current::isClosed)
                        garage.setDays(range)
                        notify("${range.size} días marcados en ${state.value.counter?.name ?: "la cuenta"}")
                    }
                }
            }
        }
    }

    private fun closedMessage(): String =
        "Ese día ya está pagado (cubierto hasta el ${state.value.paidUntil?.let(Dates::shortDay)?.lowercase()}). " +
            "Para cambiarlo, borra el pago."

    /** "Solo ver" o ya pagado: avisa en vez de cambiar nada. */
    private fun changeable(date: LocalDate): Boolean {
        if (state.value.isClosed(date)) {
            notify(closedMessage())
            return false
        }
        return editable()
    }

    /** Guarda nota y tarifa del día (marca el día si no lo estaba). */
    fun saveDay(date: LocalDate, note: String, rateText: String) = launchSafely {
        if (!changeable(date)) return@launchSafely
        val rate = rateText.takeIf { it.isNotBlank() }?.let {
            Money.parse(it) ?: return@launchSafely notify("La tarifa no es un monto válido")
        }
        garage.setDay(date, note)
        val current = state.value.days.find { it.localDate == date }?.rate
        if (rate != null && rate != current) garage.setDayRate(date, rate)
        closeDialog()
    }

    fun removeDay(date: LocalDate) = launchSafely {
        if (!changeable(date)) return@launchSafely
        garage.removeDay(date)
        closeDialog()
    }

    fun addCharge(date: LocalDate, description: String, amountText: String, onAdded: () -> Unit) = launchSafely {
        if (!changeable(date)) return@launchSafely
        val amount = Money.parse(amountText)?.takeIf { it > 0 }
            ?: return@launchSafely notify("Escribe un monto mayor a 0")
        if (description.isBlank()) return@launchSafely notify("Escribe qué es el cargo")
        garage.addCharge(date, description, amount)
        onAdded()
    }

    fun removeCharge(chargeId: String) = launchSafely {
        val day = state.value.days.find { d -> d.charges.any { it.id == chargeId } } ?: return@launchSafely
        if (changeable(day.localDate)) garage.removeCharge(chargeId)
    }

    fun saveSettings(rateText: String, autoMark: Boolean) = launchSafely {
        val rate = rateText.takeIf { it.isNotBlank() }?.let {
            Money.parse(it) ?: return@launchSafely notify("La tarifa no es un monto válido")
        }
        if (autoMark && rate == null && state.value.settings.dailyRate == null) {
            return@launchSafely notify("Configura la tarifa para usar el marcado automático")
        }
        val rateChanged = rate != null && rate != state.value.settings.dailyRate
        val autoChanged = autoMark != state.value.settings.autoMark
        if (rateChanged || autoChanged) {
            garage.updateSettings(dailyRate = rate.takeIf { rateChanged }, autoMark = autoMark.takeIf { autoChanged })
            notify(if (autoChanged && autoMark) "Listo: cada día se marcará solo" else "Configuración guardada")
        }
        closeDialog()
    }

    fun addPayment(date: LocalDate, amountText: String, note: String) = launchSafely {
        if (!editable()) return@launchSafely
        val amount = Money.parse(amountText)?.takeIf { it > 0 }
            ?: return@launchSafely notify("Escribe un monto mayor a 0")
        garage.addPayment(date, amount, note)
        notify("${state.value.copy.paymentNoun} de ${Money.format(amount)} registrado")
        closeDialog()
    }

    fun removePayment(payment: Payment) = launchSafely {
        if (!editable()) return@launchSafely
        garage.removePayment(payment.id)
        notify("${state.value.copy.paymentNoun} de ${Money.format(payment.amount)} eliminado")
    }

    private inline fun MutableStateFlow<LocalState>.update(transform: (LocalState) -> LocalState) {
        value = transform(value)
    }
}
