package com.crystalolympus.crystalolympusgame.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.core.GameSound
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.game.model.Zone
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.SpriteImage
import com.crystalolympus.crystalolympusgame.ui.components.clickableNoRipple
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors
import java.util.Locale

@Composable
fun WorldMapScreen(profile: PlayerProfile, viewModel: GameViewModel) {
    var selected by remember { mutableStateOf(Zone.entries[(profile.zonesUnlocked - 1).coerceIn(0, Zone.entries.lastIndex)]) }

    ScreenBackground(GameSprite.BG_CLOUD_ISLANDS, dim = 0.5f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(profile, viewModel, title = "WORLD MAP", onBack = viewModel::backToMenu)

            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Zone.entries.forEach { zone ->
                        ZoneNode(
                            zone = zone,
                            unlocked = zone.index < profile.zonesUnlocked,
                            cleared = zone.index < profile.zonesCleared,
                            selected = zone == selected,
                        ) {
                            if (zone.index < profile.zonesUnlocked) {
                                AudioEngine.play(GameSound.BUTTON_CLICK)
                                selected = zone
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }

                Spacer(Modifier.width(14.dp))

                ZoneDetails(
                    zone = selected,
                    unlocked = selected.index < profile.zonesUnlocked,
                    onStart = { viewModel.startRun(selected) },
                    modifier = Modifier.width(230.dp).fillMaxHeight().padding(vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ZoneNode(
    zone: Zone,
    unlocked: Boolean,
    cleared: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OlympusPanel(
        modifier = Modifier
            .width(148.dp)
            .height(178.dp)
            .alpha(if (unlocked) 1f else 0.55f)
            .clickableNoRipple(interactionSource, onClick),
        brush = if (selected) OlympusBrushes.PanelRaised else OlympusBrushes.Panel,
        borderColor = if (selected) OlympusColors.Gold else OlympusColors.PanelBorder,
        contentPadding = 10.dp,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "ZONE ${zone.index + 1}",
                color = OlympusColors.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Box(contentAlignment = Alignment.Center) {
                SpriteImage(zone.sprite, Modifier.size(76.dp))
                if (!unlocked) {
                    Text("\uD83D\uDD12", fontSize = 26.sp, color = OlympusColors.TextPrimary)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = zone.displayName.uppercase(),
                    color = if (unlocked) OlympusColors.TextPrimary else OlympusColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        cleared -> "CLEARED"
                        unlocked -> "${zone.waves} WAVES"
                        else -> "LOCKED"
                    },
                    color = if (cleared) OlympusColors.Success else OlympusColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun ZoneDetails(
    zone: Zone,
    unlocked: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OlympusPanel(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = zone.displayName.uppercase(),
                color = OlympusColors.GoldBright,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(zone.subtitle, color = OlympusColors.TextSecondary, fontSize = 10.sp, lineHeight = 13.sp)

            Spacer(Modifier.height(10.dp))
            DetailRow("Waves", zone.waves.toString())
            DetailRow("Difficulty", "x${"%.1f".format(Locale.US, zone.difficulty)}")
            DetailRow("Boss", if (zone.hasBoss) "Lightning Titan" else "None")

            Spacer(Modifier.height(10.dp))
            SectionTitle("DEFENDERS")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                zone.enemies.forEach { enemy ->
                    SpriteImage(enemy.sprite, Modifier.size(38.dp))
                }
                if (zone.hasBoss) SpriteImage(GameSprite.BOSS_LIGHTNING_TITAN, Modifier.size(38.dp))
            }

            Spacer(Modifier.weight(1f))

            OlympusButton(
                text = if (unlocked) "ENTER ZONE" else "LOCKED",
                onClick = {
                    AudioEngine.play(GameSound.BUTTON_CLICK)
                    onStart()
                },
                enabled = unlocked,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = OlympusColors.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = OlympusColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
