package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.skeo.scopeCatching
import dev.datlag.skeo.suspendCatching
import dev.datlag.skeo.DirectLink
import dev.datlag.skeo.Hoster
import dev.datlag.skeo.parseGet
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class Voe(
    override val url: String
) : Hoster {
    override suspend fun directLink(document: Document): Collection<DirectLink> {
        val html = document.html()

        val hlsLinks = HLS_MATCHER.findAll(html).mapNotNull { result ->
            val match = result.groupValues[1].ifBlank { null } ?: return@mapNotNull null
            matchToLink(match)
        }
        val mp4Links = MP4_MATCHER.findAll(html).mapNotNull { result ->
            val match = result.groupValues[1].ifBlank { null } ?: return@mapNotNull null
            matchToLink(match)
        }

        return setOf(
            *hlsLinks.toSet().toTypedArray(),
            *mp4Links.toSet().toTypedArray()
        ).map(::DirectLink)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun matchToLink(match: String): String? {
        return if (match.startsWith("https://")) {
            match
        } else {
            scopeCatching {
                Base64.UrlSafe.decode(match).decodeToString()
            }.getOrNull() ?: scopeCatching {
                Url(match)
            }.getOrNull()?.let { uri ->
                if (uri.host.equals("localhost", ignoreCase = true)) {
                    null
                } else {
                    uri.toString().ifBlank { null }
                }
            } ?: scopeCatching {
                Base64.Default.decode(match).decodeToString()
            }.getOrNull()
        }
    }

    companion object : Hoster.UrlMatcher, Hoster.UrlUpdater {
        private val URL_MATCHER = "(?://|\\.)(voe\\.sx)/(\\w+)".toRegex(RegexOption.IGNORE_CASE)
        private val LOCATION_MATCHER = "window.location.href\\s*=\\s*['\"](https.*)['\"]".toRegex(RegexOption.IGNORE_CASE)
        private val HLS_MATCHER = "['\"]hls['\"]:\\s*['\"](.*)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val MP4_MATCHER = "['\"]mp4['\"]:\\s*['\"](.*)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        override fun matches(url: String): Boolean {
            return URL_MATCHER.containsMatchIn(url)
        }

        override suspend fun updateUrl(client: HttpClient, url: String): String {
            val doc = suspendCatching {
                Ksoup.parseGet(url, client)
            }.getOrNull() ?: return url
            val script = doc.getElementsByTag("script").firstOrNull()?.html() ?: return url

            return LOCATION_MATCHER.find(script)?.groupValues?.get(1)?.ifBlank { null } ?: url
        }
    }
}