package com.arianna.app.ui.counters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.Counter
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.model.CounterDirection
import com.arianna.app.domain.repository.CounterDraft
import com.arianna.app.domain.repository.CountersRepository
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.Load
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.GlassCard
import com.arianna.app.ui.components.LoadNotice
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.ScrollingSheetLayout
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.components.TicketCard
import com.arianna.app.ui.components.TicketSkeleton
import com.arianna.app.ui.components.TopBar
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.Money
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

data class CountersUiState(
    val counters: List<Counter> = emptyList(),
    /** Para adjuntar una lista al crear un contador. */
    val lists: List<Checklist> = emptyList(),
    val creating: Boolean = false,
    val busy: Boolean = false,
    val load: Load = Load.LOADING,
) {
    /** Lo que debo (mis contadores "yo pago") y lo que me deben ("me pagan"), hasta hoy. */
    private val mine = counters.filter { it.mine }
    val toPay: Double = mine.filter { it.direction == CounterDirection.PAY }.sumOf { it.summary.pending.coerceAtLeast(0.0) }
    val toCollect: Double = mine.filter { it.direction == CounterDirection.EARN }.sumOf { it.summary.pending.coerceAtLeast(0.0) }
}

class CountersViewModel(private val counters: CountersRepository, lists: ListsRepository) : BaseViewModel() {
    private val ui = MutableStateFlow(CountersUiState())
    private val _opened = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Contador recién creado: la pantalla lo abre. */
    val opened: SharedFlow<String> = _opened.asSharedFlow()

    val state: StateFlow<CountersUiState> = loadingState(ui.value, { copy(load = it) }) {
        combine(ui, counters.observeCounters(), lists.observeLists().catch { emit(emptyList()) }) { current, list, allLists ->
            current.copy(counters = list, lists = allLists)
        }
    }

    fun openCreate(open: Boolean) {
        ui.value = ui.value.copy(creating = open)
    }

    fun create(draft: CounterDraft) = launchSafely {
        ui.value = ui.value.copy(busy = true)
        try {
            val counter = counters.create(draft)
            ui.value = ui.value.copy(creating = false)
            _opened.emit(counter.id)
        } finally {
            ui.value = ui.value.copy(busy = false)
        }
    }
}

/** Mis contadores por día (garaje, niñera, expreso…) con lo que debo o me deben de cada uno. */
@Composable
fun CountersScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val viewModel = appViewModel { CountersViewModel(it.counters, it.lists) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)
    LaunchedEffect(viewModel) { viewModel.opened.collect(onOpen) }

    CosmicBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { _ ->
            ScrollingSheetLayout(
                header = {
                    Column(
                        Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TopBar(title = "Contadores", overline = "Por día", onBack = onBack)
                        GlassCard {
                            Text("HASTA HOY", style = MaterialTheme.typography.labelLarge, color = Palette.OnDarkSoft)
                            Text(
                                Money.format(state.toPay),
                                color = Palette.OnDark,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 46.sp,
                            )
                            Text(
                                "por pagar" + if (state.toCollect > 0) " · te deben ${Money.format(state.toCollect)}" else "",
                                color = Palette.OnDarkSoft,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(14.dp))
                            PillButton(
                                "Nuevo contador",
                                onClick = { viewModel.openCreate(true) },
                                icon = Icons.Outlined.AddCircleOutline,
                                light = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                },
                sheet = {
                    LoadNotice(state.load, onRetry = viewModel::retry)
                    SheetTitle("Mis contadores")
                    Spacer(Modifier.height(12.dp))
                    when {
                        state.load != Load.READY -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TicketSkeleton()
                            TicketSkeleton()
                        }
                        state.counters.isEmpty() -> Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.CalendarMonth, null, tint = Palette.Violet)
                            Text(
                                "Soy Arianna. Marca los días del garaje, la niñera o el expreso y te digo cuánto debes. " +
                                    "También sirve para los días que trabajas y te deben.",
                                color = Palette.InkSoft,
                            )
                            PillButton("Nuevo contador", onClick = { viewModel.openCreate(true) }, icon = Icons.Outlined.AddCircleOutline)
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.counters.forEach { counter -> CounterTicket(counter, onClick = { onOpen(counter.id) }) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                },
            )
        }
    }

    if (state.creating) {
        CounterDialog(
            counter = null,
            lists = state.lists,
            busy = state.busy,
            onSave = viewModel::create,
            onDismiss = { viewModel.openCreate(false) },
        )
    }
}

@Composable
fun CounterTicket(counter: Counter, onClick: () -> Unit) {
    val summary = counter.summary
    val earn = counter.direction == CounterDirection.EARN
    TicketCard(
        footer = when {
            summary.pending > 0.005 -> if (earn) "Te deben" else "Pendiente"
            summary.pending < -0.005 -> "Saldo a favor"
            summary.daysWithoutRate > 0 -> "Sin tarifa"
            else -> "Al día"
        },
        badge = Money.format(summary.pending),
        onClick = onClick,
        footerIcon = counterIcon(counter.icon),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(counterIcon(counter.icon), null, tint = Palette.Violet, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(counter.name, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(
                    listOfNotNull(
                        "${summary.days} ${if (summary.days == 1) "día" else "días"}",
                        counter.dailyRate?.let { "${Money.format(it)}/día" },
                        "auto".takeIf { counter.autoMark },
                        "me pagan".takeIf { earn },
                        "De ${counter.ownerName}".takeIf { !counter.mine },
                        "compartido con ${counter.members.size}".takeIf { counter.mine && counter.members.isNotEmpty() },
                    ).joinToString(" · "),
                    color = Palette.InkSoft,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
