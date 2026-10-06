package pcsnapshot

import com.google.gson.JsonObject

/** Normative graph/range rules, resolved only within each named collection. */
internal object GraphRules {
    fun validate(graph: Map<String, List<JsonObject>>) {
        fun rows(name: String) = graph.getValue("data/$name.jsonl")
        fun ids(name: String, key: String = "id") = rows(name).associateBy { s(it, key) }
        val territories = ids("territories"); val observers = ids("observers")
        val objects = ids("physical-objects"); val points = ids("observation-points"); val bees = ids("bees")
        val beeCounts = bees.values.groupingBy { s(it,"observationPointId") }.eachCount()
        val sequenceMaxima = objects.values.groupBy { s(it,"territoryId") to s(it,"objectType") }
            .mapValues { (_,group) -> group.maxOf { l(it,"sequenceNumber") } }
        fun fk(row: JsonObject, field: String, target: Map<String, JsonObject>, scope: String) {
            RecordSchema.nullableString(row, field)?.let { require(it in target, scope, field) }
        }
        for (name in listOf("territories", "observers")) {
            unique(rows(name), name) { listOf(s(it, "code")) }
            rows(name).forEach { row ->
                val fields = if (name == "territories") listOf("code", "name", "region", "district") else listOf("code", "lastName", "firstName")
                fields.forEach { require(RecordSchema.nonblank(s(row, it)), name, it) }
                require(l(row, "updatedAt") >= l(row, "createdAt"), name, "updatedAt")
            }
        }
        unique(rows("physical-objects"), "physical-objects") { listOf(s(it,"territoryId"), s(it,"objectType"), l(it,"sequenceNumber").toString()) }
        rows("physical-objects").forEach {
            fk(it,"territoryId",territories,"physical-objects"); fk(it,"creatorObserverId",observers,"physical-objects")
            require(l(it,"sequenceNumber") >= 1,"physical-objects","sequenceNumber"); coordinates(it,"physical-objects")
        }
        val subtypes = listOf("apiaries" to "APIARY", "hollows" to "HOLLOW", "log-hives" to "LOG_HIVE")
        val owners = mutableMapOf<String, Int>()
        for ((name, type) in subtypes) rows(name).forEach {
            val id = s(it,"physicalObjectId")
            require(objects[id]?.let { o -> s(o,"objectType") == type } == true,name,"physicalObjectId")
            owners[id] = (owners[id] ?: 0) + 1
            if (name != "apiaries") properties(it,name)
        }
        objects.keys.forEach { require(owners[it] == 1,"physical-objects","subtype") }
        rows("physical-object-sequences").forEach { row ->
            fk(row,"territoryId",territories,"physical-object-sequences")
            val maximum = sequenceMaxima[s(row,"territoryId") to s(row,"objectType")] ?: 0
            require(l(row,"lastIssued") >= maximum && l(row,"lastIssued") >= 0,"physical-object-sequences","lastIssued")
        }
        rows("physical-object-media").forEach { fk(it,"physicalObjectId",objects,"physical-object-media") }
        unique(rows("observation-points"),"observation-points") { listOf(s(it,"territoryId"), l(it,"observationYear").toString(),s(it,"observerId"),l(it,"pointNumber").toString()) }
        require(points.values.count { nlong(it,"completedAt") == null } <= 1,"observation-points","active cardinality")
        points.values.forEach { row ->
            fk(row,"territoryId",territories,"observation-points"); fk(row,"observerId",observers,"observation-points")
            require(l(row,"observationYear") > 0 && l(row,"pointNumber") > 0,"observation-points","number")
            coordinates(row,"observation-points")
            RecordSchema.nullableDouble(row,"gpsLatitude")?.let { require(it in -90.0..90.0,"observation-points","gpsLatitude") }
            RecordSchema.nullableDouble(row,"gpsLongitude")?.let { require(it in -180.0..180.0,"observation-points","gpsLongitude") }
            RecordSchema.nullableDouble(row,"gpsAccuracyM")?.let { require(it >= 0,"observation-points","gpsAccuracyM") }
            for (field in listOf("initialGroupReleaseAt","completedAt")) nlong(row,field)?.let { require(it >= l(row,"createdAt"),"observation-points",field) }
            val count = beeCounts[s(row,"id")] ?: 0
            val result = RecordSchema.nullableString(row,"beePresenceResult")
            require(count <= 10,"bees","point cardinality")
            require(nlong(row,"completedAt") == null || result != null,"observation-points","beePresenceResult")
            require(result != "NO_BEES_FOUND" || (count == 0 && nlong(row,"completedAt") != null),"observation-points","beePresenceResult")
            require(result != "BEES_FOUND" || count > 0,"observation-points","beePresenceResult")
            require(count == 0 || result == "BEES_FOUND","observation-points","beePresenceResult")
        }
        unique(rows("bees"),"bees") {
            val position = when (s(it,"markPosition")) { "NONE" -> "THORAX"; "RIGHT_WING" -> "ABDOMEN"; else -> s(it,"markPosition") }
            listOf(s(it,"observationPointId"),s(it,"markColor"),position)
        }
        bees.values.forEach { fk(it,"observationPointId",points,"bees"); fk(it,"sourceObjectId",objects,"bees"); require(RecordSchema.nonblank(s(it,"markColor")),"bees","markColor") }
        val cycles = rows("flight-cycles")
        unique(cycles,"flight-cycles") { listOf(s(it,"beeId"),l(it,"sequenceNumber").toString()) }
        cycles.forEach { row ->
            fk(row,"beeId",bees,"flight-cycles")
            require(l(row,"sequenceNumber") >= 1,"flight-cycles","sequenceNumber")
            nlong(row,"returnTime")?.let { require(it >= l(row,"departureTime"),"flight-cycles","returnTime") }
            RecordSchema.nullableDouble(row,"azimuthDeg")?.let { require(it >= 0 && it < 360,"flight-cycles","azimuthDeg") }
            require(l(row,"updatedAt") >= l(row,"createdAt"),"flight-cycles","updatedAt")
            if (RecordSchema.bool(row,"initialGroupLaunch")) {
                val point = points.getValue(s(bees.getValue(s(row,"beeId")),"observationPointId"))
                require(l(row,"sequenceNumber") == 1L && nlong(point,"initialGroupReleaseAt") == l(row,"departureTime"),"flight-cycles","initialGroupLaunch")
            }
            if (RecordSchema.bool(row,"initialGroupLaunchCorrectionEligible")) require(l(row,"sequenceNumber") == 1L && nlong(row,"returnTime") == null,"flight-cycles","initialGroupLaunchCorrectionEligible")
        }
        cycles.groupBy { s(it,"beeId") }.values.forEach { group ->
            val sorted = group.sortedBy { l(it,"sequenceNumber") }
            sorted.forEachIndexed { i,row -> require(l(row,"sequenceNumber") == i.toLong()+1,"flight-cycles","sequence gap") }
            require(sorted.count { nlong(it,"returnTime") == null } <= 1 && sorted.dropLast(1).none { nlong(it,"returnTime") == null },"flight-cycles","open cycle")
        }
        val weather = ids("observation-point-weather","observationPointId")
        require(weather.keys == points.keys,"observation-point-weather","cardinality")
        weather.values.forEach { row ->
            val payload = listOf("temperatureC","windSpeedMps","windDirectionDeg","sampleAt","fetchedAt","source")
            if (s(row,"status") == "LOADED") {
                require(payload.all { !row.get(it).isJsonNull },"observation-point-weather","LOADED payload")
                require(RecordSchema.double(row,"windSpeedMps") >= 0,"observation-point-weather","windSpeedMps")
                val direction = RecordSchema.double(row,"windDirectionDeg")
                require(direction >= 0 && direction < 360,"observation-point-weather","windDirectionDeg")
                require(RecordSchema.nonblank(s(row,"source")),"observation-point-weather","source")
            } else require(payload.all { row.get(it).isJsonNull },"observation-point-weather","empty payload")
        }
        rows("observation-point-attachments").forEach { fk(it,"observationPointId",points,"observation-point-attachments") }
    }

