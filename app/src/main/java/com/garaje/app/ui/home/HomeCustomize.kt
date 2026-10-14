package com.garaje.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garaje.app.data.prefs.HomePrefs
import com.garaje.app.data.prefs.HomeSection
import com.garaje.app.ui.theme.Palette

private data class SectionInfo(val icon: ImageVector, val title: String, val description: String)

private val HomeSection.info: SectionInfo
    get() = when (this) {
        HomeSection.GARAGE -> SectionInfo(
            Icons.Outlined.CalendarMonth,
            "Garaje",
            "Días del carro en el garaje y cuánto debes pagar",
        )
        HomeSection.SHOPPING -> SectionInfo(
            Icons.Outlined.ShoppingCart,
            "Compras",
            "Listas de supermercado y lo que falta comprar",
        )
        HomeSection.TASKS -> SectionInfo(
            Icons.Outlined.TaskAlt,
            "Tareas",
            "Pendientes de la casa, del carro o lo que necesites",
        )
    }

/** Primera vez: "¿Qué es lo más importante para ti?". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeChoiceSheet(onChoose: (HomeSection) -> Unit) {
    ModalBottomSheet(
        // Si la cierra sin elegir, se queda con el orden por defecto.
        onDismissRequest = { onChoose(HomeSection.entries.first()) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("¿Qué es lo más importante para ti?", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            Text(
                "Lo pondremos primero en tu inicio. Puedes cambiarlo cuando quieras desde el botón de personalizar.",
                color = Palette.InkSoft,
            )
            HomeSection.entries.forEach { section ->
                val info = section.info
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(Palette.Violet, Palette.VioletDeep)))
                        .clickable { onChoose(section) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(info.icon, null, tint = Palette.OnDark, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.OnDark)
                        Text(info.description, color = Palette.OnDarkSoft, fontSize = 13.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.OnDark)
                }
            }
        }
    }
}

/** Ordenar y ocultar las secciones del inicio. */
@Composable
fun CustomizeHomeDialog(
    prefs: HomePrefs,
    onMove: (HomeSection, Int) -> Unit,
    onToggle: (HomeSection) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.InkSoft,
        title = { Text("Personalizar inicio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ordena las secciones con las flechas y apaga las que no uses.", fontSize = 13.sp)
                prefs.order.forEachIndexed { index, section ->
                    val info = section.info
                    val visible = section !in prefs.hidden
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (visible) Palette.LavenderSoft else Palette.Sheet)
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(info.icon, null, tint = if (visible) Palette.Violet else Palette.InkSoft, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            info.title,
                            color = if (visible) Palette.Ink else Palette.InkSoft,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Column {
                            ArrowButton(Icons.Outlined.KeyboardArrowUp, "Subir", enabled = index > 0) { onMove(section, -1) }
                            ArrowButton(Icons.Outlined.KeyboardArrowDown, "Bajar", enabled = index < prefs.order.lastIndex) {
                                onMove(section, 1)
                            }
                        }
                        Switch(
                            checked = visible,
                            onCheckedChange = { onToggle(section) },
                            // Siempre debe quedar al menos una sección visible.
                            enabled = !visible || prefs.visible.size > 1,
                            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Violet),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo", color = Palette.Violet) }
        },
    )
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
            Icon(icon, description, tint = if (enabled) Palette.Ink else Palette.Divider)
        }
    }
}
