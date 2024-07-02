package dev.datlag.skeo

import com.fleeksoft.ksoup.nodes.Element

internal fun Element.getSrc(): String? {
    return this.attr("src").ifBlank { null }
}

internal fun Element.getSources(): Collection<String> {
    val backupSources = scopeCatching {
        this.getElementsByTag("source").map { it.getSources() }.flatten()
    }.getOrNull().orEmpty().toTypedArray()

    return setOfNotNull(
        this.attr("src").ifBlank { null },
        *backupSources
    )
}