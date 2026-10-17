package com.arianna.app.ui.counters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.Counter
import com.arianna.app.ui.common.ListPicker
import com.arianna.app.domain.model.CounterDirection
import com.arianna.app.domain.model.CounterIcon
import com.arianna.app.domain.repository.CounterDraft
import com.arianna.app.ui.common.MoneyField
import com.arianna.app.ui.common.appFieldColors
import com.arianna.app.ui.common.asInput
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.Money

fun counterIcon(icon: CounterIcon): ImageVector = when (icon) {
    CounterIcon.CAR -> Icons.Outlined.DirectionsCar
    CounterIcon.CHILD -> Icons.Outlined.ChildCare
    CounterIcon.BUS -> Icons.Outlined.DirectionsBus
    CounterIcon.GYM -> Icons.Outlined.FitnessCenter
    CounterIcon.WORK -> Icons.Outlined.Work
    CounterIcon.PET -> Icons.Outlined.Pets
    CounterIcon.HOME -> Icons.Outlined.House
    CounterIcon.OTHER -> Icons.Outlined.CalendarMonth
}

/** Textos que cambian si yo pago (garaje, niñera) o me pagan (días trabajados). */
data class CounterCopy(
    val pendingLabel: String,
    val payButton: String,
    val paymentsTitle: String,
    val paymentNoun: String,
    val paymentDialogTitle: String,
    val noPayments: String,
    val favorLabel: String,
) {
    companion object {
        fun of(direction: CounterDirection) = when (direction) {
            CounterDirection.PAY -> CounterCopy(
                pendingLabel = "PENDIENTE HASTA HOY",
                payButton = "Pagar",
                paymentsTitle = "Pagos",
                paymentNoun = "Pago",
                paymentDialogTitle = "Registrar pago",
                noPayments = "Aún no registras pagos.",
                favorLabel = "Tienes saldo a favor.",
            )
            CounterDirection.EARN -> CounterCopy(
                pendingLabel = "TE DEBEN HASTA HOY",
                payButton = "Cobré",
                paymentsTitle = "Cobros",
                paymentNoun = "Cobro",
                paymentDialogTitle = "Registrar cobro",
                noPayments = "Aún no registras cobros.",
                favorLabel = "Te pagaron por adelantado.",
            )
        }
    }
}

/** Atajos para crear contadores comunes. */
private data class CounterIdea(val name: String, val icon: CounterIcon, val direction: CounterDirection = CounterDirection.PAY)

private val ideas = listOf(
    CounterIdea("Garaje", CounterIcon.CAR),
    CounterIdea("Niñera", CounterIcon.CHILD),
    CounterIdea("Expreso escolar", CounterIcon.BUS),
    CounterIdea("Gimnasio", CounterIcon.GYM),
    CounterIdea("Guardería de la mascota", CounterIcon.PET),
    CounterIdea("Empleada doméstica", CounterIcon.HOME),
    CounterIdea("Días trabajados", CounterIcon.WORK, CounterDirection.EARN),
)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Palette.Violet,
    selectedLabelColor = Palette.OnDark,
    selectedLeadingIconColor = Palette.OnDark,
    labelColor = Palette.Ink,
    iconColor = Palette.Violet,
)

@Composable
private fun ChipText(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

/** Crear o editar un contador: nombre, ícono, quién paga, tarifa por día y marcado automático. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CounterDialog(
    counter: Counter?,
    /** Listas que se pueden adjuntar (ej. las tareas de la niñera). */
    lists: List<Checklist>,
    busy: Boolean,
    onSave: (CounterDraft) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(counter?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(counter?.icon ?: CounterIcon.OTHER) }
    var direction by rememberSaveable { mutableStateOf(counter?.direction ?: CounterDirection.PAY) }
    var rate by rememberSaveable { mutableStateOf(counter?.dailyRate?.asInput().orEmpty()) }
    var autoMark by rememberSaveable { mutableStateOf(counter?.autoMark ?: false) }
    var listId by rememberSaveable { mutableStateOf(counter?.listId) }
    val rateValue = Money.parse(rate)
    val rateOk = rate.isBlank() || rateValue != null
    val autoOk = !autoMark || rateValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text(if (counter == null) "Nuevo contador" else "Editar «${counter.name}»") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (counter == null) {
                    Text("Ideas rápidas", fontSize = 12.sp, color = Palette.InkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ideas.forEach { idea ->
                            FilterChip(
                                selected = name == idea.name,
                                onClick = {
                                    name = idea.name
                                    icon = idea.icon
                                    direction = idea.direction
                                },
                                label = { ChipText(idea.name) },
                                leadingIcon = { Icon(counterIcon(idea.icon), null, modifier = Modifier.size(18.dp)) },
                                colors = chipColors(),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Nombre") },
                    placeholder = { Text("Garaje, niñera, expreso…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Ícono", fontSize = 12.sp, color = Palette.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CounterIcon.entries.forEach { option ->
                        FilterChip(
                            selected = icon == option,
                            onClick = { icon = option },
                            label = { Icon(counterIcon(option), contentDescription = option.name, modifier = Modifier.size(20.dp)) },
                            colors = chipColors(),
                        )
                    }
                }
                Text("¿Quién paga?", fontSize = 12.sp, color = Palette.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == CounterDirection.PAY,
                        onClick = { direction = CounterDirection.PAY },
                        label = { ChipText("Yo pago") },
                        colors = chipColors(),
                    )
                    FilterChip(
                        selected = direction == CounterDirection.EARN,
                        onClick = { direction = CounterDirection.EARN },
                        label = { ChipText("Me pagan") },
                        colors = chipColors(),
                    )
                }
                MoneyField(rate, { rate = it }, "Tarifa por día (opcional)", Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Marcar cada día solo", color = Palette.Ink)
                        Text("Con la tarifa por día, desde hoy.", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = autoMark,
                        onCheckedChange = { autoMark = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Violet),
                    )
                }
                ListPicker(lists, listId, { listId = it }, hint = "Ej. las tareas de la niñera o lo que hay que hacer en el garaje.")
                if (!rateOk) Text("La tarifa no es un monto válido", color = Palette.Danger, fontSize = 13.sp)
                if (rateOk && !autoOk) Text("Pon la tarifa para marcar cada día solo", color = Palette.Danger, fontSize = 13.sp)
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("ELIMINAR CONTADOR", color = Palette.Danger) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && rateOk && autoOk && !busy,
                onClick = { onSave(CounterDraft(name, icon, direction, rateValue, autoMark, listId)) },
            ) { Text(if (busy) "Guardando…" else "Guardar", color = Palette.Violet) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}
