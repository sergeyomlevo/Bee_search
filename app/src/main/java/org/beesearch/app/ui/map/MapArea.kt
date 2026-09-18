package org.beesearch.app.ui.map

import java.util.UUID

/**
 * Именованный ареал территории: пользовательская область offline-карты.
 *
 * Терминология: «Ареал» — вся именованная область территории, «участок» — один прямоугольник
 * внутри ареала. У территории бывает не более одного ареала, поэтому здесь нет ни коллекции
 * ареалов, ни указателя выбранного: принадлежность задаётся ключом хранения по `Territory.id`.
 *
 * Ареал остаётся device-local map intent, а не доменной сущностью и не `Territory boundary`.
 */
internal data class MapArea(
    val id: UUID,
    val name: String,
    val bounds: List<MapGeoBounds>,
) {
    init {
        require(name.isNotBlank()) { "Название ареала не может быть пустым" }
        require(bounds.isNotEmpty()) { "Ареал содержит хотя бы один участок" }
    }
}

/**
 * Результат чтения сохранённого ареала территории.
 *
 * Повреждённое значение нельзя путать с отсутствием данных: иначе уже потерянное значение
 * выглядело бы как «покрытие не выбрано» и затиралось первым же сохранением.
 */
internal sealed interface MapAreaReadResult {
    /** Ареала нет: ключа нет либо сохранён пустой legacy-набор участков. */
    data object Absent : MapAreaReadResult

    /** Ареал прочитан полностью. */
    data class Present(val area: MapArea) : MapAreaReadResult

    /** Читаемый legacy-набор участков без имени и id: подлежит миграции в ареал. */
    data class Legacy(val bounds: List<MapGeoBounds>) : MapAreaReadResult

    /** Значение сохранено, но не читается. Никогда не считается отсутствием ареала. */
    data class Corrupt(val reason: String) : MapAreaReadResult
}

/** Результат сохранения отредактированных участков. */
internal sealed interface MapAreaSaveResult {
    /** Ареал обновлён: id и имя сохранены. */
    data class Saved(val area: MapArea) : MapAreaSaveResult

    /**
     * Ареала ещё нет, поэтому сохранён legacy-набор участков без имени.
     *
     * PHASE B: эта ветка существует только до появления диалога названия при первом `Готово`
     * и удаляется вместе с ним.
     */
    data object SavedLegacySelection : MapAreaSaveResult

    /** Значение не изменено: либо оно повреждено, либо результат нарушил бы инвариант ареала. */
    data class Refused(val reason: String) : MapAreaSaveResult
}

/** Имя ареала, когда у территории нет пригодного названия. */
internal const val DEFAULT_AREA_NAME = "Ареал"

/** Сообщение о попытке сохранить ареал без участков. */
internal const val EMPTY_AREA_MESSAGE = "Ареал должен содержать хотя бы один участок"

/** Сообщение о повреждённом сохранённом значении: оно не читается и не изменяется. */
internal const val CORRUPT_AREA_MESSAGE = "Сохранённые данные ареала повреждены и не изменены"

/**
 * Начальное имя ареала: название территории, если оно пригодно для показа, иначе [DEFAULT_AREA_NAME].
 *
 * Название территории приходит с более высокого уровня как обычная строка, поэтому правило
 * остаётся чистой функцией и не связывает хранилище покрытия с Room.
 */
internal fun initialAreaName(territoryName: String?): String {
    val trimmed = territoryName?.trim().orEmpty()
    return trimmed.ifEmpty { DEFAULT_AREA_NAME }
}

/**
 * Строит ареал из legacy-набора участков. Geometry переносится без изменений: значения
 * не округляются, не нормализуются, не сортируются и не объединяются.
 */
internal fun migratedMapArea(bounds: List<MapGeoBounds>, territoryName: String?, id: UUID): MapArea =
    MapArea(id = id, name = initialAreaName(territoryName), bounds = bounds)

/** Участки ареала в виде, который ожидает проверка покрытия Map Package. */
internal fun MapArea.coverageFragments(): List<MapCoverageFragment> = bounds.map(::MapCoverageFragment)
