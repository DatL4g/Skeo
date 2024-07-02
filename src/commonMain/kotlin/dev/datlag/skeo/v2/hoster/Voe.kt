package dev.datlag.skeo.v2.hoster

import dev.datlag.skeo.scopeCatching
import dev.datlag.skeo.suspendCatching
import dev.datlag.skeo.v2.DirectLink
import dev.datlag.skeo.v2.Hoster
import io.ktor.client.HttpClient
import io.ktor.http.Url
import ktsoup.KtSoupDocument
import ktsoup.KtSoupParser
import ktsoup.parseRemote
import ktsoup.setClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class Voe(
    override val url: String,
    override val requiresHeaders: Boolean = false
) : Hoster(url, requiresHeaders) {
    override suspend fun directLink(document: KtSoupDocument): Collection<DirectLink> {
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
            }
        }
    }

    companion object : UrlMatcher, UrlUpdater {
        private val URL_MATCHER = "(?://|\\.)(voe\\.sx)/(\\w+)".toRegex(RegexOption.IGNORE_CASE)
        private val LOCATION_MATCHER = "window.location.href\\s*=\\s*['\"](https.*)['\"]".toRegex(RegexOption.IGNORE_CASE)
        private val HLS_MATCHER = "['\"]hls['\"]: ['\"](.*)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val MP4_MATCHER = "['\"]mp4['\"]: ['\"](.*)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        override fun matches(url: String): Boolean {
            return URL_MATCHER.containsMatchIn(url)
        }

        override suspend fun updateUrl(client: HttpClient, url: String): String {
            KtSoupParser.setClient(client)

            val doc = suspendCatching {
                KtSoupParser.parseRemote(url)
            }.getOrNull() ?: return url

            return LOCATION_MATCHER.find(doc.html())?.groupValues?.get(1)?.ifBlank { null } ?: url
        }
    }
}