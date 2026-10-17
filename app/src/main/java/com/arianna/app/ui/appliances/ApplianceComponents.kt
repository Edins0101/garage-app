package com.arianna.app.ui.appliances

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DryCleaning
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.domain.model.Appliance
import com.arianna.app.domain.model.ApplianceIcon
import com.arianna.app.domain.model.ApplianceUse
import com.arianna.app.domain.model.NotifyMode
import com.arianna.app.domain.repository.ApplianceDraft
import com.arianna.app.ui.common.appFieldColors
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.lists.GroupSection
import com.arianna.app.ui.lists.GroupSharing
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.ApplianceStatus
import com.arianna.app.util.Dates
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

fun applianceIcon(icon: ApplianceIcon): ImageVector = when (icon) {
    ApplianceIcon.WASHER -> Icons.Outlined.LocalLaundryService
    ApplianceIcon.DRYER -> Icons.Outlined.DryCleaning
    ApplianceIcon.BATH -> Icons.Outlined.Bathtub
    ApplianceIcon.CAR -> Icons.Outlined.DirectionsCar
    ApplianceIcon.KITCHEN -> Icons.Outlined.Kitchen
    ApplianceIcon.TV -> Icons.Outlined.Tv
    ApplianceIcon.COMPUTER -> Icons.Outlined.Computer
    ApplianceIcon.OTHER -> Icons.Outlined.Devices
}

/** La hora actual, que avanza cada 30 s (para "libre en 12 min"). */
@Composable
fun rememberNow(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    return now
}

/** Lo que se puede hacer con un equipo según su estado. */
enum class ApplianceAction { START, FINISH, RELEASE, NONE }

fun Appliance.actionFor(myId: String?): ApplianceAction = when {
    !canUse -> ApplianceAction.NONE
    !busy -> ApplianceAction.START
    busyById == myId -> ApplianceAction.FINISH
    else -> ApplianceAction.RELEASE
}

/** Tarjeta de un equipo: ícono, estado (LIBRE / EN USO) y el botón principal. */
@Composable
fun ApplianceCard(
    appliance: Appliance,
    myId: String?,
    now: Instant,
    onAction: (ApplianceAction) -> Unit,
    onClick: () -> Unit,
    busy: Boolean = false,
) {
    val inUse = appliance.busy
    val tone = if (inUse) Palette.Danger else Palette.Success
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.LavenderSoft)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(applianceIcon(appliance.icon), contentDescription = null, tint = tone, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(appliance.name, fontWeight = FontWeight.Bold, color = Palette.Ink, fontSize = 17.sp)
                Text(ApplianceStatus.line(appliance, myId, now), color = Palette.InkSoft, fontSize = 13.sp)
                if (!appliance.mine) Text("De ${appliance.ownerName}", color = Palette.InkSoft, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (inUse) "EN USO" else "LIBRE",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.OnDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tone)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        when (val action = appliance.actionFor(myId)) {
            ApplianceAction.START -> PillButton(
                "Empezar a usar", onClick = { onAction(action) }, enabled = !busy,
                icon = Icons.Outlined.PlayArrow, modifier = Modifier.fillMaxWidth(),
            )
            ApplianceAction.FINISH -> PillButton(
                "Ya terminé", onClick = { onAction(action) }, enabled = !busy,
                icon = Icons.Outlined.TaskAlt, modifier = Modifier.fillMaxWidth(),
            )
            ApplianceAction.RELEASE -> PillButton(
                "Liberar (se olvidó)", onClick = { onAction(action) }, enabled = !busy,
                light = true, modifier = Modifier.fillMaxWidth(),
            )
            ApplianceAction.NONE -> Text("Solo puedes ver su estado.", color = Palette.InkSoft, fontSize = 13.sp)
        }
    }
}

/** Opciones de tiempo al empezar: la duración habitual primero. */
private val QUICK_MINUTES = listOf(15, 30, 45, 60, 90, 120)

