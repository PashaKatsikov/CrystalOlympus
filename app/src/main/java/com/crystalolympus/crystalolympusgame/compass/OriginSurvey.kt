package com.crystalolympus.crystalolympusgame.compass

import android.app.Activity
import android.content.Context
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.deeplink.DeepLinkListener
import com.appsflyer.deeplink.DeepLinkResult
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.legend.Bearings
import com.crystalolympus.crystalolympusgame.bearing.Journal
import com.crystalolympus.crystalolympusgame.bearing.ClientTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Attribution against AppsFlyer, split deliberately in two. Read
 * `.cursor/rules/kotlin_launch_flow.mdc` before editing; there is very little
 * here that can move without a cost.
 *
 * [prime] only hooks the callbacks up, and it has to happen in the Application.
 * Part of what `init` does is register activity-lifecycle callbacks, and those
 * are how the SDK learns the app came to the foreground. Register them once an
 * activity is already resumed and that transition has been and gone: the first
 * launch then waits for whatever activity appears next. Nothing leaves the
 * device during this call.
 *
 * [ignite] is the half that actually talks to AppsFlyer, so it must wait until
 * the caller has confirmed there is a connection to talk over.
 */
class OriginSurvey(private val ctx: Context) {

    @Volatile private var attribution = CompletableDeferred<Map<String, Any?>>()
    @Volatile private var settled: Map<String, Any?>? = null

    private val deepLinkParams = mutableMapOf<String, Any?>()
    private val deepLink = CompletableDeferred<Unit>()

    private var primed = false
    private var started = false

    @Volatile private var reasked = false
    private var reaskedAt = 0L

    /** Scope for the work that has to survive any one Activity going away. */
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val gcdHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Bearings.gcdTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(Bearings.gcdTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    fun prime() {
        if (primed) return
        primed = true

        val key = Bearings.resolveTrackerKey()
        if (key.isBlank()) {
            Journal.i(TAG, "dev key not configured — attribution resolves empty")
            finish(emptyMap())
            deepLink.complete(Unit)
            return
        }

        val wired = runCatching {
            AppsFlyerLib.getInstance().apply {
                setDebugLog(BuildConfig.DEBUG)     // the SDK is chatty; keep it out of release
                subscribeForDeepLink(deepLinkListener)
                init(key, conversionListener, ctx.applicationContext)
            }
        }
        if (wired.isFailure) {
            Journal.w(TAG, "SDK would not wire up: ${wired.exceptionOrNull()?.message}")
            finish(emptyMap())
            deepLink.complete(Unit)
        }
    }

    fun ignite(host: Activity) {
        prime()
        if (started || Bearings.resolveTrackerKey().isBlank()) return
        started = true

        val lit = runCatching { AppsFlyerLib.getInstance().start(host) }
        if (lit.isFailure) {
            Journal.w(TAG, "SDK would not start: ${lit.exceptionOrNull()?.message}")
            finish(emptyMap())
            deepLink.complete(Unit)
            return
        }
        Journal.i(TAG, "SDK started from ${host.javaClass.simpleName}")
    }

    fun retrace(host: Activity) {
        if (!started) { ignite(host); return }
        val last = settled ?: return
        if (last.isNotEmpty()) return

        settled = null
        reasked = true
        reaskedAt = System.currentTimeMillis()
        attribution = CompletableDeferred()
        runCatching { AppsFlyerLib.getInstance().start(host) }
        Journal.i(TAG, "attribution asked again now the link is up")
    }

    suspend fun awaitAttribution(timeoutMs: Long): Map<String, Any?> = coroutineScope {
        val link = async { withTimeoutOrNull(Bearings.deepLinkWaitMs) { deepLink.await() } }
        val data = async { awaitConversion(timeoutMs) }
        link.await()
        data.await()
    }

    private suspend fun awaitConversion(timeoutMs: Long): Map<String, Any?> {
        val window = if (reasked) minOf(timeoutMs, RETRACE_WAIT_MS) else timeoutMs
        val fromSdk = withTimeoutOrNull(window) { attribution.await() } ?: emptyMap()
        if (fromSdk.isNotEmpty()) return fromSdk

        if (reasked) {
            val waited = System.currentTimeMillis() - reaskedAt
            if (waited < RETRACE_WAIT_MS) delay(RETRACE_WAIT_MS - waited)
        }

        val fromGcd = fetchGcd()
        if (fromGcd.isNullOrEmpty()) return fromSdk
        Journal.i(TAG, "attribution recovered from GCD")
        settled = fromGcd
        return fromGcd
    }

    private val conversionListener = object : AppsFlyerConversionListener {

        override fun onConversionDataSuccess(raw: MutableMap<String, Any?>) {
            Journal.i(TAG, "onConversionDataSuccess")
            bg.launch {
                val status = raw["af_status"]?.toString().orEmpty()
                val resolved = if (status.equals("Organic", ignoreCase = true)) {
                    delay(Bearings.organicGcdDelayMs)
                    fetchGcd() ?: raw
                } else {
                    raw
                }
                finish(resolved)
            }
        }

        override fun onConversionDataFail(err: String?) {
            Journal.w(TAG, "onConversionDataFail: $err")
            finish(emptyMap())
        }

        override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
            data?.forEach { (k, v) -> deepLinkParams[k] = v }
        }

        override fun onAttributionFailure(err: String?) {
            Journal.w(TAG, "onAttributionFailure: $err")
            finish(emptyMap())
        }
    }

