package com.crystalolympus.crystalolympusgame.view

import androidx.activity.ComponentActivity
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.CrystalOlympusApp
import com.crystalolympus.crystalolympusgame.net.Env
import com.crystalolympus.crystalolympusgame.net.GateResult
import com.crystalolympus.crystalolympusgame.attr.CfgClient
import com.crystalolympus.crystalolympusgame.pkg0.Trace
import com.crystalolympus.crystalolympusgame.pkg0.UrlGuard
import com.crystalolympus.crystalolympusgame.push.Store
import com.crystalolympus.crystalolympusgame.push.Store.RunChannel
import com.crystalolympus.crystalolympusgame.cfg.Uplink
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The first-launch (UNDECIDED) gray/white decision, factored out of [LaunchGate] so
 * the game's own [com.crystalolympus.crystalolympusgame.LoadingActivity] can run it
 * while it preloads. That is what collapses the organic path to a single loading
 * screen: the router no longer shows its own splash ahead of the game's loader.
 *
 * It only resolves — it never navigates. Side effects are limited to persisting the
 * verdict into [Store], exactly as the launcher did before.
 */
class GateRouter(private val activity: ComponentActivity) {

    sealed class Outcome {
        /** The native game. */
        data object Native : Outcome()

        /** The WebView shell, at this URL. */
        data class Gray(val url: String) : Outcome()

        /** No usable connection to decide with. */
        data class Offline(val savedUrl: String?) : Outcome()
    }

    private val vault = Store(activity.applicationContext)
    private val wire = Uplink(activity.applicationContext)

    /**
     * Mirrors `LaunchGate.handleFirstLaunch`. The caller has already sent the
     * no-connection-on-the-first-frame case straight to the offline screen; the
     * grace check here only covers a link that drops between then and now.
     */
    suspend fun resolveFirstLaunch(): Outcome {
        val forced = BuildConfig.DEBUG_FORCE_URL
        if (BuildConfig.DEBUG && forced.isNotBlank()) {
            Trace.w(TAG, "DEBUG: forcing stream URL")
            return Outcome.Gray(forced)
        }

        val coldPush = vault.coldPushUrl
        if (!coldPush.isNullOrBlank() && UrlGuard.accepts(coldPush)) {
            Trace.i(TAG, "Cold push URL → STREAM directly")
            vault.coldPushUrl = null
            vault.runChannel = RunChannel.STREAM
            return Outcome.Gray(coldPush)
        }

        if (!online()) {
            Trace.i(TAG, "No link on first decision → offline")
            return Outcome.Offline(null)
        }

        val tracker = (activity.applicationContext as CrystalOlympusApp).trackingDispatch
        tracker.ignite(activity)
        tracker.retrace(activity)
        val attribution = tracker.awaitAttribution(Env.attributionFirstMs)

        val result = fetchConfig(attribution)
        if (result.active && !result.destination.isNullOrBlank()) {
            vault.runChannel = RunChannel.STREAM
            vault.destinationUrl = result.destination
            vault.urlExpiresAt = result.expiresAt
            Trace.i(TAG, "backend → STREAM")
            return Outcome.Gray(result.destination)
        }

        // A "no" sticks forever, so it has to be a real one — the endpoint did
        // answer, and it did with this install's attribution in hand.
        when {
            !result.answered ->
                Trace.i(TAG, "endpoint unreachable → game, decision left open")
            attribution.isEmpty() ->
                Trace.i(TAG, "no attribution behind the answer → game, decision left open")
            else -> {
                vault.runChannel = RunChannel.NATIVE
                Trace.i(TAG, "backend → NATIVE")
            }
        }
        return Outcome.Native
    }

    /** Immediate check, then a short grace for a link that is still coming up. */
    private suspend fun online(): Boolean {
        if (wire.isConnected()) return true
        return withTimeoutOrNull(Env.connectGraceMs) {
            wire.connectivityFlow.first { it }
        } ?: false
    }

    private suspend fun fetchConfig(attribution: Map<String, Any?>): GateResult {
        val tracker = (activity.applicationContext as CrystalOlympusApp).trackingDispatch
        val fcmToken = vault.fcmToken ?: getFcmToken()?.also { vault.fcmToken = it }

        val body = tracker.buildRequestBody(
            attributionData = attribution,
            os              = "Android",
            locale          = Locale.getDefault().toLanguageTag().replace('-', '_'),
            pushToken       = fcmToken,
            firebaseProject = Env.resolveAnalyticsProject()
        )
        return CfgClient().fetchChannel(body)
    }

    private suspend fun getFcmToken(): String? =
        withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
                }
            }
        }

    private companion object { const val TAG = "GateRouter" }
}
