package com.arianna.app.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistItem
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.model.CounterRole
import com.arianna.app.domain.model.Group
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.repository.GroupsRepository
import com.arianna.app.domain.repository.ShareTarget
import com.arianna.app.domain.model.Product
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.repository.DataException
import com.arianna.app.domain.repository.PhotoInput
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.repository.SharingRepository
import com.arianna.app.domain.repository.UndoableDeletes
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.Load
import com.arianna.app.ui.navigation.ListDetailRoute
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class ListDetailUiState(
    val list: Checklist? = null,
    val load: Load = Load.LOADING,
    val invite: ShareInvite? = null,
    val busy: Boolean = false,
    /** Productos del catálogo que coinciden con lo que se está escribiendo. */
    val suggestions: List<Product> = emptyList(),
    /** Subiendo una foto (muestra un indicador). */
    val uploading: Boolean = false,
    /** El usuario puede editar (dueño o editor); los "solo ver" no ven acciones. */
    val canEdit: Boolean = true,
    /** Mis grupos (para compartir con uno). */
    val groups: List<Group> = emptyList(),
) {
    val loaded: Boolean get() = load == Load.READY
    val pending: List<ChecklistItem> = list?.items?.filterNot { it.done }.orEmpty()
    val done: List<ChecklistItem> = list?.items?.filter { it.done }.orEmpty()
}

@OptIn(FlowPreview::class)
class ListDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val lists: ListsRepository,
    private val sharing: SharingRepository,
    private val groups: GroupsRepository,
    private val deletes: UndoableDeletes,
    /** true mientras se usan repositorios falsos (sin backend). */
    val demoMode: Boolean,
    private val currentUserId: String?,
) : BaseViewModel() {

    private val listId = savedStateHandle.toRoute<ListDetailRoute>().id
    private val extra = MutableStateFlow(Pair<ShareInvite?, Boolean>(null, false))
    private val query = MutableStateFlow("")
    private val suggestions = MutableStateFlow<List<Product>>(emptyList())
    private val uploading = MutableStateFlow(false)

    val state: StateFlow<ListDetailUiState> = loadingState(ListDetailUiState(), { copy(load = it) }) {
        combine(
            lists.observeList(listId),
            extra,
            suggestions,
            uploading,
            groups.observeGroups().catch { emit(emptyList()) },
        ) { list, (invite, busy), found, isUploading, myGroups ->
            ListDetailUiState(
                groups = myGroups,
                list = list,
                invite = invite,
                busy = busy,
                suggestions = found,
                uploading = isUploading,
                canEdit = list == null || list.ownerId == currentUserId || list.role != CounterRole.VIEWER,
            )
        }
    }

    init {
        // Autocompletar: busca en el catálogo 250 ms después de dejar de escribir.
        viewModelScope.launch {
            query.debounce(250).distinctUntilChanged().collectLatest { text ->
                suggestions.value = if (text.isBlank()) emptyList() else runCatching {
                    lists.searchProducts(listId, text)
                        .filterNot { it.name.equals(text.trim(), ignoreCase = true) }
                }.getOrDefault(emptyList())
            }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
        if (text.isBlank()) suggestions.value = emptyList()
    }

    // ---- Fotos

    /** Comprime (con [readJpeg]) y sube la foto del producto o la propia del elemento. */
    fun attachPhoto(item: ChecklistItem, target: PhotoTarget, readJpeg: suspend () -> ByteArray) = withUpload {
        val jpeg = readJpeg()
        when (target) {
            PhotoTarget.PRODUCT -> lists.setProductImage(listId, requireProduct(item), PhotoInput.Jpeg(jpeg))
            PhotoTarget.ITEM -> lists.setItemImage(listId, item.id, jpeg)
        }
        notify(if (target == PhotoTarget.PRODUCT) "Foto guardada para \"${item.text}\"" else "Foto guardada solo en este elemento")
    }

    fun attachPhotoUrl(item: ChecklistItem, url: String) = withUpload {
        val clean = url.trim()
        if (!clean.startsWith("https://")) {
            notify("El enlace debe empezar con https://")
            return@withUpload
        }
        lists.setProductImage(listId, requireProduct(item), PhotoInput.Url(clean))
        notify("Foto guardada para \"${item.text}\"")
    }

    fun removeProductPhoto(item: ChecklistItem) = launchSafely {
        lists.removeProductImage(listId, requireProduct(item))
    }

    fun removeItemPhoto(item: ChecklistItem) = launchSafely { lists.removeItemImage(listId, item.id) }

    private fun requireProduct(item: ChecklistItem): String =
        item.productId ?: throw DataException("Este elemento no tiene producto", DataException.Kind.INVALID)

    private fun withUpload(block: suspend () -> Unit) = launchSafely {
        uploading.value = true
        try {
            block()
        } finally {
            uploading.value = false
        }
    }

    /** [onAdded] limpia el campo solo si se guardó (sin conexión no se pierde lo escrito). */
    fun addItem(text: String, quantity: String?, section: String?, onAdded: () -> Unit = {}) = launchSafely {
        lists.addItem(listId, text, quantity, section)
        onAdded()
    }

    fun moveToSection(item: ChecklistItem, section: String?) = launchSafely {
        lists.setItemSection(listId, item.id, section)
        notify(section?.let { "«${item.text}» ahora está en $it" } ?: "«${item.text}» quedó sin sección")
    }

    fun toggle(item: ChecklistItem) = launchSafely {
        lists.updateItem(listId, item.id, done = !item.done)
    }

    /** Se quita enseguida; se borra de verdad si no se toca "Deshacer". */
    fun remove(item: ChecklistItem) {
        val key = deletes.removeItemLater(listId, item.id, onError = ::notify)
        notifyUndo("«${item.text}» eliminado") { deletes.undo(key) }
    }

    /** Cambia el texto y la cantidad (vacía la quita). */
    fun editItem(item: ChecklistItem, text: String, quantity: String) = launchSafely {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return@launchSafely notify("Escribe el nombre del elemento")
        val cleanQuantity = quantity.trim()
        if (cleanText == item.text && cleanQuantity == item.quantity.orEmpty()) return@launchSafely
        lists.updateItem(listId, item.id, text = cleanText, quantity = cleanQuantity)
    }

    fun clearCompleted() = launchSafely { lists.clearCompleted(listId) }

    fun rename(title: String) = launchSafely { lists.renameList(listId, title) }

    fun invite(email: String, role: MemberRole, onDone: () -> Unit) = withBusy {
        sharing.invite(listId, email, role)
        notify("Invitación enviada a ${email.trim()}")
        onDone()
    }

    fun removeMember(email: String) = launchSafely { sharing.removeMember(listId, email) }

    /** Compartir con un grupo (o dejar de hacerlo con role null). */
    fun shareWithGroup(group: Group, role: MemberRole?) = launchSafely {
        groups.share(ShareTarget.ListTarget(listId), group, role)
        notify(if (role == null) "Ya no se comparte con «${group.name}»" else "Compartido con «${group.name}»")
    }

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
                appendLine("Únete a la lista en Arianna con el código ${invite.code}")
                append(invite.url)
            }
        }.trim()
    }
}
