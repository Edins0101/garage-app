package com.arianna.app.util

import java.time.LocalDate
import java.time.YearMonth

/** Números del calendario: días del año, rachas y días por mes (para el gráfico). */
data class CalendarStats(
    val year: Int,
    val daysThisYear: Int,
    /** Días seguidos marcados hasta hoy (o hasta ayer si hoy aún no se marca). */
    val currentStreak: Int,
    val bestStreak: Int,
    /** Últimos [MONTHS] meses, del más antiguo al actual. */
    val months: List<Pair<YearMonth, Int>>,
) {
    val maxMonth: Int get() = months.maxOfOrNull { it.second } ?: 0

    companion object {
        const val MONTHS = 6

        fun of(marked: Set<LocalDate>, today: LocalDate = LocalDate.now()): CalendarStats {
            val start = if (today in marked) today else today.minusDays(1)
            var current = 0
            var day = start
            while (day in marked) {
                current++
                day = day.minusDays(1)
            }

            var best = 0
            var run = 0
            var previous: LocalDate? = null
            for (date in marked.sorted()) {
                run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
                best = maxOf(best, run)
                previous = date
            }

            val thisMonth = YearMonth.from(today)
            val byMonth = marked.groupingBy { YearMonth.from(it) }.eachCount()
            val months = (MONTHS - 1 downTo 0).map { back ->
                val month = thisMonth.minusMonths(back.toLong())
                month to (byMonth[month] ?: 0)
            }
            return CalendarStats(
                year = today.year,
                daysThisYear = marked.count { it.year == today.year },
                currentStreak = current,
                bestStreak = best,
                months = months,
            )
        }
    }
}
