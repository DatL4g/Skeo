package dev.datlag.skeo

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.network.parseGetRequest
import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.skeo.hoster.Hoster
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

    @JvmStatic
    @JvmOverloads
    fun resolveStreams(
        document: Document,
        hoster: Hoster? = Hoster.auto(document)
    ): Set<String> {
        val fittingHoster = hoster ?: Hoster.auto(document)
        return fittingHoster?.resolveStreams(document).orEmpty()
    }

    @JvmStatic
    @JvmOverloads
    suspend fun resolveStreams(
        url: String,
        client: HttpClient,
        hoster: Hoster? = Hoster.auto(url),
        httpRequestBuilder: HttpRequestBuilder.() -> Unit = { },
    ): Set<String> {
        val document = Ksoup.parseGetRequest(
            url = url,
            httpClient = client,
            httpRequestBuilder = httpRequestBuilder
        )
        val fittingHoster = hoster ?: Hoster.auto(url) ?: Hoster.auto(document)
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
}