    private fun properties(row: JsonObject, scope: String) {
        val core = mutableListOf("tree","entranceHeightCm","entranceAzimuthDeg","outerDiameterCm")
        if (scope == "log-hives") core += listOf("material","internalDiameterCm","internalHeightCm")
        if (core.all { row.get(it).isJsonNull }) {
            require(row.get("notes").isJsonNull && row.get("internalDiameterCm").isJsonNull,scope,"historical properties")
            return
        }
        require(core.all { !row.get(it).isJsonNull },scope,"partial properties")
        for (key in listOf("tree","material").filter { row.has(it) }) require(RecordSchema.nonblank(s(row,key)) && RecordSchema.noEdgeWhitespace(s(row,key)),scope,key)
        for (key in listOf("entranceHeightCm","outerDiameterCm","internalDiameterCm","internalHeightCm").filter { row.has(it) }) RecordSchema.nullableDouble(row,key)?.let { require(it > 0,scope,key) }
        require(l(row,"entranceAzimuthDeg") in 0..359,scope,"entranceAzimuthDeg")
        RecordSchema.nullableString(row,"notes")?.let { require(it.isNotEmpty() && RecordSchema.noEdgeWhitespace(it),scope,"notes") }
    }
    private fun coordinates(row: JsonObject, scope: String) {
        require(RecordSchema.double(row,"latitude") in -90.0..90.0 && RecordSchema.double(row,"longitude") in -180.0..180.0,scope,"coordinates")
    }
    private fun unique(rows: List<JsonObject>, scope: String, key: (JsonObject)->List<String>) {
        val seen = mutableSetOf<List<String>>()
        rows.forEach { require(seen.add(key(it)),scope,"duplicate logical key") }
    }
    private fun require(valid: Boolean, scope: String, rule: String) { if (!valid) reject("LOGICAL_STATE_INCONSISTENT","data/$scope.jsonl",rule) }
    private fun s(row: JsonObject,key: String) = RecordSchema.string(row,key)
    private fun l(row: JsonObject,key: String) = RecordSchema.long(row,key)
    private fun nlong(row: JsonObject,key: String) = RecordSchema.nullableLong(row,key)
}
