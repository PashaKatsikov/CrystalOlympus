package com.crystalolympus.crystalolympusgame.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.game.model.DailyReward
import com.crystalolympus.crystalolympusgame.game.model.DailyRewards
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButtonStyle
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.SpriteImage
import com.crystalolympus.crystalolympusgame.ui.components.clickableNoRipple
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

/** Full-screen prompt for the seven-day login streak, shown at most once per app launch. */
@Composable
fun DailyRewardDialog(viewModel: GameViewModel) {
    val (reward, litDays) = remember(viewModel) { viewModel.pendingDailyReward() }
    val scrimSource = remember { MutableInteractionSource() }
    val panelSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OlympusColors.Scrim)
            .clickableNoRipple(scrimSource) { viewModel.closeDailyReward() },
        contentAlignment = Alignment.Center,
    ) {
        OlympusPanel(
            modifier = Modifier.width(360.dp).clickableNoRipple(panelSource) { /* absorb taps */ },
            borderColor = OlympusColors.Gold,
            contentPadding = 20.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "DAILY BLESSING",
                    color = OlympusColors.GoldBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The gods reward those who return.",
                    color = OlympusColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DailyRewards.schedule.forEach { entry ->
                        DayPip(entry = entry, claimed = entry.day <= litDays, active = entry.day == reward.day)
                    }
                }

                Spacer(Modifier.height(18.dp))
                SpriteImage(reward.sprite, Modifier.size(64.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Day ${reward.day} reward",
                    color = OlympusColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RewardAmount(GameSprite.REWARD_COIN, reward.coins)
                    if (reward.gems > 0) RewardAmount(GameSprite.REWARD_GEM, reward.gems)
                }

                Spacer(Modifier.height(20.dp))
                OlympusButton(
                    text = "CLAIM",
                    onClick = { viewModel.claimDaily() },
                    style = OlympusButtonStyle.Gold,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                )
            }
        }
    }
}

@Composable
private fun DayPip(entry: DailyReward, claimed: Boolean, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (claimed) OlympusBrushes.GoldButton else OlympusBrushes.PanelRaised)
                .border(2.dp, if (active) OlympusColors.GoldBright else OlympusColors.PanelBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SpriteImage(entry.sprite, Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = "D${entry.day}",
            color = if (active) OlympusColors.GoldBright else OlympusColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RewardAmount(sprite: GameSprite, amount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        SpriteImage(sprite, Modifier.size(20.dp))
        Text(text = "+$amount", color = OlympusColors.GoldBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
