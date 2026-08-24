package com.crystalolympus.crystalolympusgame.core

import android.app.Application
import android.util.Log
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.crystalolympus.crystalolympusgame.BuildConfig

/**
 * Install attribution analytics, and nothing else.
 *
 * This wraps the AppsFlyer SDK purely so the AppsFlyer dashboard can report how many installs are
 * organic versus paid/non-organic. There is no config endpoint, no WebView, no deep-link handling
 * and no feature in the app ever branches on [isOrganicInstall] - it exists only so the value is
 * visible in logcat during development.
 */
object Analytics {

    private const val TAG = "Analytics"
    private const val DEV_KEY = "LQS26ZiQCQAPhi88WzpdP7"

    /** Null until AppsFlyer answers; true/false once it does. Debug visibility only. */
    @Volatile
    var isOrganicInstall: Boolean? = null
        private set

    /** Starts the SDK. Call once, from [Application.onCreate]. */
    fun init(application: Application) {
        val listener = object : AppsFlyerConversionListener {
            override fun onConversionDataSuccess(data: MutableMap<String, Any>?) {
                isOrganicInstall = (data?.get("af_status") as? String)?.equals("Organic", ignoreCase = true)
                if (BuildConfig.DEBUG) Log.i(TAG, "conversion data: $data")
            }

            override fun onConversionDataFail(error: String?) {
                if (BuildConfig.DEBUG) Log.i(TAG, "conversion data failed: $error")
            }

            override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
                if (BuildConfig.DEBUG) Log.i(TAG, "app open attribution: $data")
            }

            override fun onAttributionFailure(error: String?) {
                if (BuildConfig.DEBUG) Log.i(TAG, "attribution failure: $error")
            }
        }

        runCatching {
            AppsFlyerLib.getInstance().apply {
                setDebugLog(BuildConfig.DEBUG)
                init(DEV_KEY, listener, application)
                start(application)
            }
        }.onFailure {
            if (BuildConfig.DEBUG) Log.i(TAG, "AppsFlyer failed to start", it)
        }
    }
}
