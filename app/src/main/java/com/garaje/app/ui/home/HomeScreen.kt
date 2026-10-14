package com.garaje.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garaje.app.domain.model.Checklist
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.ui.common.CollectMessages
import com.garaje.app.ui.common.appViewModel
import com.garaje.app.ui.components.CircleAvatar
import com.garaje.app.ui.components.CosmicBackground
import com.garaje.app.ui.components.GlassField
import com.garaje.app.ui.components.MenuTile
import com.garaje.app.ui.components.PeriodHeader
import com.garaje.app.ui.components.SectionTitle
import com.garaje.app.ui.components.SheetTitle
import com.garaje.app.ui.components.SquareIconButton
import com.garaje.app.ui.components.TicketCard
import com.garaje.app.ui.components.WeekStrip
import com.garaje.app.ui.components.ScrollingSheetLayout
import com.garaje.app.ui.navigation.ListsRoute
import com.garaje.app.ui.theme.Palette
import com.garaje.app.util.Dates
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    onOpenGarage: () -> Unit,
    onOpenLists: (ListsRoute) -> Unit,
    onOpenList: (String) -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it.auth, it.garage, it.lists) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)

    var query by rememberSaveable { mutableStateOf("") }

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
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BrandRow()
                    Spacer(Modifier.height(12.dp))
                    CircleAvatar(Icons.Outlined.Person)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.userName.isBlank()) "Hola" else "Hola, ${state.userName}",
                        style = MaterialTheme.typography.displaySmall,
                        color = Palette.OnDark,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, tint = Palette.OnDarkSoft, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            Dates.longDay(LocalDate.now()).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.OnDarkSoft,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SquareIconButton(Icons.Outlined.Tune, "Ver todas las listas", onClick = {
                            onOpenLists(ListsRoute())
                        })
                        Spacer(Modifier.width(12.dp))
                        GlassField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Buscar en tus listas…",
                            leadingIcon = Icons.Outlined.Search,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            onDone = {
                                if (query.isNotBlank()) onOpenLists(ListsRoute(query = query.trim()))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Menú principal")
                    Spacer(Modifier.height(18.dp))
                    MainMenu(onOpenGarage = onOpenGarage, onOpenLists = onOpenLists)
                    Spacer(Modifier.height(28.dp))
                }

                },
                sheet = {
                    GarageAgenda(
                        state = state,
                        onPrevious = viewModel::previousWeek,
                        onNext = viewModel::nextWeek,
                        onSelect = viewModel::select,
                        onToggle = viewModel::toggleSelected,
                        onOpenGarage = onOpenGarage,
                    )
                    Spacer(Modifier.height(28.dp))
                    RecentLists(
                        lists = state.lists.take(3),
                        onOpenList = onOpenList,
                        onSeeAll = { onOpenLists(ListsRoute()) },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            )
        }
    }
}

@Composable
private fun BrandRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.DirectionsCar, null, tint = Palette.OnDark)
        Spacer(Modifier.width(8.dp))
        Text("GARAJE", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp), color = Palette.OnDark)
    }
}

private data class MenuEntry(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun MainMenu(onOpenGarage: () -> Unit, onOpenLists: (ListsRoute) -> Unit) {
    val entries = listOf(
        MenuEntry(Icons.Outlined.CalendarMonth, "Mi garaje", onOpenGarage),
        MenuEntry(Icons.Outlined.ShoppingCart, "Compras") { onOpenLists(ListsRoute(filter = "shopping")) },
        MenuEntry(Icons.Outlined.TaskAlt, "Tareas") { onOpenLists(ListsRoute(filter = "todo")) },
        MenuEntry(Icons.Outlined.Groups, "Compartidas") { onOpenLists(ListsRoute(filter = "shared")) },
        MenuEntry(Icons.Outlined.VpnKey, "Unirme") { onOpenLists(ListsRoute(join = true)) },
        MenuEntry(Icons.Outlined.AddCircleOutline, "Nueva lista") { onOpenLists(ListsRoute(create = true)) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { entry ->
                    MenuTile(entry.icon, entry.label, entry.onClick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GarageAgenda(
    state: HomeUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToggle: () -> Unit,
    onOpenGarage: () -> Unit,
) {
    SheetTitle("Agenda del garaje") {
        Text(
            "${state.daysThisMonth} días este mes",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Violet,
            modifier = Modifier.clickable(onClick = onOpenGarage),
        )
    }
    PeriodHeader(
        label = Dates.monthName(YearMonth.from(state.weekAnchor)),
        onPrevious = onPrevious,
        onNext = onNext,
    )
    WeekStrip(
        days = Dates.week(state.weekAnchor),
        marked = state.markedDates,
        notes = state.noteDates,
        selected = state.selected,
        onDayClick = onSelect,
    )
    Spacer(Modifier.height(8.dp))

    // Barra del día seleccionado (como "Family Reunion | RESERVE ›").
    val day = state.selectedDay
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Palette.Violet),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                Dates.longDay(state.selected).replaceFirstChar { it.uppercase() },
                color = Palette.OnDark,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                when {
                    day == null -> "Libre: el carro no está en el garaje"
                    day.note != null -> "En el garaje · ${day.note}"
                    else -> "En el garaje"
                },
                color = Palette.OnDarkSoft,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .background(Palette.OnDark.copy(alpha = 0.6f)),
        )
        Row(
            Modifier
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (day == null) "MARCAR" else "QUITAR",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.OnDark,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.OnDark)
        }
    }
}

@Composable
private fun ColumnScope.RecentLists(lists: List<Checklist>, onOpenList: (String) -> Unit, onSeeAll: () -> Unit) {
    SheetTitle("Listas recientes")
    Spacer(Modifier.height(12.dp))
    if (lists.isEmpty()) {
        Text("Aún no tienes listas.", color = Palette.InkSoft)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lists.forEach { list ->
            ChecklistTicket(list, onClick = { onOpenList(list.id) })
        }
    }
    Text(
        "VER TODAS…",
        style = MaterialTheme.typography.labelLarge,
        color = Palette.Ink,
        modifier = Modifier
            .align(Alignment.End)
            .clickable(onClick = onSeeAll)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    )
}

/** Tarjeta "boleto" de una lista; también la usa la pantalla de Listas. */
@Composable
fun ChecklistTicket(list: Checklist, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val kindLabel = if (list.kind == ChecklistKind.SHOPPING) "Compras" else "Tareas"
    val shared = if (list.sharedWith.isNotEmpty()) " · compartida (${list.sharedWith.size})" else ""
    TicketCard(
        footer = kindLabel + shared,
        badge = if (list.items.isEmpty()) "VACÍA" else "${list.doneCount}/${list.items.size}",
        onClick = onClick,
        onLongClick = onLongClick,
        footerIcon = if (list.kind == ChecklistKind.SHOPPING) Icons.Outlined.ShoppingCart else Icons.Outlined.TaskAlt,
    ) {
        Text(list.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Palette.Ink)
        val pending = list.items.filterNot { it.done }.take(3).joinToString(" · ") { it.text }
        Text(
            pending.ifEmpty { if (list.items.isEmpty()) "Sin elementos" else "¡Todo listo!" },
            color = Palette.InkSoft,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { list.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Palette.Violet,
            trackColor = Palette.Sheet,
            drawStopIndicator = {},
        )
    }
}
