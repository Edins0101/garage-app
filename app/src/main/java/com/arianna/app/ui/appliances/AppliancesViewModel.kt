package com.arianna.app.ui.appliances

import com.arianna.app.domain.model.Appliance
import com.arianna.app.domain.model.ApplianceUse
import com.arianna.app.domain.model.Group
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.NotifyMode
import com.arianna.app.domain.repository.ApplianceDraft
import com.arianna.app.domain.repository.AppliancesRepository
import com.arianna.app.domain.repository.GroupsRepository
import com.arianna.app.domain.repository.ShareTarget
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.Load
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** Diálogo abierto en "¿Está libre?". */
sealed interface ApplianceDialog {
    data object Create : ApplianceDialog
    data class Edit(val id: String) : ApplianceDialog
    /** Elegir cuánto tiempo se va a usar. */
    data class Start(val id: String) : ApplianceDialog
    /** Liberar uno que está usando otra persona (se confirma). */
    data class Release(val id: String) : ApplianceDialog
    data class Detail(val id: String) : ApplianceDialog
    data class Delete(val id: String) : ApplianceDialog
}

data class AppliancesUiState(
    val appliances: List<Appliance> = emptyList(),
    val groups: List<Group> = emptyList(),
    val dialog: ApplianceDialog? = null,
    /** Historial del equipo abierto en el detalle (null = cargando). */
    val history: List<ApplianceUse>? = null,
    val busy: Boolean = false,
    val load: Load = Load.LOADING,
) {
    val free: Int get() = appliances.count { !it.busy }
    val inUse: Int get() = appliances.count { it.busy }

    private fun find(id: String) = appliances.find { it.id == id }
    val dialogAppliance: Appliance? get() = when (val d = dialog) {
        is ApplianceDialog.Edit -> find(d.id)
        is ApplianceDialog.Start -> find(d.id)
        is ApplianceDialog.Release -> find(d.id)
        is ApplianceDialog.Detail -> find(d.id)
        is ApplianceDialog.Delete -> find(d.id)
        else -> null
    }
}

private data class Local(
    val dialog: ApplianceDialog? = null,
    val history: List<ApplianceUse>? = null,
    val busy: Boolean = false,
)

class AppliancesViewModel(
    private val appliances: AppliancesRepository,
    private val groups: GroupsRepository,
    /** Para decir "En uso por ti". */
    val myId: String?,
) : BaseViewModel() {
    private val local = MutableStateFlow(Local())

    val state: StateFlow<AppliancesUiState> = loadingState(AppliancesUiState(), { copy(load = it) }) {
        combine(local, appliances.observeAppliances(), groups.observeGroups().catch { emit(emptyList()) }) { ui, list, myGroups ->
            AppliancesUiState(list, myGroups, ui.dialog, ui.history, ui.busy)
        }
    }

    fun open(dialog: ApplianceDialog?) {
        local.update { it.copy(dialog = dialog, history = null) }
        if (dialog is ApplianceDialog.Detail) loadHistory(dialog.id)
    }

    /** Vuelve a pedir el estado (otra persona pudo ocuparlo o liberarlo). */
    fun reload() = launchSafely { appliances.refresh() }

    private fun require(id: String) = state.value.appliances.find { it.id == id }

    private fun loadHistory(id: String) = launchSafely {
        val appliance = require(id) ?: return@launchSafely
        local.update { it.copy(history = appliances.history(appliance)) }
    }

    private fun withBusy(block: suspend () -> Unit) = launchSafely {
        local.update { it.copy(busy = true) }
        try {
            block()
        } finally {
            local.update { it.copy(busy = false) }
        }
    }

    fun create(draft: ApplianceDraft) = withBusy {
        appliances.create(draft)
        open(null)
        notify("«${draft.name.trim()}» listo. Compártelo con tu grupo para que todos lo vean.")
    }

    fun update(appliance: Appliance, draft: ApplianceDraft) = withBusy {
        appliances.update(appliance, draft)
        open(ApplianceDialog.Detail(appliance.id))
    }

    fun delete(appliance: Appliance) = withBusy {
        appliances.delete(appliance)
        open(null)
        notify("«${appliance.name}» eliminado")
    }

    /** "Empezar a usar" (o cambiar el tiempo si ya es mío). */
    fun start(appliance: Appliance, minutes: Int?) = withBusy {
        open(null)
        appliances.start(appliance, minutes)
        notify(if (minutes != null) "Listo: te aviso cuando se acabe el tiempo" else "Listo: los demás ya saben que está en uso")
    }

    fun finish(appliance: Appliance) = withBusy {
        open(null)
        appliances.finish(appliance)
        notify("«${appliance.name}» quedó libre")
    }

    fun setNotify(appliance: Appliance, mode: NotifyMode) = launchSafely { appliances.setNotify(appliance, mode) }

    fun shareWithGroup(appliance: Appliance, group: Group, role: MemberRole?) = launchSafely {
        groups.share(ShareTarget.ApplianceTarget(appliance.id), group, role)
        notify(if (role == null) "Ya no se comparte con «${group.name}»" else "Compartido con «${group.name}»")
    }
}
