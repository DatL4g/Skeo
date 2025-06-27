package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.network.parseGetRequest
import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.skeo.Skeo
import dev.datlag.tooling.scopeCatching
import dev.datlag.tooling.setFrom
import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data object VOE : Hoster {

    /**
     * Name of Hoster.
     */
    override val name: String = "VOE"

    private val URL_REGEX = "(voe\\.sx)/(\\w+)".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Used in old content pages, for HLS streams.
     */
    private val HLS_MATCHER = "['\"]hls['\"]:\\s*['\"](.*)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    /**
     * Used in old content pages, for MP4 streams.
     */
    private val MP4_MATCHER = "['\"]mp4['\"]:\\s*['\"](.*)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    /**
     * Used in old content pages, for Base64 decoded streams.
     */
    private val BASE64_MATCHER = "var a168c='([^']+)'".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    private val junkParts = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
    private val jsonParser = Skeo.defaultJson

    override fun matches(url: String): Boolean {
        return URL_REGEX.containsMatchIn(url)
    }

    override fun matches(document: Document): Boolean {
        val matchesMeta = document.headOrNull()?.select("meta[name=keywords]")?.mapNotNull { meta ->
            meta.attr("content").trim().ifBlank { null }
        }?.any { content ->
            content.equals(name, ignoreCase = true)
        } ?: false

        return matchesMeta || matches(document.baseUri())
    }

    override suspend fun redirect(document: Document, client: HttpClient): Document {
        return if (document.body().childrenSize() <= 1) {
            Hoster.redirectLocation(document)?.let {
                Ksoup.parseGetRequest(
                    url = it,
                    httpClient = client,
                )
            } ?: document
        } else {
            document
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun resolveStreams(document: Document): Set<String> {
        val extractedJson = extractFromScripts(document).mapNotNull { result ->
            StringUtil.resolve(
                baseUrl = document.baseUri(),
                relUrl = result
            ).ifBlank { null }
        }

        val html = document.html()
        val hlsLinks = HLS_MATCHER.findAll(html).mapNotNull { result ->
            result.groupValues.getOrNull(1)?.ifBlank { null }?.let {
                scopeCatching {
                    StringUtil.resolve(
                        baseUrl = document.baseUri(),
                        relUrl = Base64.decode(it).decodeToString()
                    ).ifBlank { null }
                }.getOrNull() ?: StringUtil.resolve(
                    baseUrl = document.baseUri(),
                    relUrl = it
                ).ifBlank { null }
            }
        }

        val mp4Links = MP4_MATCHER.findAll(html).mapNotNull { result ->
            result.groupValues.getOrNull(1)?.ifBlank { null }?.let {
                scopeCatching {
                    StringUtil.resolve(
                        baseUrl = document.baseUri(),
                        relUrl = Base64.decode(it).decodeToString()
                    ).ifBlank { null }
                }.getOrNull() ?: StringUtil.resolve(
                    baseUrl = document.baseUri(),
                    relUrl = it
                ).ifBlank { null }
            }
        }

        val base64Links = BASE64_MATCHER.findAll(html).mapNotNull { result ->
            result.groupValues.getOrNull(1)?.ifBlank { null }?.let {
                scopeCatching {
                    Base64.decode(it).decodeToString().reversed().ifBlank { null }
                }.getOrNull()
            }
        }.mapNotNull { result ->
            scopeCatching {
                jsonParser.decodeFromString<Result>(result)
            }.getOrNull()
        }.flatMap { result ->
            setOfNotNull(result.source, result.directAccessUrl)
        }

        return setFrom(
            extractedJson,
            hlsLinks.toSet(),
            mp4Links.toSet(),
            base64Links.toSet()
        )
    }

    private fun shiftLetters(input: String): String {
        return buildString {
            for (char in input) {
                var code = char.code

                if (code in 65..90) {
                    code = (code - 65 + 13) % 26 + 65
                } else if (code in 97..122) {
                    code = (code - 97 + 13) % 26 + 97
                }
                append(Char(code))
            }
        }
    }

    private fun replaceJunk(input: String): String {
        var result = input
        for (part in junkParts) {
            result = result.replace(Regex.escape(part).toRegex(), "_")
        }
        return result
    }

    private fun shiftBack(s: String, n: Int): String {
        return s.map { c -> Char(c.code - n) }.joinToString(separator = "")
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeString(encoded: String): Result? {
        val step1 = shiftLetters(encoded)
        val step2 = replaceJunk(step1).replace("_", "")
        val step3 = Base64.decode(step2).decodeToString()
        val step4 = shiftBack(step3, 3)
        val step5 = scopeCatching {
            Base64.decode(step4.reversed()).decodeToString().trim().ifBlank { null }
        }.getOrNull() ?: return null

        return scopeCatching {
            jsonParser.decodeFromString<Result>(step5)
        }.getOrNull()
    }

    private fun extractFromScripts(document: Document): Set<String> {
        return document.select("script[type=application/json]").flatMap {
            val data = it.data()
            val jsonText = data.substring(2, data.length - 2)
            val result = decodeString(jsonText)

            setOfNotNull(result?.source, result?.directAccessUrl)
        }.toSet()
    }

    @Serializable
    private data class Result(
        @SerialName("source") private val _source: String? = null,
        @SerialName("direct_access_url") private val _directAccessUrl: String? = null
    ) {
        val source: String?
            get() = _source?.trim()?.ifBlank { null }

        val directAccessUrl: String?
            get() = _directAccessUrl?.trim()?.ifBlank { null }
    }
}