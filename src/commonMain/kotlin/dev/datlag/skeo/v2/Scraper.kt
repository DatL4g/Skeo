package dev.datlag.skeo.v2

import dev.datlag.skeo.suspendCatching
import dev.datlag.skeo.v2.hoster.Streamtape
import dev.datlag.skeo.v2.hoster.Voe
import io.ktor.client.HttpClient
import ktsoup.KtSoupParser
import ktsoup.parseRemote
import ktsoup.setClient

object Scraper {

    suspend fun getLinks(
        client: HttpClient,
        url: String,
    ): Collection<DirectLink> {
        KtSoupParser.setClient(client)

        val updatedUrl = when {
            Streamtape.matches(url) -> Streamtape.updateUrl(url)
            Voe.matches(url) -> Voe.updateUrl(client, url)
            else -> url
        }

        val document = suspendCatching {
            KtSoupParser.parseRemote(updatedUrl)
        }.getOrNull() ?: if (updatedUrl != url) {
            suspendCatching {
                KtSoupParser.parseRemote(url)
            }.getOrNull()
        } else {
            null
        } ?: return emptyList()

        return when {
            Streamtape.matches(url) -> Streamtape(updatedUrl).directLink(document)
            Voe.matches(url) -> Voe(updatedUrl).directLink(document)
            else -> return emptyList()
        }
    }
}