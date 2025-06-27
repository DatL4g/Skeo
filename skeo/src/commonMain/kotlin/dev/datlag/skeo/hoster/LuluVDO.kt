package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document

data object LuluVDO : Hoster {

    override val name: String = "LuluVDO"
    val alternativeName: String = "LuluStream"

    private val URL_REGEX = "(lulu(vdo|stream)\\.com)/(\\w+)".toRegex(RegexOption.IGNORE_CASE)

    private val FILE_MATCHER = "file:\\s*\"([^\"]+)\"".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    override fun matches(url: String): Boolean {
        return URL_REGEX.containsMatchIn(url)
    }

    override fun updateUrl(url: String): String {
        if (url.contains("file_code=")) {
            return url
        }

        val id = url.split('/').mapNotNull { it.trim().ifBlank { null } }.lastOrNull() ?: url
        return "https://luluvdo.com/dl?op=embed&file_code=$id&embed=1&referer=luluvdo.com&adb=0"
    }

    override fun resolveStreams(document: Document): Set<String> {
        return FILE_MATCHER.findAll(document.html()).mapNotNull { result ->
            val url = result.groupValues.getOrNull(1)?.trim()?.ifBlank { null } ?: return@mapNotNull null

            StringUtil.resolve(
                baseUrl = document.baseUri(),
                relUrl = url
            ).ifBlank { null }
        }.toSet()
    }
}