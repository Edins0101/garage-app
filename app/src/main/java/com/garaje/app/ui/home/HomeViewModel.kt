package com.garaje.app.ui.home

import androidx.lifecycle.viewModelScope
import com.garaje.app.data.prefs.HomePrefs
import com.garaje.app.data.prefs.HomePrefsStore
import com.garaje.app.data.prefs.HomeSection
import com.garaje.app.domain.model.Checklist
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.repository.AuthRepository
import com.garaje.app.domain.repository.GarageRepository
import com.garaje.app.domain.repository.ListsRepository
import com.garaje.app.ui.common.BaseViewModel
import com.garaje.app.ui.common.Load
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
    val load: Load = Load.LOADING,
) {
    val shoppingLists: List<Checklist> = lists.filter { it.kind == ChecklistKind.SHOPPING }
    val todoLists: List<Checklist> = lists.filter { it.kind == ChecklistKind.TODO }

    val markedDates: Set<LocalDate> = days.map { it.localDate }.toSet()
    val noteDates: Set<LocalDate> = days.filter { it.note != null }.map { it.localDate }.toSet()
    val selectedDay: GarageDay? = days.find { it.localDate == selected }
    val daysThisMonth: Int = days.count {
        it.localDate.year == weekAnchor.year && it.localDate.month == weekAnchor.month
    }
}

class HomeViewModel(
    private val auth: AuthRepository,
    private val garage: GarageRepository,
    lists: ListsRepository,
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
            garage.observeSummary(),
        ) { (name, homePrefs), days, allLists, (anchor, selected), summary ->
            HomeUiState(name, days, allLists, anchor, selected, summary.pending, homePrefs)
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
