package com.crystalolympus.crystalolympusgame.boot

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
import com.crystalolympus.crystalolympusgame.WindowGlue
import com.crystalolympus.crystalolympusgame.net.Env
import com.crystalolympus.crystalolympusgame.pkg0.Trace
import com.crystalolympus.crystalolympusgame.pkg0.UserAgent
import com.crystalolympus.crystalolympusgame.prefs.PushRelay
import com.crystalolympus.crystalolympusgame.push.Store
import com.crystalolympus.crystalolympusgame.cfg.Uplink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen WebView shell.
 *
 *  - Black background everywhere (no Android system flash on load / page exit).
 *  - Safe-area paddings: top inset in portrait, left+right insets in landscape
 *    (handles notch / cutout cameras).
 *  - Instant SignalLostScreen navigation on connectivity loss — no DNS probe.
 *  - Keyboard handled by [KeyboardSlide] (the view slides, it never resizes) plus the
 *    safe-area CSS kill injection.
 *  - A loading cover over redirect hops and failed loads, so the user only ever sees
 *    a finished page — never an intermediate hop or the WebView's own error page.
 *  - Cold + warm push URL routing through Intent extras / onNewIntent.
 *  - User-Agent ends with "appid/<bundleId> appname/<AppName>".
 */
class OrbitShell : AppCompatActivity() {

    private lateinit var wv: WebView

    /** Unpadded outer view: keyboard-inset host and black backdrop. */
    private lateinit var root: FrameLayout

    /** Cutout-padded inner view that actually holds the WebView and the cover. */
    private lateinit var shell: FrameLayout
    private lateinit var vault: Store
    private lateinit var wire: Uplink
    private lateinit var keyboard: KeyboardSlide
    private val scope = CoroutineScope(Dispatchers.Main)

    /** Last main-frame URL that actually settled. What a renderer recovery reloads. */
    private var lastMainFrameUrl: String? = null

    /**
     * Deepest main-frame URL seen, settled or not. A redirect loop is resumed
     * from here rather than from the last settled page — restarting the chain
     * from its entry point only walks into the same loop again (pitfalls #30).
     */
    private var deepestHop: String? = null

    private var redirectRetries = 0
    /** Ordinary load failures re-tried behind the solid cover. */
    private var recoveryAttempts = 0
    private var rendererRecoveries = 0

    /** A failed load still reaches onPageFinished; without this it resets the budget. */
    private var loadFailed = false
    /** Keeps the cover raised across the reload a retry queues up. */
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
        vault = Store(applicationContext)
        wire  = Uplink(applicationContext)
        PushRelay.shellAlive = true

        // The keyboard-inset host is the unpadded outer view, so the slide is
        // measured against the full window; only the inner shell is kept clear of
        // the cutout. Background stays black at all times.
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        shell = FrameLayout(this).apply { fitsSystemWindows = false }
        keyboard = KeyboardSlide(root, vault)
        recreateWebView()
        root.addView(shell, matchParent())
        setContentView(root)

