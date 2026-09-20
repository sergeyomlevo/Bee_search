package org.beesearch.app.ui.map

import java.util.UUID

/**
 * Device-local ареал территории, отдельно от Room research data.
 *
 * Значение хранится в существующей записи `map_coverage_<territoryId>`, поэтому формат остаётся
 * versioned, а backup продолжает переносить его в своей settings-секции без изменения схемы архива.
 *
 * Новые пользовательские записи создаются только как полный `v2` ареал; `v1` остаётся форматом
 * чтения legacy-данных и источником миграции.
 */
internal interface MapAreaStore {
    /**
     * Читает ареал территории и при необходимости мигрирует читаемый legacy-набор участков.
     *
     * [territoryName] используется только как начальное имя мигрируемого ареала; он приходит
     * с более высокого уровня, поэтому хранилище не зависит от Room.
     */
    suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult

    /** Создаёт новый ареал. Требует непустое имя и хотя бы один участок. */
    suspend fun create(territoryId: UUID, name: String, bounds: List<MapGeoBounds>): MapAreaChangeResult

    /** Заменяет участки существующего ареала, сохраняя его UUID и имя. */
    suspend fun updateBounds(territoryId: UUID, bounds: List<MapGeoBounds>): MapAreaChangeResult

    /** Меняет только имя существующего ареала, сохраняя UUID и участки. */
    suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult

    /** Удаляет canonical ареал территории. Territory, Room и map package не затрагиваются. */
    suspend fun delete(territoryId: UUID): MapAreaChangeResult

    /**
     * Безусловно удаляет device-local запись территории.
     *
     * Отличается от [delete]: это обслуживание, которое выполняется при удалении самой Territory,
     * поэтому повреждённое значение не должно блокировать удаление. Обратимость обеспечивается
     * парой [snapshot] / [restore].
     */
    suspend fun clear(territoryId: UUID)

    /** Точное сохранённое значение: используется для обратимых операций над Territory. */
    suspend fun snapshot(territoryId: UUID): String?

    /** Восстанавливает значение, полученное из [snapshot], включая повреждённое. */
    suspend fun restore(territoryId: UUID, value: String?)
}
