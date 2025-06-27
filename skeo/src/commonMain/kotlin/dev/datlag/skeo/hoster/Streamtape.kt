package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.tooling.setFrom

data object Streamtape : Hoster {

    override val name: String = "Streamtape"

    private val URL_REGEX = "(s(?:tr)?(?:eam|have)?(?:ta?p?e?|cloud|adblock(?:plus|er))\\.(?:com|cloud|net|pe|site|link|cc|online|fun|cash|to|xyz))/(?:e|v)/([0-9a-zA-Z]+)".toRegex(RegexOption.IGNORE_CASE)
    private val SUBSTRING_LINK_MATCHER = "ById\\('.+?=\\s*([\"']//[^;<]+)".toRegex(RegexOption.IGNORE_CASE)
    private val PART_MATCHER = "[\"']?(\\S+)[\"']\\S*\\s*\\([\"'](\\S+)[\"']".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val SUBSTRING_MATCHER = "substring\\((\\d+)".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val BOTLINK_MATCHER = "'botlink.*innerHTML.*?'(.*)'.*?\\+.*?'(.*)'".toRegex(
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    override fun matches(url: String): Boolean {
        return URL_REGEX.containsMatchIn(url)
    }

    override fun updateUrl(url: String): String {
        val newUrl = if (url.contains("/e/")) {
            url.replace("/e/", "/v/")
        } else {
            url
        }

        return if (newUrl.endsWith("mp4")) {
            newUrl.substringBeforeLast('/')
        } else {
            newUrl
        }
    }

    override fun resolveStreams(document: Document): Set<String> {
        return setFrom(
            substringLinks(document),
            botLinks(document)
        )
    }

    private fun substringLinks(document: Document): Set<String> {
        val partMatches = SUBSTRING_LINK_MATCHER.findAll(document.html()).toSet()

        if (partMatches.isEmpty()) {
            return emptySet()
        }

        return partMatches.mapNotNull { match ->
            val parts = match.groupValues.getOrNull(1)?.replace("'", "\"")?.trim()?.ifBlank { null }?.split("\\+")?.ifEmpty { null } ?: return@mapNotNull null

            val srcUrl = buildString {
                parts.forEach { part ->
                    val partValues = PART_MATCHER.find(part)?.groupValues
                    val p1 = partValues?.getOrNull(1)?.trim()?.ifBlank { null }
                    val p2 = partValues?.getOrNull(2)?.trim()?.ifBlank { null }
                    var p3 = 0

                    if (part.contains("substring")) {
                        SUBSTRING_MATCHER.findAll(part).forEach { result ->
                            result.groupValues.getOrNull(1)?.trim()?.ifBlank { null }?.toIntOrNull()?.let {
                                p3 += it
                            }
                        }
                    }

                    p1?.let(::append)
                    p2?.substring(p3)?.let(::append)
                }
            }.trim().ifBlank { null } ?: return@mapNotNull null

            StringUtil.resolve(
                baseUrl = document.baseUri(),
                relUrl = srcUrl
            ).ifBlank { null }
        }.toSet()
    }

    private fun botLinks(document: Document): Set<String> {
        return BOTLINK_MATCHER.findAll(document.html()).mapNotNull { result ->
            val group1 = result.groupValues.getOrNull(1)?.trim()?.ifBlank { null } ?: return@mapNotNull null
            val group2 = result.groupValues.getOrNull(2)?.trim()?.ifBlank { null } ?: return@mapNotNull null

            StringUtil.resolve(
                baseUrl = document.baseUri(),
                relUrl = "$group1${group2.substring(4)}"
            ).ifBlank { null }
        }.toSet()
    }
}