package com.crystalolympus.crystalolympusgame.legend

import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.almanac.Cloak

/**
 * Read-only window onto the per-project fingerprint the build compiled into
 * BuildConfig. Nothing in here is meant to be edited by hand: to move any of
 * these values, edit `gray.properties` — or roll the seed — and rebuild.
 *
 * The object carries no logic at all; every member is a one-line hop to
 * BuildConfig or to [Cloak]. That is deliberate, because it lets the file be
 * renamed and re-packaged per project without anyone re-reading behaviour.
 */
object Bearings {

    // ── Identity ────────────────────────────────────────────────────────────
    val bundleId: String     = BuildConfig.GRAY_BUNDLE_ID
    val appLabel: String     = BuildConfig.GRAY_APP_LABEL
    val appNameToken: String = BuildConfig.GRAY_UA_TOKEN

    // ── Timings, all of them seed-derived rather than chosen ───────────────
    val attributionFirstMs: Long  = BuildConfig.ATTRIBUTION_FIRST_MS
    val attributionReturnMs: Long = BuildConfig.ATTRIBUTION_RETURN_MS
    val deepLinkWaitMs: Long      = BuildConfig.DEEP_LINK_WAIT_MS
    val configTimeoutMs: Long     = BuildConfig.CONFIG_TIMEOUT_MS
    val organicGcdDelayMs: Long   = BuildConfig.ORGANIC_GCD_DELAY_MS
    val gcdTimeoutMs: Long        = BuildConfig.GCD_TIMEOUT_MS
    val connectGraceMs: Long      = BuildConfig.CONNECT_GRACE_MS
    val safeAreaDelayMs: Long     = BuildConfig.SAFE_AREA_DELAY_MS
    val heartbeatMs: Long         = BuildConfig.HEARTBEAT_MS
    val pushSnoozeSeconds: Long   = BuildConfig.PUSH_SNOOZE_SEC
    val redirectRetryMax: Int     = BuildConfig.REDIRECT_RETRY_MAX

    // ── Cloak ─────────────────────────────────────────────────────────────
    fun resolveConfigEndpoint(): String  = Cloak.reveal(BuildConfig.SEC_CFG_ENDPOINT)
    fun resolveTrackerKey(): String      = Cloak.reveal(BuildConfig.SEC_AF_KEY)
    fun resolveAnalyticsProject(): String = Cloak.reveal(BuildConfig.SEC_FB_PROJECT)
    fun resolveGcdBase(): String         = Cloak.reveal(BuildConfig.SEC_GCD_BASE)

    // ── Debug override ──────────────────────────────────────────────────────
    // The build script pins this to an empty string for release, no exceptions.
    val debugForceStreamUrl: String get() = BuildConfig.DEBUG_FORCE_URL
}
