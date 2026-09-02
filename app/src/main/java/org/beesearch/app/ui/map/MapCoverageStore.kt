package org.beesearch.app.ui.map

import java.util.UUID

/** Device-local desired coverage, deliberately separate from Room research data. */
internal interface MapCoverageStore {
    suspend fun load(territoryId: UUID): List<MapCoverageFragment>
    suspend fun replace(territoryId: UUID, fragments: List<MapCoverageFragment>)
    suspend fun clear(territoryId: UUID)
}

internal object MapCoverageCodec {
    private const val VERSION = "v1"

    fun encode(fragments: List<MapCoverageFragment>): String = buildString {
        append(VERSION)
        fragments.forEach { fragment ->
            val b = fragment.bounds
            append('|').append(b.north).append(',').append(b.east)
                .append(',').append(b.south).append(',').append(b.west)
        }
    }

    fun decode(value: String?): List<MapCoverageFragment> {
        if (value == null) return emptyList()
        return runCatching {
            val parts = value.split('|')
            require(parts.firstOrNull() == VERSION)
            parts.drop(1).map { item ->
                val numbers = item.split(',').map(String::toDouble)
                require(numbers.size == 4)
                MapCoverageFragment(
                    MapGeoBounds(
                        north = numbers[0], east = numbers[1],
                        south = numbers[2], west = numbers[3],
                    ),
                )
            }
        }.getOrElse { emptyList() }
    }
}
