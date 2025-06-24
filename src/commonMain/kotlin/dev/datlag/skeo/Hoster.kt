package dev.datlag.skeo

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.HttpClient

interface Hoster {
    val url: String
    val requiresHeaders: Boolean
        get() = false

    suspend fun directLink(document: Document): Collection<DirectLink> = emptySet()
    suspend fun directLink(client: HttpClient, document: Document) = directLink(document)

    interface UrlMatcher {
        fun matches(url: String): Boolean
    }

    interface UrlUpdater {
        fun updateUrl(url: String): String = url
        suspend fun updateUrl(client: HttpClient, url: String): String = url
    }

    interface DocumentMatcher {
        fun matches(document: Document): Boolean
    }

    companion object {
        private val LOCATION_MATCHER = "window.location.href\\s*=\\s*['\"](https.*)['\"]".toRegex(RegexOption.IGNORE_CASE)

        fun redirectLocation(document: Document): String? {
            val script = document.getElementsByTag("script").firstOrNull()?.html() ?: return null
            return LOCATION_MATCHER.find(script)?.groupValues?.get(1)?.ifBlank { null }
        }
    }
}