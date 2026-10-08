package org.beesearch.app.data.backupsnapshot

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObservationPointWeatherEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.WeatherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The complete V1 weather state matrix and one-row-per-point invariant. */
class SnapshotWeatherMatrixTest {
    private val at = Instant.parse("2026-01-01T00:00:00Z")
    private val territory = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val observer = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val point = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val secondPoint = UUID.fromString("44444444-4444-4444-8444-444444444444")

    private fun point(id: UUID, number: Int = 1, completedAt: Instant? = null, presence: BeePresenceResult? = null) = ObservationPointEntity(
        java.time.LocalDate.of(2026, 1, 1), id, territory, observer, 2026, number, presence, null,
        55.7, 37.6, null, null, null, at, null, completedAt,
    )

    private fun graph(
        weather: List<ObservationPointWeatherEntity>,
        points: List<ObservationPointEntity> = listOf(point(point)),
    ) = Graph(
        territories = listOf(TerritoryEntity(territory, "T", "Territory", "R", "D", at, at)),
        observers = listOf(ObserverEntity(observer, "O", "Last", "First", null, null, at, at)),
        points = points, bees = emptyList(), cycles = emptyList(), weather = weather,
    )

    private fun weather(
        status: WeatherStatus,
        temperature: Double? = null,
        speed: Double? = null,
        direction: Double? = null,
        sample: Instant? = null,
        fetched: Instant? = null,
        source: String? = null,
        id: UUID = point,
    ) = ObservationPointWeatherEntity(id, status, temperature, speed, direction, sample, fetched, source)

    private fun settings() = PortableSettingsSnapshot(territory, observer, emptyMap())

    private fun expectLogical(messagePart: String = "weather", block: () -> Unit) {
        val error = org.junit.Assert.assertThrows(SnapshotException::class.java, block)
        assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
        assertTrue("expected weather-specific message, got ${error.message}", error.message.orEmpty().contains(messagePart, ignoreCase = true))
    }

    @Test
    fun `pending and unavailable require six null payload fields and loaded accepts valid boundaries`() {
        listOf(WeatherStatus.PENDING, WeatherStatus.UNAVAILABLE).forEach { status ->
            SnapshotDomainCodec.encode(graph(listOf(weather(status))), settings())
        }
        SnapshotDomainCodec.encode(
            graph(listOf(weather(WeatherStatus.LOADED, 0.0, 0.0, 0.0, at, at, "field"))), settings(),
        )
        SnapshotDomainCodec.encode(
            graph(listOf(weather(WeatherStatus.LOADED, Double.MAX_VALUE, Double.MAX_VALUE, 359.999999, at, at, "field"))), settings(),
        )
        expectLogical { SnapshotDomainCodec.encode(graph(listOf(weather(WeatherStatus.UNAVAILABLE, source = "none"))), settings()) }
    }

    @Test
    fun `encode rejects every unloaded payload field and every missing loaded field`() {
        val unloaded = listOf<(WeatherStatus) -> ObservationPointWeatherEntity>(
            { status -> weather(status, temperature = 1.0) },
            { status -> weather(status, speed = 1.0) },
            { status -> weather(status, direction = 1.0) },
            { status -> weather(status, sample = at) },
            { status -> weather(status, fetched = at) },
            { status -> weather(status, source = "provider") },
        ).flatMap { factory -> listOf(WeatherStatus.PENDING, WeatherStatus.UNAVAILABLE).map(factory) }
        unloaded.forEach { candidate -> expectLogical { SnapshotDomainCodec.encode(graph(listOf(candidate)), settings()) } }

        val loaded = listOf(
            weather(WeatherStatus.LOADED, speed = 1.0, direction = 1.0, sample = at, fetched = at, source = "provider"),
            weather(WeatherStatus.LOADED, temperature = 1.0, direction = 1.0, sample = at, fetched = at, source = "provider"),
            weather(WeatherStatus.LOADED, temperature = 1.0, speed = 1.0, sample = at, fetched = at, source = "provider"),
            weather(WeatherStatus.LOADED, temperature = 1.0, speed = 1.0, direction = 1.0, fetched = at, source = "provider"),
            weather(WeatherStatus.LOADED, temperature = 1.0, speed = 1.0, direction = 1.0, sample = at, source = "provider"),
            weather(WeatherStatus.LOADED, temperature = 1.0, speed = 1.0, direction = 1.0, sample = at, fetched = at),
            weather(WeatherStatus.LOADED, temperature = 1.0, speed = 1.0, direction = 1.0, sample = at, fetched = at, source = " "),
        )
        loaded.forEach { candidate -> expectLogical { SnapshotDomainCodec.encode(graph(listOf(candidate)), settings()) } }
    }

