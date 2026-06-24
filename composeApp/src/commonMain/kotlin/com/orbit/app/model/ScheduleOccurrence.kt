package com.orbit.app.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleOccurrence(
    val schedule: Schedule,
    val occurrenceIndex: Int,
    val startTime: Instant,
    val endTime: Instant
)
