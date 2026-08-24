package com.crystalolympus.crystalolympusgame.terrain

import android.content.res.Configuration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.almanac.Cartouche
import kotlin.math.max
import kotlin.math.min

/**
 * Moves the WebView up so the keyboard cannot cover the field being typed into.
 * It slides; it never resizes.
 *
 * Resizing is what this replaced, and it could not be made to work. Cut a
 * WebView's height and the page's viewport shrinks with it, which sets three
 * things going at once inside the engine: a fresh layout, a scroll to bring the
 * focused field into view computed against the layout from a moment ago, and
 * then a clamp of that scroll to the layout that actually lands. The second and
 * third disagree, and the flick a user sees is that disagreement. No amount of
 * smoothing out here helps, because none of it happens out here.
 *
 * A slide avoids all of it. Height is untouched, nothing reflows, and the view
 * moves by a transform that costs a single compositor frame. Some of the bottom
 * of the view ends up off-screen, but always less than the keyboard's height, so
 * the keyboard is over it anyway.
 *
 * The page reports where its field is, in device pixels rather than as a
 * proportion of the viewport — the viewport being the one quantity that can move
 * underneath a measurement. It is read as focus lands, which is early enough for
 * the slide to travel up with the keyboard, and read again after the keyboard
 * stops, in case the page has scrolled the field somewhere else meanwhile. Since
 * both readings are absolute positions feeding the same calculation, the second
 * can only replace the first; they cannot accumulate.
 */
class ViewGlider(private val host: View, private val vault: Cartouche) {

    private var web: WebView? = null

    /** Edges of the focused field, device pixels from the WebView's top. -1 = unknown. */
    private var fieldTop = -1f
    private var fieldBottom = -1f

    /** Focus landed in an opaque frame, so the numbers describe the frame itself. */
    private var framed = false

    private var keyboard = 0
    private var riding = false

    /**
     * The keyboard's eventual height, which lets the slide aim at its destination
     * rather than follow the animation there. Part-way through an opening the
     * system quotes heights noticeably larger than the one it finishes on; trust
     * those and the view overshoots and then falls back at the end.
     *
     * The height a previous opening actually stopped at has never once been wrong,
     * so it is remembered between runs and rotations, separately per orientation.
     */
    private var declaredKb = 0
    private var settledKb = 0

    private val probe = Runnable {
        web?.evaluateJavascript("window.$REPORT && window.$REPORT();", null)
    }

    /** Attaches to the window root. Call it once and no more. */
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

    /** Every WebView that goes on screen needs this, replacements included. */
    fun bind(view: WebView) {
        web = view
        forget()
        view.translationY = 0f
        view.addJavascriptInterface(Bridge(), BuildConfig.JS_BRIDGE_NAME)
    }

    /** Nothing is focused on a page that has only just arrived. */
    fun forget() {
        host.removeCallbacks(probe)
        fieldTop = -1f
        fieldBottom = -1f
        framed = false
        apply(animated = false)
    }

    /** Rotating leaves every stored pixel describing a viewport that is gone. */
    fun remeasure() {
        forget()
        settledKb = 0
        if (keyboard > 0) ask(SETTLE_MS)
    }

    private fun ask(delay: Long) {
        host.removeCallbacks(probe)
        host.postDelayed(probe, delay)
    }

    /** A height the keyboard came to rest at is reliable, so write it down. */
    private fun settle(height: Int) {
        keyboard = height
        if (height <= 0 || height == settledKb) return
        settledKb = height
        vault.rememberKeyboardRest(portrait(), height)
    }

    /** A mid-animation height, capped at wherever the keyboard is going to stop. */
    private fun rise(height: Int) {
        val rest = resting()
        keyboard = if (rest > 0) min(height, rest) else height
    }

    /** Best available answer, in order: measured now, measured before, or declared. */
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
     * Hides the keyboard inset from the WebView underneath.
     *
     * Leave it visible and the engine responds to it: it narrows its own visual
     * viewport and drags the page up towards the field by an amount that varies
     * between openings on one and the same field. Anything this class contributed
     * then stacked on top, so the page overshot until the next reading corrected
     * it. Withholding the inset leaves exactly one thing moving the page.
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

    /** Distance to lift the view, given where the keyboard currently is. */
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
         * @param top,bottom where the field's edges are on screen, device pixels
         *   from the WebView's top, already accounting for anything the page has
         *   scrolled on its own.
         * @param frame set when the page could name a nested frame but not the
         *   field inside it.
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

    /** Goes into every page; its job is to say where the focused field is. */
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

          // Indexed, not `$bridge.focus`. The interface name is generated per
          // project and may start with a digit — fine as a window property, not
          // legal as an identifier — and dotted access would then be a parse
          // error taking the entire script with it.
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
            setTimeout(report, ${BuildConfig.JS_FOCUS_RECHECK_MS});
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

        /** Gap left below the field, measured in CSS pixels. */
        const val MARGIN_CSS = 10

        /** Of a field taller than the gap above the keyboard, show at least this much. */
        const val HEAD_DP = 96f

        /** Time allowed for the page's own scrolling to finish before measuring. */
        const val SETTLE_MS = 140L

        const val PAN_MS = 160L
    }
}
