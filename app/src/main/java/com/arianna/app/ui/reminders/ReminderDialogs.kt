package com.arianna.app.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.data.reminders.ReminderAlarmScheduler
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.CountFrom
import com.arianna.app.ui.common.LinkedListCard
import com.arianna.app.ui.common.ListPicker
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.model.ReminderLog
import com.arianna.app.domain.model.ReminderSpace
import com.arianna.app.domain.model.RepeatUnit
import com.arianna.app.domain.repository.ReminderDraft
import com.arianna.app.ui.common.AppDatePickerDialog
import com.arianna.app.ui.common.MoneyField
import com.arianna.app.ui.common.appFieldColors
import com.arianna.app.ui.common.asInput
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.Dates
import com.arianna.app.util.Money
import com.arianna.app.util.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Opciones de repetición (de la más frecuente a la menos). */
private val repeatOptions: List<Pair<Int?, RepeatUnit?>> = listOf(
    null to null,
    1 to RepeatUnit.DAY,
    1 to RepeatUnit.WEEK,
    2 to RepeatUnit.WEEK,
    1 to RepeatUnit.MONTH,
    2 to RepeatUnit.MONTH,
    3 to RepeatUnit.MONTH,
    6 to RepeatUnit.MONTH,
    1 to RepeatUnit.YEAR,
)

/** "Se repite" como lista desplegable. En un rango no se ofrece "Una sola vez". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatDropdown(every: Int?, unit: RepeatUnit?, inRange: Boolean, onChange: (Int?, RepeatUnit?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = repeatOptions.filterNot { inRange && it.first == null }.let { base ->
        // Una repetición que no está entre las opciones (ej. cada 5 años) se muestra tal cual.
        if ((every to unit) in base) base else listOf(every to unit) + base
    }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = Recurrence.label(every, unit),
            onValueChange = {},
            readOnly = true,
            label = { Text(if (inRange) "Dentro del rango, se repite" else "Se repite") },
            leadingIcon = { Icon(Icons.Outlined.Repeat, null, tint = Palette.Violet) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            colors = appFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.Sheet) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            Recurrence.label(option.first, option.second),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.Ink,
                            fontWeight = if (option == (every to unit)) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onChange(option.first, option.second)
                        open = false
                    },
                )
            }
        }
    }
}

private val noticeOptions = listOf(0, 1, 3, 7)

/** Anticipación cuando el recordatorio tiene hora: a la hora, 15 min, 1 hora o el día anterior. */
private val alertOptions = listOf(0, 15, 60, 1440)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Palette.Violet,
    selectedLabelColor = Palette.OnDark,
    labelColor = Palette.Ink,
)

