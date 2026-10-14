package com.garaje.app.ui.garage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.model.GarageSettings
import com.garaje.app.ui.common.garajeFieldColors
import com.garaje.app.ui.components.PillButton
import com.garaje.app.ui.components.SheetTitle
import com.garaje.app.ui.theme.Palette
import com.garaje.app.util.Dates
import com.garaje.app.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun Double.asInput(): String = Money.toApi(this).removeSuffix(".00")

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' || it == ',' }.take(9)) },
        label = { Text(label) },
        prefix = { Text("$ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        colors = garajeFieldColors(),
        modifier = modifier,
    )
}

/** Detalle de un día: nota, tarifa del día y cargos extra. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: LocalDate,
    day: GarageDay?,
    defaultRate: Double?,
    onSave: (note: String, rate: String) -> Unit,
    onRemoveDay: () -> Unit,
    onAddCharge: (description: String, amount: String, onAdded: () -> Unit) -> Unit,
    onRemoveCharge: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable(date) { mutableStateOf(day?.note.orEmpty()) }
    var rate by rememberSaveable(date) { mutableStateOf((day?.rate ?: defaultRate)?.asInput().orEmpty()) }
    var chargeText by rememberSaveable(date) { mutableStateOf("") }
    var chargeAmount by rememberSaveable(date) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                Dates.longDay(date).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineSmall,
                color = Palette.Ink,
            )
            Text(
                when {
                    day == null -> "Este día no está marcado. Al guardar se marca en el garaje."
                    else -> "Total del día: ${Money.format(day.total)}"
                },
                color = if (day == null) Palette.InkSoft else Palette.Violet,
                fontWeight = if (day == null) FontWeight.Normal else FontWeight.Bold,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (mantenimiento, viaje…)") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = garajeFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(rate, { rate = it }, "Tarifa de este día", Modifier.fillMaxWidth())
            if (defaultRate == null && day?.rate == null) {
                Text(
                    "Aún no configuras una tarifa por defecto (botón \"Tarifa\" arriba).",
                    color = Palette.InkSoft,
                    fontSize = 13.sp,
                )
            }

            SheetTitle("Cargos extra")
            if (day?.charges.isNullOrEmpty()) {
                Text("Sin cargos extra.", color = Palette.InkSoft, fontSize = 14.sp)
            }
            day?.charges?.forEach { charge ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(charge.description, color = Palette.Ink, modifier = Modifier.weight(1f))
                    Text(Money.format(charge.amount), color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onRemoveCharge(charge.id) }) {
                        Icon(Icons.Outlined.Close, "Quitar cargo", tint = Palette.Danger)
                    }
                }
                HorizontalDivider(color = Palette.Divider)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chargeText,
                    onValueChange = { chargeText = it },
                    label = { Text("Ej: Lavado") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = garajeFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                MoneyField(chargeAmount, { chargeAmount = it }, "Monto", Modifier.width(110.dp))
            }
            PillButton(
                "Agregar cargo",
                onClick = {
                    onAddCharge(chargeText, chargeAmount) {
                        chargeText = ""
                        chargeAmount = ""
                    }
                },
                enabled = chargeText.isNotBlank() && chargeAmount.isNotBlank(),
                light = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            PillButton(
                if (day == null) "Marcar y guardar" else "Guardar",
                onClick = { onSave(note, rate) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (day != null) {
                TextButton(onClick = onRemoveDay, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(
                        if (day.charges.isEmpty()) "Quitar este día" else "Quitar este día y sus cargos",
                        color = Palette.Danger,
                    )
                }
            }
        }
    }
}

/** Tarifa por defecto y marcado automático. */
@Composable
fun GarageSettingsDialog(
    settings: GarageSettings,
    onSave: (rate: String, autoMark: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var rate by rememberSaveable { mutableStateOf(settings.dailyRate?.asInput().orEmpty()) }
    var autoMark by rememberSaveable { mutableStateOf(settings.autoMark) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text("Tarifa del garaje") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MoneyField(rate, { rate = it }, "Tarifa por día", Modifier.fillMaxWidth())
                Text(
                    "Cada día que marques guarda la tarifa vigente en ese momento: si la cambias, " +
                        "los días anteriores no se modifican. Los días que no tenían tarifa la toman ahora.",
                    fontSize = 13.sp,
                )
                HorizontalDivider(color = Palette.Divider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Marcar todos los días", color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Desde hoy, cada día se agrega solo con la tarifa por defecto. " +
                                "Si un día el carro no estuvo, quítalo y no vuelve a aparecer.",
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = autoMark,
                        onCheckedChange = { autoMark = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Violet),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rate, autoMark) }) { Text("Guardar", color = Palette.Violet) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}

/** Registrar un pago: monto, fecha (hoy por defecto) y nota. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    pending: Double,
    onSave: (date: LocalDate, amount: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(if (pending > 0) pending.asInput() else "") }
    var note by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)

    if (pickingDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        dateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    pickingDate = false
                }) { Text("Aceptar", color = Palette.Violet) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Cancelar", color = Palette.InkSoft) }
            },
        ) { DatePicker(state = picker) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text("Registrar pago") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pending > 0) Text("Pendiente hasta hoy: ${Money.format(pending)}", fontSize = 13.sp)
                MoneyField(amount, { amount = it }, "Monto pagado", Modifier.fillMaxWidth())
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pickingDate = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Event, null, tint = Palette.Violet, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Dates.longDay(date).replaceFirstChar { it.uppercase() },
                        color = Palette.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text("CAMBIAR", style = MaterialTheme.typography.labelMedium, color = Palette.Violet)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = garajeFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(date, amount, note) }, enabled = amount.isNotBlank()) {
                Text("Registrar", color = Palette.Violet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}
