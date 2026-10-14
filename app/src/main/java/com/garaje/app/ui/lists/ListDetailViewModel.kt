package com.garaje.app.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.garaje.app.domain.model.Checklist
import com.garaje.app.domain.model.ChecklistItem
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.domain.model.MemberRole
import com.garaje.app.domain.model.ShareInvite
import com.garaje.app.domain.repository.ListsRepository
import com.garaje.app.domain.repository.SharingRepository
import com.garaje.app.ui.common.BaseViewModel
import com.garaje.app.ui.navigation.ListDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ListDetailUiState(
    val list: Checklist? = null,
    val loaded: Boolean = false,
    val invite: ShareInvite? = null,
    val busy: Boolean = false,
) {
    val pending: List<ChecklistItem> = list?.items?.filterNot { it.done }.orEmpty()
    val done: List<ChecklistItem> = list?.items?.filter { it.done }.orEmpty()
}

class ListDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val lists: ListsRepository,
    private val sharing: SharingRepository,
    /** true mientras se usan repositorios falsos (sin backend). */
    val demoMode: Boolean,
) : BaseViewModel() {

    private val listId = savedStateHandle.toRoute<ListDetailRoute>().id
    private val extra = MutableStateFlow(Pair<ShareInvite?, Boolean>(null, false))

    val state: StateFlow<ListDetailUiState> =
        combine(lists.observeList(listId), extra) { list, (invite, busy) ->
            ListDetailUiState(list, loaded = true, invite = invite, busy = busy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())

    fun addItem(text: String, quantity: String?) = launchSafely {
        lists.addItem(listId, text, quantity)
    }

    fun toggle(item: ChecklistItem) = launchSafely {
        lists.updateItem(listId, item.id, done = !item.done)
    }

    fun remove(item: ChecklistItem) = launchSafely {
        lists.removeItem(listId, item.id)
        notify("\"${item.text}\" eliminado")
    }

    fun clearCompleted() = launchSafely { lists.clearCompleted(listId) }

    fun rename(title: String) = launchSafely { lists.renameList(listId, title) }

    fun invite(email: String, role: MemberRole, onDone: () -> Unit) = withBusy {
        sharing.invite(listId, email, role)
        notify("Invitación enviada a ${email.trim()}")
        onDone()
    }

    fun removeMember(email: String) = launchSafely { sharing.removeMember(listId, email) }

    fun createInvite() = withBusy {
        extra.value = sharing.createInvite(listId) to true
    }

    private fun withBusy(block: suspend () -> Unit) = launchSafely {
        extra.value = extra.value.first to true
        try {
            block()
        } finally {
            extra.value = extra.value.first to false
        }
    }

    /** Texto plano de la lista para WhatsApp, SMS, correo… */
    fun shareText(): String {
        val list = state.value.list ?: return ""
        val invite = state.value.invite
        fun line(item: ChecklistItem) =
            "${if (item.done) "✅" else "⬜"} ${item.text}${item.quantity?.let { " ($it)" }.orEmpty()}"

        return buildString {
            appendLine("${if (list.kind == ChecklistKind.SHOPPING) "🛒" else "📝"} ${list.title}")
            appendLine()
            state.value.pending.forEach { appendLine(line(it)) }
            if (state.value.done.isNotEmpty()) {
                appendLine()
                appendLine("Listo:")
                state.value.done.forEach { appendLine(line(it)) }
            }
            if (invite != null) {
                appendLine()
                appendLine("Únete a la lista en Garaje con el código ${invite.code}")
                append(invite.url)
            }
        }.trim()
    }
}
