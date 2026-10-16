package com.arianna.app.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.TaskAlt
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
import java.time.YearMonth

@Composable
fun RemindersScreen(kind: ReminderKind, onBack: () -> Unit) {
    val viewModel = appViewModel(key = "reminders-${kind.apiName}") { container ->
        val user = container.auth.session.value?.user
        RemindersViewModel(kind, container.reminders, user?.id.orEmpty(), user?.email.orEmpty(), container.useFakes)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    CollectMessages(viewModel, snackbar)
    val copy = state.copy

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
                        TopBar(title = copy.title, overline = copy.overline, onBack = onBack) {
                            if (state.ownSpace != null) {
                                IconButton(onClick = { viewModel.open(ReminderDialog.Share) }) {
                                    Icon(Icons.Outlined.GroupAdd, contentDescription = "Compartir", tint = Palette.OnDark)
                                }
                            }
                        }
                        SummaryCard(state, onNew = { viewModel.open(ReminderDialog.Edit(null)) })
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
                    if (state.reminders.isEmpty()) {
                        EmptyState(copy, onNew = { viewModel.open(ReminderDialog.Edit(null)) })
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
            kind = state.kind,
            copy = copy,
            reminder = dialog.reminder,
            spaces = state.writableSpaces,
            myId = state.myId,
            busy = state.busy,
            onSave = { draft, spaceId -> viewModel.save(dialog.reminder, draft, spaceId) },
            onDismiss = viewModel::close,
        )
        is ReminderDialog.Done -> ReminderDoneDialog(
            copy = copy,
            reminder = dialog.reminder,
            busy = state.busy,
            onConfirm = { amount, note -> viewModel.markDone(dialog.reminder, amount, note) },
            onDismiss = viewModel::close,
        )
        is ReminderDialog.Detail -> state.detail?.let { reminder ->
            ReminderDetailSheet(
                copy = copy,
                reminder = reminder,
                owner = state.ownerLabel(reminder),
                canEdit = state.canEdit(reminder),
                history = state.history,
                busy = state.busy,
                onDone = { viewModel.open(ReminderDialog.Done(reminder)) },
                onEdit = { viewModel.open(ReminderDialog.Edit(reminder)) },
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
        ReminderDialog.Share -> state.ownSpace?.let { space ->
            ShareSheet(
                title = "Compartir tus ${copy.title.lowercase()}",
                members = space.members,
                invite = state.invite,
                busy = state.busy,
                demoMode = viewModel.demoMode,
                onSendText = null,
                onCreateInvite = viewModel::createInvite,
                onShareInvite = { invite ->
                    shareText(
                        context,
                        "Te comparto mis ${copy.title.lowercase()} en Arianna para que no se nos pase ninguno. " +
                            "Únete con el código ${invite.code}: ${invite.url}",
                    )
                },
                onInvite = viewModel::invite,
                onRemoveMember = viewModel::removeMember,
                onDismiss = viewModel::close,
                intro = copy.shareIntro,
                codeHint = "La otra persona lo escribe en Inicio → Unirme.",
                emptyMembers = "Todavía nadie más ve tus ${copy.title.lowercase()}.",
            ) {
                if (state.sharedWithMe.isNotEmpty()) {
                    SheetTitle("Compartidos contigo")
                    state.sharedWithMe.forEach { shared ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${copy.title} de ${shared.ownerName}",
                                color = Palette.Ink,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "SALIR",
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.Danger,
                                modifier = Modifier
                                    .clickable { viewModel.leave(shared) }
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

/** Cuánto falta pagar este mes y botón para agregar. */
@Composable
private fun SummaryCard(state: RemindersUiState, onNew: () -> Unit) {
    GlassCard {
        Text(
            if (state.kind == ReminderKind.PAYMENTS) "POR PAGAR ESTE MES" else "POR HACER",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.OnDarkSoft,
        )
        if (state.load != Load.READY) {
            Spacer(Modifier.height(8.dp))
            SkeletonBox(width = 170.dp, height = 38.dp, onDark = true)
            Spacer(Modifier.height(10.dp))
            SkeletonBox(width = 220.dp, height = 13.dp, onDark = true)
        } else {
            val dueThisMonth = state.overdue.size + state.soon.size +
                state.later.count { YearMonth.from(it.dueDate) == YearMonth.from(state.today) }
            Text(
                if (state.kind == ReminderKind.PAYMENTS) Money.format(state.monthTotal)
                else when (dueThisMonth) {
                    0 -> "Al día ✨"
                    1 -> "1 este mes"
                    else -> "$dueThisMonth este mes"
                },
                color = Palette.OnDark,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 46.sp,
            )
            val parts = buildList {
                val active = state.overdue.size + state.soon.size + state.later.size
                add(if (active == 1) "1 ${state.copy.one}" else "$active ${state.copy.many}")
                if (state.overdue.isNotEmpty()) {
                    add("${state.overdue.size} vencido${if (state.overdue.size > 1) "s" else ""} (${Money.format(state.overdueTotal)})")
                }
                if (state.acceptedMembers > 0) add("compartido con ${state.acceptedMembers}")
            }
            Text(parts.joinToString(" · "), color = Palette.OnDarkSoft, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        PillButton(
            state.copy.newLabel,
            onClick = onNew,
            enabled = state.load == Load.READY,
            icon = Icons.Outlined.AddCircleOutline,
            light = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyState(copy: KindCopy, onNew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = Palette.Violet)
        Text("Soy Arianna. ${copy.empty}", color = Palette.InkSoft)
        PillButton(copy.newLabel, onClick = onNew, icon = Icons.Outlined.AddCircleOutline)
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

@Composable
private fun ReminderTicket(reminder: Reminder, state: RemindersUiState, onOpen: () -> Unit, onDone: () -> Unit) {
    val today = state.today
    val overdue = !reminder.finished && reminder.dueDate < today
    TicketCard(
        footer = listOfNotNull(Recurrence.dueLabel(reminder, today), Recurrence.label(reminder.every, reminder.unit))
            .joinToString(" · "),
        badge = reminder.amount?.let(Money::format),
        onClick = onOpen,
        footerIcon = Icons.Outlined.CalendarMonth,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontWeight = FontWeight.Bold, color = Palette.Ink)
                val due = if (reminder.finished) "Hecho" else Dates.longDay(reminder.dueDate).replaceFirstChar { it.uppercase() }
                Text(
                    listOfNotNull(due, state.ownerLabel(reminder)).joinToString(" · "),
                    color = if (overdue) Palette.Danger else Palette.InkSoft,
                    fontSize = 13.sp,
                )
                reminder.lastDone?.let { log ->
                    Text(
                        "Último: ${Dates.shortDay(log.shownDate(reminder.countFrom)).lowercase()}" +
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
                    Text(state.copy.doneLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.OnDark)
                }
            }
        }
    }
}

