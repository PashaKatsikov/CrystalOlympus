package com.crystalolympus.crystalolympusgame.meridian

import androidx.activity.ComponentActivity
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.CrystalOlympusApp
import com.crystalolympus.crystalolympusgame.legend.Bearings
import com.crystalolympus.crystalolympusgame.legend.SurveyVerdict
import com.crystalolympus.crystalolympusgame.compass.SurveyClient
import com.crystalolympus.crystalolympusgame.bearing.Journal
import com.crystalolympus.crystalolympusgame.bearing.HostRule
import com.crystalolympus.crystalolympusgame.survey.NoticeSetup
import com.crystalolympus.crystalolympusgame.almanac.Cartouche
import com.crystalolympus.crystalolympusgame.almanac.Cartouche.RunChannel
import com.crystalolympus.crystalolympusgame.waypoint.LinkSensor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * The decision an UNDECIDED install needs, lifted out of [Landfall] so that the
 * game's [com.crystalolympus.crystalolympusgame.LoadingActivity] can work
 * through it while it is warming assets up. That is the whole reason the
 * organic path shows one loading screen instead of two — the router no longer
 * needs a splash of its own in front of the game's.
 *
 * Resolving is all it does; it starts no activity. The only mark it leaves is
 * the verdict written into [Cartouche], which is what the launcher used to do.
 */
class LandfallDecision(private val activity: ComponentActivity) {

    sealed class Outcome {
        /** Open the game. */
        data object Native : Outcome()

        /** Open the shell on this URL. */
        data class Gray(val url: String) : Outcome()

        /** Nothing to decide with — there was no working link. */
        data class Offline(val savedUrl: String?) : Outcome()
    }

    private val vault = Cartouche(activity.applicationContext)
    private val wire = LinkSensor(activity.applicationContext)

    /**
     * The same sequence `Landfall.handleFirstLaunch` runs. An install that was
     * offline on its first frame never reaches this method — the caller has
     * already routed that one — so the grace period below is only for a link
     * that goes away in between.
     */
    suspend fun resolveFirstLaunch(): Outcome {
        val forced = BuildConfig.DEBUG_FORCE_URL
        if (BuildConfig.DEBUG && forced.isNotBlank()) {
            Journal.w(TAG, "DEBUG: forcing stream URL")
            return Outcome.Gray(forced)
        }

        val coldPush = vault.coldPushUrl
        if (!coldPush.isNullOrBlank() && HostRule.accepts(coldPush)) {
            Journal.i(TAG, "Cold push URL → STREAM directly")
            vault.coldPushUrl = null
            vault.runChannel = RunChannel.STREAM
            return Outcome.Gray(coldPush)
        }

        if (!online()) {
            Journal.i(TAG, "No link on first decision → offline")
            return Outcome.Offline(null)
        }

        val tracker = (activity.applicationContext as CrystalOlympusApp).trackingDispatch
        tracker.ignite(activity)
        tracker.retrace(activity)
        val attribution = tracker.awaitAttribution(Bearings.attributionFirstMs)

        val result = fetchConfig(attribution)
        if (result.active && !result.destination.isNullOrBlank()) {
            vault.runChannel = RunChannel.STREAM
            vault.destinationUrl = result.destination
            vault.urlExpiresAt = result.expiresAt
            Journal.i(TAG, "backend → STREAM")
            return Outcome.Gray(result.destination)
        }

        // Nothing ever reverses a "no", so only a real one may be written: the
        // endpoint has to have replied, and knowing this install's attribution.
        when {
            !result.answered ->
                Journal.i(TAG, "endpoint unreachable → game, decision left open")
            attribution.isEmpty() ->
                Journal.i(TAG, "no attribution behind the answer → game, decision left open")
            else -> {
                vault.runChannel = RunChannel.NATIVE
                Journal.i(TAG, "backend → NATIVE")
            }
        }
        return Outcome.Native
    }

    /** Ask once, then allow a link that is mid-handshake a moment to finish. */
    private suspend fun online(): Boolean {
        if (wire.isConnected()) return true
        return withTimeoutOrNull(Bearings.connectGraceMs) {
            wire.connectivityFlow.first { it }
        } ?: false
    }

    private suspend fun fetchConfig(attribution: Map<String, Any?>): SurveyVerdict {
        val tracker = (activity.applicationContext as CrystalOlympusApp).trackingDispatch
        val fcmToken = NoticeSetup.obtainToken(activity.applicationContext)

        val body = tracker.buildRequestBody(
            attributionData = attribution,
            os              = "Android",
            locale          = Locale.getDefault().toLanguageTag().replace('-', '_'),
            pushToken       = fcmToken,
            firebaseProject = Bearings.resolveAnalyticsProject()
        )
        return SurveyClient().fetchChannel(body)
    }

    private companion object { const val TAG = "LandfallDecision" }
}
