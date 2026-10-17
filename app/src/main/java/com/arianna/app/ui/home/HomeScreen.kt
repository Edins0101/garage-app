package com.arianna.app.ui.home

import androidx.compose.ui.text.input.KeyboardCapitalization
import com.arianna.app.ui.common.TextInputDialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.arianna.app.ui.components.TicketSkeleton
import com.arianna.app.ui.components.SkeletonBox
import com.arianna.app.ui.components.LoadNotice
import com.arianna.app.ui.common.Load
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.NotificationsActive
import com.arianna.app.ui.reminders.KindCopy
import com.arianna.app.ui.reminders.kindIcon
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.vectorResource
import com.arianna.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.data.prefs.HomePrefs
import com.arianna.app.data.prefs.HomeSection
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.model.CounterDirection
import com.arianna.app.domain.model.CounterIcon
import com.arianna.app.ui.counters.counterIcon
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.util.Recurrence
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.ConfirmDialog
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.components.CircleAvatar
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.MenuTile
import com.arianna.app.ui.components.PeriodHeader
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.SectionTitle
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.components.TicketCard
import com.arianna.app.ui.components.WeekStrip
import com.arianna.app.ui.components.ScrollingSheetLayout
import com.arianna.app.ui.navigation.ListsRoute
import com.arianna.app.ui.theme.Palette
import com.arianna.app.ui.theme.ThemeState
import com.arianna.app.AriannaApp
import androidx.compose.ui.platform.LocalContext
import com.arianna.app.util.Dates
import com.arianna.app.util.Money
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    /** Lista de contadores por día. */
    onOpenCounters: () -> Unit,
    onOpenCounter: (String) -> Unit,
    /** null = todas las categorías. */
    onOpenReminders: (ReminderKind?) -> Unit,
    onOpenLists: (ListsRoute) -> Unit,
    onOpenList: (String) -> Unit,
    onOpenGroups: () -> Unit,
    /** Código de invitación de cualquier tipo. */
    onJoinCode: (String) -> Unit,
) {
    var joining by rememberSaveable { mutableStateOf(false) }
    if (joining) {
        TextInputDialog(
            title = "Unirme",
            description = "Escribe el código de 6 caracteres que te compartieron: de una lista, recordatorios, " +
                "un calendario o un grupo.",
            label = "Código",
            confirmText = "Unirme",
            capitalization = KeyboardCapitalization.Characters,
            onConfirm = {
                joining = false
                onJoinCode(it.trim().uppercase())
            },
            onDismiss = { joining = false },
        )
    }
    val viewModel = appViewModel { HomeViewModel(it.auth, it.counters, it.lists, it.reminders, it.homePrefs) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)

    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var customizing by rememberSaveable { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    val appearanceStore = (LocalContext.current.applicationContext as AriannaApp).container.appearance

    // Primera vez: preguntar qué quiere ver primero.
    if (!state.prefs.configured) {
        WelcomeChoiceSheet(onChoose = viewModel::choosePrimary)
    }
    if (appearance) {
        AppearanceDialog(
            current = ThemeState.accent,
            onSelect = appearanceStore::set,
            onDismiss = { appearance = false },
        )
    }
    if (customizing) {
        CustomizeHomeDialog(
            prefs = state.prefs,
            onMove = viewModel::moveSection,
            onToggle = viewModel::toggleSection,
            onDismiss = { customizing = false },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Cerrar sesión",
            message = "Tus datos quedan guardados en la nube. Para volver a verlos, entra con el mismo correo.",
            confirmText = "Cerrar sesión",
            onConfirm = viewModel::logout,
            onDismiss = { confirmLogout = false },
        )
    }

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
                    BrandRow(
                        onAppearance = { appearance = true },
                        onCustomize = { customizing = true },
                        onLogout = { confirmLogout = true },
                    )
                    Spacer(Modifier.height(12.dp))
                    CircleAvatar(Icons.Outlined.Person)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.userName.isBlank()) "Hola 👋" else "Hola, ${state.userName} 👋",
                        style = MaterialTheme.typography.displaySmall,
                        color = Palette.OnDark,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.greetingLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.OnDark,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, tint = Palette.OnDarkSoft, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            Dates.longDay(LocalDate.now()).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.OnDarkSoft,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Menú principal")
                    Spacer(Modifier.height(18.dp))
                    MainMenu(
                        prefs = state.prefs,
                        onOpenCounters = onOpenCounters,
                        onOpenReminders = onOpenReminders,
                        onOpenLists = onOpenLists,
                        onOpenGroups = onOpenGroups,
                        onJoin = { joining = true },
                    )
                    Spacer(Modifier.height(28.dp))
                }

                },
                sheet = {
                    if (state.load != Load.READY) {
                        LoadNotice(state.load, onRetry = viewModel::retry)
                        state.prefs.visible.forEachIndexed { index, section ->
                            if (index > 0) Spacer(Modifier.height(28.dp))
                            SectionSkeleton(section)
                        }
                    } else {
                    // Las secciones aparecen en el orden que eligió el usuario.
                    state.prefs.visible.forEachIndexed { index, section ->
                        if (index > 0) Spacer(Modifier.height(28.dp))
                        when (section) {
                            HomeSection.GARAGE -> GarageAgenda(
                                state = state,
                                onPrevious = viewModel::previousWeek,
                                onNext = viewModel::nextWeek,
                                onSelect = viewModel::select,
                                onToggle = viewModel::toggleSelected,
                                onChooseCounter = viewModel::chooseCounter,
                                onOpenCounter = onOpenCounter,
                                onOpenCounters = onOpenCounters,
                            )
                            HomeSection.REMINDERS -> RemindersSection(
                                state = state,
                                onOpen = { onOpenReminders(null) },
                            )
                            HomeSection.SHOPPING -> ListsSection(
                                title = "Compras",
                                lists = state.shoppingLists,
                                emptyText = "No tienes listas de compras.",
                                onOpenList = onOpenList,
                                onSeeAll = { onOpenLists(ListsRoute(filter = "shopping")) },
                                onCreate = { onOpenLists(ListsRoute(filter = "shopping", create = true)) },
                            )
                            HomeSection.TASKS -> ListsSection(
                                title = "Tareas",
                                lists = state.todoLists,
                                emptyText = "No tienes listas de tareas.",
                                onOpenList = onOpenList,
                                onSeeAll = { onOpenLists(ListsRoute(filter = "todo")) },
                                onCreate = { onOpenLists(ListsRoute(filter = "todo", create = true)) },
                            )
                        }
                    }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            )
        }
    }
}

