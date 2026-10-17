package com.arianna.app.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.domain.model.Reminder
import com.arianna.app.ui.common.listProgressLabel
import com.arianna.app.ui.lists.GroupSharing
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.ConfirmDialog
import com.arianna.app.ui.common.Load
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.common.shareText
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.GlassCard
import com.arianna.app.ui.components.LoadNotice
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.ScrollingSheetLayout
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.components.SkeletonBox
import com.arianna.app.ui.components.TicketCard
import com.arianna.app.ui.components.TicketSkeleton
import com.arianna.app.ui.components.TopBar
import com.arianna.app.ui.lists.ShareSheet
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.Dates
import com.arianna.app.util.Money
import com.arianna.app.util.Recurrence
import java.text.NumberFormat

/** Recordatorios de todas las categorías, con filtros. [initialFilter] null = Todos. */
@Composable
fun RemindersScreen(
    initialFilter: ReminderKind?,
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    onOpenGroups: () -> Unit,
) {
    val viewModel = appViewModel(key = "reminders-${initialFilter?.apiName ?: "all"}") { container ->
        val user = container.auth.session.value?.user
        RemindersViewModel(
            initialFilter, container.reminders, container.lists, container.groups,
            user?.id.orEmpty(), user?.email.orEmpty(), container.useFakes,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    CollectMessages(viewModel, snackbar)

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
                        TopBar(
                            title = "Recordatorios",
                            overline = state.filter?.let { KindCopy.of(it).title } ?: "Todo en un lugar",
                            onBack = onBack,
                        ) {
                            IconButton(onClick = { viewModel.open(ReminderDialog.Share(state.defaultKind)) }) {
                                Icon(Icons.Outlined.GroupAdd, contentDescription = "Compartir", tint = Palette.OnDark)
                            }
                        }
                        FilterRow(selected = state.filter, onSelect = viewModel::setFilter)
                        SummaryCard(state, onNew = { viewModel.open(ReminderDialog.Edit(null, state.defaultKind)) })
                        Spacer(Modifier.height(10.dp))
                    }
                },
                sheet = {
                    LoadNotice(state.load, onRetry = viewModel::retry)
                    if (state.load != Load.READY) {
                        SheetTitle("Próximos")
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TicketSkeleton()
                            TicketSkeleton()
                        }
                        return@ScrollingSheetLayout
                    }
                    if (state.suggestPlate) {
                        PlateCard(onDigit = viewModel::createPlateReminder, busy = state.busy)
                        Spacer(Modifier.height(24.dp))
                    }
                    if (state.reminders.isEmpty()) {
                        EmptyState(state.filter, onNew = { viewModel.open(ReminderDialog.Edit(null, state.defaultKind)) })
                    }
                    ReminderGroup("Vencidos", state.overdue, state, viewModel)
                    ReminderGroup("Próximos 7 días", state.soon, state, viewModel)
                    ReminderGroup("Más adelante", state.later, state, viewModel)
                    ReminderGroup("Hechos", state.finished, state, viewModel)
                    Spacer(Modifier.height(24.dp))
                },
            )
        }
    }

    when (val dialog = state.dialog) {
        is ReminderDialog.Edit -> ReminderEditDialog(
            reminder = dialog.reminder,
            initialKind = dialog.kind,
            spacesFor = state::writableSpaces,
            myId = state.myId,
            lists = state.lists,
            busy = state.busy,
            onSave = { kind, draft, spaceId -> viewModel.save(dialog.reminder, kind, draft, spaceId) },
            onDismiss = viewModel::close,
        )
        is ReminderDialog.Done -> ReminderDoneDialog(
            reminder = dialog.reminder,
            busy = state.busy,
            onConfirm = { amount, note, odometer -> viewModel.markDone(dialog.reminder, amount, note, odometer) },
            onDismiss = viewModel::close,
        )
        is ReminderDialog.Detail -> state.detail?.let { reminder ->
            ReminderDetailSheet(
                reminder = reminder,
                owner = state.ownerLabel(reminder),
                canEdit = state.canEdit(reminder),
                history = state.history,
                linkedList = state.linkedList(reminder),
                busy = state.busy,
                onOpenList = { id ->
                    viewModel.close()
                    onOpenList(id)
                },
                onDone = { viewModel.open(ReminderDialog.Done(reminder)) },
                onEdit = { viewModel.open(ReminderDialog.Edit(reminder, reminder.kind)) },
                onUndo = { viewModel.undo(reminder) },
                onDelete = { viewModel.open(ReminderDialog.Delete(reminder)) },
                onDismiss = viewModel::close,
            )
        }
        is ReminderDialog.Delete -> ConfirmDialog(
            title = "Eliminar «${dialog.reminder.title}»",
            message = "Se borra el recordatorio y su historial. No se puede deshacer.",
            confirmText = "Eliminar",
            onConfirm = { viewModel.delete(dialog.reminder) },
            onDismiss = viewModel::close,
        )
        is ReminderDialog.Share -> state.ownSpace(dialog.kind)?.let { space ->
            val copy = KindCopy.of(dialog.kind)
            ShareSheet(
                title = "Compartir: ${copy.title}",
                members = space.members,
                invite = state.invite,
                busy = state.busy,
                demoMode = viewModel.demoMode,
                onSendText = null,
                onCreateInvite = { viewModel.createInvite(dialog.kind) },
                onShareInvite = { invite ->
                    shareText(
                        context,
                        "Te comparto mis recordatorios de ${copy.title} en Arianna para que no se nos pase nada. " +
                            "Únete con el código ${invite.code}: ${invite.url}",
                    )
                },
                onInvite = { email, role, onDone -> viewModel.invite(dialog.kind, email, role, onDone) },
                onRemoveMember = { viewModel.removeMember(dialog.kind, it) },
                onDismiss = viewModel::close,
                intro = "Cada categoría se comparte por separado. ${copy.shareIntro}",
                codeHint = "La otra persona lo escribe en Inicio → Unirme.",
                emptyMembers = "Todavía nadie más ve esta categoría.",
                header = {
                    KindChips(selected = dialog.kind, onSelect = { viewModel.open(ReminderDialog.Share(it)) })
                },
                groups = GroupSharing(state.groups, space.groups, { group, role -> viewModel.shareWithGroup(dialog.kind, group, role) }) {
                    viewModel.close()
                    onOpenGroups()
                },
            ) {
                val shared = state.sharedWithMe(dialog.kind)
                if (shared.isNotEmpty()) {
                    SheetTitle("Compartidos contigo")
                    shared.forEach { other ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${copy.title} de ${other.ownerName}",
                                color = Palette.Ink,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "SALIR",
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.Danger,
                                modifier = Modifier
                                    .clickable { viewModel.leave(other) }
                                    .padding(8.dp),
                            )
                        }
                        HorizontalDivider(color = Palette.Divider)
                    }
                }
            }
        }
        null -> Unit
    }
}

