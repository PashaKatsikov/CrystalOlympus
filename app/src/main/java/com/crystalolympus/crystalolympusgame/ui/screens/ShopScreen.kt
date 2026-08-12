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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.engine.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButtonStyle
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.SpriteImage
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

@Composable
fun ShopScreen(profile: PlayerProfile, viewModel: GameViewModel) {
    var message by remember { mutableStateOf<String?>(null) }

    ScreenBackground(GameSprite.BG_OLYMPUS_SKY, dim = 0.66f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(profile, viewModel, title = "TEMPLE MARKET", onBack = viewModel::backToMenu)

            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 4.dp)) {
                Text(
                    text = "Trade what you bring back from Olympus. Everything here is bought with the resources you earn in a run.",
                    color = OlympusColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShopCard(
                        title = "CRYSTAL EXCHANGE",
                        sprite = GameSprite.CRYSTAL_LIGHTNING,
                        description = "Melt raw crystals into 900 coins.",
                        priceIcon = GameSprite.CRYSTAL_LIGHTNING,
                        price = 30,
                        affordable = profile.crystals >= 30,
                        modifier = Modifier.weight(1f),
                    ) {
                        val ok = viewModel.exchange(0, 30, 0, 900, 0, 0)
                        message = if (ok) "Received 900 coins" else "Not enough crystals"
                    }

                    ShopCard(
                        title = "GEM FORGE",
                        sprite = GameSprite.REWARD_GEM,
                        description = "Refine 250 crystals into a divine gem.",
                        priceIcon = GameSprite.CRYSTAL_LIGHTNING,
                        price = 250,
                        affordable = profile.crystals >= 250,
                        modifier = Modifier.weight(1f),
                    ) {
                        val ok = viewModel.exchange(0, 250, 0, 0, 0, 1)
                        message = if (ok) "Received 1 gem" else "Not enough crystals"
                    }

                    ShopCard(
                        title = "DIVINE CHEST",
                        sprite = GameSprite.TREASURE_CHEST,
                        description = "A sealed chest of coins, crystals and rare gems.",
                        priceIcon = GameSprite.REWARD_GEM,
                        price = 2,
                        affordable = profile.gems >= 2,
                        modifier = Modifier.weight(1f),
                        highlight = true,
                    ) {
                        val reward = viewModel.openChest(2)
                        message = if (reward != null) {
                            "Chest opened: ${reward.first} coins, ${reward.second} crystals, ${reward.third} gems"
                        } else {
                            "Not enough gems"
                        }
                    }

                    ShopCard(
                        title = "OLYMPUS TRIBUTE",
                        sprite = GameSprite.REWARD_AMPHORA,
                        description = "Convert 5000 coins into 120 crystals.",
                        priceIcon = GameSprite.REWARD_COIN,
                        price = 5000,
                        affordable = profile.coins >= 5000,
                        modifier = Modifier.weight(1f),
                    ) {
                        val ok = viewModel.exchange(5000, 0, 0, 0, 120, 0)
                        message = if (ok) "Received 120 crystals" else "Not enough coins"
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    message?.let {
                        Text(it, color = OlympusColors.GoldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopCard(
    title: String,
    sprite: GameSprite,
    description: String,
    priceIcon: GameSprite,
    price: Int,
    affordable: Boolean,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onBuy: () -> Unit,
) {
    OlympusPanel(
        modifier = modifier.fillMaxHeight(),
        borderColor = if (highlight) OlympusColors.Gold else OlympusColors.PanelBorder,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = if (highlight) OlympusColors.GoldBright else OlympusColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                SpriteImage(sprite, Modifier.fillMaxHeight(0.8f))
            }
            Text(
                text = description,
                color = OlympusColors.TextSecondary,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            OlympusButton(
                text = formatAmount(price),
                onClick = onBuy,
                enabled = affordable,
                style = if (highlight) OlympusButtonStyle.Gold else OlympusButtonStyle.Blue,
                modifier = Modifier.fillMaxWidth(),
                leading = { SpriteImage(priceIcon, Modifier.size(18.dp)) },
            )
        }
    }
}
