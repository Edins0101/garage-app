package com.garaje.app.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garaje.app.ui.common.CollectMessages
import com.garaje.app.ui.common.TextInputDialog
import com.garaje.app.ui.common.appViewModel
import com.garaje.app.ui.components.CosmicBackground
import com.garaje.app.ui.components.GlassCard
import com.garaje.app.ui.components.MenuTile
import com.garaje.app.ui.components.MonthCalendar
import com.garaje.app.ui.components.PeriodHeader
import com.garaje.app.ui.components.SheetTitle
import com.garaje.app.ui.components.TicketCard
import com.garaje.app.ui.components.TopBar
import com.garaje.app.ui.components.ScrollingSheetLayout
import com.garaje.app.ui.theme.Palette
import com.garaje.app.util.Dates
import java.time.LocalDate

@Composable
fun GarageScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { GarageViewModel(it.garage) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)

    CosmicBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { padding ->
            ScrollingSheetLayout(
                header = {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TopBar(title = "Mi garaje", overline = "Calendario", onBack = onBack)

                    // Fila de fecha (como "22 OCT" del diseño).
                    GlassCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Event, null, tint = Palette.OnDark)
                            Spacer(Modifier.width(10.dp))
                            Text("HOY", style = MaterialTheme.typography.labelLarge, color = Palette.OnDarkSoft)
                            Spacer(Modifier.weight(1f))
                            Text(Dates.shortDay(LocalDate.now()), fontSize = 18.sp, color = Palette.OnDark)
                        }
                    }

                    // Contador del mes (como "NUMBER OF DAYS").
                    GlassCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "DÍAS EN ${Dates.monthName(state.month).uppercase()}",
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                                color = Palette.OnDark,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                state.monthDays.size.toString(),
                                color = Palette.Ink,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .widthIn(min = 52.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Palette.Sheet)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Selector de modo (como "TRANSPORT").
                    GlassCard {
                        Text("MODO", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = Palette.OnDark)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MenuTile(
                                Icons.Outlined.TouchApp, "Día", { viewModel.setMode(GarageMode.DAY) },
                                Modifier.weight(1f), selected = state.mode == GarageMode.DAY, compact = true,
                            )
                            MenuTile(
                                Icons.Outlined.DateRange, "Rango", { viewModel.setMode(GarageMode.RANGE) },
                                Modifier.weight(1f), selected = state.mode == GarageMode.RANGE, compact = true,
                            )
                            MenuTile(
                                Icons.Outlined.EditNote, "Nota", { viewModel.setMode(GarageMode.NOTE) },
                                Modifier.weight(1f), selected = state.mode == GarageMode.NOTE, compact = true,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(state.hint, color = Palette.OnDarkSoft, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }

                },
                sheet = {
                    PeriodHeader(
                        label = Dates.monthYear(state.month),
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                    MonthCalendar(
                        month = state.month,
                        marked = state.markedDates,
                        notes = state.noteDates,
                        rangeStart = state.rangeStart,
                        onDayClick = viewModel::onDayClick,
                        onDayLongClick = viewModel::editNote,
                    )
                    Spacer(Modifier.height(24.dp))
                    SheetTitle("Días en el garaje")
                    Spacer(Modifier.height(12.dp))
                    if (state.monthDays.isEmpty()) {
                        Text(
                            "No hay días marcados en ${Dates.monthName(state.month).lowercase()}.",
                            color = Palette.InkSoft,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.monthDays.forEach { day ->
                            TicketCard(
                                footer = if (day.note == null) "Agregar nota" else "Editar nota",
                                badge = Dates.shortDay(day.localDate),
                                onClick = { viewModel.editNote(day.localDate) },
                                footerIcon = Icons.Outlined.EditNote,
                            ) {
                                Text(
                                    Dates.longDay(day.localDate).replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Ink,
                                )
                                Text(day.note ?: "Sin nota", color = Palette.InkSoft, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            )
        }
    }

    state.editing?.let { date ->
        val day = state.editingDay
        TextInputDialog(
            title = Dates.longDay(date).replaceFirstChar { it.uppercase() },
            description = if (day == null) "Este día no está marcado. Al guardar se marca en el garaje." else null,
            label = "Nota (mantenimiento, viaje…)",
            confirmText = if (day == null) "Marcar" else "Guardar",
            initial = day?.note.orEmpty(),
            singleLine = false,
            allowEmpty = true,
            extraAction = day?.let { "Quitar día" to { viewModel.removeDay(date) } },
            onConfirm = { viewModel.saveNote(date, it) },
            onDismiss = viewModel::closeNote,
        )
    }
}
