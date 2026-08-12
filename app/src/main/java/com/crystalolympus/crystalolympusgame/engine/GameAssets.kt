package com.crystalolympus.crystalolympusgame.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumMap
import kotlin.math.roundToInt

/**
 * Decodes the shipped artwork into ready-to-draw bitmaps.
 *
 * The source art ships as a handful of large sheets, so every sheet is decoded once and then sliced
 * into the individual sprites that reference it. Transparent padding is measured and cut away here
 * rather than at draw time, which lets the renderer treat every sprite as a tight rectangle.
 */
object GameAssets {

    private val sprites = EnumMap<GameSprite, ImageBitmap>(GameSprite::class.java)
    private val heightFractions = EnumMap<GameSprite, Float>(GameSprite::class.java)

    @Volatile
    var isPreloaded: Boolean = false
        private set

    operator fun get(sprite: GameSprite): ImageBitmap? = sprites[sprite]

    fun require(sprite: GameSprite): ImageBitmap =
        sprites[sprite] ?: error("Sprite ${sprite.name} was requested before it was decoded")

    /**
     * How much of its source sheet's height the trimmed sprite covers.
     *
     * Objects on one sheet were drawn to a common scale, so this is what keeps a slab from ending up
     * as tall as the column next to it once every sprite has been cropped to its own silhouette.
     */
    fun heightFraction(sprite: GameSprite): Float = heightFractions[sprite] ?: sprite.heightFraction

    /**
     * Decodes every sprite flagged for preloading, reporting a monotonically increasing 0..1
     * fraction. The fraction is derived from real decode work, so it is safe to drive a progress
     * bar that must be honest about how much is left.
     */
    suspend fun preloadAll(context: Context, onProgress: (Float) -> Unit) = withContext(Dispatchers.Default) {
        val pending = GameSprite.entries.filter { it.preload && sprites[it] == null }
        if (pending.isEmpty()) {
            isPreloaded = true
            onProgress(1f)
            return@withContext
        }

        val bySheet = pending.groupBy { it.file }
        var done = 0
        val total = pending.size

        for ((file, sheetSprites) in bySheet) {
            val sampleSize = sheetSprites.minOf { sampleSizeFor(context, it) }
            val sheet = decodeSheet(context, file, sampleSize)
            if (sheet == null) {
                done += sheetSprites.size
                onProgress(done.toFloat() / total)
                continue
            }
            try {
                for (sprite in sheetSprites) {
                    sprites[sprite] = extract(sheet, sprite).asImageBitmap()
                    done++
                    onProgress(done.toFloat() / total)
                }
            } finally {
                sheet.recycle()
            }
        }

        isPreloaded = true
        onProgress(1f)
    }

    /**
     * Decodes a single sprite on demand, capped to [maxSize] instead of the sprite's own cap.
     * Used for the loading artwork, which only has to be as large as the screen showing it.
     */
    suspend fun loadSingle(context: Context, sprite: GameSprite, maxSize: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            sprites[sprite]?.let { return@withContext it }

            val sampleSize = sampleSizeFor(context, sprite, maxSize)
            val sheet = decodeSheet(context, sprite.file, sampleSize) ?: return@withContext null
            val result = try {
                extract(sheet, sprite, maxSize).asImageBitmap()
            } finally {
                sheet.recycle()
            }
            sprites[sprite] = result
            result
        }

    /** Frees the loading artwork once the game itself is on screen. */
    fun releaseLoadingArtwork() {
        sprites.remove(GameSprite.LOADING_LANDSCAPE)
        sprites.remove(GameSprite.LOADING_PORTRAIT)
    }

    // -----------------------------------------------------------------------------------------

    private fun sampleSizeFor(
        context: Context,
        sprite: GameSprite,
        maxSizeOverride: Int = sprite.maxSize,
    ): Int {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("images/${sprite.file}").use { BitmapFactory.decodeStream(it, null, options) }

        val cellWidth = options.outWidth * sprite.widthFraction
        val cellHeight = options.outHeight * sprite.heightFraction
        val longestEdge = maxOf(cellWidth, cellHeight).toInt()
        if (longestEdge <= 0) return 1

        // Halve until one more halving would drop the cell below the requested cap.
        var sampleSize = 1
        while (longestEdge / (sampleSize * 2) >= maxSizeOverride) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeSheet(context: Context, file: String, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.assets.open("images/$file").use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    private fun extract(
        sheet: Bitmap,
        sprite: GameSprite,
        maxSizeOverride: Int = sprite.maxSize,
    ): Bitmap {
        val cellX = (sheet.width * sprite.left).roundToInt().coerceIn(0, sheet.width - 1)
        val cellY = (sheet.height * sprite.top).roundToInt().coerceIn(0, sheet.height - 1)
        val cellWidth = (sheet.width * sprite.right).roundToInt().coerceAtMost(sheet.width) - cellX
        val cellHeight = (sheet.height * sprite.bottom).roundToInt().coerceAtMost(sheet.height) - cellY

        val bounds = if (sprite.trim) {
            opaqueBounds(sheet, cellX, cellY, cellWidth, cellHeight)
        } else {
            Rect(cellX, cellY, cellX + cellWidth, cellY + cellHeight)
        }

        heightFractions[sprite] = bounds.height().toFloat() / sheet.height

        val cropped = Bitmap.createBitmap(sheet, bounds.left, bounds.top, bounds.width(), bounds.height())

        val longestEdge = maxOf(cropped.width, cropped.height)
        if (longestEdge <= maxSizeOverride) return cropped

        val scale = maxSizeOverride.toFloat() / longestEdge
        val scaled = Bitmap.createScaledBitmap(
            cropped,
            (cropped.width * scale).toInt().coerceAtLeast(1),
            (cropped.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    /** Finds the tightest rectangle inside the given cell that still contains every visible pixel. */
    private fun opaqueBounds(sheet: Bitmap, cellX: Int, cellY: Int, width: Int, height: Int): Rect {
        val row = IntArray(width)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (y in 0 until height) {
            sheet.getPixels(row, 0, width, cellX, cellY + y, width, 1)
            var rowMinX = -1
            var rowMaxX = -1
            for (x in 0 until width) {
                if (row[x] ushr 24 > ALPHA_THRESHOLD) {
                    if (rowMinX < 0) rowMinX = x
                    rowMaxX = x
                }
            }
            if (rowMaxX >= 0) {
                if (rowMinX < minX) minX = rowMinX
                if (rowMaxX > maxX) maxX = rowMaxX
                if (y < minY) minY = y
                maxY = y
            }
        }

        if (maxX < 0) return Rect(cellX, cellY, cellX + width, cellY + height)

        return Rect(cellX + minX, cellY + minY, cellX + maxX + 1, cellY + maxY + 1)
    }

    private const val ALPHA_THRESHOLD = 8
}
