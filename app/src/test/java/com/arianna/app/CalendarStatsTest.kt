package com.arianna.app

import com.arianna.app.util.CalendarStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarStatsTest {
    private val today = LocalDate.of(2026, 10, 17)
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test
    fun rachaCuentaHastaHoyOAyer() {
        val marked = setOf(d(10, 14), d(10, 15), d(10, 16))
        // Hoy (17) aún no se marca: la racha sigue viva hasta ayer.
        assertEquals(3, CalendarStats.of(marked, today).currentStreak)
        assertEquals(4, CalendarStats.of(marked + today, today).currentStreak)
        // Si ayer tampoco se marcó, la racha se cortó.
        assertEquals(0, CalendarStats.of(setOf(d(10, 14), d(10, 15)), today).currentStreak)
    }

    @Test
    fun mejorRachaYDiasDelAnio() {
        val marked = setOf(d(12, 30, 2025), d(12, 31, 2025), d(1, 1), d(1, 2), d(3, 5), d(10, 17))
        val stats = CalendarStats.of(marked, today)
        assertEquals(4, stats.bestStreak)
        assertEquals(4, stats.daysThisYear)
        assertEquals(1, stats.currentStreak)
    }

    @Test
    fun ultimosSeisMeses() {
        val stats = CalendarStats.of(setOf(d(10, 1), d(10, 2), d(9, 5), d(3, 1)), today)
        assertEquals(6, stats.months.size)
        assertEquals(YearMonth.of(2026, 5), stats.months.first().first)
        assertEquals(YearMonth.of(2026, 10) to 2, stats.months.last())
        assertEquals(1, stats.months[4].second)
        assertEquals(2, stats.maxMonth)
    }
}
