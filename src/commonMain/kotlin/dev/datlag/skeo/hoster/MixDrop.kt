package dev.datlag.skeo.hoster

import dev.datlag.jsunpacker.JsUnpacker
import dev.datlag.skeo.Skeo
import ktsoup.KtSoupDocument

internal object MixDrop : Manipulation {
    override fun match(url: String): Boolean {
        return "(?://|\\.)(mixdro?p\\.(?:c[ho]|to|sx|bz|gl|club))/(?:f|e)/(\\w+)".toRegex().containsMatchIn(url)
    }

    override fun changeUrl(url: String): String {
        return if (url.contains("/f/")) {
            url.replace("/f/", "/e/")
        } else {
            url
        }
    }

    override fun change(initialList: Collection<String>, document: KtSoupDocument): Collection<String> {
        val mixDropResult = JsUnpacker.unpack(document.querySelectorAll("script").map { it.html() }).flatMap {
            "wurl=\\s*\"(.*?)\"".toRegex().findAll(it).toList().mapNotNull { result ->
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
        }
        return listOf(
            *initialList.toTypedArray(),
            *mixDropResult.toTypedArray()
        )
    }

    override fun headers(url: String): Map<String, String> {
        return mapOf(
            "Referer" to Skeo.baseUrl(url)
        )
    }
}