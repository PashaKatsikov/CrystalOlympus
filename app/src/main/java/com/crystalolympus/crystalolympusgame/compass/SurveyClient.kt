package com.crystalolympus.crystalolympusgame.compass

import com.crystalolympus.crystalolympusgame.legend.Bearings
import com.crystalolympus.crystalolympusgame.legend.SurveyVerdict
import com.crystalolympus.crystalolympusgame.bearing.Journal
import com.crystalolympus.crystalolympusgame.bearing.HostRule
import com.crystalolympus.crystalolympusgame.bearing.ClientTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The config endpoint's client, and nothing else: post the attribution body,
 * hand back what came of it.
 *
 * A URL inside a successful response still has to clear [HostRule] before it
 * leaves this class. One that does not is reported exactly like `ok:false`, so
 * the native part opens — but the mode is deliberately left unwritten. The
 * server did rule, our own gate then threw the ruling out, and that leaves the
 * question genuinely open for the next launch rather than settled by us.
 */
class SurveyClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(Bearings.configTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(Bearings.configTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchChannel(body: JSONObject): SurveyVerdict = withContext(Dispatchers.IO) {
        val endpoint = Bearings.resolveConfigEndpoint()
        if (endpoint.isBlank()) {
            Journal.w(TAG, "endpoint is blank — nobody to ask")
            return@withContext SurveyVerdict.unreachable()
        }
        Journal.i(TAG, "POST config endpoint")
        try {
            val req = Request.Builder()
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", ClientTag.value)
                .post(body.toString().toRequestBody(json))
                .build()

            http.newCall(req).execute().use { resp ->
                val code = resp.code
                val raw = resp.body?.string().orEmpty()
                Journal.i(TAG, "HTTP $code (${raw.length} chars)")

                if (code == 404) return@withContext SurveyVerdict.native()
                if (code !in 200..299) return@withContext SurveyVerdict.native()
                parseResponse(raw)
            }
        } catch (e: Exception) {
            Journal.w(TAG, "request never landed: ${e.message}")
            SurveyVerdict.unreachable()
        }
    }

    private fun parseResponse(raw: String): SurveyVerdict {
        if (raw.isBlank()) return SurveyVerdict.native()
        return try {
            val j = JSONObject(raw)
            val ok = j.optBoolean("ok", false)
            val url = j.optString("url", "")
            val exp = j.optLong("expires", 0L)
            if (ok && url.isNotBlank()) {
                if (!HostRule.accepts(url)) {
                    Journal.w(TAG, "endpoint URL rejected by allowlist")
                    return SurveyVerdict.native()
                }
                SurveyVerdict.stream(url, exp)
            } else {
                SurveyVerdict.native()
            }
        } catch (e: Exception) {
            Journal.w(TAG, "JSON parse error: ${e.message}")
            SurveyVerdict.native()
        }
    }

    private companion object { const val TAG = "SurveyClient" }
}
