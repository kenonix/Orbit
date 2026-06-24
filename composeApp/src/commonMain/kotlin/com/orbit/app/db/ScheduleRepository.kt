package com.orbit.app.db

import com.orbit.app.model.Location
import com.orbit.app.model.Participant
import com.orbit.app.model.RecurrenceRule
import com.orbit.app.model.Schedule
import com.orbit.app.sync.ChangelogEntry
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.orbit.app.db.OrbitDatabase
import com.orbit.app.db.ScheduleEntity

class ScheduleRepository(driver: app.cash.sqldelight.db.SqlDriver) {
    private val database = OrbitDatabase(driver)
    private val queries = database.orbitDatabaseQueries

    fun getAllSchedules(): List<Schedule> {
        return queries.selectAllSchedules().executeAsList().map { entity ->
            mapToDomain(entity)
        }
    }

    fun getScheduleById(id: String): Schedule? {
        return queries.selectScheduleById(id).executeAsOneOrNull()?.let { mapToDomain(it) }
    }

    fun upsertSchedule(schedule: Schedule, deviceId: String) {
        val latestSeq = queries.selectLatestSequenceForDevice(deviceId).executeAsOneOrNull()?.maxSeq ?: 0L
        val nextSeq = latestSeq + 1
        val now = Clock.System.now().toEpochMilliseconds()
        
        val locationJson = schedule.location?.let { Json.encodeToString(it) }
        val recurrenceJson = schedule.recurrenceRule?.let { Json.encodeToString(it) }
        val participantsJson = Json.encodeToString(schedule.participants)

        queries.transaction {
            queries.insertSchedule(
                id = schedule.id,
                title = schedule.title,
                startTime = schedule.startTime.toString(),
                endTime = schedule.endTime.toString(),
                locationJson = locationJson,
                recurrenceJson = recurrenceJson,
                participantsJson = participantsJson,
                updatedAt = now,
                isDeleted = if (schedule.isDeleted) 1 else 0
            )

            val changelogId = "${schedule.id}_$nextSeq"
            queries.insertChangelog(
                id = changelogId,
                entityId = schedule.id,
                entityType = "SCHEDULE",
                mutationType = "UPDATE",
                serializedData = Json.encodeToString(schedule.copy(updatedAt = now)),
                sequenceNumber = nextSeq,
                deviceId = deviceId,
                timestamp = now
            )
        }
    }

    fun deleteSchedule(id: String, deviceId: String) {
        val latestSeq = queries.selectLatestSequenceForDevice(deviceId).executeAsOneOrNull()?.maxSeq ?: 0L
        val nextSeq = latestSeq + 1
        val now = Clock.System.now().toEpochMilliseconds()

        queries.transaction {
            queries.deleteScheduleSoft(
                updatedAt = now,
                id = id
            )

            val changelogId = "${id}_$nextSeq"
            queries.insertChangelog(
                id = changelogId,
                entityId = id,
                entityType = "SCHEDULE",
                mutationType = "DELETE",
                serializedData = "",
                sequenceNumber = nextSeq,
                deviceId = deviceId,
                timestamp = now
            )
        }
    }

    fun getChangelogsSince(seq: Long): List<ChangelogEntry> {
        return queries.selectChangelogsSince(seq).executeAsList().map { entity ->
            ChangelogEntry(
                id = entity.id,
                entityId = entity.entityId,
                entityType = entity.entityType,
                mutationType = entity.mutationType,
                serializedData = entity.serializedData,
                sequenceNumber = entity.sequenceNumber,
                deviceId = entity.deviceId,
                timestamp = entity.timestamp
            )
        }
    }

    fun getLatestSequenceForDevice(deviceId: String): Long {
        return queries.selectLatestSequenceForDevice(deviceId).executeAsOneOrNull()?.maxSeq ?: 0L
    }

    fun applyIncomingChangelog(entry: ChangelogEntry) {
        queries.transaction {
            if (entry.mutationType == "DELETE") {
                queries.deleteScheduleSoft(entry.timestamp, entry.entityId)
            } else {
                val schedule = Json.decodeFromString<Schedule>(entry.serializedData)
                val locationJson = schedule.location?.let { Json.encodeToString(it) }
                val recurrenceJson = schedule.recurrenceRule?.let { Json.encodeToString(it) }
                val participantsJson = Json.encodeToString(schedule.participants)

                queries.insertSchedule(
                    id = schedule.id,
                    title = schedule.title,
                    startTime = schedule.startTime.toString(),
                    endTime = schedule.endTime.toString(),
                    locationJson = locationJson,
                    recurrenceJson = recurrenceJson,
                    participantsJson = participantsJson,
                    updatedAt = entry.timestamp,
                    isDeleted = 0
                )
            }

            // Record this changelog locally to prevent sync loops
            queries.insertChangelog(
                id = entry.id,
                entityId = entry.entityId,
                entityType = entry.entityType,
                mutationType = entry.mutationType,
                serializedData = entry.serializedData,
                sequenceNumber = entry.sequenceNumber,
                deviceId = entry.deviceId,
                timestamp = entry.timestamp
            )
        }
    }

    private fun mapToDomain(entity: ScheduleEntity): Schedule {
        val location = entity.locationJson?.let { Json.decodeFromString<Location>(it) }
        val recurrenceRule = entity.recurrenceJson?.let { Json.decodeFromString<RecurrenceRule>(it) }
        val participants = entity.participantsJson?.let { Json.decodeFromString<List<Participant>>(it) } ?: emptyList()

        return Schedule(
            id = entity.id,
            title = entity.title,
            startTime = Instant.parse(entity.startTime),
            endTime = Instant.parse(entity.endTime),
            location = location,
            recurrenceRule = recurrenceRule,
            participants = participants,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted != 0L
        )
    }
}
