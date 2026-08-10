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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.game.model.EquipmentItem
import com.crystalolympus.crystalolympusgame.game.model.EquipmentSlot
import com.crystalolympus.crystalolympusgame.game.model.UpgradeNode
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.SpriteImage
import com.crystalolympus.crystalolympusgame.ui.components.StatBar
import com.crystalolympus.crystalolympusgame.ui.components.clickableNoRipple
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors
import androidx.compose.runtime.remember

@Composable
fun HeroScreen(profile: PlayerProfile, viewModel: GameViewModel) {
    ScreenBackground(GameSprite.BG_TEMPLE_FLOOR, dim = 0.62f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(profile, viewModel, title = "HERO", onBack = viewModel::backToMenu)

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.weight(0.85f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    SpriteImage(
                        GameSprite.HERO_ZEUS_CHOSEN,
                        Modifier.fillMaxHeight(0.96f),
                        contentScale = ContentScale.Fit,
                    )
                }

                OlympusPanel(modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            text = "ZEUS' CHOSEN",
                            color = OlympusColors.GoldBright,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = "LEVEL ${profile.level}",
                            color = OlympusColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                        )

                        Spacer(Modifier.height(10.dp))
                        StatBar(
                            fraction = profile.experienceIntoLevel / PlayerProfile.EXPERIENCE_PER_LEVEL.toFloat(),
                            brush = OlympusBrushes.ProgressBar,
                            modifier = Modifier.fillMaxWidth(),
                            height = 12.dp,
                            label = "${profile.experienceIntoLevel} / ${PlayerProfile.EXPERIENCE_PER_LEVEL} XP",
                        )

                        Spacer(Modifier.height(14.dp))
                        SectionTitle("ATTRIBUTES")
                        Spacer(Modifier.height(8.dp))

                        val equipped = EquipmentSlot.entries.mapNotNull { profile.equippedIn(it) }
                        val bonusDamage = equipped.sumOf { it.damageBonus.toDouble() }.toFloat() * 100f
                        val bonusHealth = equipped.sumOf { it.healthBonus.toDouble() }.toFloat()
                        val bonusEnergy = equipped.sumOf { it.energyBonus.toDouble() }.toFloat()

                        AttributeRow(
                            GameSprite.SPHERE_HEALTH, "Health",
                            (520f + profile.bonusOf(UpgradeNode.MAX_HEALTH) + bonusHealth).toInt().toString(),
                        )
                        AttributeRow(
                            GameSprite.CRYSTAL_LIGHTNING, "Lightning power",
                            "+${(profile.bonusOf(UpgradeNode.LIGHTNING_POWER) + bonusDamage).toInt()}%",
                        )
                        AttributeRow(
                            GameSprite.SPHERE_POWER, "Energy",
                            (100f + profile.bonusOf(UpgradeNode.ENERGY_POOL) + bonusEnergy).toInt().toString(),
                        )
                        AttributeRow(
                            GameSprite.CLOUD_WHITE, "Move speed",
                            "+${profile.bonusOf(UpgradeNode.MOVE_SPEED).toInt()}%",
                        )
                        AttributeRow(
                            GameSprite.STONE_RUNE_BLOCK, "Armour",
                            "+${profile.bonusOf(UpgradeNode.ARMOUR).toInt()}%",
                        )
                        AttributeRow(
                            GameSprite.ARTIFACT_SCEPTER, "Critical chance",
                            "${(5f + profile.bonusOf(UpgradeNode.CRIT_CHANCE)).toInt()}%",
                        )
                    }
                }

                OlympusPanel(modifier = Modifier.weight(1.1f).fillMaxHeight().padding(vertical = 4.dp)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SectionTitle("EQUIPMENT")
                        Spacer(Modifier.height(8.dp))

                        EquipmentSlot.entries.forEach { slot ->
                            Text(
                                text = slot.displayName.uppercase(),
                                color = OlympusColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            )
                            Spacer(Modifier.height(5.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EquipmentItem.entries.filter { it.slot == slot }.forEach { item ->
                                    EquipmentTile(
                                        item = item,
                                        unlocked = profile.isUnlocked(item),
                                        equipped = profile.equippedIn(slot) == item,
                                        onClick = { viewModel.equip(item) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        Text(
                            text = "New relics are recovered by clearing zones on the world map.",
                            color = OlympusColors.TextMuted,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributeRow(sprite: GameSprite, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpriteImage(sprite, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = OlympusColors.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = OlympusColors.GoldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EquipmentTile(
    item: EquipmentItem,
    unlocked: Boolean,
    equipped: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(shape)
            .background(if (equipped) OlympusBrushes.PanelRaised else OlympusBrushes.Panel)
            .border(
                width = if (equipped) 2.dp else 1.dp,
                color = when {
                    equipped -> OlympusColors.Gold
                    unlocked -> OlympusColors.PanelBorder
                    else -> Color(0xFF223051)
                },
                shape = shape,
            )
            .clickableNoRipple(interactionSource) { if (unlocked) onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SpriteImage(item.sprite, Modifier.size(36.dp), alpha = if (unlocked) 1f else 0.3f)
            if (!unlocked) Text("\uD83D\uDD12", fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.displayName,
            color = if (unlocked) OlympusColors.TextPrimary else OlympusColors.TextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
        )
    }
}
