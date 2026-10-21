package org.beesearch.app.ui.userpoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.repository.UserPointRepository

internal data class UserPointDeletionState(
    val isWorking: Boolean = false,
    val error: String? = null,
    val pending: List<UserPoint> = emptyList(),
    val showCleanupNotice: Boolean = false,
    val cleanupMessage: String = "",
)

/** Session-only cleanup receipts. Room is authoritative; retries never repeat SQL deletion.
 * No journal or process-death cleanup recovery is introduced by this feature.
 */
internal class UserPointDeletionViewModel(
    private val repository: UserPointRepository,
    private val mediaStore: UserPointMediaStore,
) : ViewModel() {
    private data class Receipt(val point: UserPoint, val removeDirectory: Boolean)
    private val receipts = mutableListOf<Receipt>()
    private val mutable = MutableStateFlow(UserPointDeletionState())
    val state = mutable.asStateFlow()

    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun dismissCleanupNotice() { mutable.value = mutable.value.copy(showCleanupNotice = false) }

    fun retainCleanup(point: UserPoint) {
        receipts += Receipt(point, false)
        publishPending()
    }

    fun delete(territoryId: UUID, id: UUID, onDeleted: () -> Unit) {
        if (state.value.isWorking) return
        mutable.value = state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            var committed = false
            try {
                withContext(NonCancellable) {
                    // Obtain the exact deleted photo snapshot as part of the same Room transaction.
                    val deleted = repository.delete(territoryId, id)
                    committed = true
                    receipts += Receipt(deleted, true)
                    publishPending(showNotice = false)
                    cleanup()
                    mutable.value = state.value.copy(isWorking = false)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.value = state.value.copy(isWorking = false,
                    error = if (committed) null else "Не удалось удалить точку",
                    showCleanupNotice = committed && receipts.isNotEmpty())
            }
            if (committed) onDeleted()
        }
    }

    fun retryCleanup() {
        if (state.value.isWorking || receipts.isEmpty()) return
        mutable.value = state.value.copy(isWorking = true)
        viewModelScope.launch {
            withContext(NonCancellable) {
                cleanup()
                mutable.value = state.value.copy(isWorking = false)
            }
        }
    }

    private suspend fun cleanup() {
        val remaining = mutableListOf<Receipt>()
        receipts.forEach { receipt ->
            val complete = try {
                val current = repository.getPoint(receipt.point.territoryId, receipt.point.id)
                // Never delete bytes of a live point from a stale deletion receipt. For edit receipts,
                // retain any file that has become referenced again since the update.
                if (receipt.removeDirectory && current != null) false
                else {
                    val referenced = current?.photos.orEmpty().map { it.relativePath }.toSet()
                    mediaStore.cleanupOwned(receipt.point.id,
                        receipt.point.photos.filterNot { it.relativePath in referenced }, receipt.removeDirectory)
                }
            } catch (_: Exception) { false }
            if (!complete) remaining += receipt
        }
        receipts.clear()
        receipts += remaining
        publishPending()
    }

    private fun publishPending(showNotice: Boolean = true) {
        mutable.value = state.value.copy(pending = receipts.map { it.point },
            showCleanupNotice = showNotice && receipts.isNotEmpty(),
            cleanupMessage = if (receipts.any { it.removeDirectory })
                "Точка удалена. Не удалось удалить часть её фотографий"
            else "Точка сохранена. Не удалось удалить часть прежних фотографий")
    }

    companion object {
        fun factory(repository: UserPointRepository, mediaStore: UserPointMediaStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UserPointDeletionViewModel(repository, mediaStore) as T
        }
    }
}
