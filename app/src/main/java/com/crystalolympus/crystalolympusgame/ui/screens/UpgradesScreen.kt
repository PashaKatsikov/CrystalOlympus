package com.crystalolympus.crystalolympusgame.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.core.GameSound
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.game.model.UpgradeBranch
import com.crystalolympus.crystalolympusgame.game.model.UpgradeNode
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.SpriteImage
import com.crystalolympus.crystalolympusgame.ui.components.StatBar
import com.crystalolympus.crystalolympusgame.ui.components.clickableNoRipple
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors
import java.util.Locale

@Composable
fun UpgradesScreen(profile: PlayerProfile, viewModel: GameViewModel) {
    var branch by remember { mutableStateOf(UpgradeBranch.OFFENSE) }
    var selected by remember { mutableStateOf(UpgradeNode.LIGHTNING_POWER) }

    ScreenBackground(GameSprite.BG_TEMPLE_FLOOR, dim = 0.66f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(profile, viewModel, title = "UPGRADES", onBack = viewModel::backToMenu)

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.width(120.dp).fillMaxHeight().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpgradeBranch.entries.forEach { entry ->
                        BranchButton(entry, entry == branch) {
                            AudioEngine.play(GameSound.BUTTON_CLICK)
                            branch = entry
                            selected = UpgradeNode.entries.first { it.branch == entry }
                        }
                    }
                }

                OlympusPanel(modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SectionTitle(branch.displayName)
                        Spacer(Modifier.height(10.dp))
                        UpgradeNode.entries.filter { it.branch == branch }.chunked(2).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { node ->
                                    UpgradeTile(
                                        node = node,
                                        level = profile.levelOf(node),
                                        selected = node == selected,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        AudioEngine.play(GameSound.BUTTON_CLICK)
                                        selected = node
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                UpgradeDetails(
                    node = selected,
                    profile = profile,
                    onUpgrade = { viewModel.buyUpgrade(selected) },
                    modifier = Modifier.width(216.dp).fillMaxHeight().padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BranchButton(branch: UpgradeBranch, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(if (selected) OlympusBrushes.GoldButton else OlympusBrushes.Panel)
            .border(1.dp, if (selected) OlympusColors.GoldBright else OlympusColors.PanelBorder, shape)
            .clickableNoRipple(interactionSource, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = branch.displayName,
            color = if (selected) OlympusColors.Night else OlympusColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun UpgradeTile(
    node: UpgradeNode,
    level: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val maxed = level >= node.maxLevel
    OlympusPanel(
        modifier = modifier.height(84.dp).clickableNoRipple(interactionSource, onClick),
        brush = if (selected) OlympusBrushes.PanelRaised else OlympusBrushes.Panel,
        borderColor = when {
            selected -> OlympusColors.Gold
            maxed -> OlympusColors.Success
            else -> OlympusColors.PanelBorder
        },
        contentPadding = 8.dp,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            SpriteImage(node.sprite, Modifier.size(38.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = node.displayName,
                    color = OlympusColors.TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(5.dp))
                StatBar(
                    fraction = level.toFloat() / node.maxLevel,
                    brush = if (maxed) OlympusBrushes.GoldButton else OlympusBrushes.EnergyBar,
                    modifier = Modifier.fillMaxWidth(),
                    height = 9.dp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "$level / ${node.maxLevel}",
                    color = if (maxed) OlympusColors.Success else OlympusColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun UpgradeDetails(
    node: UpgradeNode,
    profile: PlayerProfile,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = profile.levelOf(node)
    val maxed = level >= node.maxLevel
    val cost = node.costForLevel(level)
    val affordable = profile.coins >= cost

    OlympusPanel(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpriteImage(node.sprite, Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = node.displayName.uppercase(),
                        color = OlympusColors.GoldBright,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 15.sp,
                    )
                    Text(
                        text = "LEVEL $level / ${node.maxLevel}",
                        color = OlympusColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(node.description, color = OlympusColors.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)

            Spacer(Modifier.height(12.dp))
            SectionTitle("EFFECT")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatBonus(level * node.perLevel, node.unit),
                    color = OlympusColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                if (!maxed) {
                    Text("  \u2192  ", color = OlympusColors.TextMuted, fontSize = 13.sp)
                    Text(
                        text = formatBonus((level + 1) * node.perLevel, node.unit),
                        color = OlympusColors.Success,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (maxed) {
                Text(
                    text = "FULLY UPGRADED",
                    color = OlympusColors.Success,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OlympusButton(
                    text = "UPGRADE  $cost",
                    onClick = onUpgrade,
                    enabled = affordable,
                    modifier = Modifier.fillMaxWidth(),
                    leading = { SpriteImage(GameSprite.REWARD_COIN, Modifier.size(20.dp)) },
                )
            }
        }
    }
}

private fun formatBonus(value: Float, unit: String): String {
    // Locale.US so a device set to a comma-decimal language still reads "+1.5s", not "+1,5s".
    val rendered = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(Locale.US, value)
    return "+$rendered$unit"
}
