package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.jsunpacker.JsUnpacker
import dev.datlag.skeo.DirectLink
import dev.datlag.skeo.Hoster
import kotlinx.serialization.Serializable

@Serializable
data class MixDrop(
    override val url: String
) : Hoster {

    override suspend fun directLink(document: Document): Collection<DirectLink> {
        return JsUnpacker.unpack(document.getElementsByTag("script").map { it.html() }).flatMap {
            LINK_MATCHER.findAll(it).mapNotNull { result ->
                val url = result.groups[1]?.value?.trim()?.ifBlank { null }

                return@mapNotNull if (url == null) {
                    null
                } else {
                    if (url.startsWith("//")) {
                        "https:$url"
                    } else {
                        url
                    }
                }
            }
        }.toSet().map(::DirectLink)
    }

    companion object : Hoster.UrlMatcher, Hoster.UrlUpdater {
        private val URL_MATCHER = "(?://|\\.)(mixdro?p\\.(?:c[ho]|to|sx|bz|gl|club))/(?:f|e)/(\\w+)".toRegex()
        private val LINK_MATCHER = "wurl=\\s*['\"](.*?)['\"]".toRegex()

        override fun matches(url: String): Boolean {
            return URL_MATCHER.containsMatchIn(url)
        }

        override fun updateUrl(url: String): String {
            return if (url.contains("/f/")) {
                url.replace("/f/", "/e/")
            } else {
                url
            }
        }
    }
}
