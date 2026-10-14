package com.garaje.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garaje.app.ui.theme.Palette
import com.garaje.app.util.Dates
import java.time.LocalDate
import java.time.YearMonth

/** Encabezado "‹ Octubre 2026 ›" para navegar entre periodos. */
@Composable
fun PeriodHeader(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior", tint = Palette.Ink, modifier = Modifier.size(32.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            color = Palette.Ink,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Siguiente", tint = Palette.Ink, modifier = Modifier.size(32.dp))
        }
    }
}

/** Banda violeta con los nombres de los días (LUN…DOM). */
@Composable
private fun WeekdayBand() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Violet)
            .padding(vertical = 4.dp),
    ) {
        Dates.weekdayShort.forEach { day ->
            Text(
                day.uppercase(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.OnDark,
            )
        }
    }
}

/** Estados visuales de una celda de día. */
private enum class DayStyle { NORMAL, MARKED, RANGE_START, OUTSIDE }

@Composable
private fun DayCell(
    date: LocalDate,
    style: DayStyle,
    isToday: Boolean,
    isSelected: Boolean,
    hasNote: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val background = when (style) {
        DayStyle.MARKED -> Palette.Violet
        DayStyle.RANGE_START -> Palette.Success
        else -> Color.Transparent
    }
    val textColor = when (style) {
        DayStyle.MARKED, DayStyle.RANGE_START -> Palette.OnDark
        DayStyle.OUTSIDE -> Palette.InkSoft.copy(alpha = 0.35f)
        DayStyle.NORMAL -> Palette.Ink
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isToday && style == DayStyle.NORMAL) Palette.DayBox else Color.Transparent)
            .combinedClickableCompat(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (isSelected) Modifier.border(2.dp, Palette.VioletBright, CircleShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 18.sp,
                fontWeight = if (style == DayStyle.MARKED || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
            )
        }
        if (hasNote) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Palette.VioletBright),
            )
        }
    }
}

/** Tira de una semana (como el SCHEDULE de la imagen). */
@Composable
fun WeekStrip(
    days: List<LocalDate>,
    marked: Set<LocalDate>,
    notes: Set<LocalDate>,
    selected: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Column(modifier.fillMaxWidth()) {
        WeekdayBand()
        Row(Modifier.fillMaxWidth()) {
            days.forEach { date ->
                DayCell(
                    date = date,
                    style = if (date in marked) DayStyle.MARKED else DayStyle.NORMAL,
                    isToday = date == today,
                    isSelected = date == selected,
                    hasNote = date in notes,
                    onClick = { onDayClick(date) },
                    onLongClick = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Calendario mensual completo con días marcados, notas y rango en curso. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    marked: Set<LocalDate>,
    notes: Set<LocalDate>,
    rangeStart: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val cells = Dates.monthGrid(month)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WeekdayBand()
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == month
                    val style = when {
                        date == rangeStart -> DayStyle.RANGE_START
                        date in marked -> DayStyle.MARKED
                        !inMonth -> DayStyle.OUTSIDE
                        else -> DayStyle.NORMAL
                    }
                    DayCell(
                        date = date,
                        style = style,
                        isToday = date == today,
                        isSelected = false,
                        hasNote = date in notes,
                        onClick = { onDayClick(date) },
                        onLongClick = { onDayLongClick(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
