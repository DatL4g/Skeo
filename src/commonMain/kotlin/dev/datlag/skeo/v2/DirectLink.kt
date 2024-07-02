package dev.datlag.skeo.v2

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
    suspend fun checkIsWorking(client: HttpClient): Boolean {
        return suspendCatching {
            client.head(url) {
                headers {
                    this@DirectLink.headers.forEach { (key, value) ->
                        append(key, value)
                    }
                }
            }.status.isSuccess()
        }.getOrNull() ?: false
    }
}
