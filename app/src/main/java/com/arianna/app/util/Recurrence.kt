package com.arianna.app.util

import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.RepeatUnit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Fechas de los recordatorios. Las mismas reglas que app/reminders.py del backend. */
object Recurrence {

    /** Suma meses respetando el día original: 31 ene + 1 mes = 28 feb; + 2 meses = 31 mar. */
    fun addMonths(date: LocalDate, months: Long, anchorDay: Int? = null): LocalDate {
        val moved = date.withDayOfMonth(1).plusMonths(months)
        return moved.withDayOfMonth(minOf(anchorDay ?: date.dayOfMonth, moved.lengthOfMonth()))
    }

    fun advance(due: LocalDate, every: Int, unit: RepeatUnit, anchorDay: Int?): LocalDate = when (unit) {
        RepeatUnit.DAY -> due.plusDays(every.toLong())
        RepeatUnit.WEEK -> due.plusWeeks(every.toLong())
        RepeatUnit.MONTH -> addMonths(due, every.toLong(), anchorDay)
        RepeatUnit.YEAR -> addMonths(due, every * 12L, anchorDay)
    }

    /** "Cada mes", "Cada 2 semanas", "Una sola vez". */
    fun label(every: Int?, unit: RepeatUnit?): String {
        if (every == null || unit == null) return "Una sola vez"
        if (every == 1) {
            return when (unit) {
                RepeatUnit.DAY -> "Cada día"
                RepeatUnit.WEEK -> "Cada semana"
                RepeatUnit.MONTH -> "Cada mes"
                RepeatUnit.YEAR -> "Cada año"
            }
        }
        val plural = when (unit) {
            RepeatUnit.DAY -> "días"
            RepeatUnit.WEEK -> "semanas"
            RepeatUnit.MONTH -> "meses"
            RepeatUnit.YEAR -> "años"
        }
        return "Cada $every $plural"
    }

    fun daysUntil(reminder: Reminder, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, reminder.dueDate)

    /** "Vence hoy", "Mañana", "En 5 días", "Venció hace 2 días". */
    fun dueLabel(reminder: Reminder, today: LocalDate = LocalDate.now()): String {
        if (reminder.finished) return "Hecho"
        val days = daysUntil(reminder, today)
        return when {
            days == 0L -> "Vence hoy"
            days == 1L -> "Mañana"
            days in 2..6 -> "En $days días"
            days > 6 -> Dates.shortDay(reminder.dueDate).lowercase().replaceFirstChar { it.uppercase() }
            days == -1L -> "Venció ayer"
            else -> "Venció hace ${-days} días"
        }
    }

    /** "El mismo día", "1 día antes", "3 días antes". */
    fun noticeLabel(days: Int): String = when (days) {
        0 -> "El mismo día"
        1 -> "1 día antes"
        7 -> "1 semana antes"
        else -> "$days días antes"
    }
}
