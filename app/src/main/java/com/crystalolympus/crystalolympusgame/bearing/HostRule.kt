package com.crystalolympus.crystalolympusgame.bearing

import com.crystalolympus.crystalolympusgame.BuildConfig
import java.net.URI

/**
 * Suffix allowlist for the URLs this app is handed from outside the WebView,
 * which means two sources only: whatever the config endpoint answers with, and
 * whatever a push payload carries. Navigation the page performs on its own is
 * exempt on purpose — an affiliate hop chain walks hosts that cannot be
 * written down ahead of time.
 *
 * `gray.allowedHosts` holds the comma-separated suffixes and the build copies
 * them into BuildConfig, so two builds can only share a list if an operator
 * types it out twice. An empty list turns the check off and traces a warning:
 * shipping that way means the app opens whatever URL a server names, which is
 * precisely what a reviewer goes looking for.
 */
internal object HostRule {

    private val suffixes: List<String> = BuildConfig.ALLOWED_HOSTS
        .split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }

    val enabled: Boolean = suffixes.isNotEmpty()

    /** Whether [url] may be passed on. With no suffixes configured, anything may. */
    fun accepts(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (suffixes.isEmpty()) return true
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return suffixes.any { s ->
            host == s || host.endsWith(".$s")
        }
    }

    fun warnIfMissing() {
        if (suffixes.isEmpty())
            Journal.w("HostRule", "gray.allowedHosts is empty — every incoming URL will be accepted")
    }
}
