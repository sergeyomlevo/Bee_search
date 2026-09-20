package org.beesearch.app.data.exchange

import java.util.UUID
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapGeoBounds

/**
 * A [MapAreaStore] that keeps the managed Area file in step with the canonical Ареал.
 *
 * Mirroring is attached to the store rather than repeated in every screen, so no user-facing path
 * can forget it. Two rules from the Area design are enforced here:
 *
 * 1. the canonical Ареал is written first - the mirror runs only after a successful canonical
 *    change, and a mirror failure never rolls the canonical change back or changes its result;
 * 2. reading never touches the mirror, so opening the map or the points browser costs nothing.
 *
 * The explicit [AreaExchangeMirror.sync] remains available for the two places that need the file
 * *now*: opening `Objects → Ареал` and sending the Ареал.
 */
internal class MirroringMapAreaStore(
    private val delegate: MapAreaStore,
    private val mirror: AreaExchangeMirror,
) : MapAreaStore {
    override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult =
        delegate.load(territoryId, territoryName)

    override suspend fun create(
        territoryId: UUID,
        name: String,
        bounds: List<MapGeoBounds>,
    ): MapAreaChangeResult = mirrorSaved(delegate.create(territoryId, name, bounds))

    override suspend fun updateBounds(
        territoryId: UUID,
        bounds: List<MapGeoBounds>,
    ): MapAreaChangeResult = mirrorSaved(delegate.updateBounds(territoryId, bounds))

    override suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult =
        mirrorSaved(delegate.rename(territoryId, name))

    override suspend fun delete(territoryId: UUID): MapAreaChangeResult {
        // Read the exact stored value before deleting: the managed file name is built from the id and
        // the name of the Ареал that is about to disappear. A snapshot is raw and side-effect free,
        // so it cannot migrate or rewrite anything.
        val removed = (MapAreaCodec.decode(delegate.snapshot(territoryId)) as? MapAreaReadResult.Present)?.area
        val result = delegate.delete(territoryId)
        if (result is MapAreaChangeResult.Deleted && removed != null) {
            mirror.remove(removed)
        }
        return result
    }

    override suspend fun clear(territoryId: UUID) = delegate.clear(territoryId)

    override suspend fun snapshot(territoryId: UUID): String? = delegate.snapshot(territoryId)

    override suspend fun restore(territoryId: UUID, value: String?) = delegate.restore(territoryId, value)

    private suspend fun mirrorSaved(result: MapAreaChangeResult): MapAreaChangeResult {
        (result as? MapAreaChangeResult.Saved)?.let { mirror.sync(it.area) }
        return result
    }
}
