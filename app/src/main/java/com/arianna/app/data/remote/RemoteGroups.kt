package com.arianna.app.data.remote

import com.arianna.app.domain.model.Group
import com.arianna.app.domain.model.GroupMember
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.repository.AppliancesRepository
import com.arianna.app.domain.repository.CountersRepository
import com.arianna.app.domain.repository.GroupsRepository
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.repository.RemindersRepository
import com.arianna.app.domain.repository.ShareTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable private data class GroupNameBody(val name: String)
@Serializable private data class GroupMemberBody(val email: String)
@Serializable private data class GroupShareBody(val role: MemberRole)

/** Grupos en el backend (docs/API.md → Grupos). Al cambiar lo compartido recarga listas, secciones y contadores. */
class RemoteGroupsRepository(
    private val api: ApiClient,
    private val lists: ListsRepository,
    private val counters: CountersRepository,
    private val reminders: RemindersRepository,
    private val appliances: AppliancesRepository,
) : GroupsRepository {
    private val groups = MutableStateFlow<List<Group>>(emptyList())

    override fun observeGroups(): Flow<List<Group>> = groups.onStart { refresh() }

    override suspend fun refresh() {
        groups.value = api.request("GET", "/groups", null, ListSerializer(Group.serializer()))!!
    }

    /** Lo que veo por los grupos cambió (entré, salí, se borró el grupo…). */
    private suspend fun refreshShared() {
        refresh()
        lists.refresh()
        counters.refresh()
        reminders.refresh()
        appliances.refresh()
    }

    override suspend fun create(name: String): Group {
        val group = api.request("POST", "/groups", api.body(GroupNameBody(name.trim())), Group.serializer())!!
        refresh()
        return group
    }

    override suspend fun rename(group: Group, name: String) {
        api.request("PATCH", "/groups/${enc(group.id)}", api.body(GroupNameBody(name.trim())), Group.serializer())
        refresh()
    }

    override suspend fun delete(group: Group) {
        api.request<Unit>("DELETE", "/groups/${enc(group.id)}", null, null)
        refreshShared()
    }

    override suspend fun addMember(group: Group, email: String): GroupMember {
        val member = api.request(
            "POST", "/groups/${enc(group.id)}/members", api.body(GroupMemberBody(email.trim())), GroupMember.serializer(),
        )!!
        refresh()
        return member
    }

    override suspend fun removeMember(group: Group, email: String) {
        api.request<Unit>("DELETE", "/groups/${enc(group.id)}/members/${enc(email)}", null, null)
        refreshShared()
    }

    override suspend fun createInvite(group: Group): ShareInvite =
        api.request("POST", "/groups/${enc(group.id)}/invites", null, ShareInvite.serializer())!!

    override suspend fun share(target: ShareTarget, group: Group, role: MemberRole?) {
        val base = when (target) {
            is ShareTarget.ListTarget -> "/lists"
            is ShareTarget.SpaceTarget -> "/spaces"
            is ShareTarget.CounterTarget -> "/trackers"
            is ShareTarget.ApplianceTarget -> "/appliances"
        }
        val path = "$base/${enc(target.id)}/groups/${enc(group.id)}"
        if (role == null) {
            api.request<Unit>("DELETE", path, null, null)
        } else {
            api.request<Unit>("PUT", path, api.body(GroupShareBody(role)), null)
        }
        when (target) {
            is ShareTarget.ListTarget -> lists.refresh()
            is ShareTarget.SpaceTarget -> reminders.refresh()
            is ShareTarget.CounterTarget -> counters.refresh()
            is ShareTarget.ApplianceTarget -> appliances.refresh()
        }
        refresh()
    }
}
