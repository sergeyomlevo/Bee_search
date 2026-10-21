package org.beesearch.app.ui.userpoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.repository.UserPointRepository

internal data class UserPointDisplayState(
    val territoryId: UUID? = null,
    val loading: Boolean = true,
    val points: List<UserPoint> = emptyList(),
    val error: String? = null,
    val writing: Set<UUID> = emptySet(),
    val writeErrors: Map<UUID, Boolean> = emptyMap(),
    val selectedId: UUID? = null,
    val revision: Long = 0,
)

/** Room is the only visibility store. Epoch checks reject even non-cooperative old reads/writes. */
internal class UserPointDisplayViewModel(private val repository: UserPointRepository) : ViewModel() {
    private val mutable = MutableStateFlow(UserPointDisplayState())
    val state = mutable.asStateFlow()
    private var epoch = 0L
    private var revision = 0L
    private var observation: Job? = null

    fun setTerritory(id: UUID?) {
        if (state.value.territoryId == id) return
        epoch++
        observation?.cancel()
        mutable.value = UserPointDisplayState(territoryId = id, loading = id != null, revision = ++revision)
        if (id != null) observe(id, epoch)
    }

    fun retryRead() {
        val id = state.value.territoryId ?: return
        observation?.cancel()
        publish { it.copy(writing = emptySet(), writeErrors = emptyMap()) }
        observe(id, ++epoch)
    }

    private fun observe(id: UUID, token: Long) {
        publish { it.copy(loading = true, error = null, points = emptyList(), selectedId = null) }
        observation = viewModelScope.launch {
            try {
                repository.observePoints(id).collect { points ->
                    if (epoch == token && state.value.territoryId == id) {
                        val owned = points.filter { it.territoryId == id }
                        publish { old -> old.copy(loading = false, error = null, points = owned,
                            selectedId = old.selectedId?.takeIf { selected -> owned.any { it.id == selected && it.isVisible } }) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (epoch == token) publish { it.copy(loading = false, points = emptyList(), error = "Не удалось загрузить Мои точки", selectedId = null) }
            }
        }
    }

    fun setVisible(id: UUID, visible: Boolean) {
        viewModelScope.launch { persistVisibility(id, visible) }
    }

    fun retryVisibility(id: UUID) { state.value.writeErrors[id]?.let { setVisible(id, it) } }

    /** The caller's composition owns this operation: leaving the card cannot replay camera intent. */
    suspend fun showOnMap(id: UUID): UserPoint? = withContext(Dispatchers.Main.immediate) { showOnMapOnMain(id) }

    private suspend fun showOnMapOnMain(id: UUID): UserPoint? {
        val snapshot = state.value
        val point = snapshot.points.firstOrNull { it.id == id } ?: return null
        val territory = snapshot.territoryId ?: return null
        val token = epoch
        if (id in snapshot.writing) return null
        if (!point.isVisible && !persistVisibility(id, true)) return null
        currentCoroutineContext().ensureActive()
        if (epoch != token || state.value.territoryId != territory) return null
        val saved = state.value.points.firstOrNull { it.id == id && it.isVisible } ?: return null
        publish { it.copy(selectedId = id) }
        return saved
    }

    private suspend fun persistVisibility(id: UUID, visible: Boolean): Boolean {
        val old = state.value
        val territory = old.territoryId ?: return false
        if (id in old.writing || old.points.none { it.id == id }) return false
        val token = epoch
        publish { it.copy(writing = it.writing + id, writeErrors = it.writeErrors - id) }
        try {
            repository.setVisible(territory, id, visible)
            val saved = repository.getPoint(territory, id) ?: error("Точка не найдена")
            currentCoroutineContext().ensureActive()
            if (epoch != token || state.value.territoryId != territory) return false
            check(saved.isVisible == visible && saved.territoryId == territory)
            publish { current -> current.copy(points = current.points.map { if (it.id == id) saved else it },
                selectedId = current.selectedId?.takeUnless { it == id && !visible }) }
            return true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (epoch == token) publish { it.copy(writeErrors = it.writeErrors + (id to visible)) }
            return false
        } finally {
            if (epoch == token) publish { it.copy(writing = it.writing - id) }
        }
    }

    private fun publish(change: (UserPointDisplayState) -> UserPointDisplayState) {
        val old = state.value
        val next = change(old)
        mutable.value = next.copy(revision = if (next.points != old.points || next.selectedId != old.selectedId) ++revision else old.revision)
    }

    companion object {
        fun factory(repository: UserPointRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UserPointDisplayViewModel(repository) as T
        }
    }
}
