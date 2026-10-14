package com.garaje.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.ui.theme.Palette

@Composable
fun garajeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Violet,
    unfocusedBorderColor = Palette.Divider,
    focusedLabelColor = Palette.Violet,
    cursorColor = Palette.Violet,
    focusedTextColor = Palette.Ink,
    unfocusedTextColor = Palette.Ink,
)

/** Diálogo genérico con un campo de texto. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    allowEmpty: Boolean = false,
    extraAction: Pair<String, () -> Unit>? = null,
    description: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (description != null) Text(description)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 3,
                    keyboardOptions = KeyboardOptions(capitalization = capitalization),
                    colors = garajeFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = allowEmpty || text.isNotBlank(),
            ) { Text(confirmText, color = Palette.Violet) }
        },
        dismissButton = {
            Row {
                extraAction?.let { (label, action) ->
                    TextButton(onClick = action) { Text(label, color = Palette.Danger) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
            }
        },
    )
}

/** Confirmación para acciones destructivas. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText, color = Palette.Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}

/** Crear lista: nombre + tipo (compras o tareas). */
@Composable
fun NewListDialog(
    onCreate: (String, ChecklistKind) -> Unit,
    onDismiss: () -> Unit,
    initialKind: ChecklistKind = ChecklistKind.SHOPPING,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        title = { Text("Nueva lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip("🛒 Compras", kind == ChecklistKind.SHOPPING) { kind = ChecklistKind.SHOPPING }
                    KindChip("✅ Tareas", kind == ChecklistKind.TODO) { kind = ChecklistKind.TODO }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre") },
                    placeholder = {
                        Text(if (kind == ChecklistKind.SHOPPING) "Ej: Supermercado" else "Ej: Pendientes de la casa")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = garajeFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, kind) }, enabled = title.isNotBlank()) {
                Text("Crear", color = Palette.Violet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Palette.Violet,
            selectedLabelColor = Palette.OnDark,
            labelColor = Palette.Ink,
        ),
    )
}
