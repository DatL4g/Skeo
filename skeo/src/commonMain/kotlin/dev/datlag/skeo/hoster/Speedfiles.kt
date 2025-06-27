package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.skeo.common.swapCase
import dev.datlag.tooling.scopeCatching
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data object Speedfiles : Hoster {

    override val name: String = "Speedfiles"

    private val URL_REGEX = "(speedfiles\\.net)/(\\w+)".toRegex(RegexOption.IGNORE_CASE)

    private val ENCODED_MATCHER = "var\\s*_0x5opu234\\s*=\\s*\"(.*?)\";".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    override fun matches(url: String): Boolean {
        return URL_REGEX.containsMatchIn(url)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun resolveStreams(document: Document): Set<String> {
        return ENCODED_MATCHER.findAll(document.html()).mapNotNull { result ->
            val encoded = result.groupValues.getOrNull(1)?.trim()?.ifBlank { null } ?: return@mapNotNull null
            val decoded = scopeCatching {
                Base64.decode(encoded).decodeToString().trim().ifBlank { null }
            }.getOrNull() ?: return@mapNotNull null
            val reversed = decoded.swapCase().reversed()
            val reversedDecoded = scopeCatching {
                Base64.decode(reversed).decodeToString().trim().reversed().ifBlank { null }
            }.getOrNull() ?: return@mapNotNull null
            val decodedHex = reversedDecoded.chunked(2).map {
                it.toInt(16).toChar()
            }.joinToString(separator = "")
            val shifted = decodedHex.map { char ->
                (char.code - 3).toChar()
            }.joinToString(separator = "")

            scopeCatching {
                Base64.decode(shifted.swapCase().reversed()).decodeToString().trim()?.ifBlank { null }
            }.getOrNull()
        }.map {
            StringUtil.resolve(
                baseUrl = document.baseUri(),
                relUrl = it
            )
        }.toSet()
    }
}