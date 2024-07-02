package dev.datlag.skeo.v2.hoster

import dev.datlag.skeo.scopeCatching
import dev.datlag.skeo.suspendCatching
import dev.datlag.skeo.v2.DirectLink
import dev.datlag.skeo.v2.Hoster
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import ktsoup.KtSoupDocument

data class Streamtape(
    override val url: String,
    override val requiresHeaders: Boolean = false
) : Hoster(url, requiresHeaders) {


    override suspend fun directLink(document: KtSoupDocument): Collection<DirectLink> {
        return setOf(
            *substringLinks(document).toTypedArray(),
            *botLinks(document).toTypedArray()
        )
    }

    private fun substringLinks(document: KtSoupDocument): Collection<DirectLink> {
        val partMatches = SUBSTRING_LINK_MATCHER.findAll(document.html()).toSet()

        if (partMatches.isEmpty()) {
            return emptyList()
        }

        return partMatches.mapNotNull { match ->
            val parts = match.groupValues.getOrNull(1)?.replace("'", "\"")?.split("\\+")
            if (parts.isNullOrEmpty()) {
                return@mapNotNull null
            }

            val srcUrl = buildString {
                parts.forEach { part ->
                    val partValues = PART_MATCHER.find(part)?.groupValues
                    val p1 = partValues?.get(1)
                    val p2 = partValues?.get(2)
                    var p3 = 0

                    if (part.contains("substring")) {
                        SUBSTRING_MATCHER.findAll(part).forEach { result ->
                            result.groupValues.getOrNull(1)?.trim()?.toIntOrNull()?.let {
                                p3 += it
                            }
                        }
                    }

                    p1?.let {
                        append(it)
                    }
                    p2?.let {
                        append(it.substring(p3))
                    }
                }
            }.ifBlank { null } ?: return@mapNotNull null

            if (srcUrl.startsWith("//")) {
                "https:$srcUrl"
            } else {
                srcUrl
            }
        }.map(::DirectLink).toSet()
    }

    private fun botLinks(document: KtSoupDocument): Collection<DirectLink> {
        val botLinks = BOTLINK_MATCHER.findAll(document.html()).mapNotNull { result ->
            scopeCatching {
                "https:${result.groups[1]!!.value}${result.groups[2]!!.value.substring(4)}"
            }.getOrNull()
        }.toSet()

        return botLinks.map(::DirectLink)
    }

    companion object : UrlMatcher, UrlUpdater {
        private val URL_MATCHER = "(?://|\\.)(s(?:tr)?(?:eam|have)?(?:ta?p?e?|cloud|adblock(?:plus|er))\\.(?:com|cloud|net|pe|site|link|cc|online|fun|cash|to|xyz))/(?:e|v)/([0-9a-zA-Z]+)".toRegex(RegexOption.IGNORE_CASE)
        private val SUBSTRING_LINK_MATCHER = "ById\\('.+?=\\s*([\"']//[^;<]+)".toRegex(RegexOption.IGNORE_CASE)
        private val PART_MATCHER = "[\"']?(\\S+)[\"']\\S*\\s*\\([\"'](\\S+)[\"']".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val SUBSTRING_MATCHER = "substring\\((\\d+)".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val BOTLINK_MATCHER = "'botlink.*innerHTML.*?'(.*)'.*?\\+.*?'(.*)'".toRegex(
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )

        override fun matches(url: String): Boolean {
            return URL_MATCHER.containsMatchIn(url)
        }

        override fun updateUrl(url: String): String {
            var newUrl = if (url.contains("/e/")) {
                url.replace("/e/", "/v/")
            } else {
                url
            }

            newUrl = if (newUrl.endsWith("mp4")) {
                newUrl.substringBeforeLast('/')
            } else {
                newUrl
            }
            return newUrl
        }
    }
}
