package dev.datlag.skeo.common

import com.fleeksoft.ksoup.nodes.Element
import dev.datlag.tooling.async.scopeCatching

internal fun String.swapCase(): String {
    return this.map { char ->
        when {
            char.isUpperCase() -> char.lowercaseChar()
            char.isLowerCase() -> char.uppercaseChar()
            else -> char // Keep non-alphabetic characters as they are
        }
    }.joinToString(separator = "")
}

internal fun Element.getSrc(): String? {
    return this.attr("src").ifBlank { null }
}

internal fun Element.getSources(): Collection<String> {
    val backupSources = scopeCatching {
        this.getElementsByTag("source").map { it.getSources() }.flatten()
    }.getOrNull().orEmpty()

    return setOfNotNull(
        this.attr("src").ifBlank { null },
        *backupSources.toTypedArray()
    )
}