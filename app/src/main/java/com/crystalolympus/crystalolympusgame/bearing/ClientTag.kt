package com.crystalolympus.crystalolympusgame.bearing

import android.os.Build
import com.crystalolympus.crystalolympusgame.BuildConfig

/**
 * Single source of the User-Agent. Anything that shows a UA to a server — the
 * config POST, the WebView, a partner GET — asks this object, so no two
 * outbound requests can disagree about who they claim to be.
 *
 * The Chrome version triple is drawn from the per-project seed and arrives via
 * BuildConfig, which is what stops two builds presenting the same string. The
 * trailing `appid/<bundle> appname/<token>` pair sits behind a build flag: no
 * real browser sends it, and backends that ask for it will normally take a
 * header instead, so leave it off without a concrete reason.
 */
internal object ClientTag {

    val value: String by lazy(LazyThreadSafetyMode.PUBLICATION) { build() }

    private fun build(): String {
        val ver = Build.VERSION.RELEASE
        val brand = safe(Build.BRAND)
        val model = safe(Build.MODEL).replace(' ', '_')
        val buildId = safe(Build.ID)
        val chrome = "${BuildConfig.UA_CHROME_MAJOR}.0.${BuildConfig.UA_CHROME_BUILD}.${BuildConfig.UA_CHROME_PATCH}"

        val base = buildString {
            append("Mozilla/5.0 (Linux; Android ")
            append(ver)
            append("; ")
            append(brand)
            append(' ')
            append(model)
            if (buildId.isNotBlank()) {
                append(" Build/")
                append(buildId)
            }
            append(") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/")
            append(chrome)
            append(" Mobile Safari/537.36")
        }

        return if (BuildConfig.GRAY_UA_APP_SUFFIX)
            "$base appid/${BuildConfig.GRAY_BUNDLE_ID} appname/${BuildConfig.GRAY_UA_TOKEN}"
        else
            base
    }

    private fun safe(s: String?): String =
        s?.filter { it in ' '..'~' } ?: ""
}
