package com.orbit.app.sync

import kotlinx.serialization.Serializable

@Serializable
data class ChangelogEntry(
    val id: String,               // Unique ID of this mutation entry (UUID/Hash)
    val entityId: String,         // ID of the target Schedule
    val entityType: String,       // e.g., "SCHEDULE"
    val mutationType: String,     // "INSERT", "UPDATE", "DELETE"
    val serializedData: String,   // JSON representation of the Schedule state
    val sequenceNumber: Long,     // Monotonically increasing sequence from the originating device
    val deviceId: String,         // Device ID that made the change
    val timestamp: Long           // Physical timestamp (for LWW conflict resolution)
)

@Serializable
data class VectorClock(
    val clocks: Map<String, Long> // Maps Device ID -> Last seen sequence number
) {
    /**
     * Compares this clock with another. Returns a list of device IDs where the other clock
     * is ahead of this clock (meaning we need updates from them).
     */
    fun findLags(other: VectorClock): Map<String, Long> {
        val lags = mutableMapOf<String, Long>()
        for ((deviceId, otherSeq) in other.clocks) {
            val mySeq = this.clocks[deviceId] ?: 0L
            if (mySeq < otherSeq) {
                lags[deviceId] = mySeq // Request changes starting after mySeq
            }
        }
        return lags
    }
}

@Serializable
data class SyncHandshake(
    val deviceId: String,
    val vectorClock: VectorClock
)

@Serializable
data class SyncPayload(
    val entries: List<ChangelogEntry>
)
