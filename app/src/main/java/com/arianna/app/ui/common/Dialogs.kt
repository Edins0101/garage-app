package com.arianna.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.arianna.app.util.Money
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.ui.theme.Palette

@Composable
fun appFieldColors() = OutlinedTextFieldDefaults.colors(
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 3,
                    keyboardOptions = KeyboardOptions(capitalization = capitalization),
                    colors = appFieldColors(),
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre") },
                    placeholder = {
                        Text(if (kind == ChecklistKind.SHOPPING) "Ej: Supermercado" else "Ej: Pendientes de la casa")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
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

/** Monto para un campo de texto: 12.50 → "12.50", 12.00 → "12". */
fun Double.asInput(): String = Money.toApi(this).removeSuffix(".00")

/** Campo de monto en dólares (solo números y separador decimal). */
@Composable
fun MoneyField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' || it == ',' }.take(9)) },
        label = { Text(label) },
        prefix = { Text("$ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        colors = appFieldColors(),
        modifier = modifier,
    )
}

/** Abre la hoja de compartir de Android (WhatsApp, SMS, correo…) con un texto. */
fun shareText(context: Context, text: String, chooserTitle: String = "Compartir") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
