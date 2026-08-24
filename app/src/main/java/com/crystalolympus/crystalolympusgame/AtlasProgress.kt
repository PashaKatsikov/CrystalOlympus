package com.crystalolympus.crystalolympusgame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.crystalolympus.crystalolympusgame.R

/**
 * The branded splash, with a bar that moves.
 *
 * It runs one of two ways. Given a duration, the bar simply fills over
 * [durationMs] and then calls [onComplete]. Set indeterminate instead and it
 * eases towards 80% under a travelling highlight and stops there, because the
 * work behind it — routing, in [Landfall]'s case — takes as long as it takes.
 * The caption keeps its dots moving throughout so nothing ever looks wedged.
 * Once the caller knows the answer, [complete] drives the bar the rest of the
 * way and hands over after that. Neither half of this is cosmetic: leaving on a
 * bar frozen at 70% reads as a hang, and holding a full one reads as a hang
 * too.
 */
class AtlasProgress(
    context: Context,
    private val durationMs: Long = 2400L,
    private val indeterminate: Boolean = false,
    private val onComplete: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val accent = 0xFFF2C14E.toInt()

    private var startTime = 0L
    private var finished = false

    private var closingAt = 0L
    private var closingFrom = 0f
    private var onClosed: (() -> Unit)? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    /**
     * The answer is in. Fill what is left of the bar, pause just long enough for
     * that to land, then continue. A second call does nothing.
     */
    fun complete(after: () -> Unit) {
        if (closingAt != 0L) return
        onClosed = after
        closingFrom = shownProgress
        closingAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private var shownProgress = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        drawBackground(canvas, w, h)

        val elapsed = SystemClock.uptimeMillis() - startTime

        // The dots run off elapsed time, so they move even when nothing else does.
        val dots = ".".repeat(((elapsed / 400L) % 4L).toInt())
        textPaint.textSize = h * 0.030f
        textPaint.color = accent
        textPaint.setShadowLayer(h * 0.006f, 0f, h * 0.003f, Color.BLACK)
        canvas.drawText("Loading$dots", w / 2f, h * 0.885f, textPaint)
        textPaint.clearShadowLayer()

        if (indeterminate) {
            val progress = if (closingAt != 0L) {
                val run = ((SystemClock.uptimeMillis() - closingAt).toFloat() / CLOSE_MS)
                    .coerceIn(0f, 1f)
                closingFrom + (1f - closingFrom) * run
            } else {
                val t = elapsed / 1000f
                (0.80f * (1f - Math.exp((-t / 1.1f).toDouble()).toFloat())).coerceIn(0f, 0.80f)
            }
            shownProgress = progress
            drawLoadingBar(canvas, w, h, progress, shimmer = true, shimmerPhase = elapsed)
            if (progress >= 1f && !finished) {
                finished = true
                val handOver = onClosed
                onClosed = null
                postDelayed({ handOver?.invoke() }, HOLD_MS)
                return
            }
        } else {
            val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            shownProgress = progress
            drawLoadingBar(canvas, w, h, progress, shimmer = false, shimmerPhase = 0L)
            if (progress >= 1f && !finished) {
                finished = true
                post { onComplete() }
                return
            }
        }
        postInvalidateOnAnimation()
    }

    private val bmpCache = HashMap<Int, Bitmap?>()

    private fun cached(res: Int): Bitmap? = bmpCache.getOrPut(res) {
        try {
            BitmapFactory.decodeResource(resources, res)
        } catch (e: Throwable) {
            null
        }
    }

    /** Picks the artwork for the current orientation and centre-crops it. */
    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        val landscape = w > h
        val res = if (landscape) R.drawable.atl_loading_landscape
                  else          R.drawable.atl_loading_portrait
        val bmp = cached(res)
        if (bmp == null) {
            paint.shader = LinearGradient(
                0f, 0f, 0f, h, 0xFF2A0A10.toInt(), 0xFF12050A.toInt(), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            return
        }
        val scale = maxOf(w / bmp.width, h / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        canvas.drawBitmap(
            bmp, null,
            RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f),
            null
        )
    }

    private fun drawLoadingBar(
        canvas: Canvas, w: Float, h: Float, progress: Float,
        shimmer: Boolean, shimmerPhase: Long
    ) {
        val barW = w * 0.62f
        val barH = h * 0.022f
        val x0 = (w - barW) / 2f
        val y0 = h * 0.915f
        val r = barH / 2f

        paint.style = Paint.Style.FILL
        paint.color = 0x88000000.toInt()
        canvas.drawRoundRect(x0, y0, x0 + barW, y0 + barH, r, r, paint)

        if (progress > 0f) {
            val fillW = barW * progress
            paint.shader = LinearGradient(
                x0, y0, x0 + barW, y0,
                0xFFE53935.toInt(), accent, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(x0, y0, x0 + fillW, y0 + barH, r, r, paint)
            paint.shader = null

            if (shimmer && fillW > barH) {
                val sw = barW * 0.18f
                val cycle = 1400f
                val phase = (shimmerPhase % cycle.toLong()) / cycle
                val cx = x0 + (fillW + sw) * phase - sw
                val left = cx.coerceIn(x0, x0 + fillW)
                val right = (cx + sw).coerceIn(x0, x0 + fillW)
                if (right > left) {
                    paint.shader = LinearGradient(
                        left, y0, right, y0, 0x00FFFFFF, 0x66FFFFFF, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(left, y0, right, y0 + barH, r, r, paint)
                    paint.shader = null
                }
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.0035f
        paint.color = accent
        canvas.drawRoundRect(x0, y0, x0 + barW, y0 + barH, r, r, paint)
        paint.style = Paint.Style.FILL
    }

    private companion object {
        /** Time the bar spends covering the last stretch to full. */
        const val CLOSE_MS = 280f

        /** Pause on a full bar before leaving. Stretch this and it reads as a stall. */
        const val HOLD_MS = 420L
    }
}
