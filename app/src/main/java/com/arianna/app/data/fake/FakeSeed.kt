package com.arianna.app.data.fake

import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistItem
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.model.GarageDay
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.CountFrom
import com.arianna.app.domain.model.MemberStatus
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.model.RepeatUnit
import com.arianna.app.domain.model.SharedMember
import com.arianna.app.domain.model.User
import java.time.Instant
import java.time.LocalDate

/** Datos de ejemplo para la primera vez que se abre la app. */
object FakeSeed {
    val currentUser = User(id = "user-me", name = "Edinson", email = "yo@arianna.app")

    fun create(): FakeDb {
        val now = Instant.now().toString()
        val today = LocalDate.now()

        fun item(id: String, text: String, done: Boolean = false, quantity: String? = null) =
            ChecklistItem(id, text, quantity, done, createdAt = now, updatedAt = now)

        fun payment(id: String, title: String, amount: Double, due: LocalDate) = Reminder(
            id = id,
            spaceId = "space-payments",
            kind = ReminderKind.PAYMENTS,
            title = title,
            amount = amount,
            every = 1,
            unit = RepeatUnit.MONTH,
            nextDue = due.toString(),
            createdAt = now,
            updatedAt = now,
        )

        return FakeDb(
            reminders = listOf(
                payment("r-luz", "Luz", 32.50, today.plusDays(2)),
                payment("r-internet", "Internet", 28.00, today.plusDays(9)),
                payment("r-celular", "Plan del celular", 15.00, today.minusDays(1)),
                payment("r-aceite", "Cambio de aceite", 45.00, today.plusDays(20)).copy(
                    spaceId = "space-car",
                    kind = ReminderKind.CAR,
                    every = 3,
                    countFrom = CountFrom.DONE,
                    everyKm = 5000,
                ),
                payment("r-cumple", "Cumpleaños de mamá", 0.0, today.plusDays(5)).copy(
                    spaceId = "space-other",
                    kind = ReminderKind.OTHER,
                    amount = null,
                    unit = RepeatUnit.YEAR,
                ),
            ),
            garageDays = listOf(3L, 2L, 1L).map { offset ->
                GarageDay(
                    date = today.minusDays(offset).toString(),
                    note = if (offset == 3L) "Cambio de aceite" else null,
                    createdAt = now,
                    updatedAt = now,
                )
            },
            lists = listOf(
                Checklist(
                    id = "list-compras",
                    title = "Supermercado",
                    kind = ChecklistKind.SHOPPING,
                    ownerId = currentUser.id,
                    items = listOf(
                        item("i1", "Leche", quantity = "2 litros"),
                        item("i2", "Huevos", quantity = "1 cubeta"),
                        item("i3", "Pan", done = true),
                        item("i4", "Café"),
                    ),
                    sharedWith = listOf(
                        SharedMember(
                            email = "pareja@ejemplo.com",
                            name = "Pareja",
                            role = MemberRole.EDITOR,
                            status = MemberStatus.ACCEPTED,
                            invitedAt = now,
                        ),
                    ),
                    createdAt = now,
                    updatedAt = now,
                ),
                Checklist(
                    id = "list-pendientes",
                    title = "Pendientes del carro",
                    kind = ChecklistKind.TODO,
                    ownerId = currentUser.id,
                    items = listOf(
                        item("t1", "Revisar presión de llantas"),
                        item("t2", "Renovar matrícula"),
                        item("t3", "Lavar el carro", done = true),
                    ),
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }
}