/** Forma de cada sección mientras llegan los datos. */
@Composable
private fun ColumnScope.SectionSkeleton(section: HomeSection) {
    SheetTitle(
        when (section) {
            HomeSection.GARAGE -> "Mis calendarios"
            HomeSection.REMINDERS -> "Recordatorios"
            HomeSection.SHOPPING -> "Compras"
            HomeSection.TASKS -> "Tareas"
        },
    )
    Spacer(Modifier.height(14.dp))
    if (section == HomeSection.GARAGE) {
        SkeletonBox(width = 140.dp, height = 22.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        SkeletonBox(height = 26.dp, shape = RoundedCornerShape(0.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBox(height = 40.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonBox(height = 64.dp, shape = RoundedCornerShape(0.dp))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TicketSkeleton()
            TicketSkeleton()
        }
    }
}

@Composable
private fun BrandRow(onAppearance: () -> Unit, onCustomize: () -> Unit, onLogout: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La "A" con estrella es la primera letra del nombre: se lee "A✦RIANNA".
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = "Arianna" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ImageVector.vectorResource(R.drawable.ic_brand), null, tint = Palette.OnDark, modifier = Modifier.size(24.dp))
            Text(
                "RIANNA",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp, letterSpacing = 3.sp),
                color = Palette.OnDark,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAppearance) {
            Icon(Icons.Outlined.Palette, contentDescription = "Apariencia", tint = Palette.OnDarkSoft)
        }
        IconButton(onClick = onCustomize) {
            Icon(Icons.Outlined.DashboardCustomize, contentDescription = "Personalizar inicio", tint = Palette.OnDarkSoft)
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Cerrar sesión", tint = Palette.OnDarkSoft)
        }
    }
}

