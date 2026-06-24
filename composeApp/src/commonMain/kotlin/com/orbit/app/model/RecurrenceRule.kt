package com.orbit.app.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class RecurrenceFrequency {
    DAILY,
    MONTHLY,
    QUARTERLY,
    SEMI_ANNUALLY,
    ANNUALLY
}

@Serializable
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val count: Int? = null,
    val until: Instant? = null
)
