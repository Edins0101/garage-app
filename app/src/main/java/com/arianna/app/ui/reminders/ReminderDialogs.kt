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
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderLog
import com.arianna.app.domain.model.ReminderSpace
import com.arianna.app.domain.model.RepeatUnit
import com.arianna.app.domain.repository.ReminderDraft
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
import java.time.ZoneOffset

/** Opciones de repetición que se ofrecen (cubren casi todos los pagos de una casa). */
private val repeatOptions: List<Pair<Int?, RepeatUnit?>> = listOf(
    1 to RepeatUnit.MONTH,
    2 to RepeatUnit.MONTH,
    3 to RepeatUnit.MONTH,
    6 to RepeatUnit.MONTH,
    1 to RepeatUnit.YEAR,
    1 to RepeatUnit.WEEK,
    null to null,
)

private val noticeOptions = listOf(0, 1, 3, 7)

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
private fun DateRow(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("Aceptar", color = Palette.Violet) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("Cancelar", color = Palette.InkSoft) }
            },
        ) { DatePicker(state = picker) }
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

/** Crear o editar un recordatorio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditDialog(
    copy: KindCopy,
    reminder: Reminder?,
    spaces: List<ReminderSpace>,
    myId: String,
    busy: Boolean,
    onSave: (ReminderDraft, spaceId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(reminder?.title.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(reminder?.amount?.asInput().orEmpty()) }
    var dueText by rememberSaveable { mutableStateOf((reminder?.dueDate ?: LocalDate.now().plusDays(7)).toString()) }
    var repeatIndex by rememberSaveable {
        mutableStateOf(
            if (reminder == null) 0
            else repeatOptions.indexOf(reminder.every to reminder.unit).takeIf { it >= 0 } ?: -1,
        )
    }
    var notice by rememberSaveable { mutableStateOf(reminder?.notifyDaysBefore ?: 3) }
    var note by rememberSaveable { mutableStateOf(reminder?.note.orEmpty()) }
    var spaceId by rememberSaveable { mutableStateOf(spaces.find { it.ownerId == myId }?.id) }
    val amountValue = Money.parse(amount)
    val amountOk = amount.isBlank() || amountValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text(if (reminder == null) copy.newLabel else "Editar «${reminder.title}»") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                MoneyField(amount, { amount = it }, "Monto aproximado (opcional)", Modifier.fillMaxWidth())
                DateRow(if (reminder == null) "Próximo vencimiento" else "Vence", LocalDate.parse(dueText)) {
                    dueText = it.toString()
                }

                Text("Se repite", fontSize = 12.sp, color = Palette.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Si se creó con otra repetición (desde otra versión), se muestra tal cual.
                    if (repeatIndex == -1 && reminder != null) {
                        FilterChip(true, {}, { ChipText(Recurrence.label(reminder.every, reminder.unit)) }, colors = chipColors())
                    }
                    repeatOptions.forEachIndexed { index, (every, unit) ->
                        FilterChip(
                            selected = repeatIndex == index,
                            onClick = { repeatIndex = index },
                            label = { ChipText(Recurrence.label(every, unit)) },
                            colors = chipColors(),
                        )
                    }
                }

                Text("Avisarme", fontSize = 12.sp, color = Palette.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    noticeOptions.forEach { days ->
                        FilterChip(
                            selected = notice == days,
                            onClick = { notice = days },
                            label = { ChipText(Recurrence.noticeLabel(days)) },
                            colors = chipColors(),
                        )
                    }
                }

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
                    placeholder = { Text("Ej. número de cuenta o dónde se paga") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!amountOk) Text("El monto no es válido", color = Palette.Danger, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && amountOk && !busy,
                onClick = {
                    val (every, unit) = repeatOptions.getOrNull(repeatIndex) ?: (reminder?.every to reminder?.unit)
                    val draft = ReminderDraft(
                        title = title,
                        amount = amountValue,
                        every = every,
                        unit = unit,
                        nextDue = LocalDate.parse(dueText),
                        notifyDaysBefore = notice,
                        note = note,
                    )
                    onSave(draft, spaceId)
                },
            ) { Text(if (busy) "Guardando…" else "Guardar", color = Palette.Violet) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}

/** "Ya pagué": monto real (por defecto el aproximado) y nota. */
@Composable
fun ReminderDoneDialog(
    copy: KindCopy,
    reminder: Reminder,
    busy: Boolean,
    onConfirm: (amount: Double?, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(reminder.amount?.asInput().orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
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
                Text("Vencimiento del ${Dates.longDay(reminder.dueDate)}.", fontSize = 13.sp)
                MoneyField(amount, { amount = it }, "Monto", Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (reminder.repeats) {
                    val next = Recurrence.advance(reminder.dueDate, reminder.every!!, reminder.unit!!, null)
                    Text("Después te aviso para el ${Dates.longDay(next)}.", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amountOk && !busy, onClick = { onConfirm(amountValue, note) }) {
                Text(if (busy) "Guardando…" else copy.doneLabel, color = Palette.Violet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}

/** Detalle: datos, acciones e historial de pagos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailSheet(
    copy: KindCopy,
    reminder: Reminder,
    owner: String?,
    canEdit: Boolean,
    history: List<ReminderLog>?,
    busy: Boolean,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                    reminder.amount?.let { "≈ ${Money.format(it)}" },
                    Recurrence.label(reminder.every, reminder.unit),
                    "Aviso: ${Recurrence.noticeLabel(reminder.notifyDaysBefore).lowercase()}",
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
                },
                color = if (overdue) Palette.Danger else Palette.Violet,
                fontWeight = FontWeight.SemiBold,
            )
            reminder.note?.let { Text(it, color = Palette.Ink) }

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
                                "Vencía el ${Dates.shortDay(log.localDueDate).lowercase()}",
                                color = Palette.Ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val by = listOfNotNull(log.doneByName?.let { "Marcó $it" }, log.note).joinToString(" · ")
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
