package com.arianna.app.ui.lists

import androidx.lifecycle.viewModelScope
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.repository.SharingRepository
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.Load
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ListFilter(val key: String?) {
    ALL(null), SHOPPING("shopping"), TODO("todo"), SHARED("shared");

    companion object {
        fun from(key: String?) = entries.find { it.key == key } ?: ALL
    }
}

data class ListsUiState(
    val lists: List<Checklist> = emptyList(),
    val filter: ListFilter = ListFilter.ALL,
    val query: String = "",
    val load: Load = Load.LOADING,
) {
    val loaded: Boolean get() = load == Load.READY
}

class ListsViewModel(
    initialFilter: String?,
    initialQuery: String?,
    private val lists: ListsRepository,
    private val sharing: SharingRepository,
) : BaseViewModel() {

    private val filter = MutableStateFlow(ListFilter.from(initialFilter))
    private val query = MutableStateFlow(initialQuery.orEmpty())

    private val _openList = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Id de la lista a abrir (después de crearla o de unirse). */
    val openList: SharedFlow<String> = _openList.asSharedFlow()

    val state: StateFlow<ListsUiState> = loadingState(ListsUiState(), { copy(load = it) }) {
        combine(lists.observeLists(), filter, query) { all, f, q ->
            val needle = q.trim().lowercase()
            val visible = all
                .filter {
                    when (f) {
                        ListFilter.ALL -> true
                        ListFilter.SHOPPING -> it.kind == ChecklistKind.SHOPPING
                        ListFilter.TODO -> it.kind == ChecklistKind.TODO
                        ListFilter.SHARED -> it.sharedWith.isNotEmpty()
                    }
                }
                .filter { list ->
                    needle.isEmpty() ||
                        list.title.lowercase().contains(needle) ||
                        list.items.any { it.text.lowercase().contains(needle) }
                }
            ListsUiState(visible, f, q)
        }
    }

    fun setFilter(value: ListFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun create(title: String, kind: ChecklistKind) = launchSafely {
        val list = lists.createList(title, kind)
        _openList.emit(list.id)
    }

    fun delete(list: Checklist) = launchSafely {
        lists.deleteList(list.id)
        notify("Lista \"${list.title}\" eliminada")
    }

    fun join(code: String) = launchSafely {
        val list = sharing.joinByCode(code)
        _openList.emit(list.id)
    }
}
