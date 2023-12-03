package dev.datlag.skeo

import ktsoup.KtSoupElement

internal fun KtSoupElement.getSrc(): String? {
    return this.attr("src")?.ifBlank { null } ?: run {
        val sources = this.querySelectorAll("source")
        sources.firstOrNull()?.getSrc()
    }
}

internal fun KtSoupElement.getSources(): Set<String> {
    return setOfNotNull(
        this.attr("src")?.ifBlank { null },
        *(scopeCatching {
            this.querySelectorAll("source").map { it.getSources() }.flatten().toTypedArray()
        }.getOrNull() ?: emptyArray())
    )
}