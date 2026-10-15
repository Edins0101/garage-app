package com.garaje.app.ui.garage

import androidx.lifecycle.viewModelScope
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.model.GarageSettings
import com.garaje.app.domain.model.GarageSummary
import com.garaje.app.domain.model.Payment
import com.garaje.app.domain.repository.GarageRepository
import com.garaje.app.ui.common.BaseViewModel
import com.garaje.app.ui.common.Load
import com.garaje.app.util.Dates
import com.garaje.app.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** Qué hace un toque sobre el calendario. */
enum class GarageMode { DAY, RANGE, NOTE }

/** Diálogo abierto en la pantalla. */
sealed interface GarageDialog {
    data class Day(val date: LocalDate) : GarageDialog
    data object Settings : GarageDialog
    data object Payment : GarageDialog
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
    val load: Load = Load.LOADING,
) {
    val markedDates: Set<LocalDate> = days.map { it.localDate }.toSet()
    /** Días con nota o cargos extra (llevan un punto en el calendario). */
    val noteDates: Set<LocalDate> =
        days.filter { it.note != null || it.charges.isNotEmpty() }.map { it.localDate }.toSet()
    val monthDays: List<GarageDay> = days.filter { YearMonth.from(it.localDate) == month }
    val monthTotal: Double = monthDays.sumOf { it.total }
    val dialogDay: GarageDay? = (dialog as? GarageDialog.Day)?.let { d -> days.find { it.localDate == d.date } }

    val hint: String = when (mode) {
        GarageMode.DAY -> "Toca un día para marcarlo o quitarlo. Mantén presionado para ver su detalle (nota, tarifa y cargos)."
        GarageMode.RANGE -> rangeStart?.let { "Inicio: ${Dates.longDay(it)}. Ahora toca el último día." }
            ?: "Toca el primer día y luego el último para marcar todo el rango."
        GarageMode.NOTE -> "Toca un día para ver y editar su nota, tarifa y cargos extra."
    }
}

private data class LocalState(
    val month: YearMonth = YearMonth.now(),
    val mode: GarageMode = GarageMode.DAY,
    val rangeStart: LocalDate? = null,
    val dialog: GarageDialog? = null,
)

class GarageViewModel(private val garage: GarageRepository) : BaseViewModel() {

    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<GarageUiState> = loadingState(GarageUiState(), { copy(load = it) }) {
        combine(
            local,
            garage.observeDays(),
            garage.observeSettings(),
            garage.observeSummary(),
            garage.observePayments(),
        ) { ui, days, settings, summary, payments ->
            GarageUiState(ui.month, ui.mode, ui.rangeStart, days, settings, summary, payments, ui.dialog)
        }
    }

    fun previousMonth() = local.update { it.copy(month = it.month.minusMonths(1)) }
    fun nextMonth() = local.update { it.copy(month = it.month.plusMonths(1)) }
    fun setMode(mode: GarageMode) = local.update { it.copy(mode = mode, rangeStart = null) }

    fun openDay(date: LocalDate) = local.update { it.copy(dialog = GarageDialog.Day(date)) }
    fun openSettings() = local.update { it.copy(dialog = GarageDialog.Settings) }
    fun openPayment() = local.update { it.copy(dialog = GarageDialog.Payment) }
    fun closeDialog() = local.update { it.copy(dialog = null) }

    fun onDayClick(date: LocalDate) {
        val ui = local.value
        when (ui.mode) {
            GarageMode.DAY -> toggle(date)
            GarageMode.NOTE -> openDay(date)
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

    /** Guarda nota y tarifa del día (marca el día si no lo estaba). */
    fun saveDay(date: LocalDate, note: String, rateText: String) = launchSafely {
        val rate = rateText.takeIf { it.isNotBlank() }?.let {
            Money.parse(it) ?: return@launchSafely notify("La tarifa no es un monto válido")
        }
        garage.setDay(date, note)
        val current = state.value.days.find { it.localDate == date }?.rate
        if (rate != null && rate != current) garage.setDayRate(date, rate)
        closeDialog()
    }

    fun removeDay(date: LocalDate) = launchSafely {
        garage.removeDay(date)
        closeDialog()
    }

    fun addCharge(date: LocalDate, description: String, amountText: String, onAdded: () -> Unit) = launchSafely {
        val amount = Money.parse(amountText)?.takeIf { it > 0 }
            ?: return@launchSafely notify("Escribe un monto mayor a 0")
        if (description.isBlank()) return@launchSafely notify("Escribe qué es el cargo")
        garage.addCharge(date, description, amount)
        onAdded()
    }

    fun removeCharge(chargeId: String) = launchSafely { garage.removeCharge(chargeId) }

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
        val amount = Money.parse(amountText)?.takeIf { it > 0 }
            ?: return@launchSafely notify("Escribe un monto mayor a 0")
        garage.addPayment(date, amount, note)
        notify("Pago de ${Money.format(amount)} registrado")
        closeDialog()
    }

    fun removePayment(payment: Payment) = launchSafely {
        garage.removePayment(payment.id)
        notify("Pago de ${Money.format(payment.amount)} eliminado")
    }

    private inline fun MutableStateFlow<LocalState>.update(transform: (LocalState) -> LocalState) {
        value = transform(value)
    }
}
