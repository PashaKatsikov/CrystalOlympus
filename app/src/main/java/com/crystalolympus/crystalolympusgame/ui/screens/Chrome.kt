package com.crystalolympus.crystalolympusgame.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.core.GameAssets
import com.crystalolympus.crystalolympusgame.core.GameSound
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusIconButton
import com.crystalolympus.crystalolympusgame.ui.components.ResourceChip
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

/** Painted backdrop shared by every menu screen. */
@Composable
fun ScreenBackground(
    sprite: GameSprite = GameSprite.BG_OLYMPUS_SKY,
    dim: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(OlympusColors.Night)) {
        GameAssets[sprite]?.let { image ->
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
                        listOf(
                            Color.Black.copy(alpha = dim * 0.9f),
                            Color(0xFF060B1C).copy(alpha = dim),
                            Color(0xFF03060F).copy(alpha = dim + 0.25f),
                        ),
                    ),
                ),
        )
        content()
    }
}

/** Top strip with an optional back arrow, a title, the player's currencies and the settings gear. */
@Composable
fun TopBar(
    profile: PlayerProfile,
    viewModel: GameViewModel,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            OlympusIconButton(onClick = onBack, size = 38.dp) {
                Text("<", color = OlympusColors.GoldBright, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
        }

        if (title != null) {
            Text(
                text = title,
                color = OlympusColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ResourceChip(GameSprite.REWARD_COIN, formatAmount(profile.coins))
            ResourceChip(GameSprite.CRYSTAL_LIGHTNING, formatAmount(profile.crystals))
            ResourceChip(GameSprite.REWARD_GEM, formatAmount(profile.gems))
            OlympusIconButton(
                onClick = {
                    AudioEngine.play(GameSound.BUTTON_CLICK)
                    viewModel.openSettings()
                },
                size = 38.dp,
            ) {
                SettingsGear()
            }
        }
    }
}

@Composable
private fun SettingsGear() {
    Text("\u2699", color = OlympusColors.GoldBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

/** Short-hand for large numbers so the top bar never overflows. */
fun formatAmount(value: Int): String = when {
    value >= 1_000_000 -> "${value / 100_000 / 10f}M"
    value >= 10_000 -> "${value / 1000}K"
    else -> value.toString()
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = OlympusColors.GoldBright,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        modifier = modifier,
    )
}

@Composable
fun IconBadge(sprite: GameSprite, size: Int = 26) {
    GameAssets[sprite]?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.size(size.dp),
            filterQuality = FilterQuality.High,
        )
    }
}
