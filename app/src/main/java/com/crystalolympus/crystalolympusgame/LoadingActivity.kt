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
import com.crystalolympus.crystalolympusgame.boot.OptInPrompt
import com.crystalolympus.crystalolympusgame.boot.OrbitShell
import com.crystalolympus.crystalolympusgame.boot.SignalLostScreen
import com.crystalolympus.crystalolympusgame.engine.GameAssets
import com.crystalolympus.crystalolympusgame.engine.GameSprite
import com.crystalolympus.crystalolympusgame.push.Store
import com.crystalolympus.crystalolympusgame.view.GateRouter
import com.crystalolympus.crystalolympusgame.ui.theme.CrystalOlympusTheme
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * The one screen that is allowed to rotate freely. It decodes the artwork the rest of the game needs
 * and shows real progress while doing it, then hands over to [MainActivity].
 *
 * On an organic first launch it also runs the gray/white decision in the background
 * (see [GateRouter]) so it is the single loading screen the user sees — the bar only
 * fills to 100% once both the assets are ready and the decision is in, and the screen
 * that follows is the game, the shell, or the offline screen accordingly.
 */
class LoadingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val resolveGate = intent.getBooleanExtra(EXTRA_RESOLVE_GATE, false)

        setContent {
            CrystalOlympusTheme {
                LoadingScreen(resolveGate = resolveGate, onFinished = ::go)
            }
        }
    }

    /**
     * @param outcome null means "just the game" (a returning native user, or any
     *   launch not carrying the resolve flag); otherwise it is the resolved verdict.
     */
    private fun go(outcome: GateRouter.Outcome?) {
        val next = when (outcome) {
            is GateRouter.Outcome.Gray -> grayIntent(outcome.url)
            is GateRouter.Outcome.Offline ->
                Intent(this, SignalLostScreen::class.java).apply {
                    if (!outcome.savedUrl.isNullOrBlank())
                        putExtra(SignalLostScreen.EXTRA_RETURN_URL, outcome.savedUrl)
                }
            else -> Intent(this, MainActivity::class.java)
        }
        next.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(next)
        fadeThrough()
        finish()
    }

    /** Notification opt-in screen first, if it is still due; otherwise the shell. */
    private fun grayIntent(url: String): Intent {
        val vault = Store(applicationContext)
        return if (vault.shouldShowNotifScreen()) {
            Intent(this, OptInPrompt::class.java).putExtra(OptInPrompt.EXTRA_TARGET_URL, url)
        } else {
            Intent(this, OrbitShell::class.java).putExtra(OrbitShell.EXTRA_STREAM_URL, url)
        }
    }

    @Suppress("DEPRECATION")
    private fun fadeThrough() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    companion object {
        /** Set by [com.crystalolympus.crystalolympusgame.view.LaunchGate] on an
         *  organic first launch: run the gray decision here, not on a splash ahead. */
        const val EXTRA_RESOLVE_GATE = "resolve_gate"
    }
}

@Composable
private fun LoadingScreen(resolveGate: Boolean, onFinished: (GateRouter.Outcome?) -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    var assetProgress by remember { mutableFloatStateOf(0f) }
    var assetsReady by remember { mutableStateOf(GameAssets.isPreloaded) }
    var displayedProgress by remember { mutableFloatStateOf(0f) }

    // On an organic first launch the gray decision runs alongside the preload; the
    // bar is not allowed to finish until it is in. When we are not resolving (a
    // returning native user), the gate is "ready" from the start and the outcome
    // stays null, meaning "straight to the game".
    var gateOutcome by remember { mutableStateOf<GateRouter.Outcome?>(null) }
    var gateReady by remember { mutableStateOf(!resolveGate) }

    val screenLongestEdge = with(configuration) {
        (max(screenWidthDp, screenHeightDp) * context.resources.displayMetrics.density).toInt()
    }

    // The backdrop has to match the current orientation, so it is decoded whenever that changes.
    LaunchedEffect(isLandscape) {
        val sprite = if (isLandscape) GameSprite.LOADING_LANDSCAPE else GameSprite.LOADING_PORTRAIT
        // Cap at 2048: it stays crisp on any phone screen while keeping the single
        // largest texture within the limit weaker GPUs guarantee and shaving the
        // peak allocation that a tight device can trip over on the way to the game.
        background = GameAssets.loadSingle(context, sprite, maxSize = screenLongestEdge.coerceIn(720, 2048))
    }

    LaunchedEffect(Unit) {
        launch {
            GameAssets.preloadAll(context) { fraction -> assetProgress = fraction }
            assetsReady = true
        }
        if (resolveGate) {
            launch {
                val activity = context as ComponentActivity
                gateOutcome = runCatching { GateRouter(activity).resolveFirstLaunch() }
                    .getOrDefault(GateRouter.Outcome.Native)
                gateReady = true
            }
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
            val everythingDone = assetsReady && background != null && gateReady &&
                elapsed >= MINIMUM_VISIBLE_SECONDS
            val ceiling = if (everythingDone) 1f else min(0.80f, min(assetProgress, timeGate))

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
        onFinished(gateOutcome)
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