    @Test
    fun `encode rejects nonfinite and out of range loaded weather`() {
        listOf(
            weather(WeatherStatus.LOADED, Double.NaN, 1.0, 1.0, at, at, "provider"),
            weather(WeatherStatus.LOADED, Double.POSITIVE_INFINITY, 1.0, 1.0, at, at, "provider"),
            weather(WeatherStatus.LOADED, 1.0, Double.NaN, 1.0, at, at, "provider"),
            weather(WeatherStatus.LOADED, 1.0, Double.NEGATIVE_INFINITY, 1.0, at, at, "provider"),
            weather(WeatherStatus.LOADED, 1.0, -0.0001, 1.0, at, at, "provider"),
            weather(WeatherStatus.LOADED, 1.0, 1.0, Double.NaN, at, at, "provider"),
            weather(WeatherStatus.LOADED, 1.0, 1.0, Double.POSITIVE_INFINITY, at, at, "provider"),
            weather(WeatherStatus.LOADED, 1.0, 1.0, -0.0001, at, at, "provider"),
            weather(WeatherStatus.LOADED, 1.0, 1.0, 360.0, at, at, "provider"),
        ).forEach { candidate -> expectLogical { SnapshotDomainCodec.encode(graph(listOf(candidate)), settings()) } }
    }

    @Test
    fun `encode requires exactly one weather row per observation point`() {
        expectLogical {
            SnapshotDomainCodec.encode(
                graph(listOf(weather(WeatherStatus.PENDING)), listOf(point(point), point(secondPoint, 2, at, BeePresenceResult.NO_BEES_FOUND))), settings(),
            )
        }
        expectLogical {
            SnapshotDomainCodec.encode(graph(listOf(weather(WeatherStatus.PENDING), weather(WeatherStatus.UNAVAILABLE))), settings())
        }
        expectLogical {
            SnapshotDomainCodec.encode(graph(listOf(weather(WeatherStatus.PENDING, id = secondPoint))), settings())
        }
    }

