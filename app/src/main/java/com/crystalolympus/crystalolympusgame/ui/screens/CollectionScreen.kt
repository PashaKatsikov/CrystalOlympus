package com.crystalolympus.crystalolympusgame.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.core.GameSound
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.game.model.Achievement
import com.crystalolympus.crystalolympusgame.game.model.CrystalType
import com.crystalolympus.crystalolympusgame.game.model.EnemyType
import com.crystalolympus.crystalolympusgame.game.model.EquipmentItem
import com.crystalolympus.crystalolympusgame.game.model.FruitType
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButtonStyle
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusTabs
import com.crystalolympus.crystalolympusgame.ui.components.SpriteImage
import com.crystalolympus.crystalolympusgame.ui.components.StatBar
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

private data class CollectionEntry(
    val sprite: GameSprite,
    val name: String,
    val detail: String,
    val discovered: Boolean,
    val count: Int,
)

private const val ACHIEVEMENTS_TAB = 4

@Composable
fun CollectionScreen(profile: PlayerProfile, viewModel: GameViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("CRYSTALS", "FRUITS", "ENEMIES", "ARTIFACTS", "ACHIEVEMENTS")

    val entries = when (tab) {
        0 -> CrystalType.entries.map { type ->
            val count = profile.crystalCount(type)
            CollectionEntry(type.sprite, type.displayName, type.effect, count > 0, count)
        }

        1 -> FruitType.entries.map { type ->
            val count = profile.fruitCount(type)
            CollectionEntry(type.sprite, type.displayName, type.description, count > 0, count)
        }

        2 -> EnemyType.entries.map { type ->
            val count = profile.defeatedCount(type.name)
            CollectionEntry(type.sprite, type.displayName, type.lore, count > 0, count)
        }

        ACHIEVEMENTS_TAB -> emptyList()

        else -> EquipmentItem.entries.map { item ->
            val unlocked = profile.isUnlocked(item)
            CollectionEntry(item.sprite, item.displayName, describeEquipment(item), unlocked, if (unlocked) 1 else 0)
        }
    }

    val discovered = entries.count { it.discovered }
    val achievementsClaimed = Achievement.entries.count { it.isClaimed(profile) }

    ScreenBackground(GameSprite.BG_TEMPLE_FLOOR, dim = 0.68f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(profile, viewModel, title = "COLLECTION", onBack = viewModel::backToMenu)

            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 4.dp)) {
                OlympusTabs(
                    tabs = tabs,
                    selectedIndex = tab,
                    onSelect = {
                        AudioEngine.play(GameSound.BUTTON_CLICK)
                        tab = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OlympusPanel(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(tabs[tab])
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (tab == ACHIEVEMENTS_TAB) {
                                    "Claimed $achievementsClaimed / ${Achievement.entries.size}"
                                } else {
                                    "Collected $discovered / ${entries.size}"
                                },
                                color = OlympusColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        if (tab == ACHIEVEMENTS_TAB) {
                            AchievementsList(profile, viewModel)
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(132.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(entries) { entry -> CollectionCard(entry) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementsList(profile: PlayerProfile, viewModel: GameViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Achievement.entries) { achievement -> AchievementRow(achievement, profile, viewModel) }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, profile: PlayerProfile, viewModel: GameViewModel) {
    val progress = achievement.progress(profile)
    val claimed = achievement.isClaimed(profile)
    val claimable = achievement.isClaimable(profile)

    OlympusPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (claimable) OlympusColors.Gold else OlympusColors.PanelBorder,
        contentPadding = 10.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                SpriteImage(
                    achievement.sprite,
                    Modifier.size(40.dp),
                    alpha = if (claimed) 0.45f else 1f,
                )
            }
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = achievement.displayName.uppercase(),
                    color = OlympusColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = achievement.description,
                    color = OlympusColors.TextMuted,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(5.dp))
                StatBar(
                    fraction = progress.toFloat() / achievement.target.toFloat(),
                    brush = OlympusBrushes.ProgressBar,
                    modifier = Modifier.fillMaxWidth(),
                    height = 8.dp,
                    label = "$progress / ${achievement.target}",
                )
            }

            Spacer(Modifier.width(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SpriteImage(GameSprite.REWARD_COIN, Modifier.size(16.dp))
                Text("+${achievement.coinReward}", color = OlympusColors.GoldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (achievement.gemReward > 0) {
                    Spacer(Modifier.width(4.dp))
                    SpriteImage(GameSprite.REWARD_GEM, Modifier.size(16.dp))
                    Text("+${achievement.gemReward}", color = OlympusColors.GoldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(12.dp))

            when {
                claimed -> Text(
                    text = "CLAIMED",
                    color = OlympusColors.Success,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(88.dp),
                    textAlign = TextAlign.Center,
                )

                claimable -> OlympusButton(
                    text = "CLAIM",
                    onClick = { viewModel.claimAchievement(achievement) },
                    style = OlympusButtonStyle.Gold,
                    modifier = Modifier.width(88.dp).height(38.dp),
                )

                else -> Text(
                    text = "LOCKED",
                    color = OlympusColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(88.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CollectionCard(entry: CollectionEntry) {
    OlympusPanel(
        modifier = Modifier.height(150.dp),
        borderColor = if (entry.discovered) OlympusColors.Gold.copy(alpha = 0.6f) else OlympusColors.PanelBorder,
        contentPadding = 8.dp,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                SpriteImage(
                    entry.sprite,
                    Modifier.fillMaxHeight(),
                    alpha = if (entry.discovered) 1f else 0.18f,
                )
                if (!entry.discovered) {
                    Text("?", color = OlympusColors.TextSecondary, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (entry.discovered) entry.name else "UNDISCOVERED",
                color = if (entry.discovered) OlympusColors.TextPrimary else OlympusColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (entry.discovered) entry.detail else "Find it during a run to reveal its entry.",
                color = OlympusColors.TextMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp,
                modifier = Modifier.weight(1f),
            )
            if (entry.discovered && entry.count > 1) {
                Text(
                    text = "x${entry.count}",
                    color = OlympusColors.GoldBright,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun describeEquipment(item: EquipmentItem): String {
    val parts = buildList {
        if (item.damageBonus > 0f) add("+${(item.damageBonus * 100).toInt()}% damage")
        if (item.healthBonus > 0f) add("+${item.healthBonus.toInt()} health")
        if (item.energyBonus > 0f) add("+${item.energyBonus.toInt()} energy")
        if (item.speedBonus > 0f) add("+${(item.speedBonus * 100).toInt()}% speed")
    }
    return parts.joinToString(", ")
}
