package com.crystalolympus.crystalolympusgame.view

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.crystalolympus.crystalolympusgame.CrystalOlympusApp
import com.crystalolympus.crystalolympusgame.LoadingActivity
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.WindowGlue
import com.crystalolympus.crystalolympusgame.OrbitLoader
import com.crystalolympus.crystalolympusgame.net.Env
import com.crystalolympus.crystalolympusgame.net.GateResult
import com.crystalolympus.crystalolympusgame.pkg0.Trace
import com.crystalolympus.crystalolympusgame.pkg0.UrlGuard
import com.crystalolympus.crystalolympusgame.pkg0.UserAgent
import com.crystalolympus.crystalolympusgame.boot.OptInPrompt
import com.crystalolympus.crystalolympusgame.boot.SignalLostScreen
import com.crystalolympus.crystalolympusgame.boot.OrbitShell
import com.crystalolympus.crystalolympusgame.attr.CfgClient
import com.crystalolympus.crystalolympusgame.prefs.PushRelay
import com.crystalolympus.crystalolympusgame.prefs.PushSupport
import com.crystalolympus.crystalolympusgame.push.Store
import com.crystalolympus.crystalolympusgame.push.Store.RunChannel
import com.crystalolympus.crystalolympusgame.cfg.Uplink
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
 * Entry-point router. Shows the branded loading screen while performing the
 * gray/white decision in the background. State machine — every branch below
 * is grounded in `.cursor/rules/kotlin_launch_flow.mdc`; changes need a read
 * there first.
 *
 *  UNDECIDED (first launch):
 *    * No internet → SignalLostScreen on the first frame. Nothing started or
 *      persisted; the offline screen relaunches this router when the link
 *      returns.
 *    * Has internet → ignite AppsFlyer → attribution + deep link → config
 *      POST → decide.
 *      ok+url         → STREAM → optional OptInPrompt → OrbitShell
 *      otherwise      → the native part, and persist NATIVE only when the
 *                       endpoint really answered AND the attribution was
 *                       non-empty.
 *
 *  STREAM (was WebView last time):
 *    * No internet → SignalLostScreen with the saved URL.
 *    * Cold push URL → OrbitShell (highest priority).
 *    * Attribution → config POST.
 *      ok+url         → OrbitShell(newUrl)
 *      failure+saved  → OrbitShell(savedUrl)
 *      failure+none   → SignalLostScreen
 *
 *  NATIVE (was the game last time):
 *    * The game, always. Once native, stay native — including if a push URL
 *      arrives for this install.
 */
class LaunchGate : AppCompatActivity() {

    private lateinit var vault: Store
    private lateinit var wire: Uplink
    private var splash: OrbitLoader? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = Store(applicationContext)
        wire  = Uplink(applicationContext)

        val pushUrl = pushUrlFrom(intent)

        // Warm-tap hand-off: the shell is still alive, take the user right back
        // to the page they were on and drop this splash entirely.
        if (pushUrl != null && vault.runChannel == RunChannel.STREAM &&
            PushRelay.handOver(pushUrl)
        ) {
            Trace.i(TAG, "Warm push handed to the live shell")
            finish()
            return
        }

        // NATIVE users keep their game, regardless of what a push carries.
        // Hand straight to the game's own loader — no gray splash on top of it,
        // so the user sees a single loading screen, not one then another.
        if (vault.runChannel == RunChannel.NATIVE) {
            Trace.i(TAG, "Returning NATIVE — straight to the game loader")
            goNative()
            return
        }

        // Installed via a link with the radio off. Straight to the no-wifi
        // screen — no splash, no bar for a decision that will not be made.
        if (pushUrl == null &&
            vault.runChannel == RunChannel.UNDECIDED &&
            !wire.isConnected()
        ) {
            Trace.i(TAG, "First run with no link → offline first frame")
            startActivity(Intent(this, SignalLostScreen::class.java))
            finish()
            return
        }

