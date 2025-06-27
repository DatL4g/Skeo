package dev.datlag.skeo.hoster

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import kotlin.jvm.JvmStatic

sealed interface Hoster {

    val name: String

    fun resolveStreams(document: Document): Set<String>

    fun matches(url: String): Boolean = false
    fun matches(document: Document): Boolean = matches(document.baseUri())

    fun updateUrl(url: String): String = url

    suspend fun redirect(
        document: Document,
        client: HttpClient
    ): Document = document

    companion object {

        private val LOCATION_MATCHER = "window.location.href\\s*=\\s*['\"](https.*)['\"]".toRegex(RegexOption.IGNORE_CASE)

        @JvmStatic
        fun auto(document: Document): Hoster? {
            return when {
                VOE.matches(document) -> VOE
                MixDrop.matches(document) -> MixDrop
                Speedfiles.matches(document) -> Speedfiles
                Streamtape.matches(document) -> Streamtape
                Vidmoly.matches(document) -> Vidmoly
                else -> null
            }
        }

        @JvmStatic
        fun auto(url: String): Hoster? {
            return when {
                VOE.matches(url) -> VOE
                MixDrop.matches(url) -> MixDrop
                Speedfiles.matches(url) -> Speedfiles
                Streamtape.matches(url) -> Streamtape
                Vidmoly.matches(url) -> Vidmoly
                else -> fromName(url) // fall back to name in case of wrong usage
            }
        }

        @JvmStatic
        fun fromName(name: String): Hoster? {
            return when {
                name.equals(VOE.name, ignoreCase = true) -> VOE
                name.equals(MixDrop.name, ignoreCase = true) -> MixDrop
                name.equals(Speedfiles.name, ignoreCase = true) -> Speedfiles
                name.equals(Streamtape.name, ignoreCase = true) -> Streamtape
                name.equals(Vidmoly.name, ignoreCase = true) -> Vidmoly
                else -> null
            }
        }

        internal fun redirectLocation(document: Document): String? {
            val script = document.getElementsByTag("script").firstOrNull()?.html() ?: return null
            return LOCATION_MATCHER.find(script)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
        }
    }
}