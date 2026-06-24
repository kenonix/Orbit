package com.orbit.app.model

import kotlinx.serialization.Serializable

@Serializable
data class Participant(
    val phoneNumber: String, // E.164 format: e.g., +821012345678
    val email: String? = null, // Optional
    val displayName: String
) {
    init {
        require(phoneNumber.isNotBlank()) { "Phone number is mandatory for participants." }
    }
}
