package com.arianna.app.data.fake

import com.arianna.app.domain.model.Group
import com.arianna.app.domain.model.GroupItem
import com.arianna.app.domain.model.GroupItemType
import com.arianna.app.domain.model.GroupMember
import com.arianna.app.domain.model.GroupRole
import com.arianna.app.domain.model.GroupShare
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.MemberStatus
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.repository.DataException
import com.arianna.app.domain.repository.GroupsRepository
import com.arianna.app.domain.repository.ShareTarget
import com.arianna.app.util.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant

private val kindTitles = mapOf("payments" to "Pagos", "home" to "Mi hogar", "car" to "Mi carro", "other" to "Otros")

/** Grupos en el teléfono: no hay otros usuarios, así que los integrantes quedan como pendientes. */
class FakeGroupsRepository(private val store: FakeStore) : GroupsRepository {
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /** Lo compartido se guarda en cada lista, sección o contador; aquí se arma la vista del grupo. */
    private fun FakeDb.withShared(group: Group): Group {
        val user = FakeSeed.currentUser
        fun item(type: GroupItemType, id: String, title: String, kind: String, shares: List<GroupShare>) =
            shares.find { it.groupId == group.id }?.let { GroupItem(type, id, title, kind, user.id, user.name, it.role) }
        val shared = lists.mapNotNull { item(GroupItemType.LIST, it.id, it.title, it.kind.name.lowercase(), it.groups) } +
            spaces.mapNotNull {
                item(GroupItemType.SPACE, it.id, kindTitles.getValue(it.kind.apiName), it.kind.apiName, it.groups)
            } +
            counters.mapNotNull { item(GroupItemType.TRACKER, it.id, it.name, it.icon.name.lowercase(), it.groups) }
        return group.copy(shared = shared)
    }

    override fun observeGroups(): Flow<List<Group>> = store.db
        .filterNotNull()
        .map { db -> db.groups.map { db.withShared(it) } }
        .onStart { store.load() }

    override suspend fun refresh() {
        store.load()
    }

    private fun FakeDb.requireGroup(id: String): Group =
        groups.find { it.id == id } ?: throw DataException("El grupo no existe", DataException.Kind.NOT_FOUND)

    private fun FakeDb.replaceGroup(group: Group): FakeDb = copy(groups = groups.map { if (it.id == group.id) group else it })

    override suspend fun create(name: String): Group {
        if (name.isBlank()) throw DataException("Ponle un nombre", DataException.Kind.INVALID)
        return store.update { db ->
            val user = FakeSeed.currentUser
            val group = Group(
                id = Ids.create("g-"),
                name = name.trim(),
                ownerId = user.id,
                ownerName = user.name,
                role = GroupRole.OWNER,
                createdAt = Instant.now().toString(),
            )
            db.copy(groups = db.groups + group) to group
        }
    }

    override suspend fun rename(group: Group, name: String) {
        if (name.isBlank()) throw DataException("Ponle un nombre", DataException.Kind.INVALID)
        store.update { db -> db.replaceGroup(db.requireGroup(group.id).copy(name = name.trim())) to Unit }
    }

    override suspend fun delete(group: Group) {
        store.update { db ->
            fun List<GroupShare>.without() = filterNot { it.groupId == group.id }
            db.copy(
                groups = db.groups.filterNot { it.id == group.id },
                lists = db.lists.map { it.copy(groups = it.groups.without()) },
                spaces = db.spaces.map { it.copy(groups = it.groups.without()) },
                counters = db.counters.map { it.copy(groups = it.groups.without()) },
                invites = db.invites.filterNot { it.groupId == group.id },
            ) to Unit
        }
    }

    override suspend fun addMember(group: Group, email: String): GroupMember {
        val normalized = email.trim().lowercase()
        if (!emailRegex.matches(normalized)) throw DataException("Correo no válido", DataException.Kind.INVALID)
        if (normalized == FakeSeed.currentUser.email) throw DataException("Ya estás en el grupo", DataException.Kind.INVALID)
        return store.update { db ->
            val current = db.requireGroup(group.id)
            current.members.find { it.email == normalized }?.let { return@update db to it }
            val member = GroupMember(normalized, status = MemberStatus.PENDING, invitedAt = Instant.now().toString())
            db.replaceGroup(current.copy(members = current.members + member)) to member
        }
    }

    override suspend fun removeMember(group: Group, email: String) {
        store.update { db ->
            val current = db.requireGroup(group.id)
            db.replaceGroup(current.copy(members = current.members.filterNot { it.email == email })) to Unit
        }
    }

    override suspend fun createInvite(group: Group): ShareInvite = store.update { db ->
        val nowInstant = Instant.now()
        val valid = db.invites.filter { Instant.parse(it.expiresAt).isAfter(nowInstant) }
        valid.find { it.groupId == group.id }?.let { return@update db.copy(invites = valid) to it }
        val code = Ids.shareCode()
        val invite = ShareInvite(
            code = code,
            url = "https://arianna-back.onrender.com/join/$code",
            groupId = group.id,
            expiresAt = nowInstant.plus(Duration.ofDays(7)).toString(),
        )
        db.copy(invites = valid + invite) to invite
    }

    override suspend fun share(target: ShareTarget, group: Group, role: MemberRole?) {
        store.update { db ->
            val current = db.requireGroup(group.id)
            fun List<GroupShare>.changed(): List<GroupShare> =
                filterNot { it.groupId == current.id } + listOfNotNull(role?.let { GroupShare(current.id, current.name, it) })
            when (target) {
                is ShareTarget.ListTarget ->
                    db.copy(lists = db.lists.map { if (it.id == target.id) it.copy(groups = it.groups.changed()) else it })
                is ShareTarget.SpaceTarget ->
                    db.copy(spaces = db.spaces.map { if (it.id == target.id) it.copy(groups = it.groups.changed()) else it })
                is ShareTarget.CounterTarget ->
                    db.copy(counters = db.counters.map { if (it.id == target.id) it.copy(groups = it.groups.changed()) else it })
            } to Unit
        }
    }
}
