package com.crystalolympus.crystalolympusgame.terrain

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.ScreenFit
import com.crystalolympus.crystalolympusgame.legend.Bearings
import com.crystalolympus.crystalolympusgame.bearing.Journal
import com.crystalolympus.crystalolympusgame.bearing.ClientTag
import com.crystalolympus.crystalolympusgame.survey.NoticeBridge
import com.crystalolympus.crystalolympusgame.almanac.Cartouche
import com.crystalolympus.crystalolympusgame.waypoint.LinkSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The WebView, filling the screen, plus everything needed to make it behave like
 * an app rather than a browser.
 *
 * Black is painted at every layer, which is what stops the system's default
 * white flashing through as a page loads or unloads. Padding follows the camera
 * cutout and nothing else: the top inset in portrait, the side insets in
 * landscape. Losing the connection navigates to [LinkDownCard] off the OS
 * callback, with no probe in between. The keyboard belongs to [ViewGlider],
 * which slides the view instead of resizing it, working alongside the injected
 * safe-area reset.
 *
 * Redirect hops and failed loads spend their time under a cover, so what the
 * user sees is a page that finished — never one of the hops on the way, and
 * never the engine's own error page. Push URLs arrive either as intent extras or
 * through onNewIntent depending on whether this activity was already alive.
 */
class MeridianView : AppCompatActivity() {

    private lateinit var wv: WebView

    /** Outer view, never padded. Hosts the keyboard inset and paints the black. */
    private lateinit var root: FrameLayout

    /** Inner view, padded for the cutout. The WebView and the cover live in here. */
    private lateinit var shell: FrameLayout
    private lateinit var vault: Cartouche
    private lateinit var wire: LinkSensor
    private lateinit var keyboard: ViewGlider
    private val scope = CoroutineScope(Dispatchers.Main)

    /** The most recent main-frame URL that finished. A dead renderer resumes here. */
    private var lastMainFrameUrl: String? = null

    /**
     * The furthest main-frame URL seen, whether or not it finished. A redirect
     * loop picks up from this rather than from the last page that settled: go
     * back to where the chain started and it simply runs the same loop again
     * (pitfalls #30).
     */
    private var deepestHop: String? = null

    private var redirectRetries = 0
    /** How many plain load failures have been re-tried under the opaque cover. */
    private var recoveryAttempts = 0
    private var rendererRecoveries = 0

    /** onPageFinished fires for a failed load too; this stops it clearing the budget. */
    private var loadFailed = false
    /** Holds the cover in place while a queued reload is on its way. */
    private var retryPending = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val fc = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        fc.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
                ?: arrayOf()
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = Cartouche(applicationContext)
        wire  = LinkSensor(applicationContext)
        NoticeBridge.shellAlive = true

        // Hanging the keyboard inset on the outer, unpadded view means the slide
        // is measured against the whole window; only the inner one is held clear
        // of the cutout. Both stay black throughout.
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        shell = FrameLayout(this).apply { fitsSystemWindows = false }
        keyboard = ViewGlider(root, vault)
        recreateWebView()
        root.addView(shell, matchParent())
        setContentView(root)

