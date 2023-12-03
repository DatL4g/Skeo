package dev.datlag.skeo

data class Stream(
    val sources: Set<String>,
    val headers: Map<String, String>
)
