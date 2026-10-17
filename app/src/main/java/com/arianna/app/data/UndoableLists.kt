package com.arianna.app.data

import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.repository.UndoableDeletes
import com.arianna.app.domain.repository.UndoableDeletes.Companion.UNDO_WINDOW_MS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Envuelve cualquier [ListsRepository] (falso o remoto) para que borrar listas y elementos se
 * pueda deshacer. Lo pendiente de borrar no aparece en ningún flujo. El borrado corre en su propio
 * scope: sigue aunque el usuario salga de la pantalla.
 */
class UndoableLists(private val delegate: ListsRepository) : ListsRepository by delegate, UndoableDeletes {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()
    private val hiddenLists = MutableStateFlow<Set<String>>(emptySet())
    private val hiddenItems = MutableStateFlow<Set<String>>(emptySet())

    override fun observeLists(): Flow<List<Checklist>> =
        combine(delegate.observeLists(), hiddenLists, hiddenItems) { lists, lh, ih ->
            lists.filterNot { it.id in lh }.map { it.withoutItems(ih) }
        }

    override fun observeList(id: String): Flow<Checklist?> =
        combine(delegate.observeList(id), hiddenLists, hiddenItems) { list, lh, ih ->
            list?.takeUnless { it.id in lh }?.withoutItems(ih)
        }

    override fun deleteListLater(id: String, onError: (String) -> Unit): String =
        schedule("list:$id", hiddenLists, id, onError) { delegate.deleteList(id) }

    override fun removeItemLater(listId: String, itemId: String, onError: (String) -> Unit): String =
        schedule("item:$itemId", hiddenItems, itemId, onError) { delegate.removeItem(listId, itemId) }

    override fun undo(key: String) {
        jobs.remove(key)?.cancel()
        val id = key.substringAfter(':')
        hiddenLists.update { it - id }
        hiddenItems.update { it - id }
    }

    private fun schedule(
        key: String,
        hidden: MutableStateFlow<Set<String>>,
        id: String,
        onError: (String) -> Unit,
        commit: suspend () -> Unit,
    ): String {
        jobs.remove(key)?.cancel()
        hidden.update { it + id }
        jobs[key] = scope.launch {
            delay(UNDO_WINDOW_MS)
            try {
                commit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo eliminar")
            } finally {
                // Si falló, vuelve a aparecer; si no, ya no está en los datos.
                hidden.update { it - id }
                jobs.remove(key)
            }
        }
        return key
    }

    private fun Checklist.withoutItems(hidden: Set<String>): Checklist =
        if (hidden.isEmpty() || items.none { it.id in hidden }) this else copy(items = items.filterNot { it.id in hidden })
}