        // These four calls are ordered, not grouped. Edge-to-edge and the cutout
        // mode have to be set before the keyboard's listener and animation
        // callback go on, or the IME inset animation never reaches the host view
        // and the slide has nothing to follow.
        hideSystemUi()
        enableNotchCutout()
        applyInsets()
        keyboard.install()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (wv.canGoBack()) wv.goBack()
            }
        })

        // First URL to load, in order of precedence: a push that arrived warm, a
        // push stored while the app was cold, the intent extra, then the saved one.
        val warmPush = intent.takeIf { it.getBooleanExtra(EXTRA_PUSH_WARM, false) }
            ?.getStringExtra(EXTRA_PUSH_URL)
        val coldPush = vault.consumeColdPushUrl()
        val initial  = warmPush
            ?: coldPush
            ?: intent.getStringExtra(EXTRA_STREAM_URL)
            ?: vault.destinationUrl

        if (initial.isNullOrBlank()) {
            Journal.w(TAG, "No URL to load — finishing")
            finish(); return
        }
        Journal.i(TAG, "loading initial URL (warm=${warmPush != null}, cold=${coldPush != null})")
        wv.loadUrl(initial)

        // Leave on the OS callback rather than waiting for a request to fail.
        scope.launch {
            wire.connectivityFlow.collect { online ->
                if (!online) {
                    Journal.i(TAG, "Connectivity lost (callback) → LinkDownCard")
                    goOffline()
                }
            }
        }

        // And a poll on top, for the case the callback does not cover: a page that
        // has already finished loading issues no further requests, so switching
        // the radio off underneath it produces no failure to react to.
        scope.launch {
            while (true) {
                delay(Bearings.heartbeatMs)
                if (navigatedOffline) continue
                if (!wire.isConnected()) {
                    Journal.i(TAG, "Heartbeat: no network → LinkDownCard")
                    goOffline()
                }
            }
        }

        scope.launch {
            delay(Bearings.safeAreaDelayMs)
            injectSafeAreaKill()
        }
    }

    /** Constructs the WebView, mounts it and attaches every listener it needs. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun recreateWebView() {
        wv = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = buildUserAgent()
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true
                blockNetworkImage = false
                // Popups open in this same view. Allow genuine second windows and
                // the WebView starts insisting on somewhere to put them, then
                // throws when there is nowhere ("Parent WebView cannot host its
                // own popup window").
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = true
            }
            setBackgroundColor(Color.BLACK)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
        }
        shell.addView(wv, matchParent())
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        wv.webViewClient   = buildClient()
        wv.webChromeClient = buildChromeClient()
        keyboard.bind(wv)
    }

    // ── Loading cover ───────────────────────────────────────────────────

    private var cover: View? = null
    private var coverJob: Job? = null

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )

    /**
     * Puts a scrim and a spinner over the page while the next one is fetched. The
     * WebView carries on painting underneath, so following a link leaves the user
     * looking at a dimmed version of what they were reading until the destination
     * is ready. A chain of redirects passes entirely behind this and never gets a
     * frame of its own.
     *
     * @param solid conceals the page rather than dimming it. That is for a failed
     *   load, where the thing underneath is the engine's error page and must not
     *   be visible for even a moment.
     */
    private fun raiseCover(solid: Boolean = false) {
        coverJob?.cancel()
        coverJob = null
        val existing = cover
        if (existing != null) {
            existing.animate().cancel()
            existing.alpha = 1f
            if (solid) existing.setBackgroundColor(COVER_SOLID)
            existing.bringToFront()
            return
        }
        val fresh = FrameLayout(this).apply {
            setBackgroundColor(if (solid) COVER_SOLID else COVER_SCRIM)
            // Eats touches — whatever is underneath is already leaving.
            isClickable = true
            addView(
                android.widget.ProgressBar(this@MeridianView).apply {
                    isIndeterminate = true
                    indeterminateTintList =
                        android.content.res.ColorStateList.valueOf(GOLD)
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER
                )
            )
        }
        cover = fresh
        shell.addView(fresh, matchParent())
        // A page that goes quiet must not be able to keep the screen forever.
        scope.launch {
            delay(COVER_MAX_MS)
            if (cover === fresh) {
                Journal.w(TAG, "Loading cover timed out")
                dropCover(0L)
            }
        }
    }

    /**
     * @param after how long to wait before the page is visible again. A redirect
     *   hop finishes and begins the next load within a frame or two, and this
     *   delay is what stops the cover flickering off and back on in the gap.
     */
    private fun dropCover(after: Long = COVER_LINGER_MS) {
        val current = cover ?: return
        // There is already a reload queued, and revealing the page now would put
        // the failed one on screen for however long that takes to arrive.
        if (retryPending) return
        coverJob?.cancel()
        coverJob = scope.launch {
            delay(after)
            if (cover !== current) return@launch
            cover = null
            current.animate().alpha(0f).setDuration(150L).withEndAction {
                shell.removeView(current)
            }.start()
        }
    }

    // ── WebView clients ────────────────────────────────────────────────

    private var pageStartMs = 0L

    private fun buildClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            val u = req.url.toString()
            val scheme = u.substringBefore(':').lowercase()
            return when {
                scheme in WEB_SCHEMES -> {
                    if (req.isForMainFrame) deepestHop = u
                    false  // this WebView takes it
                }
                scheme == "intent" -> { openIntentUri(u); true }
                // What is left addresses another app — a bank, a wallet, a
                // messenger, a store. Passing one to the WebView earns an
                // ERR_UNKNOWN_URL_SCHEME and nothing else, and trying to keep a
                // list of the schemes worth recognising is a losing game.
                else -> { openExternally(u); true }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            pageStartMs = System.currentTimeMillis()
            loadFailed = false
            keyboard.forget()
            // Server-side 30x hops do not all pass through
            // shouldOverrideUrlLoading, so recording what the engine committed
            // to here is the other half of the trail.
            if (url != BLANK) deepestHop = url
            // Every load gets a cover, not just the first. Through a redirect
            // chain the user stays behind the scrim — see-through, so the page
            // they came from is still there — and meets only the page that
            // settles, never a hop or an error.
            if (url != BLANK) raiseCover()
            Journal.i(TAG, "onPageStarted")
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (url == BLANK) return
            // First paint is the earliest opportunity, and it is early enough
            // that the safe-area reset and the keyboard reporter are both in
            // place before a field can be tapped.
            injectSafeAreaKill()
            view.evaluateJavascript(keyboard.script, null)
        }

        override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
            if (!req.isForMainFrame) return
            loadFailed = true
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) err.errorCode else -1
            val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) err.description.toString() else ""
            val failing = runCatching { req.url.toString() }.getOrNull()
            Journal.w(TAG, "main-frame error $code on ${req.url.host}")

            // A custom scheme that gets this far has already gone out to the
            // system. Whatever is behind it was never broken, so uncover it.
            if (code == ERROR_UNSUPPORTED_SCHEME) {
                dropCover(0L)
                return
            }

            // This comes first, ahead of any probe that might take seconds,
            // because the engine has already drawn its error page and that page
            // must not be seen. An opaque cover buries it while the reload runs.
            raiseCover(solid = true)

            val isLoop = code == -9 || code == -1007 ||
                    desc.contains("too_many", ignoreCase = true)
            if (isLoop && redirectRetries < Bearings.redirectRetryMax) {
                redirectRetries++
                // Chromium stops at 20 hops and affiliate chains regularly run
                // past that. Continue from the furthest hop observed; going back
                // to the chain's entry point just retraces the same loop.
                val resumeAt = deepestHop ?: failing
                if (!resumeAt.isNullOrBlank()) {
                    Journal.i(TAG, "redirect loop, resuming attempt $redirectRetries")
                    retryAfterPause(resumeAt, LOOP_RETRY_PAUSE_MS)
                    return
                }
            }

            // DNS and disconnect codes settle the question; probing adds nothing.
            if (code in NETWORK_ERRORS) {
                goOffline()
                return
            }

            val retryTarget = failing?.takeIf { it.startsWith("http") } ?: lastMainFrameUrl
            if (recoveryAttempts < MAX_RECOVERY_ATTEMPTS && !retryTarget.isNullOrBlank()) {
                recoveryAttempts++
                retryAfterPause(retryTarget)
                return
            }

            if (!wire.isConnected()) {
                goOffline()
                return
            }
            // With a working link the offline screen would just send the user
            // back here immediately, so keep working on the last good page.
            val fallback = lastMainFrameUrl?.takeIf { it != failing } ?: failing
            if (!fallback.isNullOrBlank()) {
                retryAfterPause(fallback, SLOW_RETRY_PAUSE_MS)
            } else {
                goOffline()
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            Journal.i(TAG, "onPageFinished")
            if (url == BLANK) return
            // A load that failed arrives here as well, error page committed and
            // reload already queued. Treat that as success and the retry budget
            // resets and the error page is handed back to the user.
            if (loadFailed) return
            redirectRetries = 0
            recoveryAttempts = 0
            lastMainFrameUrl = url
            deepestHop = url
            injectSafeAreaKill()
            view.evaluateJavascript(keyboard.script, null)
            dropCover()
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail
        ): Boolean {
            Journal.w(TAG, "render process gone, crashed=${detail.didCrash()}")
            if (isFinishing || view !== wv) {
                runCatching { view.destroy() }
                return true
            }
            if (rendererRecoveries >= MAX_RENDERER_RECOVERIES) {
                Journal.w(TAG, "renderer recovery budget exhausted → LinkDownCard")
                goOffline()
                return true
            }
            rendererRecoveries++
            replaceWebView()
            return true
        }
    }

    /**
     * Reloads with the opaque cover up, so the failed page stays out of sight.
     * The pause costs nothing the user can perceive because it lands mid
     * navigation: the engine is still tearing the failed one down, and a load
     * issued re-entrantly from the error callback gets dropped or postponed.
     */
    private fun retryAfterPause(url: String, pause: Long = RETRY_PAUSE_MS) {
        raiseCover(solid = true)
        retryPending = true
        scope.launch {
            delay(pause)
            retryPending = false
            if (!isFinishing && !isDestroyed) runCatching { wv.loadUrl(url) }
        }
    }

    /**
     * Stands up a new WebView after the renderer dies and restores the last good
     * page. Nothing can be done with the dead one, not even asking it what it had
     * been displaying, so [lastMainFrameUrl] is the only record left.
     */
    private fun replaceWebView() {
        val resumeAt = lastMainFrameUrl ?: vault.destinationUrl ?: return
        val dead = wv
        shell.removeView(dead)
        runCatching { dead.destroy() }
        recreateWebView()
        wv.loadUrl(resumeAt)
    }

    private fun buildChromeClient() = object : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            // Catches a page that reports its way to 100% without ever finishing.
            // about:blank is loaded on one path only, the exit to the offline
            // screen, so its progress tells us nothing about what is awaited.
            if (newProgress < 100 || view.url == BLANK) return
            dropCover()
        }

        override fun onShowFileChooser(
            view: WebView, callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams
        ): Boolean {
            fileCallback?.onReceiveValue(arrayOf())
            fileCallback = callback
            return try {
                filePicker.launch(params.createIntent())
                true
            } catch (_: Exception) {
                fileCallback = null
                false
            }
        }
    }

    // ── Destinations that belong to another app ─────────────────────────

    private fun openExternally(url: String) {
        val intent = runCatching {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull() ?: return
        launchOrIgnore(intent)
    }

    /**
     * An intent:// URI names the app it wants and normally carries a
     * browser_fallback_url too, which leaves three separate things to attempt
     * before the user ends up staring at nothing happening.
     */
    private fun openIntentUri(url: String) {
        val parsed = runCatching {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return
        val fallback = parsed.getStringExtra("browser_fallback_url")
        parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        parsed.addCategory(Intent.CATEGORY_BROWSABLE)
        parsed.component = null
        parsed.selector = null

        if (launchOrIgnore(parsed)) return
        // The app it asked for may not be installed even though something else
        // on the device handles the scheme perfectly well.
        parsed.`package` = null
        if (launchOrIgnore(parsed)) return
        if (!fallback.isNullOrBlank()) wv.loadUrl(fallback)
    }

    private fun launchOrIgnore(intent: Intent): Boolean =
        runCatching { startActivity(intent) }.isSuccess

    // ── Navigation ──────────────────────────────────────────────────────

    @Volatile private var navigatedOffline = false

    private fun goOffline() {
        if (navigatedOffline) return
        navigatedOffline = true
        val cur = lastMainFrameUrl ?: wv.url
        try { wv.stopLoading(); wv.loadUrl(BLANK) } catch (_: Exception) {}
        startActivity(Intent(this, LinkDownCard::class.java).apply {
            if (!cur.isNullOrBlank()) putExtra(LinkDownCard.EXTRA_RETURN_URL, cur)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigatedOffline = false

        if (intent.getBooleanExtra(EXTRA_PUSH_WARM, false)) {
            val url = intent.getStringExtra(EXTRA_PUSH_URL)
            if (!url.isNullOrBlank() && com.crystalolympus.crystalolympusgame.bearing.HostRule.accepts(url)) {
                Journal.i(TAG, "warm push → loading")
                wv.loadUrl(url)
                return
            }
        }

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        val current = wv.url
        val target = streamUrl ?: vault.destinationUrl
        if (!target.isNullOrBlank() &&
            (current.isNullOrBlank() || current == BLANK || current != target)) {
            Journal.i(TAG, "onNewIntent → reloading target")
            wv.loadUrl(target)
        }
    }

    // ── Insets / safe area ──────────────────────────────────────────────

    /**
     * Pads by orientation so the WebView is never underneath the camera cutout:
     * the top inset in portrait, and both side insets in landscape, since the
     * cutout could be on either edge once the device is turned.
     */
    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(shell) { v, insets ->
            val isLandscape = resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val cutout = insets.displayCutout
            // The cutout, and nothing else. ScreenFit has already hidden the
            // navigation bar, so padding for that only buys a side margin which
            // jumps every time the bar comes briefly back — opening the keyboard
            // in landscape does it — and the WebView shows that as the page width
            // twitching. The keyboard is ViewGlider's job and it slides the view
            // rather than padding it, so there is no bottom inset here either.
            val topPad   = if (!isLandscape) (cutout?.safeInsetTop   ?: 0) else 0
            val leftPad  = if (isLandscape)  (cutout?.safeInsetLeft  ?: 0) else 0
            val rightPad = if (isLandscape)  (cutout?.safeInsetRight ?: 0) else 0
            v.setPadding(leftPad, topPad, rightPad, 0)
            insets
        }
        ViewCompat.requestApplyInsets(shell)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ViewCompat.requestApplyInsets(shell)
        // The focused field is somewhere else in the new viewport, which makes
        // every measurement taken in the old one worthless.
        keyboard.remeasure()
        ScreenFit.apply(this)
    }

    private fun hideSystemUi() = ScreenFit.apply(this)

    private fun enableNotchCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // ── JS injections ───────────────────────────────────────────────────

    /**
     * Flattens the safe-area variables the page reads. This window is already
     * padded for the cutout, so a page that respects `env(safe-area-inset-*)`
     * of its own accord adds a second empty strip above ours. Setting the
     * variables to zero takes that strip away.
     *
     * The one thing it may not do is touch the page's box model. A previous
     * attempt zeroed `padding-left`, `padding-right` and `margin` across
     * `html, body, #__nuxt, #app, #root`, and since those declarations are
     * precisely how sites build their gutters, every layout collapsed against
     * both screen edges — pitfalls #10. What is left is `padding-top` alone,
     * applied only to wrapper classes known to add a status-bar offset
     * themselves.
     */
    private fun injectSafeAreaKill() {
        val sentinel = BuildConfig.JS_SAFE_AREA_SENTINEL
        val running  = sentinel + "R"
        val wrappers = BuildConfig.JS_SAFE_AREA_SELECTORS
        val fastMs   = BuildConfig.JS_REAPPLY_FAST_MS
        val slowMs   = BuildConfig.JS_REAPPLY_SLOW_MS
        val pollMs   = BuildConfig.JS_REAPPLY_POLL_MS
        wv.evaluateJavascript("""
            (function(){
              if(window.$running) return; window.$running = true;
              var CSS_ID = '$sentinel';
              var CSS_TEXT =
                ':root{' +
                  '--safe-area-inset-top:0px!important;' +
                  '--safe-area-inset-right:0px!important;' +
                  '--safe-area-inset-bottom:0px!important;' +
                  '--safe-area-inset-left:0px!important;' +
                  '--sat:0px!important;--sar:0px!important;' +
                  '--sab:0px!important;--sal:0px!important;' +
                  '--safe-top:0px!important;--safe-right:0px!important;' +
                  '--safe-bottom:0px!important;--safe-left:0px!important;' +
                '}' +
                '$wrappers{' +
                  'padding-top:0!important;' +
                '}';
              function apply(){
                var head = document.head || document.documentElement;
                if (!head) return;
                var m = document.querySelector('meta[name="viewport"]');
                if (m && !/viewport-fit\s*=\s*contain/i.test(m.getAttribute('content') || '')) {
                  var c = (m.getAttribute('content') || '')
                    .replace(/,?\s*viewport-fit\s*=\s*\w+/ig, '').trim();
                  m.setAttribute('content', c + (c ? ', ' : '') + 'viewport-fit=contain');
                }
                var s = document.getElementById(CSS_ID);
                if (!s) {
                  s = document.createElement('style');
                  s.id = CSS_ID;
                  head.appendChild(s);
                }
                if (s.textContent !== CSS_TEXT) s.textContent = CSS_TEXT;
                if (head.lastElementChild !== s) head.appendChild(s);
              }
              apply();
              ['pushState','replaceState'].forEach(function(fn){
                var orig = history[fn];
                history[fn] = function(){
                  var r = orig.apply(this, arguments);
                  setTimeout(apply, $fastMs);
                  setTimeout(apply, $slowMs);
                  return r;
                };
              });
              window.addEventListener('popstate', function(){ setTimeout(apply, $fastMs); });
              setInterval(apply, $pollMs);
            })();
        """.trimIndent(), null)
    }

    // ── User agent ──────────────────────────────────────────────────────

    private fun buildUserAgent(): String = ClientTag.value

    override fun onStart() {
        super.onStart()
        navigatedOffline = false
        NoticeBridge.onWarmUrl = { url ->
            runOnUiThread {
                Journal.i(TAG, "NoticeBridge warm URL → loading")
                try { wv.loadUrl(url) } catch (_: Exception) {}
            }
        }
        NoticeBridge.consume()?.let { url ->
            Journal.i(TAG, "queued push URL → loading")
            runCatching { wv.loadUrl(url) }
        }
    }

    override fun onStop() {
        if (NoticeBridge.onWarmUrl != null) NoticeBridge.onWarmUrl = null
        super.onStop()
    }

    override fun onDestroy() {
        if (NoticeBridge.onWarmUrl != null) NoticeBridge.onWarmUrl = null
        NoticeBridge.shellAlive = false
        scope.cancel()
        try { wv.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_PUSH_URL   = "push_url"
        const val EXTRA_PUSH_WARM  = "push_warm"
        private const val TAG = "MeridianView"

        /** What this WebView can render itself. The rest addresses another app. */
        private val WEB_SCHEMES =
            setOf("http", "https", "about", "data", "blob", "file", "javascript")

        private const val BLANK = "about:blank"

        /** Enough to span a single redirect hop without being noticeable. */
        private const val COVER_LINGER_MS = 220L

        /** Hard ceiling on how long any page may keep the screen, done or not. */
        private const val COVER_MAX_MS = 20_000L

        /** Renderer restarts allowed per Activity before giving up and going offline. */
        private const val MAX_RENDERER_RECOVERIES = 3

        /** Plain load failures retried under the opaque cover before going offline. */
        private const val MAX_RECOVERY_ATTEMPTS = 2

        /** Translucent, so the page being left shows through a redirect and no hop
         *  ever gets a blank frame to itself. */
        private const val COVER_SCRIM = 0xB2000000.toInt()

        /** Fully opaque, for burying the engine's error page during a retry. */
        private const val COVER_SOLID = 0xFF0B0203.toInt()

        /** Colour of the spinner on the cover. */
        private const val GOLD = 0xFFF2C14E.toInt()

        /** Waits before a queued reload, one per cause. Each is dead time already. */
        private const val RETRY_PAUSE_MS = 500L
        private const val LOOP_RETRY_PAUSE_MS = 60L
        private const val SLOW_RETRY_PAUSE_MS = 3_000L

        private val NETWORK_ERRORS = setOf(-2, -6, -7, -8, -11)
    }
}
