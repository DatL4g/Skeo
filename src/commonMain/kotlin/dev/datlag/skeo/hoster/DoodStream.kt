package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.nodes.Document
import dev.datlag.skeo.scopeCatching
import dev.datlag.skeo.DirectLink
import dev.datlag.skeo.Hoster
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.set
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class DoodStream(
    override val url: String,
    override val requiresHeaders: Boolean = true
) : Hoster {

    private val expiry: Long
        get() = Clock.System.now().toEpochMilliseconds()

    private val validChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    private fun randomString(length: Int = 10): String {
        return CharArray(length) { validChars.random() }.concatToString()
    }

    override suspend fun directLink(client: HttpClient, document: Document): Collection<DirectLink> {
        val docUrl = document.location()?.ifBlank { null } ?: url
        val match = LINK_MATCHER.findAll(document.html()).mapNotNull { result ->
            scopeCatching {
                val builder = URLBuilder(docUrl)
                builder.set(
                    path = result.value
                )
                builder.buildString() to result.groupValues[1]
            }.getOrNull()
        }.toSet()

        return match.mapNotNull { (updatedUrl, token) ->
            val (referer, data) = client.get(updatedUrl).getResponseUrl() ?: client.get(updatedUrl) {
                headers {
                    append("Referer", docUrl)
                }
            }.getResponseUrl() ?: client.get(updatedUrl) {
                headers {
                    append("Referer", this@DoodStream.url)
                }
            }.getResponseUrl() ?: return@mapNotNull null


            DirectLink(
                url = "${data}${randomString()}?token=$token&expiry=$expiry",
                headers = mapOf("Referer" to referer)
            )
        }.toSet()
    }

    private suspend fun HttpResponse.getResponseUrl(): Pair<String, String>? {
        return if (this.status.isSuccess()) {
            val data = this.bodyAsText().trim()
            if (data.startsWith("https://")) {
                this.request.url.toString() to data
            } else {
                null
            }
        } else {
            null
        }
    }

    companion object : Hoster.UrlMatcher, Hoster.UrlUpdater {
        private val URL_MATCHER = "(?://|\\.)(d((0+)|(o+))d(stream)?)\\.(com|yt|wf|cx|sh|watch|pm|to|so|ws|la)".toRegex(RegexOption.IGNORE_CASE)
        private val LINK_MATCHER = "/pass_md5/[\\w-]+/([\\w-]+)".toRegex(RegexOption.IGNORE_CASE)

        override fun matches(url: String): Boolean {
            return URL_MATCHER.containsMatchIn(url)
        }

        override fun updateUrl(url: String): String {
            return if (url.contains("/d/")) {
                url.replace("/d/", "/e/")
            } else {
                url
            }
        }
    }
}
