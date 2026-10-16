package com.arianna.app

import com.arianna.app.data.reminders.ReminderRules
import com.arianna.app.domain.model.CountFrom
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.model.RepeatUnit
import com.arianna.app.util.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemindersTest {
    private val today = LocalDate.of(2026, 10, 16)

    private fun reminder(due: LocalDate, notify: Int = 3, finished: Boolean = false) = Reminder(
        id = "r1",
        spaceId = "s1",
        kind = ReminderKind.PAYMENTS,
        title = "Luz",
        amount = 32.5,
        every = 1,
        unit = RepeatUnit.MONTH,
        nextDue = due.toString(),
        notifyDaysBefore = notify,
        finished = finished,
        createdAt = "2026-10-01T00:00:00Z",
        updatedAt = "2026-10-01T00:00:00Z",
    )

    @Test
    fun monthsKeepTheOriginalDay() {
        // Igual que el backend (tests/test_reminders.py).
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.addMonths(LocalDate.of(2026, 1, 31), 1))
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.addMonths(LocalDate.of(2026, 2, 28), 1, anchorDay = 31))
        assertEquals(LocalDate.of(2027, 2, 15), Recurrence.addMonths(LocalDate.of(2026, 11, 15), 3))
        assertEquals(LocalDate.of(2025, 2, 28), Recurrence.advance(LocalDate.of(2024, 2, 29), 1, RepeatUnit.YEAR, 29))
        assertEquals(LocalDate.of(2026, 10, 30), Recurrence.advance(today, 2, RepeatUnit.WEEK, null))
    }

    @Test
    fun labels() {
        assertEquals("Cada mes", Recurrence.label(1, RepeatUnit.MONTH))
        assertEquals("Cada 3 meses", Recurrence.label(3, RepeatUnit.MONTH))
        assertEquals("Una sola vez", Recurrence.label(null, null))
        assertEquals("Vence hoy", Recurrence.dueLabel(reminder(today), today))
        assertEquals("Mañana", Recurrence.dueLabel(reminder(today.plusDays(1)), today))
        assertEquals("Venció hace 3 días", Recurrence.dueLabel(reminder(today.minusDays(3)), today))
    }

    @Test
    fun noticeOnChosenDayDueDayAndWeeklyWhenOverdue() {
        fun titles(due: LocalDate, notify: Int = 3) =
            ReminderRules.notices(listOf(reminder(due, notify)), today).map { it.title }

        assertEquals(listOf("«Luz» vence en 3 días"), titles(today.plusDays(3)))
        assertEquals(listOf("«Luz» vence mañana"), titles(today.plusDays(1), notify = 1))
        assertTrue(titles(today.plusDays(2)).isEmpty())
        assertEquals(listOf("Hoy vence «Luz»"), titles(today))
        assertEquals(1, titles(today.minusDays(1)).size)
        assertTrue(titles(today.minusDays(3)).isEmpty())
        assertEquals(1, titles(today.minusDays(7)).size)

        val notice = ReminderRules.notices(listOf(reminder(today)), today).single()
        assertEquals("$32.50 · Toca para marcarlo como pagado", notice.body)
        assertTrue(ReminderRules.notices(listOf(reminder(today, finished = true)), today).isEmpty())
    }

    @Test
    fun maintenanceCountsFromTheDayItWasDone() {
        // Igual que test_home_counts_from_the_day_it_was_done del backend.
        val aire = reminder(LocalDate.of(2026, 1, 31)).copy(every = 3, countFrom = CountFrom.DONE)
        assertEquals(LocalDate.of(2026, 5, 20), Recurrence.nextAfterDone(aire, LocalDate.of(2026, 2, 20)))
        val luz = reminder(LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.nextAfterDone(luz, LocalDate.of(2026, 2, 20)))
        assertEquals(null, Recurrence.nextAfterDone(luz.copy(every = null, unit = null), today))
    }
}