/** Todos · Pagos · Mi hogar · Mi carro · Otros (sobre el fondo oscuro). */
@Composable
private fun FilterRow(selected: ReminderKind?, onSelect: (ReminderKind?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (listOf<ReminderKind?>(null) + ReminderKind.entries).forEach { kind ->
            FilterChip(
                selected = selected == kind,
                onClick = { onSelect(kind) },
                label = { Text(kind?.let { KindCopy.of(it).title } ?: "Todos", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = kind?.let { { Icon(kindIcon(it), null, modifier = Modifier.size(18.dp)) } },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Palette.Glass,
                    labelColor = Palette.OnDark,
                    iconColor = Palette.OnDark,
                    selectedContainerColor = Palette.OnDark,
                    selectedLabelColor = Palette.Violet,
                    selectedLeadingIconColor = Palette.Violet,
                ),
                border = null,
            )
        }
    }
}

/** Selector de categoría (para la hoja de compartir y el diálogo de crear). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KindChips(selected: ReminderKind, onSelect: (ReminderKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReminderKind.entries.forEach { kind ->
            FilterChip(
                selected = selected == kind,
                onClick = { onSelect(kind) },
                label = { Text(KindCopy.of(kind).title, style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(kindIcon(kind), null, modifier = Modifier.size(18.dp)) },
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

/** Cuánto falta pagar este mes (o cuántos recordatorios) y botón para agregar. */
@Composable
private fun SummaryCard(state: RemindersUiState, onNew: () -> Unit) {
    val money = state.filter == ReminderKind.PAYMENTS || (state.filter == null && state.monthTotal > 0)
    GlassCard {
        Text(
            if (money) "POR PAGAR ESTE MES" else "ESTE MES",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.OnDarkSoft,
        )
        if (state.load != Load.READY) {
            Spacer(Modifier.height(8.dp))
            SkeletonBox(width = 170.dp, height = 38.dp, onDark = true)
            Spacer(Modifier.height(10.dp))
            SkeletonBox(width = 220.dp, height = 13.dp, onDark = true)
        } else {
            Text(
                when {
                    money -> Money.format(state.monthTotal)
                    state.monthCount == 0 -> "Al día ✨"
                    state.monthCount == 1 -> "1 pendiente"
                    else -> "${state.monthCount} pendientes"
                },
                color = Palette.OnDark,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 46.sp,
            )
            val parts = buildList {
                val active = state.overdue.size + state.soon.size + state.later.size
                add(if (active == 1) "1 recordatorio" else "$active recordatorios")
                if (state.overdue.isNotEmpty()) {
                    val total = if (state.overdueTotal > 0) " (${Money.format(state.overdueTotal)})" else ""
                    add("${state.overdue.size} vencido${if (state.overdue.size > 1) "s" else ""}$total")
                }
                if (state.sharedCount > 0) add("compartido con ${state.sharedCount}")
            }
            Text(parts.joinToString(" · "), color = Palette.OnDarkSoft, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        PillButton(
            "Nuevo recordatorio",
            onClick = onNew,
            enabled = state.load == Load.READY,
            icon = Icons.Outlined.AddCircleOutline,
            light = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Mi carro: la matrícula se calcula con el último dígito de la placa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlateCard(onDigit: (Int) -> Unit, busy: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.LavenderSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DirectionsCar, null, tint = Palette.Violet)
            Spacer(Modifier.width(8.dp))
            Text("¿En qué número termina tu placa?", color = Palette.Ink, fontWeight = FontWeight.Bold)
        }
        Text(
            "Te aviso cuando te toque matricular (1 feb · 2 mar · 3 abr … 9 oct · 0 nov).",
            color = Palette.InkSoft,
            fontSize = 13.sp,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0).forEach { digit ->
                Text(
                    "$digit",
                    color = Palette.OnDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (busy) Palette.DayBox else Palette.Violet)
                        .clickable(enabled = !busy) { onDigit(digit) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(filter: ReminderKind?, onNew: () -> Unit) {
    val text = when (filter) {
        null -> "Agrega la luz, la limpieza del aire, el cambio de aceite o un cumpleaños y te aviso a tiempo."
        ReminderKind.PAYMENTS -> "Agrega la luz, el internet o la tarjeta y te aviso antes de que venzan."
        ReminderKind.HOME -> "Agrega el mantenimiento del aire, la lavadora o la fumigación y te aviso."
        ReminderKind.CAR -> "Agrega el cambio de aceite, el SOAT o la revisión técnica y te aviso."
        ReminderKind.OTHER -> "Cumpleaños, citas, la cédula o la vacuna de la mascota: lo que no quieras olvidar."
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(filter?.let(::kindIcon) ?: Icons.Outlined.NotificationsActive, null, tint = Palette.Violet)
        Text("Soy Arianna. $text", color = Palette.InkSoft)
        PillButton("Nuevo recordatorio", onClick = onNew, icon = Icons.Outlined.AddCircleOutline)
    }
}

@Composable
private fun ReminderGroup(title: String, reminders: List<Reminder>, state: RemindersUiState, viewModel: RemindersViewModel) {
    if (reminders.isEmpty()) return
    SheetTitle(title) {
        val total = reminders.sumOf { it.amount ?: 0.0 }
        if (total > 0) Text(Money.format(total), color = Palette.Violet, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        reminders.forEach { reminder ->
            ReminderTicket(
                reminder = reminder,
                state = state,
                onOpen = { viewModel.open(ReminderDialog.Detail(reminder.id)) },
                onDone = { viewModel.open(ReminderDialog.Done(reminder)) },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** "45.300 km" */
fun formatKm(km: Int): String = NumberFormat.getIntegerInstance(Dates.locale).format(km) + " km"

@Composable
private fun ReminderTicket(reminder: Reminder, state: RemindersUiState, onOpen: () -> Unit, onDone: () -> Unit) {
    val today = state.today
    val overdue = !reminder.finished && reminder.dueDate < today
    val copy = KindCopy.of(reminder.kind)
    TicketCard(
        footer = listOfNotNull(
            copy.title.takeIf { state.filter == null },
            Recurrence.dueLabel(reminder, today),
            Recurrence.label(reminder.every, reminder.unit),
        ).joinToString(" · "),
        badge = reminder.amount?.let(Money::format),
        onClick = onOpen,
        footerIcon = if (state.filter == null) kindIcon(reminder.kind) else Icons.Outlined.CalendarMonth,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontWeight = FontWeight.Bold, color = Palette.Ink)
                val due = if (reminder.finished) "Hecho" else Dates.longDay(reminder.dueDate).replaceFirstChar { it.uppercase() }
                Text(
                    listOfNotNull(
                        due,
                        reminder.time?.takeUnless { reminder.finished }?.let(Dates::time),
                        reminder.nextKm?.let { "o a los ${formatKm(it)}" },
                        state.ownerLabel(reminder),
                    ).joinToString(" · "),
                    color = if (overdue) Palette.Danger else Palette.InkSoft,
                    fontSize = 13.sp,
                )
                state.linkedList(reminder)?.let { list ->
                    Text("Lista «${list.title}» · ${listProgressLabel(list).lowercase()}", color = Palette.Violet, fontSize = 12.sp)
                }
                reminder.lastDone?.let { log ->
                    Text(
                        "Último: ${Dates.shortDay(log.shownDate(reminder.countFrom)).lowercase()}" +
                            (log.odometer?.let { " · ${formatKm(it)}" } ?: "") +
                            (log.amount?.let { " · ${Money.format(it)}" } ?: "") +
                            (log.doneByName?.let { " · $it" } ?: ""),
                        color = Palette.InkSoft,
                        fontSize = 12.sp,
                    )
                }
            }
            if (!reminder.finished && state.canEdit(reminder)) {
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Palette.Violet)
                        .clickable(onClick = onDone)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.TaskAlt, null, tint = Palette.OnDark)
                    Spacer(Modifier.width(6.dp))
                    Text(copy.doneLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.OnDark)
                }
            }
        }
    }
}
