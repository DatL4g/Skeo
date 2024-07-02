package dev.datlag.skeo

import dev.datlag.skeo.suspendCatching
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.request.headers
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class DirectLink(
    val url: String,
    val headers: Map<String, String> = emptyMap()
) {
}
