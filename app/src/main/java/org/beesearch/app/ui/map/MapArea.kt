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

/** Результат изменения сохранённого ареала. */
internal sealed interface MapAreaChangeResult {
    /** Ареал создан, обновлён или переименован. */
    data class Saved(val area: MapArea) : MapAreaChangeResult

    /** Ареал удалён: canonical значение отсутствует. */
    data object Deleted : MapAreaChangeResult

    /** Значение не изменено: нарушен инвариант ареала либо сохранённые данные повреждены. */
    data class Refused(val reason: String) : MapAreaChangeResult
}

/** Имя ареала, когда у территории нет пригодного названия. */
internal const val DEFAULT_AREA_NAME = "Ареал"

/** Сообщение о попытке сохранить ареал без участков. */
// Kept to one short sentence on purpose: this reaches the user through a Toast, which is limited
// to two lines and truncates a longer message at font_scale 1.7. The deletion path is documented
// in Help and on the «Ареал» screen instead.
internal const val EMPTY_AREA_MESSAGE = "Ареал должен содержать хотя бы один участок."

/** Сообщение о пустом названии ареала. */
internal const val BLANK_AREA_NAME_MESSAGE = "Введите название ареала"

/** Сообщение о повреждённом сохранённом значении: оно не читается и не изменяется. */
internal const val CORRUPT_AREA_MESSAGE = "Сохранённые данные ареала повреждены и не изменены"

/**
 * Defensive message for a legacy value that was read without being migrated.
 *
 * The store migrates on every read, so the editor never sees this state; it exists only so that no
 * caller can silently overwrite stored участки.
 */
internal const val UNMIGRATED_AREA_MESSAGE = "Сохранённые участки ещё не переведены в ареал"

/** Имя ареала без крайних пробелов или `null`, если после trim оно пустое. */
internal fun normalizedAreaName(name: String): String? = name.trim().ifEmpty { null }

/**
 * Начальное имя ареала: название территории, если оно пригодно для показа, иначе [DEFAULT_AREA_NAME].
 *
 * Название территории приходит с более высокого уровня как обычная строка, поэтому правило
 * остаётся чистой функцией и не связывает хранилище покрытия с Room.
 */
internal fun initialAreaName(territoryName: String?): String =
    normalizedAreaName(territoryName.orEmpty()) ?: DEFAULT_AREA_NAME

/**
 * Строит ареал из legacy-набора участков. Geometry переносится без изменений: значения
 * не округляются, не нормализуются, не сортируются и не объединяются.
 */
internal fun migratedMapArea(bounds: List<MapGeoBounds>, territoryName: String?, id: UUID): MapArea =
    MapArea(id = id, name = initialAreaName(territoryName), bounds = bounds)

/** Участки ареала в виде, который ожидает проверка покрытия Map Package. */
internal fun MapArea.coverageFragments(): List<MapCoverageFragment> = bounds.map(::MapCoverageFragment)

/**
 * Что должно произойти при подтверждении редактора участков.
 *
 * Решение вынесено из UI, чтобы «Готово» и Back → «Сохранить» шли одним путём, а правила первого
 * создания и защиты существующего ареала проверялись без устройства.
 */
internal sealed interface AreaCommitPlan {
    /** Ареала нет и сохранять нечего: редактор просто закрывается. */
    data object ExitWithoutCreating : AreaCommitPlan

    /** Ареала нет, но есть участки: нужно спросить название и создать ареал. */
    data class CreateWithName(val initialName: String) : AreaCommitPlan

    /** Ареал есть: сохраняются только участки, имя и UUID не меняются. */
    data object UpdateBounds : AreaCommitPlan

    /** Сохранение невозможно: значение не изменяется, редактор остаётся открытым. */
    data class Refused(val message: String) : AreaCommitPlan
}

/**
 * Пустой draft существующего ареала никогда не сохраняется и никогда не удаляет ареал: удаление
 * возможно только отдельным подтверждённым действием.
 */
internal fun planAreaCommit(
    persisted: MapAreaReadResult,
    draft: List<MapCoverageFragment>,
    territoryName: String?,
): AreaCommitPlan = when (persisted) {
    is MapAreaReadResult.Corrupt -> AreaCommitPlan.Refused(CORRUPT_AREA_MESSAGE)
    is MapAreaReadResult.Legacy -> AreaCommitPlan.Refused(UNMIGRATED_AREA_MESSAGE)

    MapAreaReadResult.Absent -> if (draft.isEmpty()) {
        AreaCommitPlan.ExitWithoutCreating
    } else {
        AreaCommitPlan.CreateWithName(initialAreaName(territoryName))
    }

    is MapAreaReadResult.Present -> if (draft.isEmpty()) {
        AreaCommitPlan.Refused(EMPTY_AREA_MESSAGE)
    } else {
        AreaCommitPlan.UpdateBounds
    }
}
