package dev.datlag.skeo.hoster

import ktsoup.KtSoupDocument

interface Manipulation {
    fun match(url: String): Boolean
    fun changeUrl(url: String): String = url
    fun change(initialList: Collection<String>, document: KtSoupDocument): Collection<String>
    fun headers(url: String): Map<String, String>
    fun changeOnIFrame(parentUrl: String, iFrameUrl: String) : Manipulation = this
}