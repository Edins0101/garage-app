package com.arianna.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.ui.theme.Palette

fun listIcon(kind: ChecklistKind): ImageVector =
    if (kind == ChecklistKind.SHOPPING) Icons.Outlined.ShoppingCart else Icons.Outlined.TaskAlt

/** "Faltan 3 de 8" / "Completa" / "Vacía". */
fun listProgressLabel(list: Checklist): String = when {
    list.items.isEmpty() -> "Todavía está vacía"
    list.pendingCount == 0 -> "Completa · ${list.items.size} de ${list.items.size}"
    list.pendingCount == 1 -> "Falta 1 de ${list.items.size}"
    else -> "Faltan ${list.pendingCount} de ${list.items.size}"
}

/** Elegir la lista adjunta de un recordatorio o contador ("Ninguna" + mis listas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListPicker(lists: List<Checklist>, selectedId: String?, onSelect: (String?) -> Unit, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Lista adjunta (opcional)", fontSize = 12.sp, color = Palette.InkSoft)
        if (lists.isEmpty()) {
            Text("Crea una lista de compras o tareas para adjuntarla aquí.", fontSize = 13.sp, color = Palette.InkSoft)
            return
        }
        Text(hint, fontSize = 12.sp, color = Palette.InkSoft)
        val colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Palette.Violet,
            selectedLabelColor = Palette.OnDark,
            selectedLeadingIconColor = Palette.OnDark,
            labelColor = Palette.Ink,
            iconColor = Palette.Violet,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("Ninguna", style = MaterialTheme.typography.bodyMedium) },
                colors = colors,
            )
            lists.forEach { list ->
                FilterChip(
                    selected = selectedId == list.id,
                    onClick = { onSelect(list.id) },
                    label = {
                        Text(list.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingIcon = { Icon(listIcon(list.kind), null, modifier = Modifier.size(18.dp)) },
                    colors = colors,
                )
            }
        }
    }
}

/**
 * Tarjeta de la lista adjunta con su avance; al tocarla se abre. [list] null con [listId] presente
 * significa que la lista no está compartida conmigo.
 */
@Composable
fun LinkedListCard(list: Checklist?, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.LavenderSoft)
            .then(if (list != null) Modifier.clickable { onOpen(list.id) } else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            list?.let { listIcon(it.kind) } ?: Icons.Outlined.TaskAlt,
            null,
            tint = Palette.Violet,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (list == null) {
                Text("Lista adjunta", color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                Text("No está compartida contigo.", color = Palette.InkSoft, fontSize = 13.sp)
            } else {
                Text(list.title, color = Palette.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listProgressLabel(list), color = Palette.InkSoft, fontSize = 13.sp)
                if (list.items.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { list.progress },
                        color = Palette.Violet,
                        trackColor = Palette.Lavender,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (list != null) {
            Spacer(Modifier.width(12.dp))
            Text("ABRIR", style = MaterialTheme.typography.labelMedium, color = Palette.Violet)
        }
    }
}
