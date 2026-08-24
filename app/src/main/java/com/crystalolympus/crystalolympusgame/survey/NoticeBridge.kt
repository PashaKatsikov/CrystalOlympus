package com.crystalolympus.crystalolympusgame.survey

/**
 * Process-wide hand-off for single-use push URLs. Nothing here is written to
 * storage — a URL like this is good for exactly one delivery.
 *
 * There are two arrival paths and they cannot share a code path. A push landing
 * while the shell is in front of the user goes straight down [onWarmUrl]. A push
 * tapped with the app in the background wakes the launcher instead, and by then
 * the shell has already dropped its callback, so the URL is parked in [queue],
 * the launcher renders nothing, and the returning shell collects it. Raising the
 * splash on that path would pull the page the user was reading off screen for
 * nothing.
 */
object NoticeBridge {

    @Volatile
    var onWarmUrl: ((String) -> Unit)? = null

    /** True between MeridianView's onCreate and onDestroy, screen state aside. */
    @Volatile
    var shellAlive = false

    @Volatile
    private var queue: String? = null

    /** @return true once the shell owns this URL, meaning the caller must not route. */
    fun handOver(url: String): Boolean {
        val live = onWarmUrl
        if (live != null) {
            live(url)
            return true
        }
        if (!shellAlive) return false
        queue = url
        return true
    }

    fun consume(): String? {
        val url = queue
        queue = null
        return url
    }
}