private data class MenuEntry(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun MainMenu(
    prefs: HomePrefs,
    onOpenCounters: () -> Unit,
    onOpenReminders: (ReminderKind?) -> Unit,
    onOpenLists: (ListsRoute) -> Unit,
    onOpenGroups: () -> Unit,
    onJoin: () -> Unit,
) {
    // Los accesos principales siguen el orden del usuario; las secciones ocultas no aparecen.
    val main = prefs.visible.map { section ->
        when (section) {
            HomeSection.GARAGE -> MenuEntry(Icons.Outlined.CalendarMonth, "Calendario", onOpenCounters)
            HomeSection.REMINDERS -> MenuEntry(Icons.Outlined.NotificationsActive, "Recordatorios") { onOpenReminders(null) }
            HomeSection.SHOPPING -> MenuEntry(Icons.Outlined.ShoppingCart, "Compras") { onOpenLists(ListsRoute(filter = "shopping")) }
            HomeSection.TASKS -> MenuEntry(Icons.Outlined.TaskAlt, "Tareas") { onOpenLists(ListsRoute(filter = "todo")) }
        }
    }
    // "Unirme" sirve para cualquier código: lista, recordatorios, calendario o grupo.
    val extras = listOf(
        MenuEntry(Icons.Outlined.Diversity3, "Grupos", onOpenGroups),
        MenuEntry(Icons.Outlined.VpnKey, "Unirme", onJoin),
    )
    // Hasta 6 mosaicos van en filas de 3; si son más, en filas de 4 (se achican los textos).
    val entries = main + extras
    val columns = if (entries.size > 6) 4 else 3
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { entry ->
                    MenuTile(entry.icon, entry.label, entry.onClick, Modifier.weight(1f))
                }
                // Mantiene el ancho de los mosaicos cuando la última fila queda incompleta.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
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
    onChooseCounter: (String) -> Unit,
    onOpenCounter: (String) -> Unit,
    onOpenCounters: () -> Unit,
) {
    val counter = state.counter
    if (counter == null) {
        SheetTitle("Mis calendarios")
        Spacer(Modifier.height(14.dp))
        Text(
            "Marca los días del garaje, la niñera o el expreso y te digo cuánto debes hasta hoy.",
            color = Palette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        PillButton("Nuevo calendario", onClick = onOpenCounters, light = true, icon = Icons.Outlined.AddCircleOutline)
        return
    }
    val earn = counter.direction == CounterDirection.EARN
    SheetTitle(counter.name) {
        Text(
            when {
                state.pending > 0 -> (if (earn) "Te deben " else "Pendiente ") + Money.format(state.pending)
                else -> "${state.daysThisMonth} días este mes"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Violet,
            modifier = Modifier.clickable { onOpenCounter(counter.id) },
        )
    }
    if (state.counters.size > 1) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.counters.forEach { option ->
                FilterChip(
                    selected = option.id == counter.id,
                    onClick = { onChooseCounter(option.id) },
                    label = { Text(option.name, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(counterIcon(option.icon), null, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Palette.Violet,
                        selectedLabelColor = Palette.OnDark,
                        selectedLeadingIconColor = Palette.OnDark,
                        labelColor = Palette.Ink,
                        iconColor = Palette.Violet,
                    ),
                )
            }
        }
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
        paid = state.paidDates,
        paymentDays = state.paymentDates,
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
                    state.isClosed(state.selected) && day != null -> "Pagado" + (day.note?.let { " · $it" } ?: "")
                    // El garaje conserva sus textos; los demás contadores, genéricos.
                    counter.icon == CounterIcon.CAR && day == null -> "Libre: el carro no está en el garaje"
                    counter.icon == CounterIcon.CAR -> "En el garaje" + (day?.note?.let { " · $it" } ?: "")
                    day == null -> "Sin marcar"
                    else -> "Marcado" + (day.note?.let { " · $it" } ?: "")
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
                when {
                    state.isClosed(state.selected) -> "PAGADO"
                    day == null -> "MARCAR"
                    else -> "QUITAR"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Palette.OnDark,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.OnDark)
        }
    }
}

/** Los próximos recordatorios (de cualquier categoría); toca para ir a Recordatorios. */
@Composable
private fun ColumnScope.RemindersSection(state: HomeUiState, onOpen: () -> Unit) {
    val upcoming = state.upcomingReminders
    SheetTitle("Próximos recordatorios")
    Spacer(Modifier.height(14.dp))
    if (upcoming.isEmpty()) {
        Text(
            "Agrega la luz, la limpieza del aire, la matrícula del carro o un cumpleaños y te aviso a tiempo.",
            color = Palette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            "Nuevo recordatorio",
            onClick = onOpen,
            light = true,
            icon = Icons.Outlined.AddCircleOutline,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        upcoming.forEach { reminder ->
            val overdue = reminder.dueDate < LocalDate.now()
            TicketCard(
                footer = "${KindCopy.of(reminder.kind).title} · ${Recurrence.dueLabel(reminder)}",
                badge = reminder.amount?.let(Money::format),
                onClick = onOpen,
                footerIcon = kindIcon(reminder.kind),
            ) {
                Text(reminder.title, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(
                    Dates.longDay(reminder.dueDate).replaceFirstChar { it.uppercase() },
                    color = if (overdue) Palette.Danger else Palette.InkSoft,
                    fontSize = 13.sp,
                )
            }
        }
    }
    SeeAll(state.moreReminders, "VER LOS", onOpen)
}

/** "VER LOS 5…" solo si hay más de los que se muestran en el inicio. */
@Composable
private fun ColumnScope.SeeAll(total: Int, prefix: String, onClick: () -> Unit) {
    if (total <= HOME_PREVIEW) {
        Spacer(Modifier.height(8.dp))
        return
    }
    Text(
        "$prefix $total…",
        style = MaterialTheme.typography.labelLarge,
        color = Palette.Ink,
        modifier = Modifier
            .align(Alignment.End)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    )
}

@Composable
private fun ColumnScope.ListsSection(
    title: String,
    lists: List<Checklist>,
    emptyText: String,
    onOpenList: (String) -> Unit,
    onSeeAll: () -> Unit,
    onCreate: () -> Unit,
) {
    SheetTitle(title) {
        val pending = lists.sumOf { list -> list.items.count { !it.done } }
        if (pending > 0) {
            Text("$pending pendientes", color = Palette.Violet, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(12.dp))
    if (lists.isEmpty()) {
        Text(emptyText, color = Palette.InkSoft)
        Spacer(Modifier.height(10.dp))
        PillButton("Crear lista", onClick = onCreate, light = true, modifier = Modifier.fillMaxWidth())
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lists.take(HOME_PREVIEW).forEach { list -> ChecklistTicket(list, onClick = { onOpenList(list.id) }) }
    }
    SeeAll(lists.size, "VER LAS", onSeeAll)
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
