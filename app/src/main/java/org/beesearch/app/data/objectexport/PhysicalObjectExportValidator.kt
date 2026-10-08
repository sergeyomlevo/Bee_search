package org.beesearch.app.data.objectexport

import java.security.MessageDigest
import java.util.UUID
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.model.PhysicalObjectType

/**
 * Domain and binary consistency checks shared by encode and decode.
 *
 * The rules are the object-owned boundary expressed as executable statements: a package holds one
 * object of a supported type, its own media, and a context snapshot that agrees with the object. A
 * media entry is accepted only if its stored path is the deterministic path of that object and its
 * bytes match the persisted size and SHA-256.
 */
internal object PhysicalObjectExportValidator {
    private val hashPattern = Regex("[0-9a-f]{64}")

    fun validate(
        graph: PhysicalObjectExportGraph,
        mediaBytes: Map<UUID, ByteArray>,
        allowFixationDate: Boolean = true,
    ) {
        validateSupportedType(graph.type)
        if (!allowFixationDate && graph.fixationDate != null) {
            throw LegacyPhysicalObjectExportNotRepresentable(
                "legacy physical-object export cannot represent fixationDate",
            )
        }
        validateProperties(graph)
        if (graph.territoryId != graph.territory.id) invalid("territory context mismatch")
        if (graph.creatorObserverId == null) {
            if (graph.observer != null) invalid("observer snapshot without a creator")
        } else {
            if (graph.observer == null) invalid("missing observer snapshot")
            if (graph.observer.id != graph.creatorObserverId) invalid("observer context mismatch")
        }
        if (graph.sequenceNumber <= 0) invalid("invalid sequence number")
        if (!graph.latitude.isFinite() || graph.latitude !in -90.0..90.0) invalid("invalid latitude")
        if (!graph.longitude.isFinite() || graph.longitude !in -180.0..180.0) invalid("invalid longitude")

        val mediaIds = hashSetOf<UUID>()
        val mediaPaths = hashSetOf<String>()
        graph.media.forEach { media ->
            if (!mediaIds.add(media.id)) invalid("duplicate media id")
            if (!mediaPaths.add(media.relativePath)) invalid("duplicate media path")
            if (media.physicalObjectId != graph.id) invalid("media belongs to another object")
            if (media.relativePath != PhysicalObjectMediaFileStore.relativePath(graph.id, media.id)) {
                invalid("media storage path mismatch")
            }
            if (media.byteSize <= 0 || media.byteSize > PhysicalObjectExportContract.MAX_ENTRY_BYTES) {
                invalid("invalid media size")
            }
            if (!media.sha256.matches(hashPattern)) invalid("invalid media hash")
        }
        if (mediaBytes.keys != mediaIds) invalid("media blob set mismatch")
        graph.media.forEach { media ->
            val content = mediaBytes.getValue(media.id)
            if (content.size.toLong() != media.byteSize) {
                throw PhysicalObjectExportIntegrityError("media ${media.id} size mismatch")
            }
            if (sha256(content) != media.sha256) {
                throw PhysicalObjectExportIntegrityError("media ${media.id} SHA-256 mismatch")
            }
        }
    }

    /**
     * Apiary is a real physical type, but it has no creation, card or media lifecycle yet, so the V1
     * profile refuses it instead of writing a package that no screen can produce or explain.
     */
    fun validateSupportedType(type: PhysicalObjectType) {
        if (type != PhysicalObjectType.HOLLOW && type != PhysicalObjectType.LOG_HIVE) {
            throw UnsupportedPhysicalObjectExportType("unsupported physical object type")
        }
    }

    private fun validateProperties(graph: PhysicalObjectExportGraph) {
        when (graph.type) {
            PhysicalObjectType.HOLLOW -> if (graph.properties is PhysicalObjectExportProperties.LogHive) {
                invalid("properties do not match the object type")
            }

            PhysicalObjectType.LOG_HIVE -> if (graph.properties is PhysicalObjectExportProperties.Hollow) {
                invalid("properties do not match the object type")
            }

            PhysicalObjectType.APIARY -> throw UnsupportedPhysicalObjectExportType(
                "unsupported physical object type",
            )
        }
    }

    private fun invalid(message: String): Nothing = throw InvalidPhysicalObjectExport(message)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
