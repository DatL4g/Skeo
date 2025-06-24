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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class Voe(
    override val url: String
) : Hoster {

    private val junkParts = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")

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

        val extractedJson = extractFromScript(document).mapNotNull { result ->
            matchToLink(result)
        }

        return setOfNotNull(
            *extractedJson.toTypedArray(),
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

    private fun shiftLetters(input: String): String {
        var result = StringBuilder()
        for (char in input) {
            var code = char.code
            if (65 <= code && code <= 90) {
                code = (code - 65 + 13) % 26 + 65
            } else if (97 <= code && code <= 122) {
                code = (code - 97 + 13) % 26 + 97
            }
            result.append(Char(code))
        }
        return result.toString()
    }

    private fun replaceJunk(input: String): String {
        var result = input
        for (part in junkParts) {
            result = result.replace(Regex.escape(part).toRegex(), "_")
        }
        return result
    }

    private fun shiftBack(s: String, n: Int): String {
        return s.map { c -> Char(c.code - n) }.joinToString("")
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeString(encoded: String): JsonElement? {
        val step1 = shiftLetters(encoded)
        val step2 = replaceJunk(step1).replace("_", "")
        val step3 = Base64.decode(step2).decodeToString()
        val step4 = shiftBack(step3, 3)
        val step5 = Base64.decode(step4.reversed()).decodeToString()
        return scopeCatching {
            json.parseToJsonElement(step5)
        }.getOrNull()?.also {
            println(it.toString())
        }
    }

    private fun extractFromScript(document: Document): Set<String> {
        val script = document.selectFirst("script[type=application/json]")
        return script?.let {
            val data = it.data()
            val jsonText = data.substring(2, data.length - 2)
            val json = decodeString(jsonText)
            json?.let {
                setOfNotNull(
                    it.jsonObject["source"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                    it.jsonObject["direct_access_url"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
                )
            }
        } ?: emptySet()
    }

    companion object : Hoster.UrlMatcher, Hoster.UrlUpdater, Hoster.DocumentMatcher {
        private val URL_MATCHER = "(?://|\\.)(voe\\.sx)/(\\w+)".toRegex(RegexOption.IGNORE_CASE)
        private val HLS_MATCHER = "['\"]hls['\"]:\\s*['\"](.*)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val MP4_MATCHER = "['\"]mp4['\"]:\\s*['\"](.*)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        private val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

        override fun matches(url: String): Boolean {
            return URL_MATCHER.containsMatchIn(url)
        }

        override fun matches(document: Document): Boolean {
            return document.headOrNull()?.select("meta[name=keywords]")?.mapNotNull { meta ->
                meta.attr("content").trim().ifBlank { null }
            }?.any { content ->
                content.equals("VOE", ignoreCase = true)
            } ?: false
        }

        override suspend fun updateUrl(client: HttpClient, url: String): String {
            val doc = suspendCatching {
                Ksoup.parseGet(url, client)
            }.getOrNull() ?: return url

            return Hoster.redirectLocation(doc) ?: url
        }
    }
}