package com.orbit.app.model

import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)
