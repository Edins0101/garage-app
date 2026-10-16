package com.arianna.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Utilidades de fechas en español (Ecuador). Semanas de lunes a domingo. */
object Dates {
    val locale: Locale = Locale.forLanguageTag("es-EC")

    fun monthName(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase() }

    fun monthYear(month: YearMonth): String = "${monthName(month)} ${month.year}"

    /** "Lun", "Mar"... empezando en lunes. */
    val weekdayShort: List<String> = DayOfWeek.entries.map {
        it.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.').replaceFirstChar { c -> c.uppercase() }
    }

    /** "miércoles 14 de octubre" */
    fun longDay(date: LocalDate): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val month = date.month.getDisplayName(TextStyle.FULL, locale)
        return "$weekday ${date.dayOfMonth} de $month"
    }

    /** "14 OCT" */
    fun shortDay(date: LocalDate): String {
        val month = date.month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').uppercase(locale)
        return "${date.dayOfMonth} $month"
    }

    fun startOfWeek(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun week(date: LocalDate): List<LocalDate> {
        val start = startOfWeek(date)
        return (0L until 7L).map { start.plusDays(it) }
    }

    /** Celdas del mes (con días de relleno de meses vecinos), siempre semanas completas. */
    fun monthGrid(month: YearMonth): List<LocalDate> {
        val start = startOfWeek(month.atDay(1))
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    /** Todas las fechas entre a y b (inclusive), sin importar el orden. */
    fun between(a: LocalDate, b: LocalDate): List<LocalDate> {
        val (start, end) = if (a <= b) a to b else b to a
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }
}
