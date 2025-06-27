package dev.datlag.skeo

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.network.parseGetRequest
import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.skeo.common.getSources
import dev.datlag.skeo.hoster.Hoster
import dev.datlag.tooling.setFrom
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.head
import io.ktor.client.request.url
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

data object Skeo {

    internal val defaultJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val cleanRegex = "http(s?)://\\S+\\.(mp4|m3u8|webm|mkv|flv|vob|drc|gifv|avi|((m?)(2?)ts)|mov|qt|wmv|yuv|rm((vb)?)|viv|asf|amv|m4p|m4v|mp2|mp((e)?)g|mpe|mpv|m2v|svi|3gp|3g2|mxf|roq|nsv|f4v|f4p|f4a|f4b|dll)".toRegex(
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    private val queryRegex = "${cleanRegex.pattern}(\\?\\w+=(\\w|-)*(?:&(?:\\w+=(\\w|[-_.~%])*|=(\\w|[-_.~%])+))*)?".toRegex(
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    @JvmStatic
    @JvmOverloads
    fun resolveStreams(
        document: Document,
        hoster: Hoster? = Hoster.auto(document)
    ): Set<String> {
        val fittingHoster = hoster ?: Hoster.auto(document)
        val hosterSpecific = fittingHoster?.resolveStreams(document).orEmpty()

        return setFrom(
            hosterSpecific,
            videoSources(document),
            videosInDocument(document)
        )
    }

    @JvmStatic
    @JvmOverloads
    suspend fun resolveStreams(
        url: String,
        client: HttpClient,
        hoster: Hoster? = Hoster.auto(url),
        httpRequestBuilder: HttpRequestBuilder.() -> Unit = { },
    ): Set<String> {
        val urlHoster = hoster ?: Hoster.auto(url)
        val document = Ksoup.parseGetRequest(
            url = urlHoster?.updateUrl(url) ?: url,
            httpClient = client,
            httpRequestBuilder = httpRequestBuilder
        )
        val fittingHoster = urlHoster ?: Hoster.auto(document)
        val doc = fittingHoster?.redirect(document, client) ?: document

        return resolveStreams(doc, hoster)
    }

    /**
     * Removes common sample videos from collection.
     */
    @JvmStatic
    fun filterNotSample(items: Collection<String>): Set<String> = TestVideo.filter(items).toSet()

    /**
     * Removes non-reachable links from collection.
     */
    @JvmStatic
    suspend fun filterReachable(
        items: Collection<String>,
        client: HttpClient,
        block: HttpRequestBuilder.(item: String) -> Unit = { }
    ): Set<String> = coroutineScope {
        return@coroutineScope items.toSet().map { item -> async {
            val response = client.head {
                url(item)
                block(item)
            }

            if (response.status.isSuccess()) {
                item
            } else {
                null
            }
        } }.awaitAll().filterNotNull().toSet()
    }

    private fun videoSources(document: Document): Set<String> {
        return document.getElementsByTag("video").flatMap {
            it.getSources().mapNotNull { s -> s.trim().ifBlank { null } }
        }.mapNotNull {
            StringUtil.resolve(
                baseUrl = document.baseUri(),
                relUrl = it
            ).ifBlank { null }
        }.toSet()
    }

    private fun videosInDocument(document: Document): Set<String> {
        val html = document.html()

        val cleanLinks = cleanRegex.findAll(html).mapNotNull {
            it.value.trim().ifBlank { null }
        }.toSet()

        val queryLinks = queryRegex.findAll(html).mapNotNull {
            it.value.trim().ifBlank { null }
        }.toSet()

        val scriptLinks = document.dataNodes().flatMap { setOf(it.getUnpackedData(), it.getWholeData()) }.flatMap {
            val scriptCleanLinks = cleanRegex.findAll(it).mapNotNull { result ->
                result.value.trim().ifBlank { null }
            }.toSet()

            val scriptQueryLinks = queryRegex.findAll(it).mapNotNull { result ->
                result.value.trim().ifBlank { null }
            }.toSet()

            setFrom(scriptCleanLinks, scriptQueryLinks)
        }.toSet()

        return setFrom(
            cleanLinks,
            queryLinks,
            scriptLinks
        ).mapNotNull {
            StringUtil.resolve(
                baseUrl = document.baseUri(),
                relUrl = it
            ).ifBlank { null }
        }.toSet()
    }
}