package dev.datlag.skeo

import kotlinx.serialization.Serializable

@Serializable
data class Stream(
    val sources: Set<String>,
    val headers: Map<String, String>
)
