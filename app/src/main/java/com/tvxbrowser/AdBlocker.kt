package com.tvxbrowser

object AdBlocker {
    private val blocked = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "adform.net", "adsrvr.org", "taboola.com",
        "outbrain.com", "scorecardresearch.com", "popads.net",
        "popcash.net", "zedo.com", "criteo.com"
    )

    fun isBlocked(url: String): Boolean {
        val host = try { android.net.Uri.parse(url).host?.lowercase() ?: return false }
        catch (_: Exception) { return false }
        return blocked.any { host == it || host.endsWith(".$it") }
    }
}
