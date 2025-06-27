package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document

data object Vidmoly : Hoster {

    override val name: String = "Vidmoly"

    private val URL_REGEX = "(vidmoly\\.to)/(\\w+)".toRegex(RegexOption.IGNORE_CASE)

    private val FILE_MATCHER = "file:\\s*\"(https?://.*?)\"".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    override fun matches(url: String): Boolean {
        return URL_REGEX.containsMatchIn(url)
    }

    override fun resolveStreams(document: Document): Set<String> {
        return document.dataNodes().flatMap { setOf(it.getUnpackedData(), it.getWholeData()) }.flatMap {
            FILE_MATCHER.findAll(it).mapNotNull { result ->
                val url = result.groupValues.getOrNull(1)?.trim()?.ifBlank { null } ?: return@mapNotNull null

                StringUtil.resolve(
                    baseUrl = document.baseUri(),
                    relUrl = url
                ).ifBlank { null }
            }
        }.toSet()
    }
}