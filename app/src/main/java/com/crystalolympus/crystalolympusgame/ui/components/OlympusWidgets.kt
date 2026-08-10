package com.crystalolympus.crystalolympusgame.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.GameAssets
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

/** A framed panel in the game's marble-and-gold style. */
@Composable
fun OlympusPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    brush: Brush = OlympusBrushes.Panel,
    borderColor: Color = OlympusColors.PanelBorder,
    contentPadding: Dp = 12.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(contentPadding),
        content = content,
    )
}

enum class OlympusButtonStyle { Gold, Blue, Danger }

@Composable
fun OlympusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OlympusButtonStyle = OlympusButtonStyle.Gold,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)

    val brush = when (style) {
        OlympusButtonStyle.Gold -> OlympusBrushes.GoldButton
        OlympusButtonStyle.Blue -> OlympusBrushes.BlueButton
        OlympusButtonStyle.Danger -> OlympusBrushes.DangerButton
    }
    val labelColor = when (style) {
        OlympusButtonStyle.Gold -> Color(0xFF3A2400)
        else -> OlympusColors.TextPrimary
    }

    Box(
        modifier = modifier
            .scale(if (pressed && enabled) 0.96f else 1f)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(brush)
            .border(BorderStroke(1.5.dp, OlympusColors.GoldBright.copy(alpha = 0.65f)), shape)
            .then(
                if (enabled) {
                    Modifier.clickableNoRipple(interactionSource, onClick)
                } else {
                    Modifier
                }
            )
            .defaultMinSize(minWidth = 120.dp, minHeight = 42.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = labelColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Small square icon button, used for back arrows, pause, settings. */
@Composable
fun OlympusIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .size(size)
            .scale(if (pressed) 0.92f else 1f)
            .clip(shape)
            .background(OlympusBrushes.PanelRaised)
            .border(BorderStroke(1.dp, OlympusColors.PanelBorder), shape)
            .clickableNoRipple(interactionSource, onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Resource readout used in the top bars: sprite icon plus an amount. */
@Composable
fun ResourceChip(
    sprite: GameSprite,
    amount: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xB3060D22))
            .border(BorderStroke(1.dp, OlympusColors.PanelBorder), RoundedCornerShape(50))
            .padding(start = 4.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpriteImage(sprite, Modifier.size(iconSize))
        Spacer(Modifier.width(6.dp))
        Text(
            text = amount,
            color = OlympusColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Draws a decoded game sprite, falling back to empty space when it has not been decoded. */
@Composable
fun SpriteImage(
    sprite: GameSprite,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    alpha: Float = 1f,
) {
    val bitmap: ImageBitmap? = GameAssets[sprite]
    if (bitmap == null) {
        Box(modifier)
        return
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
}

/** Horizontal bar with a gold frame, used for health, energy and experience. */
@Composable
fun StatBar(
    fraction: Float,
    brush: Brush,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    label: String? = null,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(Color(0xCC060B1C))
            .border(BorderStroke(1.dp, OlympusColors.PanelBorder), shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxSize()
                .clip(shape)
                .background(brush),
        )
        if (label != null) {
            Text(
                text = label,
                color = OlympusColors.TextPrimary,
                fontSize = (height.value * 0.62f).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Tab strip matching the collection and shop screens. */
@Composable
fun OlympusTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            val interactionSource = remember(index) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        if (selected) OlympusBrushes.PanelRaised
                        else Brush.verticalGradient(listOf(Color(0xFF0C1432), Color(0xFF060C1E))),
                    )
                    .border(
                        BorderStroke(1.dp, if (selected) OlympusColors.Gold else OlympusColors.PanelBorder),
                        shape,
                    )
                    .clickableNoRipple(interactionSource) { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = if (selected) OlympusColors.GoldBright else OlympusColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Slowly pulsing glow used behind hero art and rare rewards. */
@Composable
fun rememberPulse(periodMillis: Int = 2200, from: Float = 0.85f, to: Float = 1.05f): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseValue",
    )
    return value
}
