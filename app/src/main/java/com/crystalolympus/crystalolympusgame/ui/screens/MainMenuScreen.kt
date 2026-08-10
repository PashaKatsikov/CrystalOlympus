package com.crystalolympus.crystalolympusgame.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.crystalolympus.crystalolympusgame.game.model.Zone
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.Screen
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.SpriteImage
import com.crystalolympus.crystalolympusgame.ui.components.clickableNoRipple
import com.crystalolympus.crystalolympusgame.ui.components.rememberPulse
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

@Composable
fun MainMenuScreen(profile: PlayerProfile, viewModel: GameViewModel) {
    ScreenBackground(GameSprite.BG_OLYMPUS_SKY, dim = 0.42f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(profile, viewModel)

            Row(Modifier.fillMaxWidth().weight(1f)) {
                // Zeus watches over the menu from the left, as in the reference layout.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    GameAssets[GameSprite.ZEUS_MENTOR]?.let { image ->
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxHeight(0.94f)
                                .padding(start = 8.dp),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.High,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1.45f)
                        .fillMaxHeight()
                        .padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val pulse = rememberPulse(2600, 0.97f, 1.03f)
                    GameAssets[GameSprite.LOGO]?.let { image ->
                        Image(
                            bitmap = image,
                            contentDescription = "Crystal Olympus",
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .scale(pulse),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.High,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OlympusButton(
                        text = "PLAY",
                        onClick = {
                            AudioEngine.play(GameSound.BUTTON_CLICK)
                            viewModel.navigate(Screen.WorldMap)
                        },
                        modifier = Modifier.width(220.dp).height(54.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Level ${profile.level}  \u2022  Best wave ${profile.bestWave}",
                        color = OlympusColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }

                Box(Modifier.weight(0.9f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                    ProgressSummary(profile, Modifier.padding(end = 14.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MenuTile("HERO", GameSprite.HERO_ZEUS_CHOSEN, Modifier.weight(1f)) {
                    viewModel.navigate(Screen.Hero)
                }
                MenuTile("UPGRADES", GameSprite.ALTAR_UPGRADE, Modifier.weight(1f)) {
                    viewModel.navigate(Screen.Upgrades)
                }
                MenuTile("COLLECTION", GameSprite.CRYSTAL_DIVINE, Modifier.weight(1f)) {
                    viewModel.navigate(Screen.Collection)
                }
                MenuTile("SHOP", GameSprite.TREASURE_CHEST, Modifier.weight(1f)) {
                    viewModel.navigate(Screen.Shop)
                }
            }
        }
    }
}

@Composable
private fun ProgressSummary(profile: PlayerProfile, modifier: Modifier = Modifier) {
    OlympusPanel(modifier = modifier.width(180.dp)) {
        Column {
            SectionTitle("OLYMPUS RESTORED")
            Spacer(Modifier.height(8.dp))
            SummaryRow("Zones cleared", "${profile.zonesCleared}/${Zone.entries.size}")
            SummaryRow("Runs", profile.totalRuns.toString())
            SummaryRow("Hero level", profile.level.toString())
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Restore the shattered sources of divine energy and bring the sky kingdom back to life.",
                color = OlympusColors.TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = OlympusColors.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = OlympusColors.GoldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MenuTile(
    label: String,
    sprite: GameSprite,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OlympusPanel(
        modifier = modifier
            .height(62.dp)
            .clickableNoRipple(interactionSource) {
                AudioEngine.play(GameSound.BUTTON_CLICK)
                onClick()
            },
        contentPadding = 6.dp,
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            SpriteImage(sprite, Modifier.size(34.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = OlympusColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
        }
    }
}
