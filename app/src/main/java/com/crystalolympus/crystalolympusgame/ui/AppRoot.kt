package com.crystalolympus.crystalolympusgame.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crystalolympus.crystalolympusgame.ui.screens.BattleScreen
import com.crystalolympus.crystalolympusgame.ui.screens.CollectionScreen
import com.crystalolympus.crystalolympusgame.ui.screens.DailyRewardDialog
import com.crystalolympus.crystalolympusgame.ui.screens.HeroScreen
import com.crystalolympus.crystalolympusgame.ui.screens.MainMenuScreen
import com.crystalolympus.crystalolympusgame.ui.screens.SettingsDialog
import com.crystalolympus.crystalolympusgame.ui.screens.ShopScreen
import com.crystalolympus.crystalolympusgame.ui.screens.UpgradesScreen
import com.crystalolympus.crystalolympusgame.ui.screens.WorldMapScreen
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

@Composable
fun AppRoot(viewModel: GameViewModel = viewModel()) {
    val profile by viewModel.profile.collectAsState()
    val screen = viewModel.screen

    LaunchedEffect(Unit) {
        viewModel.maybeShowDailyReward()
    }

    BackHandler(enabled = screen != Screen.MainMenu || viewModel.settingsOpen || viewModel.dailyRewardOpen) {
        when {
            viewModel.dailyRewardOpen -> viewModel.closeDailyReward()
            viewModel.settingsOpen -> viewModel.closeSettings()
            screen is Screen.Battle -> viewModel.session?.pause()
            else -> viewModel.backToMenu()
        }
    }

    Box(Modifier.fillMaxSize().background(OlympusColors.Night)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.MainMenu -> MainMenuScreen(profile, viewModel)
                Screen.WorldMap -> WorldMapScreen(profile, viewModel)
                Screen.Hero -> HeroScreen(profile, viewModel)
                Screen.Upgrades -> UpgradesScreen(profile, viewModel)
                Screen.Collection -> CollectionScreen(profile, viewModel)
                Screen.Shop -> ShopScreen(profile, viewModel)
                is Screen.Battle -> BattleScreen(profile, viewModel)
            }
        }

        if (viewModel.settingsOpen) {
            SettingsDialog(profile, viewModel)
        }

        if (viewModel.dailyRewardOpen) {
            DailyRewardDialog(viewModel)
        }
    }
}