/** "¿Cuánto vas a tardar?": avisa a los demás la hora estimada y me recuerda liberarlo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartDialog(appliance: Appliance, onStart: (Int?) -> Unit, onDismiss: () -> Unit) {
    val options = (listOfNotNull(appliance.defaultMinutes) + QUICK_MINUTES).distinct()
    var chosen by rememberSaveable { mutableStateOf<Int?>(appliance.defaultMinutes ?: 45) }
    var custom by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text("Usar ${appliance.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("¿Cuánto vas a tardar? Los demás verán a qué hora queda libre y te aviso cuando se acabe el tiempo.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { minutes ->
                        TimeChip(ApplianceStatus.duration(minutes), chosen == minutes && custom.isBlank()) {
                            chosen = minutes
                            custom = ""
                        }
                    }
                    TimeChip("No sé", chosen == null && custom.isBlank()) {
                        chosen = null
                        custom = ""
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                    label = { Text("Otro (minutos)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val minutes = custom.toIntOrNull()?.coerceIn(1, 1440) ?: chosen
            TextButton(onClick = { onStart(minutes) }) { Text("Empezar", color = Palette.Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) } },
    )
}

@Composable
private fun TimeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Palette.Violet,
            selectedLabelColor = Palette.OnDark,
            labelColor = Palette.Ink,
        ),
    )
}

/** Crear o editar un equipo: nombre, ícono y cuánto dura un uso normal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApplianceDialog(appliance: Appliance?, busy: Boolean, onSave: (ApplianceDraft) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(appliance?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(appliance?.icon ?: ApplianceIcon.WASHER) }
    var minutes by rememberSaveable { mutableStateOf(appliance?.defaultMinutes?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text(if (appliance == null) "Nuevo equipo" else "Editar ${appliance.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Lavadora, Baño de arriba") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ApplianceIcon.entries.forEach { option ->
                        FilterChip(
                            selected = icon == option,
                            onClick = {
                                // Si el nombre sigue vacío, se sugiere el del ícono.
                                if (name.isBlank() || ApplianceIcon.entries.any { it.label == name }) {
                                    name = if (option == ApplianceIcon.OTHER) "" else option.label
                                }
                                icon = option
                            },
                            label = { Text(option.label, style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(applianceIcon(option), null, modifier = Modifier.size(18.dp)) },
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
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text("Duración normal (minutos, opcional)") },
                    placeholder = { Text("Ej. 45 para un lavado") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !busy,
                onClick = { onSave(ApplianceDraft(name, icon, minutes.toIntOrNull()?.coerceIn(1, 1440))) },
            ) { Text("Guardar", color = Palette.Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) } },
    )
}

/** Detalle: avisos, historial, compartir con grupos, editar y eliminar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplianceDetailSheet(
    appliance: Appliance,
    myId: String?,
    history: List<ApplianceUse>?,
    sharing: GroupSharing?,
    onNotify: (NotifyMode) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now = rememberNow()
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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(applianceIcon(appliance.icon), null, tint = Palette.Violet, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(appliance.name, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                    Text(ApplianceStatus.line(appliance, myId, now), color = Palette.InkSoft, fontSize = 13.sp)
                }
            }

            SheetTitle("Mis avisos")
            Column {
                NotifyMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = appliance.notify == mode, role = Role.RadioButton, onClick = { onNotify(mode) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = appliance.notify == mode,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Palette.Violet),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(mode.label, color = Palette.Ink)
                    }
                }
            }

            if (sharing != null) GroupSection(sharing)

            SheetTitle("Historial")
            when {
                history == null -> Text("Cargando…", color = Palette.InkSoft)
                history.isEmpty() -> Text("Todavía nadie lo ha usado.", color = Palette.InkSoft)
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    history.take(10).forEach { use -> HistoryRow(use) }
                }
            }

            if (appliance.mine) {
                HorizontalDivider(color = Palette.Divider)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Editar", onClick = onEdit, icon = Icons.Outlined.Edit, light = true, modifier = Modifier.weight(1f))
                    PillButton("Eliminar", onClick = onDelete, icon = Icons.Outlined.DeleteOutline, light = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(use: ApplianceUse) {
    val zone = ZoneId.systemDefault()
    val start = Instant.parse(use.startedAt)
    val day = Dates.longDay(start.atZone(zone).toLocalDate()).replaceFirstChar { it.uppercase() }
    val end = use.endedAt?.let(Instant::parse)
    Column {
        Text(use.userName, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
        Text(
            buildString {
                append("$day · ${ApplianceStatus.clock(start, zone)}")
                if (end == null) {
                    append(" · en uso")
                } else {
                    append(" a ${ApplianceStatus.clock(end, zone)}")
                    when {
                        use.auto -> append(" · se liberó solo")
                        use.endedByName != null && use.endedByName != use.userName -> append(" · lo liberó ${use.endedByName}")
                    }
                }
            },
            color = Palette.InkSoft,
            fontSize = 13.sp,
        )
    }
}

/** Confirmación antes de liberar algo que está usando otra persona. */
@Composable
fun ReleaseDialog(appliance: Appliance, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text("¿Liberar ${appliance.name}?") },
        text = { Text("Está en uso por ${appliance.busyByName ?: "otra persona"}. Libéralo solo si ya terminó y se olvidó de hacerlo. Le avisaré.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Liberar", color = Palette.Violet) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) } },
    )
}

/** Mientras la pantalla está abierta, se recarga cada minuto (otra persona pudo ocuparlo o liberarlo). */
@Composable
fun AutoReload(onReload: () -> Unit) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            onReload()
        }
    }
}
