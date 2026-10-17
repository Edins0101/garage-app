package com.arianna.app.ui.common

import android.content.res.Configuration
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Calendario para elegir una fecha: claro, con buen contraste y siempre en español
 * (aunque el teléfono esté en otro idioma). Se usa en recordatorios y pagos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Elige la fecha",
    /** Fechas permitidas (ej. "hasta" no puede ser antes de "desde"). */
    minDate: LocalDate? = null,
) {
    SpanishLocale {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    minDate == null || !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(minDate)
            },
        )
        val colors = DatePickerDefaults.colors(
            containerColor = Palette.Sheet,
            titleContentColor = Palette.InkSoft,
            headlineContentColor = Palette.Ink,
            weekdayContentColor = Palette.InkSoft,
            subheadContentColor = Palette.InkSoft,
            navigationContentColor = Palette.Ink,
            yearContentColor = Palette.Ink,
            currentYearContentColor = Palette.Violet,
            selectedYearContentColor = Palette.OnDark,
            selectedYearContainerColor = Palette.Violet,
            dayContentColor = Palette.Ink,
            disabledDayContentColor = Palette.InkSoft.copy(alpha = 0.35f),
            selectedDayContentColor = Palette.OnDark,
            selectedDayContainerColor = Palette.Violet,
            todayContentColor = Palette.Violet,
            todayDateBorderColor = Palette.Violet,
            dividerColor = Palette.Divider,
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            colors = colors,
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    onDismiss()
                }) { Text("Aceptar", color = Palette.Violet) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
            },
        ) {
            DatePicker(
                state = picker,
                colors = colors,
                showModeToggle = false,
                title = { Text(title, color = Palette.InkSoft, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                headline = {
                    val chosen = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    Text(
                        chosen?.let { Dates.longDay(it).replaceFirstChar { c -> c.uppercase() } } ?: "Sin fecha",
                        color = Palette.Ink,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                },
            )
        }
    }
}

/** Nombres de meses y días en español dentro de [content]. */
@Composable
private fun SpanishLocale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val configuration = remember(base) { Configuration(base).apply { setLocale(Dates.locale) } }
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(LocalConfiguration provides configuration, LocalContext provides localized, content = content)
}
