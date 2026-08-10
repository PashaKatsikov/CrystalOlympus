package com.crystalolympus.crystalolympusgame

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.GameAssets
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.ui.theme.CrystalOlympusTheme
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * The one screen that is allowed to rotate freely. It decodes the artwork the rest of the game needs
 * and shows real progress while doing it, then hands over to [MainActivity].
 */
class LoadingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CrystalOlympusTheme {
                LoadingScreen(onFinished = ::openGame)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun openGame() {
        startActivity(Intent(this, MainActivity::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }
}

@Composable
private fun LoadingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    var assetProgress by remember { mutableFloatStateOf(0f) }
    var assetsReady by remember { mutableStateOf(GameAssets.isPreloaded) }
    var displayedProgress by remember { mutableFloatStateOf(0f) }

    val screenLongestEdge = with(configuration) {
        (max(screenWidthDp, screenHeightDp) * context.resources.displayMetrics.density).toInt()
    }

    // The backdrop has to match the current orientation, so it is decoded whenever that changes.
    LaunchedEffect(isLandscape) {
        val sprite = if (isLandscape) GameSprite.LOADING_LANDSCAPE else GameSprite.LOADING_PORTRAIT
        background = GameAssets.loadSingle(context, sprite, maxSize = screenLongestEdge.coerceIn(720, 2400))
    }

    LaunchedEffect(Unit) {
        launch {
            GameAssets.preloadAll(context) { fraction -> assetProgress = fraction }
            assetsReady = true
        }
    }

    // Drives the bar. It is only ever allowed to reach 1.0 once the decoding really has finished,
    // and the hand-off happens on the very next frame after that, so a full bar always means
    // "the game is about to open".
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        var previousNanos = startNanos

        while (true) {
            val nowNanos = withFrameNanos { it }
            val delta = ((nowNanos - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
            previousNanos = nowNanos
            val elapsed = (nowNanos - startNanos) / 1_000_000_000f

            val timeGate = elapsed / MINIMUM_VISIBLE_SECONDS
            val everythingDone = assetsReady && background != null && elapsed >= MINIMUM_VISIBLE_SECONDS
            val ceiling = if (everythingDone) 1f else min(0.97f, min(assetProgress, timeGate))

            displayedProgress = if (everythingDone) {
                min(1f, displayedProgress + delta * FINAL_FILL_PER_SECOND)
            } else {
                displayedProgress + (ceiling - displayedProgress) * min(1f, delta * 5f)
            }

            if (displayedProgress >= 0.9999f) {
                displayedProgress = 1f
                break
            }
        }

        // Let one frame present the completely filled bar before the screen changes.
        withFrameNanos { }
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(OlympusColors.DeepBlue)) {
        background?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color(0x33030A1C),
                        1f to Color(0xCC030A1C),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            LoadingLabel()
            Spacer(Modifier.height(14.dp))
            LoadingProgressBar(
                progress = displayedProgress,
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.46f else 0.82f)
                    .height(20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${(displayedProgress * 100f).toInt()}%",
                color = OlympusColors.GoldBright,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(if (isLandscape) 6.dp else 40.dp))
        }
    }
}

/** "Loading" followed by three dots that light up in sequence. */
@Composable
private fun LoadingLabel() {
    val transition = rememberInfiniteTransition(label = "dots")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dotCycle",
    )
    val litDots = cycle.toInt().coerceIn(0, 2) + 1

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "Loading",
            color = OlympusColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
        repeat(3) { index ->
            Text(
                text = ".",
                color = if (index < litDots) OlympusColors.GoldBright else OlympusColors.TextPrimary.copy(alpha = 0.22f),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
        }
    }
}

@Composable
private fun LoadingProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val glow by shimmer.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xCC050B1E))
            .border(2.dp, Brush.horizontalGradient(listOf(OlympusColors.GoldDark, OlympusColors.GoldBright, OlympusColors.GoldDark)), shape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(OlympusBrushes.ProgressBar),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(horizontal = 6.dp)
                    .background(Color.White.copy(alpha = glow), shape),
            )
        }
    }
}

private const val MINIMUM_VISIBLE_SECONDS = 2.4f
private const val FINAL_FILL_PER_SECOND = 1.6f
