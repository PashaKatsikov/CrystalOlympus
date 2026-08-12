package com.crystalolympus.crystalolympusgame.boot

import android.content.res.Configuration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.push.Store
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps the focused field clear of the keyboard by sliding the WebView, never by
 * shortening it.
 *
 * Shortening was the original sin. Taking height away from a WebView shrinks the
 * page's viewport, and the page answers with a burst of work in one go: it re-lays out,
 * scrolls the focused field into view against the layout it had a moment earlier, then
 * clamps that scroll to the layout that finally lands. The last two steps disagree, and
 * their disagreement is the flick — no amount of easing on this side could remove it,
 * because none of it was on this side.
 *
 * Sliding has none of that. The page keeps its full height and never reflows; the view
 * is moved by a transform, which costs one compositor frame. The strip left bare at the
 * bottom is always shorter than the keyboard, so the keyboard covers it.
 *
 * Where the field is comes from the page itself, in device pixels — deliberately not as
 * a share of the page's viewport, because that viewport is exactly the thing that can
 * change out from under the measurement. It is read the moment the field takes focus,
 * so the slide can ride the keyboard up in step with it, then read again once the
 * keyboard has settled, in case the page moved the field on its own in the meantime.
 * Both readings feed the same absolute sum, so a later one can only ever correct an
 * earlier one — the two cannot add up.
 */
class KeyboardSlide(private val host: View, private val vault: Store) {

    private var web: WebView? = null

    /** Focused field, in device pixels down the WebView. Negative when unknown. */
    private var fieldTop = -1f
    private var fieldBottom = -1f

    /** The focus went into a frame we cannot see into; [fieldTop] is the frame's. */
    private var framed = false

    private var keyboard = 0
    private var riding = false

    /**
     * How tall the keyboard will be when it stops, so the slide can go straight to
     * where it belongs instead of chasing the animation past it. Mid-animation the
     * system reports heights well above the one it settles on, and a slide that took
     * those at face value would sail past its mark and drop back at the end.
     *
     * A height an earlier opening actually came to rest at is the one figure that has
     * never lied, so it is kept across runs and rotations, one per orientation.
     */
    private var declaredKb = 0
    private var settledKb = 0

    private val probe = Runnable {
        web?.evaluateJavascript("window.$REPORT && window.$REPORT();", null)
    }

