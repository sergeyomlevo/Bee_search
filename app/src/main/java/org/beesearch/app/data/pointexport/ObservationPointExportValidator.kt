package org.beesearch.app.data.pointexport

import java.security.MessageDigest
import java.util.UUID
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.WeatherStatus

/** Domain graph and binary consistency checks shared by encode and decode. */
internal object ObservationPointExportValidator {
    private val hashPattern = Regex("[0-9a-f]{64}")

    fun validate(graph: ObservationPointExportGraph, bytes: Map<UUID, ByteArray>) {
        val point = graph.point
        if (point.territoryId != graph.territory.id) invalid("territory context mismatch")
        if (point.observerId != graph.observer.id) invalid("observer context mismatch")
        val allIds = hashSetOf<UUID>()
        fun unique(id: UUID, type: String) {
            if (!allIds.add(id)) invalid("duplicate $type id")
        }
        unique(point.id, "point")
        unique(graph.territory.id, "territory")
        unique(graph.observer.id, "observer")
        graph.beeHistories.forEach { history ->
            val bee = history.bee
            unique(bee.id, "bee")
            if (bee.observationPointId != point.id) invalid("bee belongs to another point")
            val sequences = hashSetOf<Int>()
            history.flightCycles.forEach { cycle ->
                unique(cycle.id, "cycle")
                if (cycle.beeId != bee.id) invalid("flight cycle belongs to another bee")
                if (cycle.sequenceNumber <= 0 || !sequences.add(cycle.sequenceNumber)) invalid("invalid flight cycle sequence")
                if (cycle.returnTime != null && cycle.returnTime < cycle.departureTime) invalid("flight return precedes departure")
                cycle.azimuthDeg?.let { if (!it.isFinite() || it < 0.0 || it >= 360.0) invalid("invalid azimuth") }
            }
        }
        graph.weather?.let { weather ->
            if (weather.observationPointId != point.id) invalid("weather belongs to another point")
            when (weather.status) {
                WeatherStatus.LOADED -> if (
                    weather.temperatureC?.isFinite() != true ||
                    weather.windSpeedMps?.let { it.isFinite() && it >= 0.0 } != true ||
                    weather.windDirectionDeg?.let { it.isFinite() && it >= 0.0 && it < 360.0 } != true ||
                    weather.sampleAt == null || weather.fetchedAt == null || weather.source.isNullOrBlank()
                ) invalid("invalid loaded weather")
                WeatherStatus.PENDING, WeatherStatus.UNAVAILABLE -> if (
                    weather.temperatureC != null || weather.windSpeedMps != null || weather.windDirectionDeg != null ||
                    weather.sampleAt != null || weather.fetchedAt != null || weather.source != null
                ) invalid("unloaded weather contains values")
            }
        }
        val attachmentIds = hashSetOf<UUID>()
        graph.attachments.forEach { attachment ->
            unique(attachment.id, "attachment")
            attachmentIds += attachment.id
            if (attachment.observationPointId != point.id) invalid("attachment belongs to another point")
            if (attachment.type != AttachmentType.PHOTO) invalid("unsupported attachment type")
            if (attachment.relativePath != ObservationAttachmentFileStore.relativePath(point.id, attachment.id)) invalid("attachment storage path mismatch")
            if (attachment.byteSize <= 0 || attachment.byteSize > ObservationPointExportContract.MAX_ENTRY_BYTES) invalid("invalid attachment size")
            if (!attachment.sha256.matches(hashPattern)) invalid("invalid attachment hash")
        }
        if (bytes.keys != attachmentIds) invalid("attachment blob set mismatch")
        graph.attachments.forEach { attachment ->
            val content = bytes.getValue(attachment.id)
            if (content.size.toLong() != attachment.byteSize) throw ObservationPointExportIntegrityError("attachment ${attachment.id} size mismatch")
            if (sha256(content) != attachment.sha256) throw ObservationPointExportIntegrityError("attachment ${attachment.id} SHA-256 mismatch")
        }
    }

    private fun invalid(message: String): Nothing = throw InvalidObservationPointExport(message)
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
