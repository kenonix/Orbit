package com.orbit.app.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Schedule(
    val id: String,
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val location: Location? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val participants: List<Participant> = emptyList(),
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    init {
        require(startTime <= endTime) { "Start time must be before or equal to end time." }
    }
}
