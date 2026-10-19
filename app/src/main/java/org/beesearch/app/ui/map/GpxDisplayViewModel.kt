package org.beesearch.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.beesearch.app.data.local.room.GpxReadyMetadata

internal data class GpxDisplayRow(
    val id: UUID, val name: String, val selected: Boolean = false,
    val saving: Boolean = false, val preparing: Boolean = false, val error: String? = null,
    val deleting: Boolean = false, val deleteError: String? = null,
)
internal data class GpxDisplayUiState(
    val territoryId: UUID? = null, val loading: Boolean = true,
    val error: String? = null, val rows: List<GpxDisplayRow> = emptyList(),
)
internal interface GpxVisibilityStore {
    suspend fun load(territoryId: UUID): Set<UUID>
    suspend fun setVisible(territoryId: UUID, importId: UUID, visible: Boolean)
}

/** Feature owner only: immutable prepared data; native objects belong to the existing session. */
internal class GpxDisplayViewModel(
    private val catalog: suspend (UUID) -> List<GpxReadyMetadata>,
    private val prepare: suspend (UUID, UUID, Long) -> GpxPreparedOverlay,
    private val store: GpxVisibilityStore,
    private val delete: suspend (UUID, UUID) -> Unit = { _, _ -> error("Deletion unavailable") },
    deletions: kotlinx.coroutines.flow.Flow<UUID> = kotlinx.coroutines.flow.emptyFlow(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(GpxDisplayUiState())
    val state = mutableState.asStateFlow()
    private val mutableOverlays = MutableStateFlow<List<GpxPreparedOverlay>>(emptyList())
    val overlays = mutableOverlays.asStateFlow()
    private var epoch = 0L
    private var revision = 0L
    private var preparationGeneration = 0L
    private var loadJob: Job? = null
    private var geometryJob: Job? = null
    private val writes = Mutex()
    private val failedWrites = mutableMapOf<UUID, Boolean>()
    private val pendingDeletes = mutableSetOf<UUID>()
    private val deletedIds = mutableSetOf<UUID>()

    init {
        viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            deletions.collect { onDeleted(it) }
        }
    }
    fun deleteImport(importId: UUID) {
        val initial = mutableState.value
        val territory = initial.territoryId ?: return
        val row = initial.rows.find { it.id == importId } ?: return
        if (row.deleting || row.saving || !pendingDeletes.add(importId)) return
        updateRow(importId) { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            writes.withLock {
                try {
                    delete(territory, importId)
                    onDeleted(importId)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (mutableState.value.territoryId == territory) updateRow(importId) {
                        it.copy(deleting = false, deleteError = "Не удалось удалить трек")
                    }
                } finally {
                    pendingDeletes.remove(importId)
                }
            }
        }
    }
    private fun onDeleted(id: UUID) {
        deletedIds.add(id)
        if (mutableState.value.rows.none { it.id == id } && mutableOverlays.value.none { it.id == id }) return
        epoch++; preparationGeneration++
        loadJob?.cancel(); geometryJob?.cancel(); failedWrites.remove(id)
        mutableOverlays.value = mutableOverlays.value.filterNot { it.id == id }
        mutableState.value = mutableState.value.copy(rows = mutableState.value.rows.filterNot { it.id == id })
        mutableState.value.territoryId?.let { prepareSelected(it, epoch) }
    }

    fun setTerritory(id: UUID?) {
        if (mutableState.value.territoryId == id && epoch != 0L) return
        epoch++
        loadJob?.cancel(); geometryJob?.cancel()
        mutableOverlays.value = emptyList()
        failedWrites.clear()
        mutableState.value = GpxDisplayUiState(territoryId = id, loading = id != null)
        if (id != null) refresh()
    }

    fun refresh() {
        val id = mutableState.value.territoryId ?: return
        val token = ++epoch
        loadJob?.cancel(); geometryJob?.cancel()
        mutableOverlays.value = emptyList()
        failedWrites.clear()
        mutableState.value = GpxDisplayUiState(id)
        loadJob = viewModelScope.launch {
            try {
                val metadata = catalog(id)
                val selected = store.load(id)
                if (!current(id, token)) return@launch
                mutableState.value = GpxDisplayUiState(id, loading = false,
                    rows = names(metadata.filterNot { it.id in deletedIds }).map { (row, name) -> GpxDisplayRow(row.id, name, row.id in selected, deleting = row.id in pendingDeletes) })
                prepareSelected(id, token)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (current(id, token)) mutableState.value = GpxDisplayUiState(id, loading = false, error = "Не удалось загрузить GPX")
            }
        }
    }

    fun setVisible(importId: UUID, visible: Boolean) {
        val initial = mutableState.value
        val id = initial.territoryId ?: return
        val token = epoch
        val row = initial.rows.find { it.id == importId } ?: return
        if (row.deleting || row.saving || initial.loading || row.selected == visible) return
        failedWrites.remove(importId)
        updateRow(importId) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            writes.withLock {
                if (!current(id, token)) return@withLock
                try {
                    store.setVisible(id, importId, visible)
                    if (!current(id, token)) return@withLock
                    updateRow(importId) { it.copy(selected = visible, saving = false, preparing = visible, error = null) }
                    if (!visible) mutableOverlays.value = mutableOverlays.value.filterNot { it.id == importId }
                    prepareSelected(id, token)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (current(id, token)) {
                        failedWrites[importId] = visible
                        updateRow(importId) { it.copy(saving = false, error = "Не удалось сохранить") }
                    }
                }
            }
        }
    }

    fun retry(importId: UUID?) {
        if (importId == null) { refresh(); return }
        failedWrites.remove(importId)?.let { setVisible(importId, it); return }
        val id = mutableState.value.territoryId ?: return
        mutableOverlays.value = mutableOverlays.value.filterNot { it.id == importId }
        updateRow(importId) { it.copy(error = null) }
        prepareSelected(id, epoch)
    }

    /** Scope and revision reject callbacks from an old Territory or a replaced preparation. */
    fun onPresentation(scope: Any?, status: Map<String, OverlayPresentation>) {
        if (scope != mutableState.value.territoryId) return
        mutableOverlays.value.forEach { overlay ->
            val result = status[overlay.key]?.takeIf { it.revision == overlay.revision } ?: return@forEach
            updateRow(overlay.id) { row ->
                if (overlay.id in failedWrites) return@updateRow row
                row.copy(preparing = result.result == OverlayResult.DEFERRED,
                    error = when (result.result) {
                        OverlayResult.APPLIED, OverlayResult.NO_CHANGE, OverlayResult.DEFERRED -> null
                        else -> "Не удалось показать трек"
                    })
            }
        }
    }

    private fun prepareSelected(id: UUID, token: Long) {
        val batch = ++preparationGeneration
        fun latest() = current(id, token) && preparationGeneration == batch
        geometryJob?.cancel()
        geometryJob = viewModelScope.launch {
            // Single sequential producer; no fan-out and no geometry/cache for hidden imports.
            for (row in mutableState.value.rows.filter { it.selected }) {
                if (!latest()) return@launch
                if (mutableOverlays.value.any { it.id == row.id }) continue
                updateRow(row.id) { it.copy(preparing = true, error = null) }
                try {
                    val result = prepare(id, row.id, ++revision)
                    if (!latest() || mutableState.value.rows.none { it.id == row.id && it.selected }) return@launch
                    mutableOverlays.value = mutableOverlays.value + result
                    // Remains preparing until the native session acknowledges the entire declaration.
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: OutOfMemoryError) {
                    if (latest()) updateRow(row.id) { it.copy(preparing = false,
                        error = "Недостаточно памяти. Отключите часть треков и повторите") }
                }
                catch (_: GpxPresentationCapacityException) {
                    if (latest()) updateRow(row.id) { it.copy(preparing = false,
                        error = "Недостаточно памяти. Отключите часть треков и повторите") }
                }
                catch (_: Exception) {
                    if (latest()) updateRow(row.id) { it.copy(preparing = false, error = "Не удалось показать трек") }
                }
            }
        }
    }

    private fun current(id: UUID, token: Long) = epoch == token && mutableState.value.territoryId == id
    private fun updateRow(id: UUID, change: (GpxDisplayRow) -> GpxDisplayRow) {
        val state = mutableState.value
        val rows = state.rows.map { if (it.id == id) change(it) else it }
        if (rows != state.rows) mutableState.value = state.copy(rows = rows)
    }

    companion object {
        fun factory(catalog: suspend (UUID) -> List<GpxReadyMetadata>,
            prepare: suspend (UUID, UUID, Long) -> GpxPreparedOverlay, store: GpxVisibilityStore,
            delete: suspend (UUID, UUID) -> Unit = { _, _ -> error("Deletion unavailable") },
            deletions: kotlinx.coroutines.flow.Flow<UUID> = kotlinx.coroutines.flow.emptyFlow()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GpxDisplayViewModel(catalog, prepare, store, delete, deletions) as T
        }

        internal fun names(metadata: List<GpxReadyMetadata>): List<Pair<GpxReadyMetadata, String>> {
            val sorted = metadata.sortedWith(compareByDescending<GpxReadyMetadata> { it.importedAtEpochMs }.thenBy { it.id.toString() })
            val format = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.systemDefault())
            fun date(row: GpxReadyMetadata) = format.format(Instant.ofEpochMilli(row.importedAtEpochMs))
            val base = sorted.map { it.displayName?.takeIf(String::isNotBlank) ?: "GPX от ${date(it)}" }
            val counts = base.groupingBy { it }.eachCount()
            val dated = sorted.indices.map { i -> if (counts.getValue(base[i]) > 1) "${base[i]} · ${date(sorted[i])}" else base[i] }
            val duplicates = dated.groupingBy { it }.eachCount()
            val seen = mutableMapOf<String, Int>()
            return sorted.mapIndexed { i, row ->
                val name = dated[i]
                val occurrence = (seen[name] ?: 0) + 1
                seen[name] = occurrence
                row to if (duplicates.getValue(name) > 1) "$name · $occurrence" else name
            }
        }
    }
}
