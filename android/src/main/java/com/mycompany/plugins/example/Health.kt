package com.mycompany.plugins.example

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import java.time.Instant
import kotlin.reflect.KClass

class Health {

    fun echo(value: String?): String? {
        Logger.info("Echo", value ?: "null")
        return value
    }

    suspend fun readSamples(
        healthConnectClient: HealthConnectClient,
        types: List<String>,
        start: Instant,
        end: Instant,
        limit: Int,
        ascending: Boolean,
        includeMetadata: Boolean
    ): List<JSObject> {
        val samples = mutableListOf<JSObject>()

        for (type in types) {
            val recordClass = getRecordType(type)
            if (recordClass != null) {
                val request = ReadRecordsRequest(
                    recordType = recordClass,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = ascending,
                    pageSize = limit
                )
                val response = healthConnectClient.readRecords(request)

                response.records.forEach { record ->
                    mapRecordToJS(record, type, samples, includeMetadata)
                }
            }
        }
        return samples
    }

    suspend fun writeSamples(healthConnectClient: HealthConnectClient, samplesArray: List<JSObject>): Int {
        val records = mutableListOf<androidx.health.connect.client.records.Record>()

        for (sample in samplesArray) {
            val record = mapJSToRecord(sample)
            if (record != null) {
                records.add(record)
            }
        }

        if (records.isNotEmpty()) {
            healthConnectClient.insertRecords(records)
        }
        return records.size
    }

    private fun getRecordType(type: String): KClass<out androidx.health.connect.client.records.Record>? {
        return when (type) {
            "steps" -> StepsRecord::class
            "distance" -> DistanceRecord::class
            "calories" -> TotalCaloriesBurnedRecord::class
            "heart_rate" -> HeartRateRecord::class
            "sleep" -> SleepSessionRecord::class
            else -> null
        }
    }

    private fun mapRecordToJS(
        record: androidx.health.connect.client.records.Record,
        type: String,
        outputList: MutableList<JSObject>,
        includeMetadata: Boolean
    ) {
        fun createSample(valNum: Double, unitStr: String, startDate: Instant, endDate: Instant?): JSObject {
            val js = JSObject()
            js.put("type", type)
            js.put("value", valNum)
            js.put("unit", unitStr)
            js.put("startDate", startDate.toString())
            if (endDate != null) js.put("endDate", endDate.toString())

            val metaId = record.metadata.id
            val metaOrigin = record.metadata.dataOrigin.packageName
            js.put("sourceName", metaOrigin)
            js.put("sourceId", metaId)

            if (includeMetadata) {
                val meta = JSObject()
                meta.put("id", metaId)
                meta.put("dataOrigin", metaOrigin)
                meta.put("lastModifiedTime", record.metadata.lastModifiedTime.toString())
                meta.put("clientRecordId", record.metadata.clientRecordId)
                meta.put("clientRecordVersion", record.metadata.clientRecordVersion)
                js.put("metadata", meta)
            }
            return js
        }

        when (record) {
            is StepsRecord -> outputList.add(createSample(record.count.toDouble(), "count", record.startTime, record.endTime))
            is DistanceRecord -> outputList.add(createSample(record.distance.inMeters, "m", record.startTime, record.endTime))
            is TotalCaloriesBurnedRecord -> outputList.add(createSample(record.energy.inKilocalories, "kcal", record.startTime, record.endTime))
            is SleepSessionRecord -> {
                val duration = java.time.Duration.between(record.startTime, record.endTime).toMinutes()
                outputList.add(createSample(duration.toDouble(), "min", record.startTime, record.endTime))
            }
            is HeartRateRecord -> {
                record.samples.forEach { sample ->
                    val js = JSObject()
                    js.put("type", type)
                    js.put("value", sample.beatsPerMinute.toDouble())
                    js.put("unit", "bpm")
                    js.put("startDate", sample.time.toString())
                    
                    val metaId = record.metadata.id
                    val metaOrigin = record.metadata.dataOrigin.packageName
                    js.put("sourceName", metaOrigin)
                    js.put("sourceId", metaId)

                    if (includeMetadata) {
                        val meta = JSObject()
                        meta.put("id", metaId)
                        meta.put("dataOrigin", metaOrigin)
                        js.put("metadata", meta)
                    }
                    outputList.add(js)
                }
            }
        }
    }

    private fun mapJSToRecord(js: JSObject): androidx.health.connect.client.records.Record? {
        val type = js.getString("type")
        val value = js.getDouble("value")
        val startDateStr = js.getString("startDate")
        val endDateStr = js.getString("endDate")

        if (type == null || startDateStr == null) return null

        val start = Instant.parse(startDateStr)
        val end = if (endDateStr != null) Instant.parse(endDateStr) else start.plusSeconds(1)

        val zoneId = java.time.ZoneId.systemDefault()
        val zoneOffset = zoneId.rules.getOffset(start)

        return when (type) {
            "steps" -> StepsRecord(
                startTime = start,
                startZoneOffset = zoneOffset,
                endTime = end,
                endZoneOffset = zoneOffset,
                count = value.toLong(),
                metadata = Metadata.manualEntry()
            )
            "distance" -> DistanceRecord(
                startTime = start,
                startZoneOffset = zoneOffset,
                endTime = end,
                endZoneOffset = zoneOffset,
                distance = Length.meters(value),
                metadata = Metadata.manualEntry()
            )
            "calories" -> TotalCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = zoneOffset,
                endTime = end,
                endZoneOffset = zoneOffset,
                energy = Energy.kilocalories(value),
                metadata = Metadata.manualEntry()
            )
            "sleep" -> SleepSessionRecord(
                startTime = start,
                startZoneOffset = zoneOffset,
                endTime = end,
                endZoneOffset = zoneOffset,
                title = null,
                notes = null,
                metadata = Metadata.manualEntry()
            )
            "heart_rate" -> HeartRateRecord(
                startTime = start,
                startZoneOffset = zoneOffset,
                endTime = end,
                endZoneOffset = zoneOffset,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = start,
                        beatsPerMinute = value.toLong()
                    )
                ),
                metadata = Metadata.manualEntry()
            )
            else -> null
        }
    }
}
