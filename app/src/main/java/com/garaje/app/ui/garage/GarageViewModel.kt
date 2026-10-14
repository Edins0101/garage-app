package com.garaje.app.ui.garage

import androidx.lifecycle.viewModelScope
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.repository.GarageRepository
import com.garaje.app.ui.common.BaseViewModel
import com.garaje.app.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** Qué hace un toque sobre el calendario. */
enum class GarageMode { DAY, RANGE, NOTE }

data class GarageUiState(
    val month: YearMonth = YearMonth.now(),
    val mode: GarageMode = GarageMode.DAY,
    val rangeStart: LocalDate? = null,
    val days: List<GarageDay> = emptyList(),
    /** Día cuyo diálogo de nota está abierto. */
    val editing: LocalDate? = null,
) {
    val markedDates: Set<LocalDate> = days.map { it.localDate }.toSet()
    val noteDates: Set<LocalDate> = days.filter { it.note != null }.map { it.localDate }.toSet()
    val monthDays: List<GarageDay> = days.filter { YearMonth.from(it.localDate) == month }
    val editingDay: GarageDay? = editing?.let { date -> days.find { it.localDate == date } }

    val hint: String = when (mode) {
        GarageMode.DAY -> "Toca un día para marcarlo o quitarlo. Mantén presionado para agregar una nota."
        GarageMode.RANGE -> rangeStart?.let { "Inicio: ${Dates.longDay(it)}. Ahora toca el último día." }
            ?: "Toca el primer día y luego el último para marcar todo el rango."
        GarageMode.NOTE -> "Toca un día para escribir o editar su nota."
    }
}

private data class LocalState(
    val month: YearMonth = YearMonth.now(),
    val mode: GarageMode = GarageMode.DAY,
    val rangeStart: LocalDate? = null,
    val editing: LocalDate? = null,
)

class GarageViewModel(private val garage: GarageRepository) : BaseViewModel() {

    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<GarageUiState> = combine(local, garage.observeDays()) { ui, days ->
        GarageUiState(ui.month, ui.mode, ui.rangeStart, days, ui.editing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageUiState())

    fun previousMonth() = local.update { it.copy(month = it.month.minusMonths(1)) }
    fun nextMonth() = local.update { it.copy(month = it.month.plusMonths(1)) }
    fun setMode(mode: GarageMode) = local.update { it.copy(mode = mode, rangeStart = null) }
    fun editNote(date: LocalDate) = local.update { it.copy(editing = date) }
    fun closeNote() = local.update { it.copy(editing = null) }

    fun onDayClick(date: LocalDate) {
        val ui = local.value
        when (ui.mode) {
            GarageMode.DAY -> toggle(date)
            GarageMode.NOTE -> editNote(date)
            GarageMode.RANGE -> {
                val start = ui.rangeStart
                if (start == null) {
                    local.update { it.copy(rangeStart = date) }
                } else {
                    local.update { it.copy(rangeStart = null) }
                    launchSafely {
                        val range = Dates.between(start, date)
                        garage.setDays(range)
                        notify("${range.size} días marcados en el garaje")
                    }
                }
            }
        }
    }

    private fun toggle(date: LocalDate) = launchSafely {
        if (date in state.value.markedDates) garage.removeDay(date) else garage.setDay(date)
    }

    fun saveNote(date: LocalDate, note: String) = launchSafely {
        garage.setDay(date, note)
        closeNote()
    }

    fun removeDay(date: LocalDate) = launchSafely {
        garage.removeDay(date)
        closeNote()
    }

    private inline fun MutableStateFlow<LocalState>.update(transform: (LocalState) -> LocalState) {
        value = transform(value)
    }
}
