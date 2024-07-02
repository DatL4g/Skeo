package dev.datlag.skeo

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.parser.Parser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request

internal suspend fun Ksoup.parseGet(
    url: String,
    client: HttpClient,
    parser: Parser = Parser.htmlParser()
): Document {
    val httpResponse = client.get(url)
    val finalUrl = httpResponse.request.url.toString()
    val response = httpResponse.bodyAsText()
    return parse(html = response, parser = parser, baseUri = finalUrl)
}