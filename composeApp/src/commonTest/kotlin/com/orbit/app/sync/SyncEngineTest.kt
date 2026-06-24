package com.orbit.app.sync

import com.orbit.app.model.Location
import com.orbit.app.model.Schedule
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEngineTest {

    @Test
    fun testVectorClockLagDetection() {
        val myClock = VectorClock(
            mapOf(
                "deviceA" to 10L,
                "deviceB" to 5L
            )
        )
        val peerClock = VectorClock(
            mapOf(
                "deviceA" to 8L,  // Peer lags on A
                "deviceB" to 12L, // Peer is ahead on B
                "deviceC" to 3L   // Peer has edits from C we don't know
            )
        )

        val lags = myClock.findLags(peerClock)

        // We expect to find that we are lagging on deviceB and deviceC
        assertEquals(2, lags.size)
        assertEquals(5L, lags["deviceB"]) // We want changes after our seq 5
        assertEquals(0L, lags["deviceC"]) // We want changes after our seq 0
    }

    @Test
    fun testCalculateDeltaToSend() {
        val peerClock = VectorClock(
            mapOf(
                "deviceA" to 5L,
                "deviceB" to 10L
            )
        )

        val changelogs = listOf(
            ChangelogEntry("c1", "s1", "SCHEDULE", "UPDATE", "data1", 4L, "deviceA", 1000L), // Peer already saw (4 <= 5)
            ChangelogEntry("c2", "s1", "SCHEDULE", "UPDATE", "data2", 6L, "deviceA", 1002L), // Peer is missing (6 > 5)
            ChangelogEntry("c3", "s2", "SCHEDULE", "UPDATE", "data3", 11L, "deviceB", 1003L) // Peer is missing (11 > 10)
        )

        val delta = SyncEngine.calculateDeltaToSend(changelogs, peerClock)

        assertEquals(2, delta.size)
        assertEquals("c2", delta[0].id)
        assertEquals("c3", delta[1].id)
    }

    @Test
    fun testMergeIncomingChangesLastWriteWins() {
        val initialSchedule = Schedule(
            id = "schedule123",
            title = "Initial Title",
            startTime = Instant.parse("2026-06-01T10:00:00Z"),
            endTime = Instant.parse("2026-06-01T11:00:00Z"),
            updatedAt = 1000L // Old timestamp
        )

        val db = mutableMapOf("schedule123" to initialSchedule)

        // Incoming change with LWW timestamp 2000L (newer)
        val updatedSchedule = initialSchedule.copy(title = "Updated Title", updatedAt = 2000L)
        val incoming = listOf(
            ChangelogEntry(
                id = "mut_1",
                entityId = "schedule123",
                entityType = "SCHEDULE",
                mutationType = "UPDATE",
                serializedData = Json.encodeToString(updatedSchedule),
                sequenceNumber = 1L,
                deviceId = "deviceB",
                timestamp = 2000L
            )
        )

        val appliedCount = SyncEngine.mergeIncomingChanges(
            incomingEntries = incoming,
            getCurrentSchedule = { id -> db[id] },
            saveSchedule = { s -> db[s.id] = s },
            deleteSchedule = { id -> db.remove(id) }
        )

        assertEquals(1, appliedCount)
        assertEquals("Updated Title", db["schedule123"]?.title)
        assertEquals(2000L, db["schedule123"]?.updatedAt)
    }

    @Test
    fun testMergeIncomingChangesLagsDiscarded() {
        val initialSchedule = Schedule(
            id = "schedule123",
            title = "Newer Title",
            startTime = Instant.parse("2026-06-01T10:00:00Z"),
            endTime = Instant.parse("2026-06-01T11:00:00Z"),
            updatedAt = 3000L // New timestamp
        )

        val db = mutableMapOf("schedule123" to initialSchedule)

        // Incoming change with LWW timestamp 2000L (older than local 3000L)
        val oldSchedule = initialSchedule.copy(title = "Stale Title", updatedAt = 2000L)
        val incoming = listOf(
            ChangelogEntry(
                id = "mut_1",
                entityId = "schedule123",
                entityType = "SCHEDULE",
                mutationType = "UPDATE",
                serializedData = Json.encodeToString(oldSchedule),
                sequenceNumber = 1L,
                deviceId = "deviceB",
                timestamp = 2000L
            )
        )

        val appliedCount = SyncEngine.mergeIncomingChanges(
            incomingEntries = incoming,
            getCurrentSchedule = { id -> db[id] },
            saveSchedule = { s -> db[s.id] = s },
            deleteSchedule = { id -> db.remove(id) }
        )

        // Expect no changes applied due to LWW rule
        assertEquals(0, appliedCount)
        assertEquals("Newer Title", db["schedule123"]?.title)
    }
}
