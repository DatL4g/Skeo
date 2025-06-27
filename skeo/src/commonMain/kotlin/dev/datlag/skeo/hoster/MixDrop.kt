package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document

data object MixDrop : Hoster {

    override val name: String = "MixDrop"

    private val URL_REGEX = "(mixdro?p\\.(?:c[ho]|to|sx|bz|gl|club))/(?:f|e)/(\\w+)".toRegex(RegexOption.IGNORE_CASE)

    private val LINK_MATCHER = "wurl=\\s*['\"](.*?)['\"]".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    override fun matches(url: String): Boolean {
        return URL_REGEX.containsMatchIn(url)
    }

    override fun updateUrl(url: String): String {
        return url.replace("/f/", "/e/")
    }

    override fun resolveStreams(document: Document): Set<String> {
        return document.dataNodes().flatMap { setOf(it.getUnpackedData(), it.getWholeData()) }.flatMap {
            LINK_MATCHER.findAll(it).mapNotNull { result ->
                val url = result.groupValues.getOrNull(1)?.trim()?.ifBlank { null } ?: return@mapNotNull null

                StringUtil.resolve(
                    baseUrl = document.baseUri(),
                    relUrl = url
                ).ifBlank { null }
            }
        }.toSet()
    }
}