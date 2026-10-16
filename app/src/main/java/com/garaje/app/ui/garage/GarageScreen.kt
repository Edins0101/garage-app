package com.garaje.app.ui.garage

import com.garaje.app.ui.components.TicketSkeleton
import com.garaje.app.ui.components.SkeletonBox
import com.garaje.app.ui.components.LoadNotice
import com.garaje.app.ui.common.Load
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
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
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
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.model.Payment
import com.garaje.app.ui.common.CollectMessages
import com.garaje.app.ui.common.ConfirmDialog
import com.garaje.app.ui.common.appViewModel
import com.garaje.app.ui.components.CosmicBackground
import com.garaje.app.ui.components.GlassCard
import com.garaje.app.ui.components.MenuTile
import com.garaje.app.ui.components.MonthCalendar
import com.garaje.app.ui.components.PeriodHeader
import com.garaje.app.ui.components.PillButton
import com.garaje.app.ui.components.ScrollingSheetLayout
import com.garaje.app.ui.components.SheetTitle
import com.garaje.app.ui.components.TicketCard
import com.garaje.app.ui.components.TopBar
import com.garaje.app.ui.theme.Palette
import com.garaje.app.util.Dates
import com.garaje.app.util.Money
import java.time.LocalDate

@Composable
fun GarageScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { GarageViewModel(it.garage) }
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
                        TopBar(title = "Mi garaje", overline = "Calendario y cuenta", onBack = onBack)
                        AccountCard(
                            state = state,
                            onSettings = viewModel::openSettings,
                            onPay = viewModel::openPayment,
                        )
                        ModeCard(state = state, onMode = viewModel::setMode)
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
                    SheetTitle("Pagos") {
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
                        Text("Aún no registras pagos.", color = Palette.InkSoft)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.payments.forEach { payment ->
                            TicketCard(
                                footer = payment.note ?: "Pago",
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
            pending = state.summary.pending,
            onSave = viewModel::addPayment,
            onDismiss = viewModel::closeDialog,
        )
        null -> Unit
    }
    deletingPayment?.let { payment ->
        ConfirmDialog(
            title = "Eliminar pago",
            message = "¿Eliminar el pago de ${Money.format(payment.amount)} del ${Dates.longDay(payment.localDate)}?",
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
                "PENDIENTE HASTA HOY",
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
            Text("Tienes saldo a favor.", color = Palette.Success, fontSize = 13.sp)
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
            PillButton("Pagar", onClick = onPay, icon = Icons.Outlined.Payments, light = true)
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
