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
import com.arianna.app.domain.repository.CountersRepository
import com.arianna.app.domain.model.Counter
import com.arianna.app.domain.model.CounterDirection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    /** Lo pendiente hasta hoy del contador que se ve en la agenda. */
    val pending: Double = 0.0,
    /** Qué secciones ve el usuario en el inicio y en qué orden. */
    val prefs: HomePrefs = HomePrefs(configured = true),
    /** Recordatorios de todas las categorías, por fecha de vencimiento. */
    val reminders: List<Reminder> = emptyList(),
    /** Contadores por día; la agenda muestra [counter]. */
    val counters: List<Counter> = emptyList(),
    val counter: Counter? = null,
    val load: Load = Load.LOADING,
) {
    /** Lo que debo hasta hoy en todos los contadores "yo pago". */
    private val toPay: Double = counters.filter { it.direction == CounterDirection.PAY }
        .sumOf { it.summary.pending.coerceAtLeast(0.0) }
    private val toCollect: Double = counters.filter { it.direction == CounterDirection.EARN }
        .sumOf { it.summary.pending.coerceAtLeast(0.0) }

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
                    HomeSection.GARAGE -> listOfNotNull(
                        if (toPay > 0.005) {
                            // Con un solo contador se nombra ("$15 de Mi garaje"); con varios, el total.
                            val single = counters.singleOrNull { it.direction == CounterDirection.PAY }
                            "${Money.format(toPay)} " + (single?.let { "de ${it.name}" } ?: "por pagar")
                        } else null,
                        if (toCollect > 0.005) "te deben ${Money.format(toCollect)}" else null,
                    ).joinToString(" · ").ifEmpty { null }
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
    private val counters: CountersRepository,
    lists: ListsRepository,
    reminders: RemindersRepository,
    private val prefsStore: HomePrefsStore,
) : BaseViewModel() {

    private val userId = auth.session.value?.user?.id ?: "local"
    private val prefs = prefsStore.observe(userId)
    private val userName = MutableStateFlow("")
    private val week = MutableStateFlow(LocalDate.now() to LocalDate.now())
    /** Contador elegido en la agenda (null = el primero). */
    private val chosenCounter = MutableStateFlow<String?>(null)

    /** Lista de contadores + el de la agenda con sus días. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val agenda = combine(counters.observeCounters(), chosenCounter) { all, chosen ->
        all to (all.find { it.id == chosen } ?: all.firstOrNull())
    }.flatMapLatest { (all, counter) ->
        if (counter == null) flowOf(Triple(all, null, emptyList()))
        else counters.counter(counter.id).observeDays().map { days -> Triple(all, counter, days) }
    }

    val state: StateFlow<HomeUiState> = loadingState(HomeUiState(), { copy(load = it) }) {
        combine(
            combine(userName, prefs) { name, homePrefs -> name to homePrefs },
            agenda,
            lists.observeLists(),
            week,
            reminders.observeReminders(),
        ) { (name, homePrefs), (all, counter, days), allLists, (anchor, selected), allReminders ->
            HomeUiState(
                userName = name,
                days = days,
                lists = allLists,
                weekAnchor = anchor,
                selected = selected,
                pending = counter?.summary?.pending ?: 0.0,
                prefs = homePrefs,
                reminders = allReminders,
                counters = all,
                counter = counter,
            )
        }
    }

    fun chooseCounter(id: String) {
        chosenCounter.value = id
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
        val counter = state.value.counter ?: return@launchSafely
        val date = state.value.selected
        val repository = counters.counter(counter.id)
        if (state.value.selectedDay != null) repository.removeDay(date) else repository.setDay(date)
    }
}
