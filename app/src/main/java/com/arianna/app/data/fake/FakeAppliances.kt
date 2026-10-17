package com.arianna.app.data.fake

import com.arianna.app.domain.model.Appliance
import com.arianna.app.domain.model.ApplianceUse
import com.arianna.app.domain.model.CounterRole
import com.arianna.app.domain.model.NotifyMode
import com.arianna.app.domain.repository.ApplianceDraft
import com.arianna.app.domain.repository.AppliancesRepository
import com.arianna.app.domain.repository.DataException
import com.arianna.app.util.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant

/**
 * "¿Está libre?" en el teléfono (sin backend). Igual que el servidor: se libera solo
 * [GRACE] después de la hora estimada, o [MAX_BUSY] después si no tiene hora.
 */
class FakeAppliancesRepository(private val store: FakeStore) : AppliancesRepository {

    override fun observeAppliances(): Flow<List<Appliance>> = store.db
        .filterNotNull()
        .map { db -> db.appliances.map { it.expired() ?: it } }
        .onStart { store.load() }

    override suspend fun refresh() {
        store.update { db -> db.expireAll() to Unit }
    }

    private fun FakeDb.require(id: String): Appliance =
        appliances.find { it.id == id } ?: throw DataException("El equipo no existe", DataException.Kind.NOT_FOUND)

    private fun FakeDb.replace(appliance: Appliance): FakeDb =
        copy(appliances = appliances.map { if (it.id == appliance.id) appliance else it })

    private fun validate(draft: ApplianceDraft) {
        if (draft.name.isBlank()) throw DataException("Ponle un nombre", DataException.Kind.INVALID)
        if (draft.defaultMinutes != null && draft.defaultMinutes !in 1..1440) {
            throw DataException("La duración debe ser de 1 minuto a 24 horas", DataException.Kind.INVALID)
        }
    }

    override suspend fun create(draft: ApplianceDraft): Appliance {
        validate(draft)
        val user = FakeSeed.currentUser
        val appliance = Appliance(
            id = Ids.create("ap-"),
            name = draft.name.trim(),
            icon = draft.icon,
            defaultMinutes = draft.defaultMinutes,
            ownerId = user.id,
            ownerName = user.name,
            role = CounterRole.OWNER,
        )
        return store.update { db -> db.copy(appliances = db.appliances + appliance) to appliance }
    }

    override suspend fun update(appliance: Appliance, draft: ApplianceDraft) {
        validate(draft)
        store.update { db ->
            db.replace(db.require(appliance.id).copy(name = draft.name.trim(), icon = draft.icon, defaultMinutes = draft.defaultMinutes)) to Unit
        }
    }

    override suspend fun delete(appliance: Appliance) {
        store.update { db ->
            db.copy(appliances = db.appliances.filterNot { it.id == appliance.id }, applianceUses = db.applianceUses - appliance.id) to Unit
        }
    }

    override suspend fun start(appliance: Appliance, minutes: Int?): Appliance = store.update { db ->
        val db2 = db.expireAll()
        val current = db2.require(appliance.id)
        val user = FakeSeed.currentUser
        if (current.busy && current.busyById != user.id) {
            throw DataException("${current.busyByName ?: "Alguien"} lo está usando", DataException.Kind.INVALID)
        }
        val now = Instant.now()
        val length = minutes ?: current.defaultMinutes
        val uses = db2.applianceUses[current.id].orEmpty()
        val newUses = if (current.busy) uses else listOf(ApplianceUse(Ids.create("use-"), user.name, now.toString())) + uses
        val updated = current.copy(
            busy = true,
            busyById = user.id,
            busyByName = user.name,
            busySince = current.busySince ?: now.toString(),
            expectedEnd = length?.let { now.plus(Duration.ofMinutes(it.toLong())).toString() },
        )
        db2.replace(updated).copy(applianceUses = db2.applianceUses + (current.id to newUses)) to updated
    }

    override suspend fun finish(appliance: Appliance): Appliance = store.update { db ->
        val current = db.require(appliance.id)
        if (!current.busy) return@update db to current
        val freed = current.free()
        db.replace(freed).copy(applianceUses = db.closeUse(current.id, FakeSeed.currentUser.name, auto = false)) to freed
    }

    override suspend fun setNotify(appliance: Appliance, mode: NotifyMode) {
        store.update { db -> db.replace(db.require(appliance.id).copy(notify = mode)) to Unit }
    }

    override suspend fun history(appliance: Appliance): List<ApplianceUse> {
        refresh()
        return store.load().applianceUses[appliance.id].orEmpty().take(30)
    }

    // ---- liberación automática

    private fun Appliance.releaseDue(): Instant? {
        val since = busySinceInstant ?: return null
        return expectedEndInstant?.plus(GRACE) ?: since.plus(MAX_BUSY)
    }

    /** El mismo equipo ya liberado si pasó su hora; null si sigue igual. */
    private fun Appliance.expired(): Appliance? =
        releaseDue()?.takeIf { Instant.now().isAfter(it) }?.let { free() }

    private fun Appliance.free() = copy(busy = false, busyById = null, busyByName = null, busySince = null, expectedEnd = null)

    private fun FakeDb.expireAll(): FakeDb {
        var db = this
        appliances.forEach { appliance ->
            appliance.expired()?.let { freed -> db = db.replace(freed).copy(applianceUses = db.closeUse(appliance.id, null, auto = true)) }
        }
        return db
    }

    private fun FakeDb.closeUse(applianceId: String, endedBy: String?, auto: Boolean): Map<String, List<ApplianceUse>> {
        val uses = applianceUses[applianceId].orEmpty()
        val closed = uses.map { if (it.endedAt == null) it.copy(endedAt = Instant.now().toString(), endedByName = endedBy, auto = auto) else it }
        return applianceUses + (applianceId to closed)
    }

    private companion object {
        val GRACE: Duration = Duration.ofMinutes(30)
        val MAX_BUSY: Duration = Duration.ofHours(12)
    }
}