    /** Installs on the window root, once. */
    fun install() {
        ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            if (!riding) {
                settle(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                apply(animated = keyboard > 0)
                if (keyboard > 0) ask(SETTLE_MS)
            }
            withoutKeyboard(insets)
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            host,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) riding = true
                }

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        declaredKb = bounds.upperBound.bottom
                    }
                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    running: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (riding) {
                        rise(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                        apply(animated = false)
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    riding = false
                    declaredKb = 0
                    ViewCompat.requestApplyInsets(host)
                    if (keyboard > 0) ask(SETTLE_MS) else apply(animated = false)
                }
            }
        )

        ViewCompat.requestApplyInsets(host)
    }

    /** Call for every WebView the stage puts on screen, including replacements. */
    fun bind(view: WebView) {
        web = view
        forget()
        view.translationY = 0f
        view.addJavascriptInterface(Bridge(), BuildConfig.JS_BRIDGE_NAME)
    }

    /** A new page has nothing focused yet. */
    fun forget() {
        host.removeCallbacks(probe)
        fieldTop = -1f
        fieldBottom = -1f
        framed = false
        apply(animated = false)
    }

    /** After a rotation the stored pixels belong to the old viewport. */
    fun remeasure() {
        forget()
        settledKb = 0
        if (keyboard > 0) ask(SETTLE_MS)
    }

    private fun ask(delay: Long) {
        host.removeCallbacks(probe)
        host.postDelayed(probe, delay)
    }

    /** A height the keyboard has stopped at is the truth, and worth keeping. */
    private fun settle(height: Int) {
        keyboard = height
        if (height <= 0 || height == settledKb) return
        settledKb = height
        vault.rememberKeyboardRest(portrait(), height)
    }

    /** A height reported mid-animation, held to what the keyboard will come to rest at. */
    private fun rise(height: Int) {
        val rest = resting()
        keyboard = if (rest > 0) min(height, rest) else height
    }

    /** Where the keyboard is going to stop: measured this session, or last, or declared. */
    private fun resting(): Int {
        if (settledKb <= 0) {
            val kept = vault.keyboardRest(portrait())
            if (kept in 1 until host.height) settledKb = kept
        }
        return if (settledKb > 0) settledKb else declaredKb
    }

    private fun portrait(): Boolean =
        host.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE

    /**
     * Strips the keyboard out of what the WebView below is told about the window.
     *
     * Left in, the engine acts on it: it shrinks its own visual viewport and hauls the
     * page up towards the field, by an amount that differs from one opening to the next
     * on the very same field. Whatever this class then added rode on top of that, so the
     * page overshot until the next reading pulled it back. Keeping the keyboard from the
     * engine leaves one hand on the wheel.
     */
    private fun withoutKeyboard(insets: WindowInsetsCompat): WindowInsetsCompat =
        runCatching {
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build()
        }.getOrDefault(insets)

    private fun apply(animated: Boolean) {
        val view = web ?: return
        val target = -offset(view)
        view.animate().cancel()
        if (animated && view.translationY != target) {
            view.animate().translationY(target).setDuration(PAN_MS).start()
        } else {
            view.translationY = target
        }
    }

    /** How far up the view has to go for the current keyboard height. */
    private fun offset(view: View): Float {
        val height = keyboard
        val span = view.height
        if (height <= 0 || span <= 0 || fieldBottom < 0f) return 0f

        val aim: Float
        val ceiling: Float
        if (framed) {
            aim = fieldBottom
            ceiling = min(height.toFloat(), max(0f, fieldTop))
        } else {
            aim = min(fieldBottom, fieldTop + HEAD_DP * view.resources.displayMetrics.density)
            ceiling = height.toFloat()
        }

        return (aim - (span - height)).coerceIn(0f, ceiling)
    }

    private inner class Bridge {

        /**
         * @param top,bottom edges of the focused field on screen, in device pixels down
         *   the WebView, with whatever the page has already done folded in.
         * @param frame the page could only point at a nested frame, not at the field.
         */
        @JavascriptInterface
        fun focus(frame: Boolean, top: Double, bottom: Double) {
            val view = web ?: return
            view.post {
                framed = frame
                fieldTop = top.toFloat()
                fieldBottom = bottom.toFloat()
                if (keyboard > 0) apply(animated = !riding)
            }
        }
    }

    /** Reports where the focused field sits on screen. Injected into every page. */
    val script: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val bridge = BuildConfig.JS_BRIDGE_NAME
        """
        (function(){
          if (window.$GUARD) return;
          window.$GUARD = true;

          function editable(el){
            if (!el) return false;
            var tag = el.tagName;
            if (tag === 'INPUT') {
              var type = (el.type || 'text').toLowerCase();
              return type !== 'checkbox' && type !== 'radio' && type !== 'button' &&
                     type !== 'submit' && type !== 'reset' && type !== 'file' &&
                     type !== 'range' && type !== 'image' && type !== 'color';
            }
            return tag === 'TEXTAREA' || el.isContentEditable === true;
          }

          function box(el, win){
            if (el.isContentEditable) {
              try {
                var sel = win.getSelection();
                if (sel && sel.rangeCount) {
                  var r = sel.getRangeAt(0).getBoundingClientRect();
                  if (r && r.height > 0) return r;
                }
              } catch(e) {}
            }
            return el.getBoundingClientRect();
          }

          function locate(){
            var el = document.activeElement;
            var win = window;
            var offset = 0;
            var depth = 0;
            while (el && (el.tagName === 'IFRAME' || el.tagName === 'FRAME') && depth++ < 4) {
              var outline = el.getBoundingClientRect();
              var doc = null;
              try { doc = el.contentDocument; } catch(e) { doc = null; }
              var inner = doc ? doc.activeElement : null;
              if (!inner || inner === doc.body) {
                return { frame: true, top: offset + outline.top, bottom: offset + outline.bottom };
              }
              watch(doc);
              win = el.contentWindow || win;
              offset += outline.top;
              el = inner;
            }
            if (!editable(el)) return null;
            var r = box(el, win);
            return { frame: false, top: offset + r.top, bottom: offset + r.bottom };
          }

          // Bracket access, not `$bridge.focus`: the injected interface name is a
          // per-project fingerprint and can begin with a digit, which is a valid
          // window property key but an illegal JS identifier — dotted access would
          // be a parse error that kills this whole script.
          var bridge = window["$bridge"];

          function report(){
            if (!bridge) return;
            var at = locate();
            if (!at) return;
            var vv = window.visualViewport;
            var lift = vv ? vv.offsetTop : 0;
            var zoom = (vv && vv.scale) ? vv.scale : 1;
            var px = (window.devicePixelRatio || 1) * zoom;
            try {
              bridge.focus(
                at.frame,
                (at.top - lift) * px,
                (at.bottom - lift + $MARGIN_CSS) * px
              );
            } catch(e) {}
          }

          function kick(){
            report();
            setTimeout(report, 200);
          }

          var queued = false;
          function soon(){
            if (queued) return;
            queued = true;
            var run = function(){ queued = false; report(); };
            if (window.requestAnimationFrame) requestAnimationFrame(run);
            else setTimeout(run, 16);
          }
          if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', soon);
            window.visualViewport.addEventListener('scroll', soon);
          }

          function watch(doc){
            try {
              if (!doc || doc.$GUARD) return;
              doc.$GUARD = true;
              doc.addEventListener('focusin', kick, true);
            } catch(e) {}
          }

          window.$REPORT = report;
          watch(document);
        })();
        """.trimIndent()
    }

    private companion object {
        val GUARD = BuildConfig.JS_KEYBOARD_SENTINEL + "Kb"
        val REPORT = BuildConfig.JS_KEYBOARD_SENTINEL + "KbReport"

        /** Breathing room under the field, in CSS pixels. */
        const val MARGIN_CSS = 10

        /** How much of a tall field has to be visible for typing to make sense. */
        const val HEAD_DP = 96f

        /** Grace for the page to finish any scrolling of its own before it is read. */
        const val SETTLE_MS = 140L

        const val PAN_MS = 160L
    }
}
