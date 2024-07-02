package dev.datlag.skeo

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.jsunpacker.JsUnpacker
import dev.datlag.skeo.hoster.DoodStream
import dev.datlag.skeo.hoster.MixDrop
import dev.datlag.skeo.hoster.Streamtape
import dev.datlag.skeo.hoster.Voe
import io.ktor.client.HttpClient
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder

data object Skeo {

    private val cleanRegex = "http(s?)://\\S+\\.(mp4|m3u8|webm|mkv|flv|vob|drc|gifv|avi|((m?)(2?)ts)|mov|qt|wmv|yuv|rm((vb)?)|viv|asf|amv|m4p|m4v|mp2|mp((e)?)g|mpe|mpv|m2v|svi|3gp|3g2|mxf|roq|nsv|f4v|f4p|f4a|f4b|dll)".toRegex(
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    private val queryRegex = "${cleanRegex.pattern}(\\?\\w+=(\\w|-)*(?:&(?:\\w+=(\\w|[-_.~%])*|=(\\w|[-_.~%])+))*)?".toRegex(
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    suspend fun loadVideos(
        client: HttpClient,
        url: String,
        resolveIFrames: Boolean = true
    ): Collection<DirectLink> {
        val updatedUrl = when {
            Streamtape.matches(url) -> Streamtape.updateUrl(url)
            Voe.matches(url) -> Voe.updateUrl(client, url)
            MixDrop.matches(url) -> MixDrop.updateUrl(url)
            DoodStream.matches(url) -> DoodStream.updateUrl(url)
            else -> url
        }

        val document = suspendCatching {
            Ksoup.parseGet(updatedUrl, client)
        }.getOrNull() ?: if (updatedUrl != url) {
            suspendCatching {
                Ksoup.parseGet(url, client)
            }.getOrNull()
        } else {
            null
        } ?: return emptyList()

        val hosterSpecific = when {
            Streamtape.matches(url) -> Streamtape(updatedUrl).directLink(document)
            Voe.matches(url) -> Voe(updatedUrl).directLink(document)
            MixDrop.matches(url) -> MixDrop(updatedUrl).directLink(document)
            DoodStream.matches(url) -> DoodStream(updatedUrl).directLink(client, document)
            else -> emptySet()
        }
        val documentLinks = directLinksInDoc(document)
        val iFrameLinks = if (resolveIFrames) {
            val docUrl = document.location()?.ifBlank { null } ?: updatedUrl

            val iFrameSources = document.getElementsByTag("iframe").flatMap {
                it.getSources()
            }.toSet().map {
                normalizeIframeUrl(docUrl, it)
            }.toSet()

            iFrameSources.flatMap {
                loadVideos(
                    client = client,
                    url = it,
                    resolveIFrames = false
                )
            }
        } else {
            emptySet()
        }

        return setOf(
            *hosterSpecific.toTypedArray(),
            *documentLinks.toTypedArray(),
            *iFrameLinks.toTypedArray()
        )
    }

    private fun directLinksInDoc(document: Document): Collection<DirectLink> {
        val videoElements = document.getElementsByTag("video").map {
            it.getSources()
        }.flatten()
        val html = document.html()

        val regexResult = cleanRegex.findAll(html).map {
            it.value
        }.toSet()
        val queryRegexResult = queryRegex.findAll(html).map {
            it.value
        }.toSet()

        val jsResult = JsUnpacker.unpack(document.getElementsByTag("script").map { it.html() }).flatMap {
            val jsRegexResult = cleanRegex.findAll(it).map { result -> result.value }.toSet()
            val jsQueryRegexResult = queryRegex.findAll(it).map { result -> result.value }.toSet()

            setOf(
                *jsQueryRegexResult.toTypedArray(),
                *jsRegexResult.toTypedArray()
            )
        }.toSet()

        return setOf(
            *videoElements.toTypedArray(),
            *queryRegexResult.toTypedArray(),
            *regexResult.toTypedArray(),
            *jsResult.toTypedArray()
        ).map(::DirectLink)
    }

    private fun normalizeIframeUrl(parentUrl: String, url: String): String {
        return if (url.startsWith("http")) {
            url
        } else if (url.startsWith("//")) {
            "https:$url"
        } else {
            val base = baseUrl(parentUrl)
            val prefix = if (base.startsWith("http")) {
                ""
            } else {
                "https://"
            }
            val ending = if (base.endsWith("/") && url.startsWith("/")) {
                url.substring(1)
            } else if (base.endsWith("/") || url.startsWith("/")) {
                url
            } else {
                "/$url"
            }
            "${prefix}${base}${ending}"
        }
    }

    private fun baseUrl(url: String): String {
        return scopeCatching {
            val newUrl = URLBuilder(url)
            newUrl.user = null
            newUrl.password = null
            newUrl.pathSegments = emptyList()
            newUrl.encodedParameters = ParametersBuilder(0)
            newUrl.fragment = String()
            newUrl.trailingQuery = false
            newUrl.buildString()
        }.getOrNull() ?: run {
            val regex = "^(?:https?://)?(?:[^@\\n]+@)?(?:www\\.)?([^:/\\n?]+)".toRegex(RegexOption.IGNORE_CASE)
            regex.find(url)?.value
        } ?: url
    }
}