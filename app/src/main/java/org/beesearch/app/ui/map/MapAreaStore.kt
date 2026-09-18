package org.beesearch.app.ui.map

import java.util.UUID

/**
 * Device-local ареал территории, отдельно от Room research data.
 *
 * Значение хранится в существующей записи `map_coverage_<territoryId>`, поэтому формат остаётся
 * versioned, а backup продолжает переносить его в своей settings-секции без изменения схемы архива.
 */
internal interface MapAreaStore {
    /**
     * Читает ареал территории и при необходимости мигрирует читаемый legacy-набор участков.
     *
     * [territoryName] используется только как начальное имя мигрируемого ареала; он приходит
     * с более высокого уровня, поэтому хранилище не зависит от Room.
     */
    suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult

    /**
     * Сохраняет отредактированные участки.
     *
     * Существующий ареал сохраняет свой id и имя. Повреждённое значение не перезаписывается.
     */
    suspend fun saveBounds(
        territoryId: UUID,
        bounds: List<MapGeoBounds>,
        territoryName: String?,
    ): MapAreaSaveResult

    suspend fun clear(territoryId: UUID)

    /** Точное сохранённое значение: используется для обратимых операций над Territory. */
    suspend fun snapshot(territoryId: UUID): String?

    /** Восстанавливает значение, полученное из [snapshot], включая повреждённое. */
    suspend fun restore(territoryId: UUID, value: String?)
}
