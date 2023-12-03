package dev.datlag.skeo

import dev.datlag.jsunpacker.JsUnpacker
import dev.datlag.skeo.hoster.Manipulation
import dev.datlag.skeo.hoster.MixDrop
import dev.datlag.skeo.hoster.Streamtape
import io.ktor.client.*
import io.ktor.http.*
import ktsoup.KtSoupDocument
import ktsoup.KtSoupParser
import ktsoup.parseRemote
import ktsoup.setClient

data object Skeo {

    suspend fun loadVideos(document: KtSoupDocument): Stream? {
        val sources = getStreamsInDoc(document)
        return if (sources.isEmpty()) {
            null
        } else {
            Stream(
                sources = sources,
                headers = emptyMap()
            )
        }
    }

    suspend fun loadVideos(
        client: HttpClient,
        url: String,
        resolveIFrames: Boolean = true,
        vararg manipulator: Manipulation
    ): Stream? {
        KtSoupParser.setClient(client)

        val defaultManipulator = setOf(
            Streamtape,
            MixDrop
        )

        val allManipulator = setOf(
            *manipulator,
            *defaultManipulator.toTypedArray(),
        )
        val specificManipulator = allManipulator.filter {
            it.match(url)
        }
        val newUrl = specificManipulator.fold(url) { str, it ->
            it.changeUrl(str)
        }

        val doc = suspendCatching {
            KtSoupParser.parseRemote(newUrl)
        }.getOrNull() ?: return null

        val docStreams = getStreamsInDoc(doc)

        val results = applyHosterManipulation(
            url = newUrl,
            initialList = docStreams,
            doc = doc,
            manipulatorList = allManipulator
        ).let { (src, header) ->
            if (src.isEmpty()) {
                null
            } else {
                Stream(sources = src.toSet(), headers = header)
            }
        }
        val iframeResults = if (resolveIFrames) {
            doc.querySelectorAll("iframe")
                .flatMap { it.getSources() }
                .toSet()
                .mapNotNull { src ->
                    val iframeUrl = normalizeIframeUrl(newUrl, src)
                    loadVideos(
                        client = client,
                        url = iframeUrl,
                        resolveIFrames = false,
                        manipulator = allManipulator.map {
                            it.changeOnIFrame(newUrl, iframeUrl)
                        }.toTypedArray()
                    )
                }
        } else {
            emptyList()
        }

        val allSources = setOf(
            *(results?.sources ?: emptySet()).toTypedArray(),
            *iframeResults.flatMap { it.sources }.toTypedArray()
        )

        return if (allSources.isEmpty()) {
            null
        } else {
            val allHeaders = setOf(
                results?.headers ?: emptyMap<String, String>(),
                *iframeResults.map { it.headers }.toTypedArray()
            )

            Stream(
                sources = allSources,
                headers = allHeaders.flatMap { it.entries }.associate { it.key to it.value }
            )
        }
    }

    private suspend fun getStreamsInDoc(document: KtSoupDocument): Set<String> {
        val videoElements = document.querySelectorAll("video").map {
            it.getSources()
        }.flatten()
        val html = document.html()
        val regex = Regex(
            "http(s?)://\\S+\\.(mp4|m3u8|webm|mkv|flv|vob|drc|gifv|avi|((m?)(2?)ts)|mov|qt|wmv|yuv|rm((vb)?)|viv|asf|amv|m4p|m4v|mp2|mp((e)?)g|mpe|mpv|m2v|svi|3gp|3g2|mxf|roq|nsv|f4v|f4p|f4a|f4b|dll)",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )
        val regexWithQueryParams = "${regex.pattern}(\\?\\w+=(\\w|-)*(?:&(?:\\w+=(\\w|[-_.~%])*|=(\\w|[-_.~%])+))*)?".toRegex(
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )

        val regexResult = regex.findAll(html).map {
            it.value
        }.toList()
        val regexWithQueryResult = regexWithQueryParams.findAll(html).map {
            it.value
        }.toList()

        val jsResult = JsUnpacker.unpack(document.querySelectorAll("script").map { it.html() }).flatMap {
            val jsRegexResult = regex.findAll(it).map { result -> result.value }.toList()
            val jsRegexWithQueryResult = regexWithQueryParams.findAll(it).map { result -> result.value }.toList()

            setOf(
                *jsRegexResult.toTypedArray(),
                *jsRegexWithQueryResult.toTypedArray()
            )
        }

        return setOf(
            *videoElements.toTypedArray(),
            *regexResult.toTypedArray(),
            *regexWithQueryResult.toTypedArray(),
            *jsResult.toTypedArray()
        )
    }

    private suspend fun normalizeIframeUrl(parentUrl: String, url: String): String {
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

    private suspend fun applyHosterManipulation(
        url: String,
        initialList: Collection<String>,
        doc: KtSoupDocument,
        manipulatorList: Set<Manipulation>
    ): Pair<Collection<String>, Map<String, String>> {
        val manipulator = manipulatorList.mapNotNull {
            if (it.match(url)) {
                it
            } else {
                null
            }
        }

        return (manipulator.fold(initialList) { list, it ->
            it.change(list, doc)
        }) to (manipulator.map { it.headers(url) }.flatMap { it.entries }.associate { it.key to it.value })
    }

    internal fun baseUrl(url: String): String {
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