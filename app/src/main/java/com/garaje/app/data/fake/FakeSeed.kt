package com.garaje.app.data.fake

import com.garaje.app.domain.model.Checklist
import com.garaje.app.domain.model.ChecklistItem
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.domain.model.GarageDay
import com.garaje.app.domain.model.MemberRole
import com.garaje.app.domain.model.MemberStatus
import com.garaje.app.domain.model.SharedMember
import com.garaje.app.domain.model.User
import java.time.Instant
import java.time.LocalDate

/** Datos de ejemplo para la primera vez que se abre la app. */
object FakeSeed {
    val currentUser = User(id = "user-me", name = "Edinson", email = "yo@garaje.app")

    fun create(): FakeDb {
        val now = Instant.now().toString()
        val today = LocalDate.now()

        fun item(id: String, text: String, done: Boolean = false, quantity: String? = null) =
            ChecklistItem(id, text, quantity, done, createdAt = now, updatedAt = now)

        return FakeDb(
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