        // Cold-start URL: STREAM channel already means WebView was the last face
        // of the app; UNDECIDED will consume it inside the resolver below. NATIVE
        // was handled above.
        if (pushUrl != null) {
            Trace.i(TAG, "Cold push URL received")
            vault.coldPushUrl = pushUrl
        }

        // Organic / first decision: the game's own LoadingActivity is the single
        // loading screen. It preloads the game AND runs the gray decision in the
        // background (see GateRouter), then goes to the game, the shell, or the
        // offline screen. Showing the router's own splash here as well is exactly
        // what made an organic user watch two loaders in a row.
        if (vault.runChannel == RunChannel.UNDECIDED) {
            Trace.i(TAG, "UNDECIDED → resolve inside the single game loader")
            startActivity(
                Intent(this, LoadingActivity::class.java)
                    .putExtra(LoadingActivity.EXTRA_RESOLVE_GATE, true)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
            return
        }

        // STREAM (returning WebView user): keep the branded router splash while a
        // fresh config call runs.
        val loader = OrbitLoader(this, indeterminate = true) { /* never auto-completes */ }
        splash = loader
        setContentView(loader)
        WindowGlue.apply(this)

        scope.launch { route() }
    }

    // ── State machine ───────────────────────────────────────────────────────

    private suspend fun route() {
        val forced = BuildConfig.DEBUG_FORCE_URL
        if (BuildConfig.DEBUG && forced.isNotBlank()) {
            Trace.w(TAG, "DEBUG: forcing stream URL")
            goGray(forced)
            return
        }

        val coldPush = vault.coldPushUrl
        if (!coldPush.isNullOrBlank() && UrlGuard.accepts(coldPush)) {
            Trace.i(TAG, "Cold push URL → STREAM directly")
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
        val attribution = tracker.awaitAttribution(Env.attributionFirstMs)

        val result = fetchConfig(attribution)
        if (result.active && !result.destination.isNullOrBlank()) {
            vault.runChannel     = RunChannel.STREAM
            vault.destinationUrl = result.destination
            vault.urlExpiresAt   = result.expiresAt
            goGray(result.destination)
        } else {
            // A "no" sticks forever, so it has to be a real one — the endpoint
            // did answer, and it did with this install's attribution in hand.
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
            goNative()
        }
    }

    private suspend fun handleOnlineReturn() {
        if (!ensureInternet(isFirstLaunch = false)) return

        val coldPush = vault.consumeColdPushUrl()
        if (!coldPush.isNullOrBlank() && UrlGuard.accepts(coldPush)) {
            goGray(coldPush)
            return
        }

        val savedUrl = if (vault.isUrlValid()) vault.destinationUrl else null

        val tracker = (applicationContext as CrystalOlympusApp).trackingDispatch
        tracker.ignite(this)
        tracker.retrace(this)
        val attribution = tracker.awaitAttribution(Env.attributionReturnMs)

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
                startActivity(Intent(this, SignalLostScreen::class.java))
                finish()
            }
        }
    }

    private suspend fun ensureInternet(isFirstLaunch: Boolean): Boolean {
        if (wire.isConnected()) return true

        // The old code left a collect on `wire.connectivityFlow` hanging past
        // the first `resume`. This variant hands ownership of a Job to the
        // suspended coroutine and cancels it on completion or cancellation.
        val gate = Channel<Boolean>(capacity = Channel.CONFLATED)
        val watcher: Job = scope.launch {
            wire.connectivityFlow.collect { ok -> gate.trySend(ok) }
        }
        val online = try {
            withTimeoutOrNull(Env.connectGraceMs) {
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
            Intent(this, SignalLostScreen::class.java).apply {
                if (!savedUrl.isNullOrBlank())
                    putExtra(SignalLostScreen.EXTRA_RETURN_URL, savedUrl)
            }
        )
        finish()
        return false
    }

    private suspend fun fetchConfig(attribution: Map<String, Any?>): GateResult {
        val tracker = (applicationContext as CrystalOlympusApp).trackingDispatch
        val fcmToken = PushSupport.obtainToken(applicationContext)

        val body = tracker.buildRequestBody(
            attributionData = attribution,
            os              = "Android",
            locale          = Locale.getDefault().toLanguageTag().replace('-', '_'),
            pushToken       = fcmToken,
            firebaseProject = Env.resolveAnalyticsProject()
        )
        return CfgClient().fetchChannel(body)
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    private fun handOver(go: () -> Unit) {
        val view = splash
        if (view == null) go() else view.complete { if (!isFinishing) go() }
    }

    /**
     * The native path owns exactly one loading screen: the game's own
     * [LoadingActivity], whose bar fills to 100% right before MainActivity.
     * So this hands over immediately and never runs the router's own bar out
     * to full — otherwise the user would watch one bar complete here and a
     * second one start over inside the game.
     */
    private fun goNative() {
        startActivity(
            Intent(this, LoadingActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun goGray(url: String) = handOver {
        val target = if (vault.shouldShowNotifScreen()) OptInPrompt::class.java
                     else OrbitShell::class.java
        val extra = if (target == OptInPrompt::class.java)
            OptInPrompt.EXTRA_TARGET_URL else OrbitShell.EXTRA_STREAM_URL
        startActivity(
            Intent(this, target)
                .putExtra(extra, url)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * The URL a notification tap carried, in either shape it can arrive in.
     *
     * A data-only message reaches [FcmReceiver], which builds the tap intent with
     * this class's own extras. A message that carries a `notification` block is
     * drawn by the Firebase SDK itself whenever the app is not in the
     * foreground — that path never runs our service, and the tap opens the
     * launcher with the raw `data` payload as plain string extras instead.
     * Reading only our own extras is why a pushed link was dropped and the
     * shell reopened on the previously saved page (pitfalls #32).
     */
    private fun pushUrlFrom(intent: Intent): String? {
        val own = if (intent.getBooleanExtra(EXTRA_FROM_PUSH, false))
            intent.getStringExtra(EXTRA_PUSH_URL) else null
        val raw = intent.getStringExtra(FCM_KEY_URL) ?: intent.getStringExtra(FCM_KEY_LINK)
        return (own ?: raw)?.trim()?.takeIf { it.isNotBlank() && UrlGuard.accepts(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pushUrl = pushUrlFrom(intent)

        if (!pushUrl.isNullOrBlank()) {
            when (vault.runChannel) {
                RunChannel.NATIVE -> {
                    Trace.i(TAG, "Push tap while NATIVE — game stays open")
                    return
                }
                RunChannel.STREAM -> {
                    if (PushRelay.handOver(pushUrl)) {
                        finish()
                        return
                    }
                    val dest = vault.destinationUrl?.takeIf { vault.isUrlValid() }
                    startActivity(
                        Intent(this, OrbitShell::class.java)
                            .putExtra(OrbitShell.EXTRA_STREAM_URL, dest ?: pushUrl)
                            .putExtra(OrbitShell.EXTRA_PUSH_URL, pushUrl)
                            .putExtra(OrbitShell.EXTRA_PUSH_WARM, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                    finish()
                }
                RunChannel.UNDECIDED -> vault.coldPushUrl = pushUrl
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /** Present the current UA to callers who need to log it. */
    fun currentUserAgent(): String = UserAgent.value

    companion object {
        private const val TAG = "LaunchGate"
        const val EXTRA_FROM_PUSH = "from_push"
        const val EXTRA_PUSH_URL  = "push_url"

        /** Payload keys FcmReceiver reads, and the ones the SDK forwards verbatim. */
        private const val FCM_KEY_URL  = "url"
        private const val FCM_KEY_LINK = "link"
    }
}
