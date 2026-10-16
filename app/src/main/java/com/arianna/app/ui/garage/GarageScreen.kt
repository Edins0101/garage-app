package com.arianna.app.ui.garage

import com.arianna.app.ui.components.TicketSkeleton
import com.arianna.app.ui.components.SkeletonBox
import com.arianna.app.ui.components.LoadNotice
import com.arianna.app.ui.common.Load
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.ui.platform.LocalContext
import com.arianna.app.ui.common.shareText
import com.arianna.app.ui.lists.ShareSheet
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.domain.model.GarageDay
import com.arianna.app.domain.model.Payment
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.ConfirmDialog
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.GlassCard
import com.arianna.app.ui.components.MenuTile
import com.arianna.app.ui.components.MonthCalendar
import com.arianna.app.ui.components.PeriodHeader
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.ScrollingSheetLayout
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.components.TicketCard
import com.arianna.app.ui.components.TopBar
import com.arianna.app.ui.counters.CounterDialog
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.Dates
import com.arianna.app.util.Money
import java.time.LocalDate

/** Calendario y cuenta de un contador por día (el garaje es uno más). */
@Composable
fun GarageScreen(counterId: String, onBack: () -> Unit) {
    val viewModel = appViewModel(key = "counter-$counterId") { container ->
        val email = container.auth.session.value?.user?.email.orEmpty()
        GarageViewModel(counterId, container.counters, email, container.useFakes)
    }
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)
    var deletingPayment by remember { mutableStateOf<Payment?>(null) }

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
                        val counter = state.counter
                        TopBar(
                            title = counter?.name ?: "Contador",
                            overline = when {
                                counter == null || counter.mine -> "Contador por día"
                                counter.canEdit -> "De ${counter.ownerName} · puedes editar"
                                else -> "De ${counter.ownerName} · solo ver"
                            },
                            onBack = onBack,
                        ) {
                            if (counter != null && counter.mine) {
                                IconButton(onClick = viewModel::openShare) {
                                    Icon(Icons.Outlined.GroupAdd, contentDescription = "Compartir", tint = Palette.OnDark)
                                }
                                IconButton(onClick = viewModel::openEditCounter) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Editar contador", tint = Palette.OnDark)
                                }
                            } else if (counter != null) {
                                IconButton(onClick = viewModel::openLeave) {
                                    Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = "Dejar de ver", tint = Palette.OnDark)
                                }
                            }
                        }
                        AccountCard(
                            state = state,
                            onSettings = viewModel::openSettings,
                            onPay = viewModel::openPayment,
                        )
                        if (state.canEdit) ModeCard(state = state, onMode = viewModel::setMode)
                        Spacer(Modifier.height(10.dp))
                    }
                },
                sheet = {
                    LoadNotice(state.load, onRetry = viewModel::retry)
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
                        onDayLongClick = viewModel::openDay,
                    )
                    Spacer(Modifier.height(24.dp))
                    SheetTitle("Días en ${Dates.monthName(state.month).lowercase()}") {
                        if (state.monthDays.isNotEmpty()) {
                            Text(
                                "${state.monthDays.size} · ${Money.format(state.monthTotal)}",
                                color = Palette.Violet,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.load != Load.READY) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TicketSkeleton()
                            TicketSkeleton()
                        }
                    } else if (state.monthDays.isEmpty()) {
                        Text("No hay días marcados este mes.", color = Palette.InkSoft)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.monthDays.sortedByDescending { it.date }.forEach { day ->
                            DayTicket(day, onClick = { viewModel.openDay(day.localDate) })
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    SheetTitle(state.copy.paymentsTitle) {
                        Text(
                            "+ REGISTRAR",
                            style = MaterialTheme.typography.labelLarge,
                            color = Palette.Violet,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = viewModel::openPayment)
                                .padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.load != Load.READY) {
                        TicketSkeleton()
                    } else if (state.payments.isEmpty()) {
                        Text(state.copy.noPayments, color = Palette.InkSoft)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.payments.forEach { payment ->
                            TicketCard(
                                footer = payment.note ?: state.copy.paymentNoun,
                                badge = Money.format(payment.amount),
                                onClick = {},
                                onLongClick = { deletingPayment = payment },
                                footerIcon = Icons.Outlined.Payments,
                            ) {
                                Text(
                                    Dates.longDay(payment.localDate).replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Ink,
                                )
                                Text("Mantén presionado para eliminar", color = Palette.InkSoft, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                },
            )
        }
    }

    when (val dialog = state.dialog) {
        is GarageDialog.Day -> DayDetailSheet(
            date = dialog.date,
            day = state.dialogDay,
            defaultRate = state.settings.dailyRate,
            onSave = { note, rate -> viewModel.saveDay(dialog.date, note, rate) },
            onRemoveDay = { viewModel.removeDay(dialog.date) },
            onAddCharge = { description, amount, onAdded -> viewModel.addCharge(dialog.date, description, amount, onAdded) },
            onRemoveCharge = viewModel::removeCharge,
            onDismiss = viewModel::closeDialog,
        )
        GarageDialog.Settings -> GarageSettingsDialog(
            settings = state.settings,
            onSave = viewModel::saveSettings,
            onDismiss = viewModel::closeDialog,
        )
        GarageDialog.Payment -> PaymentDialog(
            title = state.copy.paymentDialogTitle,
            pending = state.summary.pending,
            onSave = viewModel::addPayment,
            onDismiss = viewModel::closeDialog,
        )
        GarageDialog.EditCounter -> state.counter?.let { counter ->
            CounterDialog(
                counter = counter,
                busy = false,
                onSave = viewModel::saveCounter,
                onDismiss = viewModel::closeDialog,
                onDelete = viewModel::openDeleteCounter,
            )
        }
        GarageDialog.Share -> state.counter?.let { counter ->
            ShareSheet(
                title = "Compartir «${counter.name}»",
                members = counter.members,
                invite = state.invite,
                busy = state.busy,
                demoMode = viewModel.demoMode,
                onSendText = null,
                onCreateInvite = viewModel::createInvite,
                onShareInvite = { invite ->
                    shareText(
                        context,
                        "Te comparto «${counter.name}» en Arianna para que veas los días y la cuenta. " +
                            "Únete con el código ${invite.code}: ${invite.url}",
                    )
                },
                onInvite = viewModel::invite,
                onRemoveMember = viewModel::removeMember,
                onDismiss = viewModel::closeDialog,
                intro = "Con \"Puede editar\" marca días y registra pagos (ej. tu pareja). " +
                    "Con \"Solo ver\" ve los días y cuánto se debe (ej. la niñera). Solo tú cambias la tarifa.",
                codeHint = "Quien use el código entra como \"Solo ver\". Para darle edición, invita su correo.",
                emptyMembers = "Todavía nadie más ve este contador.",
            )
        }
        GarageDialog.Leave -> ConfirmDialog(
            title = "Dejar de ver «${state.counter?.name.orEmpty()}»",
            message = "Ya no verás sus días ni la cuenta. ${state.counter?.ownerName.orEmpty()} puede volver a invitarte.",
            confirmText = "Dejar de ver",
            onConfirm = { viewModel.leave(onLeft = onBack) },
            onDismiss = viewModel::closeDialog,
        )
        GarageDialog.DeleteCounter -> ConfirmDialog(
            title = "Eliminar «${state.counter?.name.orEmpty()}»",
            message = "Se borran todos sus días, cargos y pagos. No se puede deshacer.",
            confirmText = "Eliminar",
            onConfirm = { viewModel.deleteCounter(onDeleted = onBack) },
            onDismiss = viewModel::closeDialog,
        )
        null -> Unit
    }
    deletingPayment?.let { payment ->
        ConfirmDialog(
            title = "Eliminar ${state.copy.paymentNoun.lowercase()}",
            message = "¿Eliminar el ${state.copy.paymentNoun.lowercase()} de ${Money.format(payment.amount)} del ${Dates.longDay(payment.localDate)}?",
            confirmText = "Eliminar",
            onConfirm = { viewModel.removePayment(payment) },
            onDismiss = { deletingPayment = null },
        )
    }
}

/** Resumen de la cuenta hasta hoy: pendiente, total, pagado y accesos rápidos. */
@Composable
private fun AccountCard(state: GarageUiState, onSettings: () -> Unit, onPay: () -> Unit) {
    val summary = state.summary
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.copy.pendingLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Palette.OnDarkSoft,
                modifier = Modifier.weight(1f),
            )
            Text(Dates.shortDay(LocalDate.parse(summary.until)), color = Palette.OnDarkSoft, fontSize = 14.sp)
        }
        if (state.load != Load.READY) {
            Spacer(Modifier.height(8.dp))
            SkeletonBox(width = 170.dp, height = 38.dp, onDark = true)
            Spacer(Modifier.height(10.dp))
            SkeletonBox(width = 230.dp, height = 13.dp, onDark = true)
            Spacer(Modifier.height(14.dp))
            SkeletonBox(height = 46.dp, shape = RoundedCornerShape(24.dp), onDark = true)
            return@GlassCard
        }
        Text(
            Money.format(summary.pending),
            color = if (summary.pending < 0) Palette.Success else Palette.OnDark,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 46.sp,
        )
        Text(
            "${summary.days} ${if (summary.days == 1) "día" else "días"} ${Money.format(summary.daysTotal)}" +
                (if (summary.chargesTotal > 0) " + cargos ${Money.format(summary.chargesTotal)}" else "") +
                " · pagado ${Money.format(summary.paid)}",
            color = Palette.OnDarkSoft,
            fontSize = 13.sp,
        )
        if (summary.pending < 0) {
            Text(state.copy.favorLabel, color = Palette.Success, fontSize = 13.sp)
        }
        if (summary.daysWithoutRate > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Palette.VioletBright, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${summary.daysWithoutRate} días sin tarifa no suman a la cuenta",
                    color = Palette.OnDarkSoft,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoChip(
                icon = if (state.settings.autoMark) Icons.Outlined.AutoMode else Icons.Outlined.Sell,
                text = state.settings.dailyRate?.let { "${Money.format(it)}/día" + if (state.settings.autoMark) " · auto" else "" }
                    ?: "Poner tarifa",
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
            if (state.canEdit) PillButton(state.copy.payButton, onClick = onPay, icon = Icons.Outlined.Payments, light = true)
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.GlassStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.OnDark, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        // Se achica si no cabe (pantallas pequeñas o letra del sistema grande) en vez de cortarse.
        BasicText(
            text,
            style = TextStyle(color = Palette.OnDark, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
        )
    }
}

/** Selector de modo (como "TRANSPORT" del diseño). */
@Composable
private fun ModeCard(state: GarageUiState, onMode: (GarageMode) -> Unit) {
    GlassCard {
        Text("MODO", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = Palette.OnDark)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuTile(
                Icons.Outlined.TouchApp, "Día", { onMode(GarageMode.DAY) },
                Modifier.weight(1f), selected = state.mode == GarageMode.DAY, compact = true,
            )
            MenuTile(
                Icons.Outlined.DateRange, "Rango", { onMode(GarageMode.RANGE) },
                Modifier.weight(1f), selected = state.mode == GarageMode.RANGE, compact = true,
            )
            MenuTile(
                Icons.Outlined.EditNote, "Detalle", { onMode(GarageMode.NOTE) },
                Modifier.weight(1f), selected = state.mode == GarageMode.NOTE, compact = true,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(state.hint, color = Palette.OnDarkSoft, fontSize = 13.sp)
    }
}

@Composable
private fun DayTicket(day: GarageDay, onClick: () -> Unit) {
    val details = buildList {
        add(day.rate?.let { "Tarifa ${Money.format(it)}" } ?: "Sin tarifa")
        day.charges.forEach { add("${it.description} ${Money.format(it.amount)}") }
    }.joinToString(" · ")
    TicketCard(
        footer = day.note ?: "Ver detalle",
        badge = Money.format(day.total),
        onClick = onClick,
        footerIcon = Icons.Outlined.EditNote,
    ) {
        Text(
            Dates.longDay(day.localDate).replaceFirstChar { it.uppercase() },
            fontWeight = FontWeight.Bold,
            color = Palette.Ink,
        )
        Text(details, color = Palette.InkSoft, fontSize = 13.sp)
    }
}
