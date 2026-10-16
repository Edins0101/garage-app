package com.arianna.app.ui.home

import androidx.lifecycle.viewModelScope
import com.arianna.app.data.prefs.HomePrefs
import com.arianna.app.data.prefs.HomePrefsStore
import com.arianna.app.data.prefs.HomeSection
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.model.GarageDay
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.repository.RemindersRepository
import com.arianna.app.domain.repository.AuthRepository
import com.arianna.app.domain.repository.GarageRepository
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.Load
import com.arianna.app.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUiState(
    val userName: String = "",
    val days: List<GarageDay> = emptyList(),
    val lists: List<Checklist> = emptyList(),
    /** Cualquier día de la semana que se muestra en la agenda. */
    val weekAnchor: LocalDate = LocalDate.now(),
    val selected: LocalDate = LocalDate.now(),
    /** Monto pendiente de pagar al garaje hasta hoy. */
    val pending: Double = 0.0,
    /** Qué secciones ve el usuario en el inicio y en qué orden. */
    val prefs: HomePrefs = HomePrefs(configured = true),
    /** Recordatorios de todas las categorías, por fecha de vencimiento. */
    val reminders: List<Reminder> = emptyList(),
    val load: Load = Load.LOADING,
) {
    private val today = LocalDate.now()
    private val activeReminders = reminders.filterNot { it.finished }

    /** Lo que vence en los próximos 7 días o ya venció. */
    private val urgent: List<Reminder> = activeReminders.filter { it.dueDate <= today.plusDays(7) }
    val upcomingReminders: List<Reminder> = activeReminders.take(3)

    val shoppingLists: List<Checklist> = lists.filter { it.kind == ChecklistKind.SHOPPING }
    val todoLists: List<Checklist> = lists.filter { it.kind == ChecklistKind.TODO }

    val markedDates: Set<LocalDate> = days.map { it.localDate }.toSet()
    val noteDates: Set<LocalDate> = days.filter { it.note != null }.map { it.localDate }.toSet()
    val selectedDay: GarageDay? = days.find { it.localDate == selected }
    val daysThisMonth: Int = days.count {
        it.localDate.year == weekAnchor.year && it.localDate.month == weekAnchor.month
    }

    /** Lo que Arianna resume en el saludo, solo de las secciones que el usuario ve. */
    val greetingLine: String = when (load) {
        Load.LOADING -> "Soy Arianna, estoy revisando tus pendientes…"
        Load.ERROR -> "Soy Arianna, no pude revisar tus pendientes."
        Load.READY -> {
            val parts = prefs.visible.mapNotNull { section ->
                when (section) {
                    HomeSection.GARAGE -> if (pending > 0.005) "${Money.format(pending)} del garaje" else null
                    HomeSection.REMINDERS -> {
                        val payments = urgent.count { it.kind == ReminderKind.PAYMENTS }
                        val others = urgent.size - payments
                        listOfNotNull(
                            payments.takeIf { it > 0 }?.let { if (it == 1) "1 pago por vencer" else "$it pagos por vencer" },
                            others.takeIf { it > 0 }?.let { if (it == 1) "1 recordatorio" else "$it recordatorios" },
                        ).joinToString(" · ").ifEmpty { null }
                    }
                    HomeSection.SHOPPING -> openItems(shoppingLists).takeIf { it > 0 }?.let { "$it por comprar" }
                    HomeSection.TASKS -> openItems(todoLists).takeIf { it > 0 }?.let { if (it == 1) "1 tarea" else "$it tareas" }
                }
            }
            if (parts.isEmpty()) "Soy Arianna. Todo al día por hoy ✨"
            else "Soy Arianna, esto tienes pendiente:\n" + parts.joinToString(" · ")
        }
    }
}

private fun openItems(lists: List<Checklist>): Int = lists.sumOf { list -> list.items.count { !it.done } }

class HomeViewModel(
    private val auth: AuthRepository,
    private val garage: GarageRepository,
    lists: ListsRepository,
    reminders: RemindersRepository,
    private val prefsStore: HomePrefsStore,
) : BaseViewModel() {

    private val userId = auth.session.value?.user?.id ?: "local"
    private val prefs = prefsStore.observe(userId)
    private val userName = MutableStateFlow("")
    private val week = MutableStateFlow(LocalDate.now() to LocalDate.now())

    val state: StateFlow<HomeUiState> = loadingState(HomeUiState(), { copy(load = it) }) {
        combine(
            combine(userName, prefs) { name, homePrefs -> name to homePrefs },
            garage.observeDays(),
            lists.observeLists(),
            week,
            combine(garage.observeSummary(), reminders.observeReminders()) { summary, all -> summary to all },
        ) { (name, homePrefs), days, allLists, (anchor, selected), (summary, payments) ->
            HomeUiState(name, days, allLists, anchor, selected, summary.pending, homePrefs, payments)
        }
    }

    init {
        launchSafely { userName.value = auth.currentUser().name }
    }

    fun previousWeek() = shiftWeek(-1)
    fun nextWeek() = shiftWeek(1)

    private fun shiftWeek(weeks: Long) {
        val (anchor, selected) = week.value
        week.value = anchor.plusWeeks(weeks) to selected.plusWeeks(weeks)
    }

    fun logout() = auth.logout()

    /** Primera elección: qué quiere ver primero. */
    fun choosePrimary(section: HomeSection) = prefsStore.save(userId, prefs.value.withPrimary(section))
    fun moveSection(section: HomeSection, delta: Int) = prefsStore.save(userId, prefs.value.moved(section, delta))
    fun toggleSection(section: HomeSection) = prefsStore.save(userId, prefs.value.toggled(section))

    fun select(date: LocalDate) {
        week.value = week.value.first to date
    }

    /** Marca o desmarca el día seleccionado. */
    fun toggleSelected() = launchSafely {
        val date = state.value.selected
        if (state.value.selectedDay != null) garage.removeDay(date) else garage.setDay(date)
    }
}