    @Test
    fun `reader rejects mutated weather rows with typed logical weather errors`() {
        val valid = SnapshotDomainCodec.encode(
            graph(listOf(weather(WeatherStatus.LOADED, 20.0, 2.0, 180.0, at, at, "field"))), settings(),
        )
        val mutations = listOf(
            Triple("\"status\":\"LOADED\",\"temperatureC\":20.0", "\"status\":\"PENDING\",\"temperatureC\":1.0", "unloaded payload"),
            Triple("\"temperatureC\":20.0", "\"temperatureC\":null", "loaded field"),
            Triple("\"windSpeedMps\":2.0", "\"windSpeedMps\":null", "loaded field"),
            Triple("\"windDirectionDeg\":180.0", "\"windDirectionDeg\":null", "loaded field"),
            Triple("\"sampleAt\":${at.toEpochMilli()}", "\"sampleAt\":null", "loaded field"),
            Triple("\"fetchedAt\":${at.toEpochMilli()}", "\"fetchedAt\":null", "loaded field"),
            Triple("\"source\":\"field\"", "\"source\":null", "loaded field"),
            Triple("\"windSpeedMps\":2.0", "\"windSpeedMps\":-1.0", "wind speed"),
            Triple("\"windDirectionDeg\":180.0", "\"windDirectionDeg\":360.0", "wind direction"),
            Triple("\"source\":\"field\"", "\"source\":\"\"", "source"),
        )
        mutations.forEach { (from, to, _) ->
            val original = valid.records.getValue("data/observation-point-weather.jsonl").single()
            val mutated = original.replace(from, to)
            assertTrue("mutation did not change weather record", original != mutated)
            expectReaderLogical(valid.copy(records = valid.records + ("data/observation-point-weather.jsonl" to listOf(mutated))), "weather")
        }

        val unloadedMutations = listOf(
            "\"temperatureC\":null" to "\"temperatureC\":1.0",
            "\"windSpeedMps\":null" to "\"windSpeedMps\":1.0",
            "\"windDirectionDeg\":null" to "\"windDirectionDeg\":1.0",
            "\"sampleAt\":null" to "\"sampleAt\":${at.toEpochMilli()}",
            "\"fetchedAt\":null" to "\"fetchedAt\":${at.toEpochMilli()}",
            "\"source\":null" to "\"source\":\"provider\"",
        )
        listOf(WeatherStatus.PENDING, WeatherStatus.UNAVAILABLE).forEach { status ->
            val candidate = SnapshotDomainCodec.encode(graph(listOf(weather(status))), settings())
            val original = candidate.records.getValue("data/observation-point-weather.jsonl").single()
            unloadedMutations.forEach { (from, to) ->
                val mutated = original.replace(from, to)
                assertTrue("unloaded mutation did not change weather record", original != mutated)
                expectReaderLogical(candidate.copy(records = candidate.records + ("data/observation-point-weather.jsonl" to listOf(mutated))), "weather")
            }
        }

        listOf(WeatherStatus.PENDING, WeatherStatus.UNAVAILABLE).forEach { status ->
            val candidate = SnapshotDomainCodec.encode(graph(listOf(weather(status))), settings())
            val root = Files.createTempDirectory("snapshot-weather-valid").toFile()
            try {
                SnapshotArchive().build(File(root, "candidate.zip"), SnapshotIdentity(UUID.randomUUID(), territory, "Dev", 0), candidate)
            } finally {
                root.deleteRecursively()
            }
        }
        val unavailable = SnapshotDomainCodec.encode(graph(listOf(weather(WeatherStatus.UNAVAILABLE))), settings())
        val source = unavailable.records.getValue("data/observation-point-weather.jsonl").single()
        val sourceMutation = source.replace("\"source\":null", "\"source\":\"none\"")
        assertTrue(source != sourceMutation)
        expectReaderLogical(unavailable.copy(records = unavailable.records + ("data/observation-point-weather.jsonl" to listOf(sourceMutation))), "weather")
    }

    @Test
    fun `reader rejects duplicate missing and dangling weather rows`() {
        val one = SnapshotDomainCodec.encode(graph(listOf(weather(WeatherStatus.PENDING))), settings())
        val weatherPath = "data/observation-point-weather.jsonl"
        expectReaderLogical(one.copy(records = one.records + (weatherPath to listOf(one.records.getValue(weatherPath).single(), one.records.getValue(weatherPath).single()))), "weather")
        val twoPointsFull = SnapshotDomainCodec.encode(
            graph(listOf(weather(WeatherStatus.PENDING), weather(WeatherStatus.UNAVAILABLE, id = secondPoint)), listOf(point(point), point(secondPoint, 2, at, BeePresenceResult.NO_BEES_FOUND))),
            settings(),
        )
        expectReaderLogical(twoPointsFull.copy(records = twoPointsFull.records + (weatherPath to twoPointsFull.records.getValue(weatherPath).take(1))), "weather")
        val dangling = one.copy(records = one.records + (weatherPath to listOf(one.records.getValue(weatherPath).single().replace(point.toString(), secondPoint.toString()))))
        expectReaderLogical(dangling, "weather")
    }

    private fun expectReaderLogical(entries: SnapshotDomainEntries, messagePart: String) {
        val root = Files.createTempDirectory("snapshot-weather-reader").toFile()
        try {
            val error = org.junit.Assert.assertThrows(SnapshotException::class.java) {
                SnapshotArchive().build(
                    File(root, "candidate.zip"),
                    SnapshotIdentity(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), territory, "Dev", 0),
                    entries,
                )
            }
            assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
            assertTrue("expected $messagePart in ${error.message}", error.message.orEmpty().contains(messagePart, ignoreCase = true))
        } finally {
            root.deleteRecursively()
        }
    }
}
