package com.orbit.app.sync

import com.orbit.app.model.Schedule
import kotlinx.serialization.json.Json

enum class ConnectionType {
    NONE,
    MDNS_LAN,
    BLE_PROXIMITY,
    WIFI_DIRECT_HIGH_SPEED,
    CIRCUIT_RELAY_WAN
}

data class PeerStatus(
    val deviceId: String,
    val deviceName: String,
    val connectionType: ConnectionType,
    val isConnected: Boolean,
    val syncProgressPercent: Float
)

object SyncEngine {

    /**
     * Determines which changelog entries the peer is missing.
     * Compares the local changelog entries against the peer's [VectorClock].
     */
    fun calculateDeltaToSend(
        localChangelog: List<ChangelogEntry>,
        peerClock: VectorClock
    ): List<ChangelogEntry> {
        return localChangelog.filter { entry ->
            val peerLastSeen = peerClock.clocks[entry.deviceId] ?: 0L
            entry.sequenceNumber > peerLastSeen
        }.sortedBy { it.sequenceNumber }
    }

    /**
     * Merges incoming changelog entries from a peer.
     * Uses Last-Write-Wins (LWW) based on timestamps for conflict resolution.
     * Calls [onApplied] for each schedule that was successfully updated/created.
     */
    fun mergeIncomingChanges(
        incomingEntries: List<ChangelogEntry>,
        getCurrentSchedule: (String) -> Schedule?,
        saveSchedule: (Schedule) -> Unit,
        deleteSchedule: (String) -> Unit
    ): Int {
        var appliedCount = 0

        // Group entries by entityId to only apply the latest one if multiple updates exist
        val latestEntries = incomingEntries
            .groupBy { it.entityId }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.timestamp }!! }

        for ((scheduleId, incomingEntry) in latestEntries) {
            val localSchedule = getCurrentSchedule(scheduleId)
            
            // Conflict resolution: Last-Write-Wins (LWW)
            val shouldApply = localSchedule == null || incomingEntry.timestamp > localSchedule.updatedAt

            if (shouldApply) {
                if (incomingEntry.mutationType == "DELETE") {
                    deleteSchedule(scheduleId)
                } else {
                    try {
                        val parsedSchedule = Json.decodeFromString<Schedule>(incomingEntry.serializedData)
                        saveSchedule(parsedSchedule.copy(updatedAt = incomingEntry.timestamp))
                    } catch (e: Exception) {
                        // Skip corrupted/unparseable items
                        continue
                    }
                }
                appliedCount++
            }
        }

        return appliedCount
    }
}