        // Order matters: edge-to-edge (setDecorFitsSystemWindows=false) and the
        // cutout mode must be in place BEFORE the keyboard listener/animation
        // callback are installed, otherwise the IME insets animation never reaches
        // the host and the slide has nothing to ride (matches foollegends).
        hideSystemUi()
        enableNotchCutout()
        applyInsets()
        keyboard.install()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (wv.canGoBack()) wv.goBack()
            }
        })

        // Choose the initial URL: warm push > intent extra > saved.
        val warmPush = intent.takeIf { it.getBooleanExtra(EXTRA_PUSH_WARM, false) }
            ?.getStringExtra(EXTRA_PUSH_URL)
        val coldPush = vault.consumeColdPushUrl()
        val initial  = warmPush
            ?: coldPush
            ?: intent.getStringExtra(EXTRA_STREAM_URL)
            ?: vault.destinationUrl

        if (initial.isNullOrBlank()) {
            Trace.w(TAG, "No URL to load — finishing")
            finish(); return
        }
        Trace.i(TAG, "loading initial URL (warm=${warmPush != null}, cold=${coldPush != null})")
        wv.loadUrl(initial)

        // Connectivity monitoring — react instantly on OS callback.
        scope.launch {
            wire.connectivityFlow.collect { online ->
                if (!online) {
                    Trace.i(TAG, "Connectivity lost (callback) → SignalLostScreen")
                    goOffline()
                }
            }
        }

        // Heartbeat — covers the case where the page is already loaded and the user
        // turns off the internet: no WebView request fails, so we actively probe.
        scope.launch {
            while (true) {
                delay(Env.heartbeatMs)
                if (navigatedOffline) continue
                if (!wire.isConnected()) {
                    Trace.i(TAG, "Heartbeat: no network → SignalLostScreen")
                    goOffline()
                }
            }
        }

        scope.launch {
            delay(Env.safeAreaDelayMs)
            injectSafeAreaKill()
        }
    }

    /** Builds the WebView, puts it in the container and hooks everything to it. */
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
                // Performance + compatibility.
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true
                blockNetworkImage = false
                // Popups stay in this view. Asking for real second windows is what
                // makes the WebView demand a host for them and throw when it cannot
                // get one ("Parent WebView cannot host its own popup window").
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
     * Dims the page and shows a spinner while the next one is on its way. The page
     * underneath keeps being painted by the WebView, so a click on a link leaves the
     * user looking at what they were reading, dimmed, until the destination is ready
     * — including through a chain of redirects, which never gets a moment on screen
     * of its own.
     *
     * @param solid hides the page completely instead of dimming it. Used after a
     *   failed load, where what sits underneath is the WebView's own error page,
     *   which must never be seen.
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
            // Swallows taps: the page underneath is on its way out.
            isClickable = true
            addView(
                android.widget.ProgressBar(this@OrbitShell).apply {
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
        // A page that never reports back must not hold the screen for good.
        scope.launch {
            delay(COVER_MAX_MS)
            if (cover === fresh) {
                Trace.w(TAG, "Loading cover timed out")
                dropCover(0L)
            }
        }
    }

    /**
     * @param after grace before the page is handed back. A redirect hop finishes and
     *   starts the next load within a frame or two, and this is what keeps the cover
     *   from blinking off and on between them.
     */
    private fun dropCover(after: Long = COVER_LINGER_MS) {
        val current = cover ?: return
        // A reload is already on its way in; handing the screen back now would show
        // the failed page for the gap in between.
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
                    false  // load inside this WebView
                }
                scheme == "intent" -> { openIntentUri(u); true }
                // Everything else is an app link: banks, wallets, messengers, stores.
                // Handing it to the WebView would only produce ERR_UNKNOWN_URL_SCHEME,
                // and the list of schemes worth knowing about has no end.
                else -> { openExternally(u); true }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            pageStartMs = System.currentTimeMillis()
            loadFailed = false
            keyboard.forget()
            // shouldOverrideUrlLoading does not see every server-side 30x, so the
            // URL the engine actually committed to is the other half of the trail.
            if (url != BLANK) deepestHop = url
            // Cover every load, not just the first: while a redirect chain runs,
            // the user stays behind the loading scrim (semi-transparent, so the
            // page they came from shows through) and only meets the final page
            // once it settles — never an intermediate hop or an error page.
            if (url != BLANK) raiseCover()
            Trace.i(TAG, "onPageStarted")
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (url == BLANK) return
            // Inject as early as the first paint so the safe-area reset and the
            // keyboard reporter are live before the user can touch a field.
            injectSafeAreaKill()
            view.evaluateJavascript(keyboard.script, null)
        }

        override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
            if (!req.isForMainFrame) return
            loadFailed = true
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) err.errorCode else -1
            val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) err.description.toString() else ""
            val failing = runCatching { req.url.toString() }.getOrNull()
            Trace.w(TAG, "main-frame error $code on ${req.url.host}")

            // A custom scheme reaching this point was already handed to the system;
            // the page behind it is still fine, so give it straight back.
            if (code == ERROR_UNSUPPORTED_SCHEME) {
                dropCover(0L)
                return
            }

            // Before anything else, and before any probing that could take seconds:
            // the WebView paints its own error page for this, and it must not be
            // seen. A SOLID cover hides it completely while we reload behind it.
            raiseCover(solid = true)

            val isLoop = code == -9 || code == -1007 ||
                    desc.contains("too_many", ignoreCase = true)
            if (isLoop && redirectRetries < Env.redirectRetryMax) {
                redirectRetries++
                // Chromium gives up after 20 hops; affiliate chains are routinely
                // longer. Resume from the deepest hop we saw rather than restart
                // the chain from its entry point, which only walks the same loop.
                val resumeAt = deepestHop ?: failing
                if (!resumeAt.isNullOrBlank()) {
                    Trace.i(TAG, "redirect loop, resuming attempt $redirectRetries")
                    retryAfterPause(resumeAt, LOOP_RETRY_PAUSE_MS)
                    return
                }
            }

            // A DNS / disconnect code is conclusive — no point probing the link.
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
            // The link is alive, so the offline screen would only bounce straight
            // back here. Keep retrying the last good page instead.
            val fallback = lastMainFrameUrl?.takeIf { it != failing } ?: failing
            if (!fallback.isNullOrBlank()) {
                retryAfterPause(fallback, SLOW_RETRY_PAUSE_MS)
            } else {
                goOffline()
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            Trace.i(TAG, "onPageFinished")
            if (url == BLANK) return
            // A failed load still lands here, with the error page committed and a
            // reload already queued. Counting it as settled would reset the retry
            // budget and hand the error page back to the user.
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
            Trace.w(TAG, "render process gone, crashed=${detail.didCrash()}")
            if (isFinishing || view !== wv) {
                runCatching { view.destroy() }
                return true
            }
            if (rendererRecoveries >= MAX_RENDERER_RECOVERIES) {
                Trace.w(TAG, "renderer recovery budget exhausted → SignalLostScreen")
                goOffline()
                return true
            }
            rendererRecoveries++
            replaceWebView()
            return true
        }
    }

    /**
     * Reloads behind the solid cover, leaving the failed page hidden underneath.
     * The short pause is dead time in the middle of a navigation, not a delay the
     * user can feel: the engine is still unwinding the failed navigation and would
     * swallow or defer a re-entrant load called straight from the error callback.
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
     * Builds a fresh WebView after a renderer death and puts the last good page back.
     * The dead one cannot be reused for anything, including being asked what it was
     * showing, so [lastMainFrameUrl] is what there is to go on.
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
            // Backstop for a page that reports progress but never a finished load.
            // about:blank is only ever loaded on the way out to the offline screen,
            // so its progress says nothing about the page the user is waiting for.
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

    // ── Links the WebView cannot take ───────────────────────────────────

    private fun openExternally(url: String) {
        val intent = runCatching {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull() ?: return
        launchOrIgnore(intent)
    }

    /**
     * intent:// URIs name a target app and usually carry a browser_fallback_url, so
     * there are three things to try before the user is left looking at nothing.
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
        // The named app may be missing while some other app handles the scheme.
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
        startActivity(Intent(this, SignalLostScreen::class.java).apply {
            if (!cur.isNullOrBlank()) putExtra(SignalLostScreen.EXTRA_RETURN_URL, cur)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigatedOffline = false

        if (intent.getBooleanExtra(EXTRA_PUSH_WARM, false)) {
            val url = intent.getStringExtra(EXTRA_PUSH_URL)
            if (!url.isNullOrBlank() && com.crystalolympus.crystalolympusgame.pkg0.UrlGuard.accepts(url)) {
                Trace.i(TAG, "warm push → loading")
                wv.loadUrl(url)
                return
            }
        }

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        val current = wv.url
        val target = streamUrl ?: vault.destinationUrl
        if (!target.isNullOrBlank() &&
            (current.isNullOrBlank() || current == BLANK || current != target)) {
            Trace.i(TAG, "onNewIntent → reloading target")
            wv.loadUrl(target)
        }
    }

    // ── Insets / safe area ──────────────────────────────────────────────

    /**
     * Apply orientation-aware padding so the WebView never sits under the camera
     * notch / cutout.
     *   portrait  → top inset only
     *   landscape → left + right insets (cutout on either side)
     */
    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(shell) { v, insets ->
            val isLandscape = resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val cutout = insets.displayCutout
            // Camera-cutout safe area ONLY. The navigation bar is already hidden
            // by WindowGlue, so padding for it just adds a side margin that jumps
            // whenever the bar transiently reappears — e.g. as the keyboard opens
            // in landscape — and the WebView renders that jump as a width jitter.
            // The keyboard itself is handled by KeyboardSlide sliding the view,
            // not by padding here, so there is no bottom inset to apply either.
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
        // The field sits somewhere else in the new viewport, so anything measured in
        // the old one has to be thrown away.
        keyboard.remeasure()
        WindowGlue.apply(this)
    }

    private fun hideSystemUi() = WindowGlue.apply(this)

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
     * Safe-area CSS kill. The window already pads for the cutout, so a page that
     * also honours `env(safe-area-inset-*)` would leave a second empty band on
     * top of ours. Zeroing the variables removes that band.
     *
     * What it must not do is lay a finger on the page's own box model. An
     * earlier version zeroed `padding-left`, `padding-right` and `margin` on
     * `html, body, #__nuxt, #app, #root` — but sites build their gutters with
     * exactly those declarations, so the whole layout got squeezed flat against
     * both edges (pitfalls #10). Only `padding-top`, and only on the chrome
     * wrappers that are known to add a status-bar offset of their own.
     */
    private fun injectSafeAreaKill() {
        val sentinel = BuildConfig.JS_SAFE_AREA_SENTINEL
        val running  = sentinel + "R"
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
                '.gameview-mobile-header,.app-header{' +
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
                  setTimeout(apply, 80);
                  setTimeout(apply, 400);
                  return r;
                };
              });
              window.addEventListener('popstate', function(){ setTimeout(apply, 80); });
              setInterval(apply, 2500);
            })();
        """.trimIndent(), null)
    }

    // ── User agent ──────────────────────────────────────────────────────

    private fun buildUserAgent(): String = UserAgent.value

    override fun onStart() {
        super.onStart()
        navigatedOffline = false
        PushRelay.onWarmUrl = { url ->
            runOnUiThread {
                Trace.i(TAG, "PushRelay warm URL → loading")
                try { wv.loadUrl(url) } catch (_: Exception) {}
            }
        }
        PushRelay.consume()?.let { url ->
            Trace.i(TAG, "queued push URL → loading")
            runCatching { wv.loadUrl(url) }
        }
    }

    override fun onStop() {
        if (PushRelay.onWarmUrl != null) PushRelay.onWarmUrl = null
        super.onStop()
    }

    override fun onDestroy() {
        if (PushRelay.onWarmUrl != null) PushRelay.onWarmUrl = null
        PushRelay.shellAlive = false
        scope.cancel()
        try { wv.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_PUSH_URL   = "push_url"
        const val EXTRA_PUSH_WARM  = "push_warm"
        private const val TAG = "OrbitShell"

        /** Everything the WebView itself can take. Anything else belongs to an app. */
        private val WEB_SCHEMES =
            setOf("http", "https", "about", "data", "blob", "file", "javascript")

        private const val BLANK = "about:blank"

        /** Long enough to bridge one redirect hop, short enough not to be felt. */
        private const val COVER_LINGER_MS = 220L

        /** No page may hold the screen longer than this, finished or not. */
        private const val COVER_MAX_MS = 20_000L

        /** Renderer recoveries per Activity — beyond this we go offline. */
        private const val MAX_RENDERER_RECOVERIES = 3

        /** Ordinary load failures re-tried behind the solid cover, then offline. */
        private const val MAX_RECOVERY_ATTEMPTS = 2

        /** Semi-transparent scrim: the previous page reads through it during a
         *  redirect, so a hop never gets a blank moment on screen. */
        private const val COVER_SCRIM = 0xB2000000.toInt()

        /** Opaque cover used to hide the engine's own error page while a failed
         *  load is retried behind it. */
        private const val COVER_SOLID = 0xFF0B0203.toInt()

        /** Spinner tint on the cover. */
        private const val GOLD = 0xFFF2C14E.toInt()

        /** Pauses before a queued reload, by cause. All are dead time mid-navigation. */
        private const val RETRY_PAUSE_MS = 500L
        private const val LOOP_RETRY_PAUSE_MS = 60L
        private const val SLOW_RETRY_PAUSE_MS = 3_000L

        private val NETWORK_ERRORS = setOf(-2, -6, -7, -8, -11)
    }
}
