package com.arianna.app.data.remote

import com.arianna.app.domain.model.Appliance
import com.arianna.app.domain.model.ApplianceIcon
import com.arianna.app.domain.model.ApplianceUse
import com.arianna.app.domain.model.NotifyMode
import com.arianna.app.domain.repository.ApplianceDraft
import com.arianna.app.domain.repository.AppliancesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable private data class StartBody(val minutes: Int? = null)
@Serializable private data class NotifyBody(val mode: NotifyMode)

/** "¿Está libre?" en el backend (docs/API.md → ¿Está libre?). */
class RemoteAppliancesRepository(private val api: ApiClient) : AppliancesRepository {
    private val appliances = MutableStateFlow<List<Appliance>>(emptyList())

    override fun observeAppliances(): Flow<List<Appliance>> = appliances.onStart { refresh() }

    override suspend fun refresh() {
        appliances.value = api.request("GET", "/appliances", null, ListSerializer(Appliance.serializer()))!!
    }

    /** null explícito en defaultMinutes: quita la duración. */
    private fun draftJson(draft: ApplianceDraft) = buildJsonObject {
        put("name", draft.name.trim())
        put("icon", api.json.encodeToJsonElement(ApplianceIcon.serializer(), draft.icon))
        put("defaultMinutes", draft.defaultMinutes)
    }.toString()

    private fun replace(updated: Appliance) {
        appliances.value = appliances.value.map { if (it.id == updated.id) updated else it }
    }

    override suspend fun create(draft: ApplianceDraft): Appliance {
        val created = api.request("POST", "/appliances", draftJson(draft), Appliance.serializer())!!
        refresh()
        return created
    }

    override suspend fun update(appliance: Appliance, draft: ApplianceDraft) {
        replace(api.request("PATCH", "/appliances/${enc(appliance.id)}", draftJson(draft), Appliance.serializer())!!)
    }

    override suspend fun delete(appliance: Appliance) {
        api.request<Unit>("DELETE", "/appliances/${enc(appliance.id)}", null, null)
        appliances.value = appliances.value.filterNot { it.id == appliance.id }
    }

    override suspend fun start(appliance: Appliance, minutes: Int?): Appliance {
        // 409 si otra persona lo ocupó mientras tanto: se recarga para mostrar quién.
        val updated = try {
            api.request("POST", "/appliances/${enc(appliance.id)}/start", api.body(StartBody(minutes)), Appliance.serializer())!!
        } catch (e: Exception) {
            runCatching { refresh() }
            throw e
        }
        replace(updated)
        return updated
    }

    override suspend fun finish(appliance: Appliance): Appliance {
        val updated = api.request("POST", "/appliances/${enc(appliance.id)}/finish", null, Appliance.serializer())!!
        replace(updated)
        return updated
    }

    override suspend fun setNotify(appliance: Appliance, mode: NotifyMode) {
        replace(api.request("PUT", "/appliances/${enc(appliance.id)}/notify", api.body(NotifyBody(mode)), Appliance.serializer())!!)
    }

    override suspend fun history(appliance: Appliance): List<ApplianceUse> =
        api.request("GET", "/appliances/${enc(appliance.id)}/history", null, ListSerializer(ApplianceUse.serializer()))!!
}