/** Texto de las opciones: más chico que el estilo de etiquetas del tema (que va en mayúsculas espaciadas). */
@Composable
private fun ChipText(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

/** Fila con la fecha y "CAMBIAR" que abre el calendario. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, minDate: LocalDate? = null) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        AppDatePickerDialog(initial = date, onPick = onChange, onDismiss = { picking = false }, title = label, minDate = minDate)
    }
    Column {
        Text(label, fontSize = 12.sp, color = Palette.InkSoft)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { picking = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Event, null, tint = Palette.Violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(Dates.longDay(date).replaceFirstChar { it.uppercase() }, color = Palette.Ink, modifier = Modifier.weight(1f))
            Text("CAMBIAR", style = MaterialTheme.typography.labelMedium, color = Palette.Violet)
        }
    }
}

/** Fila con la hora: "AGREGAR" abre el reloj; con hora, "CAMBIAR" y "QUITAR". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(time: LocalTime?, onChange: (LocalTime?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        val start = time ?: LocalTime.of(9, 0)
        val picker = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = Palette.Sheet,
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(picker.hour, picker.minute))
                    picking = false
                }) { Text("Aceptar", color = Palette.Violet) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("Cancelar", color = Palette.InkSoft) }
            },
            text = {
                TimePicker(
                    state = picker,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Palette.LavenderSoft,
                        selectorColor = Palette.Violet,
                        clockDialSelectedContentColor = Palette.OnDark,
                        clockDialUnselectedContentColor = Palette.Ink,
                        timeSelectorSelectedContainerColor = Palette.Lavender,
                        timeSelectorSelectedContentColor = Palette.Ink,
                        timeSelectorUnselectedContainerColor = Palette.LavenderSoft,
                        timeSelectorUnselectedContentColor = Palette.InkSoft,
                        periodSelectorBorderColor = Palette.Divider,
                        periodSelectorSelectedContainerColor = Palette.Lavender,
                        periodSelectorSelectedContentColor = Palette.Ink,
                        periodSelectorUnselectedContainerColor = Palette.Sheet,
                        periodSelectorUnselectedContentColor = Palette.InkSoft,
                    ),
                )
            },
        )
    }
    Column {
        Text("Hora (opcional)", fontSize = 12.sp, color = Palette.InkSoft)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { picking = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Schedule, null, tint = Palette.Violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                time?.let(Dates::time) ?: "Sin hora: te aviso en la mañana",
                color = if (time == null) Palette.InkSoft else Palette.Ink,
                modifier = Modifier.weight(1f),
            )
            if (time != null) {
                Text(
                    "QUITAR",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Danger,
                    modifier = Modifier
                        .clickable { onChange(null) }
                        .padding(horizontal = 8.dp),
                )
            }
            Text(if (time == null) "AGREGAR" else "CAMBIAR", style = MaterialTheme.typography.labelMedium, color = Palette.Violet)
        }
    }
}

/** Crear o editar un recordatorio. Al crear se elige la categoría (y, si hay varias, de quién). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditDialog(
    reminder: Reminder?,
    initialKind: ReminderKind,
    spacesFor: (ReminderKind) -> List<ReminderSpace>,
    myId: String,
    /** Listas que se pueden adjuntar. */
    lists: List<Checklist>,
    busy: Boolean,
    onSave: (ReminderKind, ReminderDraft, spaceId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(reminder?.kind ?: initialKind) }
    val copy = KindCopy.of(kind)
    var title by rememberSaveable { mutableStateOf(reminder?.title.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(reminder?.amount?.asInput().orEmpty()) }
    var dueText by rememberSaveable { mutableStateOf((reminder?.dueDate ?: LocalDate.now().plusDays(7)).toString()) }
    // "Un día" o "Rango de fechas" (ej. la pastilla cada día del 18 al 25).
    var inRange by rememberSaveable { mutableStateOf(reminder?.endsOn != null) }
    var endText by rememberSaveable {
        mutableStateOf(reminder?.endsOn ?: (reminder?.dueDate ?: LocalDate.now().plusDays(7)).plusDays(6).toString())
    }
    var every by rememberSaveable { mutableStateOf(if (reminder == null) 1 else reminder.every) }
    var unit by rememberSaveable { mutableStateOf(if (reminder == null) RepeatUnit.MONTH else reminder.unit) }
    var notice by rememberSaveable { mutableStateOf(reminder?.notifyDaysBefore ?: 3) }
    var note by rememberSaveable { mutableStateOf(reminder?.note.orEmpty()) }
    var countFrom by rememberSaveable { mutableStateOf(reminder?.countFrom ?: CountFrom.defaultFor(kind)) }
    var everyKm by rememberSaveable { mutableStateOf(reminder?.everyKm?.toString().orEmpty()) }
    var timeText by rememberSaveable { mutableStateOf(reminder?.time?.toString()) }
    var alert by rememberSaveable { mutableStateOf(reminder?.alertMinutes ?: 0) }
    var listId by rememberSaveable { mutableStateOf(reminder?.listId) }
    val time = timeText?.let(LocalTime::parse)
    val context = LocalContext.current
    val exactAllowed = ReminderAlarmScheduler.canScheduleExact(context)
    val spaces = spacesFor(kind)
    var spaceId by rememberSaveable(kind) { mutableStateOf(spaces.find { it.ownerId == myId }?.id) }
    val amountValue = Money.parse(amount)
    val amountOk = amount.isBlank() || amountValue != null
    val kmValue = everyKm.toIntOrNull()
    val kmOk = everyKm.isBlank() || (kmValue != null && kmValue in 100..200_000)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text(if (reminder == null) "Nuevo recordatorio" else "Editar «${reminder.title}»") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (reminder == null) {
                    KindChips(selected = kind, onSelect = {
                        kind = it
                        countFrom = CountFrom.defaultFor(it)
                    })
                    if (copy.suggestions.isNotEmpty()) {
                        Text("Ideas rápidas", fontSize = 12.sp, color = Palette.InkSoft)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            copy.suggestions.forEach { suggestion ->
                                FilterChip(
                                    selected = title == suggestion.title,
                                    onClick = {
                                        title = suggestion.title
                                        every = suggestion.every
                                        unit = suggestion.unit
                                        countFrom = suggestion.countFrom ?: CountFrom.defaultFor(kind)
                                        everyKm = suggestion.everyKm?.toString().orEmpty()
                                    },
                                    label = { ChipText(suggestion.title) },
                                    colors = chipColors(),
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Nombre") },
                    placeholder = { Text(copy.namePlaceholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(amount, { amount = it }, "${copy.amountLabel} aproximado (opcional)", Modifier.fillMaxWidth())
                Text("Cuándo", fontSize = 12.sp, color = Palette.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(inRange.not(), { inRange = false }, { ChipText("Un día") }, colors = chipColors())
                    FilterChip(
                        selected = inRange,
                        onClick = {
                            inRange = true
                            // En un rango lo normal es cada día, contando desde la fecha fija.
                            if (every == null || unit == RepeatUnit.MONTH || unit == RepeatUnit.YEAR) {
                                every = 1
                                unit = RepeatUnit.DAY
                                notice = 0
                            }
                            countFrom = CountFrom.DUE
                            if (LocalDate.parse(endText) < LocalDate.parse(dueText)) endText = LocalDate.parse(dueText).plusDays(6).toString()
                        },
                        label = { ChipText("Rango de fechas") },
                        colors = chipColors(),
                    )
                }
                if (inRange) {
                    DateRow("Desde", LocalDate.parse(dueText), {
                        dueText = it.toString()
                        if (LocalDate.parse(endText) < it) endText = it.toString()
                    })
                    DateRow("Hasta", LocalDate.parse(endText), { endText = it.toString() }, minDate = LocalDate.parse(dueText))
                } else {
                    DateRow(if (reminder == null) "Próxima fecha" else "Vence", LocalDate.parse(dueText), { dueText = it.toString() })
                }
                TimeRow(time) { timeText = it?.toString() }

                RepeatDropdown(every, unit, inRange) { newEvery, newUnit ->
                    every = newEvery
                    unit = newUnit
                    // Algo de cada día se avisa el mismo día (no "3 días antes").
                    if (newUnit == RepeatUnit.DAY) notice = 0
                }

                if (every != null && unit != null && !inRange) {
                    Text("El siguiente se cuenta desde", fontSize = 12.sp, color = Palette.InkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = countFrom == CountFrom.DUE,
                            onClick = { countFrom = CountFrom.DUE },
                            label = { ChipText("La fecha fija") },
                            colors = chipColors(),
                        )
                        FilterChip(
                            selected = countFrom == CountFrom.DONE,
                            onClick = { countFrom = CountFrom.DONE },
                            label = { ChipText("El día que lo marco") },
                            colors = chipColors(),
                        )
                    }
                }

                if (kind == ReminderKind.CAR) {
                    OutlinedTextField(
                        value = everyKm,
                        onValueChange = { input -> everyKm = input.filter(Char::isDigit).take(6) },
                        label = { Text("O cada tantos km (opcional)") },
                        placeholder = { Text("Ej. 5000") },
                        suffix = { Text("km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = appFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text("Avisarme", fontSize = 12.sp, color = Palette.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (time == null) {
                        noticeOptions.forEach { days ->
                            FilterChip(
                                selected = notice == days,
                                onClick = { notice = days },
                                label = { ChipText(Recurrence.noticeLabel(days)) },
                                colors = chipColors(),
                            )
                        }
                    } else {
                        alertOptions.forEach { minutes ->
                            FilterChip(
                                selected = alert == minutes,
                                onClick = { alert = minutes },
                                label = { ChipText(Recurrence.alertLabel(minutes)) },
                                colors = chipColors(),
                            )
                        }
                    }
                }
                if (time != null && !exactAllowed) {
                    // Android 12+ puede tener apagadas las alarmas exactas: el aviso llegaría unos minutos tarde.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Para avisarte a la hora exacta, permite las alarmas de Arianna.",
                            fontSize = 13.sp,
                            color = Palette.InkSoft,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "PERMITIR",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Violet,
                            modifier = Modifier
                                .clickable { runCatching { context.startActivity(ReminderAlarmScheduler.exactAlarmSettings(context)) } }
                                .padding(8.dp),
                        )
                    }
                }

                ListPicker(lists, listId, { listId = it }, hint = "Ej. «Compras del mes» con la lista del súper: al avisarte te digo cuánto falta.")

                if (reminder == null && spaces.size > 1) {
                    Text("Guardar en", fontSize = 12.sp, color = Palette.InkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        spaces.forEach { space ->
                            FilterChip(
                                selected = spaceId == space.id,
                                onClick = { spaceId = space.id },
                                label = { ChipText(if (space.ownerId == myId) "Míos" else "De ${space.ownerName}") },
                                colors = chipColors(),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Nota (opcional)") },
                    placeholder = { Text("Ej. número de cuenta, taller o teléfono") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!amountOk) Text("El monto no es válido", color = Palette.Danger, fontSize = 13.sp)
                if (!kmOk) Text("Los km deben estar entre 100 y 200.000", color = Palette.Danger, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && amountOk && kmOk && !busy,
                onClick = {
                    val draft = ReminderDraft(
                        title = title,
                        amount = amountValue,
                        every = every,
                        unit = unit,
                        nextDue = LocalDate.parse(dueText),
                        notifyDaysBefore = notice,
                        note = note,
                        countFrom = countFrom,
                        everyKm = kmValue.takeIf { kind == ReminderKind.CAR },
                        endsOn = LocalDate.parse(endText).takeIf { inRange && every != null },
                        dueTime = time,
                        alertMinutes = alert.takeIf { time != null },
                        listId = listId,
                    )
                    onSave(kind, draft, spaceId)
                },
            ) { Text(if (busy) "Guardando…" else "Guardar", color = Palette.Violet) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}

/** "Ya pagué" / "Hecho": monto real (por defecto el aproximado), nota y, en Mi carro, el kilometraje. */
@Composable
fun ReminderDoneDialog(
    reminder: Reminder,
    busy: Boolean,
    onConfirm: (amount: Double?, note: String, odometer: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = KindCopy.of(reminder.kind)
    var amount by rememberSaveable { mutableStateOf(reminder.amount?.asInput().orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    var odometer by rememberSaveable { mutableStateOf("") }
    val amountValue = Money.parse(amount)
    val amountOk = amount.isBlank() || amountValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text("${copy.doneTitle}: «${reminder.title}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Fecha del ${Dates.longDay(reminder.dueDate)}.", fontSize = 13.sp)
                MoneyField(
                    amount,
                    { amount = it },
                    if (reminder.amount == null) "${copy.amountLabel} (opcional)" else copy.amountLabel,
                    Modifier.fillMaxWidth(),
                )
                if (reminder.kind == ReminderKind.CAR) {
                    OutlinedTextField(
                        value = odometer,
                        onValueChange = { input -> odometer = input.filter(Char::isDigit).take(7) },
                        label = { Text("Kilometraje actual (opcional)") },
                        suffix = { Text("km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = appFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Recurrence.nextAfterDone(reminder, LocalDate.now())?.let { next ->
                    val km = reminder.everyKm?.let { every -> odometer.toIntOrNull()?.plus(every) }
                    Text(
                        "Después te aviso para el ${Dates.longDay(next)}" +
                            (km?.let { " (o a los ${formatKm(it)})" } ?: "") + ".",
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amountOk && !busy, onClick = { onConfirm(amountValue, note, odometer.toIntOrNull()) }) {
                Text(if (busy) "Guardando…" else copy.doneLabel, color = Palette.Violet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}

/** Detalle: datos, acciones e historial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailSheet(
    reminder: Reminder,
    owner: String?,
    canEdit: Boolean,
    history: List<ReminderLog>?,
    /** Lista adjunta (null si no tiene o no está compartida conmigo). */
    linkedList: Checklist?,
    busy: Boolean,
    onOpenList: (String) -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = KindCopy.of(reminder.kind)
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(reminder.title, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                val facts = listOfNotNull(
                    copy.title,
                    reminder.amount?.let { "≈ ${Money.format(it)}" },
                    Recurrence.label(reminder) +
                        (reminder.everyKm?.let { " o ${formatKm(it)}" } ?: ""),
                    reminder.time?.let { "Aviso: ${Recurrence.alertLabel(reminder.alertMinutes ?: 0).lowercase()}" }
                        ?: "Aviso: ${Recurrence.noticeLabel(reminder.notifyDaysBefore).lowercase()}",
                    owner,
                )
                Text(facts.joinToString(" · "), color = Palette.InkSoft, fontSize = 14.sp)
            }
            val overdue = !reminder.finished && reminder.dueDate < LocalDate.now()
            Text(
                when {
                    reminder.finished -> "Hecho"
                    Recurrence.daysUntil(reminder) > 6 -> "Vence el ${Dates.longDay(reminder.dueDate)}"
                    else -> "${Recurrence.dueLabel(reminder)} · ${Dates.longDay(reminder.dueDate)}"
                } + (reminder.time?.let { " · ${Dates.time(it)}" } ?: "") +
                    (reminder.nextKm?.let { " · o a los ${formatKm(it)}" } ?: ""),
                color = if (overdue) Palette.Danger else Palette.Violet,
                fontWeight = FontWeight.SemiBold,
            )
            reminder.note?.let { Text(it, color = Palette.Ink) }
            if (reminder.listId != null) LinkedListCard(linkedList, onOpenList)

            if (canEdit) {
                if (!reminder.finished) {
                    PillButton(copy.doneLabel, onClick = onDone, enabled = !busy, icon = Icons.Outlined.TaskAlt, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Editar", onClick = onEdit, icon = Icons.Outlined.Edit, light = true, modifier = Modifier.weight(1f))
                    if (reminder.lastDone != null) {
                        PillButton("Deshacer", onClick = onUndo, enabled = !busy, icon = Icons.AutoMirrored.Outlined.Undo, light = true, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Text("Solo puedes ver esta sección.", color = Palette.InkSoft, fontSize = 13.sp)
            }

            SheetTitle("Historial")
            when {
                history == null -> CircularProgressIndicator(color = Palette.Violet, modifier = Modifier.size(24.dp))
                history.isEmpty() -> Text("Todavía no lo marcas como ${copy.donePast}.", color = Palette.InkSoft, fontSize = 14.sp)
                else -> history.forEach { log ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (reminder.countFrom == CountFrom.DONE) "Hecho el ${Dates.shortDay(log.doneDate).lowercase()}"
                                else "Vencía el ${Dates.shortDay(log.localDueDate).lowercase()}",
                                color = Palette.Ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val by = listOfNotNull(
                                log.odometer?.let(::formatKm),
                                log.doneByName?.let { "Marcó $it" },
                                log.note,
                            ).joinToString(" · ")
                            if (by.isNotEmpty()) Text(by, color = Palette.InkSoft, fontSize = 13.sp)
                        }
                        log.amount?.let { Text(Money.format(it), color = Palette.Violet, fontWeight = FontWeight.Bold) }
                    }
                    HorizontalDivider(color = Palette.Divider)
                }
            }

            if (canEdit) {
                Text(
                    "ELIMINAR",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Danger,
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
