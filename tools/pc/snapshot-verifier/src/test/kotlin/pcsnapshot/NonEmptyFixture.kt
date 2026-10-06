package pcsnapshot

/** A complete, hand-authored graph used to exercise the normative V1 schema. */
object NonEmptyFixture {
    const val territory = "30000000-0000-4000-8000-00000000000a"
    const val observer = "30000000-0000-4000-8000-000000000002"
    const val apiary = "30000000-0000-4000-8000-000000000003"
    const val hollow = "30000000-0000-4000-8000-000000000004"
    const val logHive = "30000000-0000-4000-8000-000000000005"
    const val point = "30000000-0000-4000-8000-000000000006"
    const val bee = "30000000-0000-4000-8000-000000000007"
    const val cycle = "30000000-0000-4000-8000-000000000008"
    const val media = "30000000-0000-4000-8000-000000000009"
    const val zeroMedia = "30000000-0000-4000-8000-000000000010"
    const val attachment = "30000000-0000-4000-8000-000000000011"
    const val incompleteAttachment = "30000000-0000-4000-8000-000000000012"
    const val shaImage = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val shaAttachment = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

    private val records = linkedMapOf(
        "data/territories.jsonl" to """
            {"id":"$territory","code":"T-01","name":"North Meadow","region":"Central","district":"Field","createdAt":1000,"updatedAt":2000}
        """.trimIndent() + "\n",
        "data/observers.jsonl" to """
            {"id":"$observer","code":"O-01","lastName":"Ivanov","firstName":"Ivan","middleName":null,"contact":"field@example.test","createdAt":1000,"updatedAt":2000}
        """.trimIndent() + "\n",
        "data/physical-objects.jsonl" to """
            {"id":"$apiary","territoryId":"$territory","objectType":"APIARY","sequenceNumber":1,"latitude":55.1,"longitude":37.1,"createdAt":1000,"creatorObserverId":"$observer"}
            {"id":"$hollow","territoryId":"$territory","objectType":"HOLLOW","sequenceNumber":1,"latitude":55.2,"longitude":37.2,"createdAt":1000,"creatorObserverId":null}
            {"id":"$logHive","territoryId":"$territory","objectType":"LOG_HIVE","sequenceNumber":1,"latitude":55.3,"longitude":37.3,"createdAt":1000,"creatorObserverId":"$observer"}
        """.trimIndent() + "\n",
        "data/apiaries.jsonl" to """{"physicalObjectId":"$apiary","name":"Main apiary"}\n""",
        "data/hollows.jsonl" to """{"physicalObjectId":"$hollow","tree":null,"entranceHeightCm":null,"entranceAzimuthDeg":null,"outerDiameterCm":null,"internalDiameterCm":null,"notes":null,"name":"Old hollow"}\n""",
        "data/log-hives.jsonl" to """{"physicalObjectId":"$logHive","tree":"Oak","entranceHeightCm":120.0,"entranceAzimuthDeg":0,"outerDiameterCm":50.0,"material":"Pine","internalDiameterCm":40.0,"internalHeightCm":80.0,"notes":"Sound","name":null}\n""",
        "data/physical-object-sequences.jsonl" to """
            {"territoryId":"$territory","objectType":"APIARY","lastIssued":1}
            {"territoryId":"$territory","objectType":"HOLLOW","lastIssued":1}
            {"territoryId":"$territory","objectType":"LOG_HIVE","lastIssued":1}
        """.trimIndent() + "\n",
        "data/physical-object-media.jsonl" to """
            {"id":"$media","physicalObjectId":"$apiary","type":"IMAGE","relativePath":"photos/a.jpg","originalFileName":"a.jpg","mimeType":"image/jpeg","byteSize":10,"sha256":"$shaImage","createdAt":1000}
            {"id":"$zeroMedia","physicalObjectId":"$logHive","type":"VIDEO","relativePath":"videos/unknown","originalFileName":null,"mimeType":null,"byteSize":0,"sha256":"$shaImage","createdAt":1000}
        """.trimIndent() + "\n",
        "data/observation-points.jsonl" to """{"id":"$point","territoryId":"$territory","observerId":"$observer","observationYear":2026,"pointNumber":1,"beePresenceResult":"BEES_FOUND","code":"P-1","latitude":55.15,"longitude":37.15,"gpsLatitude":55.14,"gpsLongitude":37.14,"gpsAccuracyM":3.5,"createdAt":3000,"initialGroupReleaseAt":3000,"completedAt":null,"description":"Open point"}\n""",
        "data/bees.jsonl" to """{"id":"$bee","observationPointId":"$point","markColor":"blue","markPosition":"THORAX","createdAt":3000,"sourceObjectId":"$logHive"}\n""",
        "data/flight-cycles.jsonl" to """{"id":"$cycle","beeId":"$bee","sequenceNumber":1,"departureTime":3000,"returnTime":null,"azimuthDeg":0.0,"azimuthCaptureConsumed":false,"initialGroupLaunch":true,"initialGroupLaunchCorrectionEligible":true,"createdAt":3000,"updatedAt":3000}\n""",
        "data/observation-point-weather.jsonl" to """{"observationPointId":"$point","status":"PENDING","temperatureC":null,"windSpeedMps":null,"windDirectionDeg":null,"sampleAt":null,"fetchedAt":null,"source":null}\n""",
        "data/observation-point-attachments.jsonl" to """
            {"id":"$attachment","observationPointId":"$point","type":"PHOTO","relativePath":"notes/a.jpg","originalFileName":"a.jpg","mimeType":"image/jpeg","byteSize":20,"sha256":"$shaAttachment","createdAt":3000}
            {"id":"$incompleteAttachment","observationPointId":"$point","type":"PHOTO","relativePath":"notes/missing","originalFileName":null,"mimeType":null,"createdAt":3000}
        """.trimIndent() + "\n",
        "settings/map-coverage.jsonl" to """{"territoryId":"$territory","encoded":"v1|55.2,37.3,55.0,37.0"}\n""",
        "references/media-blobs.jsonl" to """
            {"byteSize":10,"canonicalExtension":"jpg","sha256":"$shaImage"}
            {"byteSize":20,"canonicalExtension":"jpg","sha256":"$shaAttachment"}
        """.trimIndent() + "\n"
    ).mapValues { it.value.replace("\\n", "\n").toByteArray(Charsets.UTF_8) }

    fun entries(): LinkedHashMap<String, ByteArray> = LinkedHashMap(records).also {
        it["settings/portable.json"] = "{\"currentObserverId\":\"$observer\",\"currentTerritoryId\":\"$territory\"}".toByteArray()
    }

    fun bytes(createdAt: Long = 0): ByteArray {
        val files = entries()
        files["settings/portable.json"] = "{\"currentObserverId\":\"$observer\",\"currentTerritoryId\":\"$territory\"}".toByteArray()
        files["manifest.json"] = Fixture.manifest(files).let {
            if (createdAt == 0L) it
            else it.toString(Charsets.UTF_8).replace("\"createdAtEpochMs\":0", "\"createdAtEpochMs\":$createdAt").toByteArray()
        }
        return Fixture.zip(files)
    }
}
