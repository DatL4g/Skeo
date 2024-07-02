package dev.datlag.skeo.v2

import dev.datlag.skeo.v2.hoster.Streamtape
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import ktsoup.KtSoupDocument

@Serializable
abstract class Hoster(
    open val url: String,
    open val requiresHeaders: Boolean = false
) {
    abstract suspend fun directLink(document: KtSoupDocument): Collection<DirectLink>

    interface UrlMatcher {
        fun matches(url: String): Boolean
    }

    interface UrlUpdater {
        fun updateUrl(url: String): String = url
        suspend fun updateUrl(client: HttpClient, url: String): String = url
    }
}