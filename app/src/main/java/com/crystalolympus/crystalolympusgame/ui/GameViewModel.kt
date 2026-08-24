package com.crystalolympus.crystalolympusgame.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.crystalolympus.crystalolympusgame.CrystalOlympusApp
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.core.GameSound
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.game.GameSession
import com.crystalolympus.crystalolympusgame.game.RunResult
import com.crystalolympus.crystalolympusgame.game.model.Achievement
import com.crystalolympus.crystalolympusgame.game.model.EquipmentItem
import com.crystalolympus.crystalolympusgame.game.model.UpgradeNode
import com.crystalolympus.crystalolympusgame.game.model.Zone
import kotlinx.coroutines.flow.StateFlow

sealed interface Screen {
    data object MainMenu : Screen
    data object WorldMap : Screen
    data object Hero : Screen
    data object Upgrades : Screen
    data object Collection : Screen
    data object Shop : Screen
    data class Battle(val zone: Zone) : Screen
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as CrystalOlympusApp).profileRepository

    val profile: StateFlow<PlayerProfile> = repository.profile

    var screen by mutableStateOf<Screen>(Screen.MainMenu)
        private set
    var settingsOpen by mutableStateOf(false)
        private set
    var session by mutableStateOf<GameSession?>(null)
        private set

    // --- Navigation ------------------------------------------------------------------------------

    fun navigate(target: Screen) {
        AudioEngine.play(GameSound.MENU_OPEN)
        screen = target
    }

    fun backToMenu() {
        AudioEngine.play(GameSound.MENU_CLOSE)
        session = null
        screen = Screen.MainMenu
    }

    fun openSettings() {
        AudioEngine.play(GameSound.MENU_OPEN)
        settingsOpen = true
    }

    fun closeSettings() {
        AudioEngine.play(GameSound.MENU_CLOSE)
        settingsOpen = false
    }

    // --- Runs -------------------------------------------------------------------------------------

    fun startRun(zone: Zone) {
        session = GameSession(zone, profile.value)
        screen = Screen.Battle(zone)
        AudioEngine.play(GameSound.PORTAL_OPEN)
    }

    fun restartRun() {
        val zone = (screen as? Screen.Battle)?.zone ?: return
        session = GameSession(zone, profile.value)
    }

    /** Banks the rewards of a finished run and returns to the world map. */
    fun claimRun(result: RunResult) {
        repository.update { current ->
            val crystalsFound = current.crystalsFound.toMutableMap()
            result.crystalsByType.forEach { (type, count) ->
                crystalsFound[type.name] = (crystalsFound[type.name] ?: 0) + count
            }
            val fruitsFound = current.fruitsFound.toMutableMap()
            result.fruitsByType.forEach { (type, count) ->
                fruitsFound[type.name] = (fruitsFound[type.name] ?: 0) + count
            }
            val enemies = current.enemiesDefeated.toMutableMap()
            result.enemiesDefeated.forEach { (name, count) ->
                enemies[name] = (enemies[name] ?: 0) + count
            }

            val clearedIndex = result.zone.index
            val unlocked = if (result.victory) {
                maxOf(current.zonesUnlocked, (clearedIndex + 2).coerceAtMost(Zone.entries.size))
            } else {
                current.zonesUnlocked
            }
            val cleared = if (result.victory) maxOf(current.zonesCleared, clearedIndex + 1) else current.zonesCleared

            val newlyUnlockedGear = EquipmentItem.entries
                .filter { it.unlockZoneIndex < cleared }
                .map { it.name }

            current.copy(
                coins = current.coins + result.coins,
                crystals = current.crystals + result.crystals,
                gems = current.gems + result.gems,
                experience = current.experience + result.experience,
                zonesUnlocked = unlocked,
                zonesCleared = cleared,
                bestWave = maxOf(current.bestWave, result.waveReached),
                totalRuns = current.totalRuns + 1,
                crystalsFound = crystalsFound,
                fruitsFound = fruitsFound,
                enemiesDefeated = enemies,
                unlockedEquipment = current.unlockedEquipment + newlyUnlockedGear,
            )
        }

        session = null
        screen = Screen.WorldMap
    }

    // --- Progression ---------------------------------------------------------------------------------

    fun buyUpgrade(node: UpgradeNode) {
        val current = profile.value
        val level = current.levelOf(node)
        if (level >= node.maxLevel) return
        val cost = node.costForLevel(level)
        if (current.coins < cost) return

        repository.update {
            it.copy(
                coins = it.coins - cost,
                upgrades = it.upgrades + (node.name to level + 1),
            )
        }
        AudioEngine.play(GameSound.UPGRADE)
    }

    fun equip(item: EquipmentItem) {
        if (!profile.value.isUnlocked(item)) return
        repository.update { it.copy(equipped = it.equipped + (item.slot.name to item.name)) }
        AudioEngine.play(GameSound.BUTTON_CLICK)
    }

    fun exchange(coinCost: Int, crystalCost: Int, gemCost: Int, coinGain: Int, crystalGain: Int, gemGain: Int): Boolean {
        val current = profile.value
        if (current.coins < coinCost || current.crystals < crystalCost || current.gems < gemCost) return false

        repository.update {
            it.copy(
                coins = it.coins - coinCost + coinGain,
                crystals = it.crystals - crystalCost + crystalGain,
                gems = it.gems - gemCost + gemGain,
            )
        }
        AudioEngine.play(GameSound.REWARD)
        return true
    }

    /** Opens a chest, granting a randomised bundle of currencies. */
    fun openChest(gemCost: Int): Triple<Int, Int, Int>? {
        val current = profile.value
        if (current.gems < gemCost) return null

        val coins = 400 + (0..900).random()
        val crystals = 20 + (0..60).random()
        val gems = (0..2).random()

        repository.update {
            it.copy(
                coins = it.coins + coins,
                crystals = it.crystals + crystals,
                gems = it.gems - gemCost + gems,
            )
        }
        AudioEngine.play(GameSound.REWARD)
        return Triple(coins, crystals, gems)
    }

    // --- Achievements -------------------------------------------------------------------------------

    /** Grants the reward for [achievement] exactly once. Returns false if it was not ready to claim. */
    fun claimAchievement(achievement: Achievement): Boolean {
        val current = profile.value
        if (!achievement.isClaimable(current)) return false

        repository.update {
            it.copy(
                coins = it.coins + achievement.coinReward,
                gems = it.gems + achievement.gemReward,
                claimedAchievements = it.claimedAchievements + achievement.name,
            )
        }
        AudioEngine.play(GameSound.REWARD)
        return true
    }

    // --- Settings ---------------------------------------------------------------------------------------

    fun setSoundEnabled(enabled: Boolean) {
        AudioEngine.soundEnabled = enabled
        repository.update { it.copy(soundEnabled = enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        AudioEngine.vibrationEnabled = enabled
        repository.update { it.copy(vibrationEnabled = enabled) }
    }

    fun setHighQuality(enabled: Boolean) {
        repository.update { it.copy(highQuality = enabled) }
    }

    fun resetProgress() {
        repository.reset()
        AudioEngine.play(GameSound.MENU_CLOSE)
    }
}
