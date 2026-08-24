package com.crystalolympus.crystalolympusgame.game.model

import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile

/**
 * One-off milestones measured against stats [PlayerProfile] already accumulates during normal play.
 * Each entry is claimed at most once and pays out a fixed bundle of coins and gems; claiming never
 * happens automatically, so a run that completes a milestone leaves it sitting in the Collection
 * screen until the player opens it themselves.
 */
enum class Achievement(
    val displayName: String,
    val description: String,
    val sprite: GameSprite,
    val target: Int,
    val coinReward: Int,
    val gemReward: Int,
    private val progressOf: (PlayerProfile) -> Int,
) {
    FIRST_VICTORY(
        "First Light", "Clear your first zone of Olympus.",
        GameSprite.ARTIFACT_ZEUS_SEAL, target = 1, coinReward = 200, gemReward = 1,
        progressOf = { it.zonesCleared },
    ),
    ZONE_CLEARER(
        "Restorer of Olympus", "Clear every zone of Olympus.",
        GameSprite.OLYMPUS_PORTAL, target = Zone.entries.size, coinReward = 1500, gemReward = 5,
        progressOf = { it.zonesCleared },
    ),
    MONSTER_HUNTER(
        "Monster Hunter", "Defeat 50 mythical creatures.",
        GameSprite.WEAPON_LIGHTNING_BLADE, target = 50, coinReward = 300, gemReward = 1,
        progressOf = { profile -> profile.enemiesDefeated.values.sum() },
    ),
    SCOURGE_OF_TITANS(
        "Scourge of the Titans", "Defeat 500 mythical creatures.",
        GameSprite.ARTIFACT_SCEPTER, target = 500, coinReward = 2000, gemReward = 4,
        progressOf = { profile -> profile.enemiesDefeated.values.sum() },
    ),
    CRYSTAL_COLLECTOR(
        "Crystal Collector", "Collect 250 energy crystals.",
        GameSprite.CRYSTAL_DIVINE, target = 250, coinReward = 400, gemReward = 2,
        progressOf = { profile -> profile.crystalsFound.values.sum() },
    ),
    ORCHARD_KEEPER(
        "Orchard Keeper", "Gather 100 magic fruits.",
        GameSprite.FRUIT_GOLDEN_GRAPES, target = 100, coinReward = 350, gemReward = 1,
        progressOf = { profile -> profile.fruitsFound.values.sum() },
    ),
    STORM_SURVIVOR(
        "Storm Survivor", "Reach wave 10 in a single run.",
        GameSprite.THUNDERSTORM_CLOUD, target = 10, coinReward = 500, gemReward = 2,
        progressOf = { it.bestWave },
    ),
    VETERAN(
        "Veteran of Olympus", "Complete 25 runs.",
        GameSprite.SHIELD_DIVINE, target = 25, coinReward = 600, gemReward = 2,
        progressOf = { it.totalRuns },
    ),
    ARMOURY(
        "Armoury of the Gods", "Unlock 6 pieces of equipment.",
        GameSprite.STONE_BALUSTRADE, target = 6, coinReward = 450, gemReward = 2,
        progressOf = { profile -> profile.unlockedEquipment.size },
    ),
    ;

    /** Current progress, clamped to [target] so callers never need to coerce a fraction themselves. */
    fun progress(profile: PlayerProfile): Int = progressOf(profile).coerceIn(0, target)

    fun isComplete(profile: PlayerProfile): Boolean = progressOf(profile) >= target

    fun isClaimed(profile: PlayerProfile): Boolean = name in profile.claimedAchievements

    fun isClaimable(profile: PlayerProfile): Boolean = isComplete(profile) && !isClaimed(profile)
}
