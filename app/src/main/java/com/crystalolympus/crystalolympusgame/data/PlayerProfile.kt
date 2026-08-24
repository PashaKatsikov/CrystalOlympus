package com.crystalolympus.crystalolympusgame.data

import com.crystalolympus.crystalolympusgame.game.model.CrystalType
import com.crystalolympus.crystalolympusgame.game.model.EquipmentItem
import com.crystalolympus.crystalolympusgame.game.model.EquipmentSlot
import com.crystalolympus.crystalolympusgame.game.model.FruitType
import com.crystalolympus.crystalolympusgame.game.model.UpgradeNode
import kotlinx.serialization.Serializable

/** Everything that survives between runs. Persisted as JSON in shared preferences. */
@Serializable
data class PlayerProfile(
    val coins: Int = 250,
    val crystals: Int = 20,
    val gems: Int = 5,
    val experience: Int = 0,
    val zonesUnlocked: Int = 1,
    val zonesCleared: Int = 0,
    val bestWave: Int = 0,
    val totalRuns: Int = 0,
    val upgrades: Map<String, Int> = emptyMap(),
    val equipped: Map<String, String> = emptyMap(),
    val unlockedEquipment: Set<String> = setOf(
        EquipmentItem.LIGHTNING_BLADE.name,
        EquipmentItem.SEAL_OF_ZEUS.name,
    ),
    val crystalsFound: Map<String, Int> = emptyMap(),
    val fruitsFound: Map<String, Int> = emptyMap(),
    val enemiesDefeated: Map<String, Int> = emptyMap(),
    val artifactsFound: Set<String> = emptySet(),
    val claimedAchievements: Set<String> = emptySet(),
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val highQuality: Boolean = true,
) {
    val level: Int get() = 1 + experience / EXPERIENCE_PER_LEVEL
    val experienceIntoLevel: Int get() = experience % EXPERIENCE_PER_LEVEL

    fun levelOf(node: UpgradeNode): Int = upgrades[node.name] ?: 0

    fun bonusOf(node: UpgradeNode): Float = levelOf(node) * node.perLevel

    fun equippedIn(slot: EquipmentSlot): EquipmentItem? =
        equipped[slot.name]?.let { name -> EquipmentItem.entries.firstOrNull { it.name == name } }

    fun isUnlocked(item: EquipmentItem): Boolean = item.name in unlockedEquipment

    fun crystalCount(type: CrystalType): Int = crystalsFound[type.name] ?: 0

    fun fruitCount(type: FruitType): Int = fruitsFound[type.name] ?: 0

    fun defeatedCount(enemyName: String): Int = enemiesDefeated[enemyName] ?: 0

    companion object {
        const val EXPERIENCE_PER_LEVEL = 1000
    }
}