    private val deepLinkListener = DeepLinkListener { result ->
        if (result.status != DeepLinkResult.Status.FOUND) {
            Journal.i(TAG, "deep link status=${result.status}")
            deepLink.complete(Unit)
            return@DeepLinkListener
        }
        runCatching {
            val click = result.deepLink.clickEvent
            click.keys().forEach { k -> deepLinkParams[k] = click.opt(k) }
        }
        deepLink.complete(Unit)
    }

    private fun finish(data: Map<String, Any?>) {
        settled = data
        if (!attribution.isCompleted) attribution.complete(data)
    }

    /**
     * Asks the GCD API for AppsFlyer's own record of this install; null if it
     * has none to give.
     *
     * Note where the dev key goes: the query string. Put it in an
     * `Authorization: Bearer` header and every call comes back 400 saying
     * `"The 'devkey' query parameter is not found"` — at which point this
     * fallback recovers nothing at all and does so quietly, because it is only
     * ever a fallback. That is pitfalls #37, and it hid for weeks.
     */
    private suspend fun fetchGcd(): Map<String, Any?>? = withContext(Dispatchers.IO) {
        try {
            val uid = AppsFlyerLib.getInstance().getAppsFlyerUID(ctx) ?: return@withContext null
            val base = Bearings.resolveGcdBase()
            val key = Bearings.resolveTrackerKey()
            if (base.isBlank() || key.isBlank()) return@withContext null
            val req = Request.Builder()
                .url("$base${Bearings.bundleId}?devkey=$key&device_id=$uid")
                .addHeader("User-Agent", ClientTag.value)
                .get()
                .build()
            Journal.i(TAG, "GCD GET $base${Bearings.bundleId}?devkey=***&device_id=$uid")
            gcdHttp.newCall(req).execute().use { resp ->
                // Log the body, not just the status: on its own a 400 cannot
                // distinguish a request we built wrong from an install
                // AppsFlyer has simply not been told about, and those two need
                // opposite fixes.
                val body = resp.body?.string().orEmpty()
                Journal.i(TAG, "GCD HTTP ${resp.code} body=${body.take(2_000)}")
                if (!resp.isSuccessful || body.isBlank()) return@withContext null
                jsonToMap(JSONObject(body))
            }
        } catch (e: Exception) {
            Journal.w(TAG, "GCD fetch failed: ${e.message}")
            null
        }
    }

    fun getAppsFlyerId(): String =
        AppsFlyerLib.getInstance().getAppsFlyerUID(ctx) ?: ""

    /**
     * Assembles what gets posted to the config endpoint. Conversion fields go in
     * untouched, deep-link keys fill any gaps they leave, and the device fields
     * are written last so they overwrite either.
     */
    fun buildRequestBody(
        attributionData: Map<String, Any?>,
        os: String,
        locale: String,
        pushToken: String?,
        firebaseProject: String
    ): JSONObject = JSONObject().apply {
        attributionData.forEach { (k, v) -> if (v != null) put(k, v.toString()) }
        deepLinkParams.forEach { (k, v) -> if (v != null && !has(k)) put(k, v.toString()) }

        put("af_id", getAppsFlyerId())
        put("bundle_id", Bearings.bundleId)
        put("os", os)
        put("store_id", Bearings.bundleId)
        put("locale", locale)
        if (!pushToken.isNullOrBlank()) put("push_token", pushToken)
        if (firebaseProject.isNotBlank()) put("firebase_project_id", firebaseProject)
        Journal.i(TAG, "request body composed (${length()} fields)")
    }

    fun shutdown() {
        bg.cancel()
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any?> =
        obj.keys().asSequence().associateWith { obj.opt(it) }

    private companion object {
        const val TAG = "OriginSurvey"
        const val RETRACE_WAIT_MS = 8_000L
    }
}
