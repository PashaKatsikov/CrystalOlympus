package com.crystalolympus.crystalolympusgame.game.model

import androidx.compose.ui.graphics.Color
import com.crystalolympus.crystalolympusgame.engine.GameSprite
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

/** The five energy types that drive the combat system. */
enum class CrystalType(
    val displayName: String,
    val sprite: GameSprite,
    val color: Color,
    val effect: String,
) {
    LIGHTNING(
        displayName = "Lightning Crystal",
        sprite = GameSprite.CRYSTAL_LIGHTNING,
        color = OlympusColors.Lightning,
        effect = "Bolts arc to one extra enemy and reach further.",
    ),
    FIRE(
        displayName = "Fire Crystal",
        sprite = GameSprite.CRYSTAL_FIRE,
        color = OlympusColors.Ember,
        effect = "Hits detonate and scorch the ground.",
    ),
    NATURE(
        displayName = "Nature Crystal",
        sprite = GameSprite.CRYSTAL_NATURE,
        color = OlympusColors.Nature,
        effect = "Restores health and hardens your guard.",
    ),
    MAGIC(
        displayName = "Magic Crystal",
        sprite = GameSprite.CRYSTAL_MAGIC,
        color = OlympusColors.Magic,
        effect = "Abilities hit harder and recharge faster.",
    ),
    DIVINE(
        displayName = "Divine Crystal",
        sprite = GameSprite.CRYSTAL_DIVINE,
        color = OlympusColors.Divine,
        effect = "Amplifies every other crystal you carry.",
    ),
}

/** Temporary buffs picked up from the magical fruit growing across Olympus. */
enum class FruitType(
    val displayName: String,
    val sprite: GameSprite,
    val durationSeconds: Float,
    val description: String,
) {
    SKY_APPLE("Sky Apple", GameSprite.FRUIT_SKY_APPLE, 14f, "+35% attack power"),
    SUN_BERRY("Sun Berry", GameSprite.FRUIT_SUN_BERRY, 14f, "+30% move and attack speed"),
    GOLDEN_GRAPES("Golden Grapes", GameSprite.FRUIT_GOLDEN_GRAPES, 18f, "Double coins and crystals"),
    CRYSTAL_POMEGRANATE("Crystal Pomegranate", GameSprite.FRUIT_CRYSTAL_POMEGRANATE, 12f, "Crystal effects doubled"),
}

/** How an enemy approaches the hero. */
enum class EnemyBehaviour { BRUTE, FLYER, CASTER, RUNNER, PHANTOM, TITAN }

enum class EnemyType(
    val displayName: String,
    val sprite: GameSprite,
    val behaviour: EnemyBehaviour,
    val baseHealth: Float,
    val baseDamage: Float,
    val speed: Float,
    val radius: Float,
    val drawHeight: Float,
    val coinReward: Int,
    val lore: String,
) {
    STONE_CYCLOPS(
        "Stone Cyclops", GameSprite.ENEMY_STONE_CYCLOPS, EnemyBehaviour.BRUTE,
        baseHealth = 150f, baseDamage = 16f, speed = 52f, radius = 34f, drawHeight = 118f,
        coinReward = 12,
        lore = "A boulder given legs. Slow, patient and impossible to shove aside.",
    ),
    SKY_HARPY(
        "Sky Harpy", GameSprite.ENEMY_SKY_HARPY, EnemyBehaviour.FLYER,
        baseHealth = 70f, baseDamage = 9f, speed = 108f, radius = 26f, drawHeight = 104f,
        coinReward = 10,
        lore = "Circles above the ruins and dives the moment you look away.",
    ),
    FIRE_SATYR(
        "Fire Satyr", GameSprite.ENEMY_FIRE_SATYR, EnemyBehaviour.CASTER,
        baseHealth = 88f, baseDamage = 12f, speed = 74f, radius = 26f, drawHeight = 100f,
        coinReward = 11,
        lore = "Leaves burning hoofprints wherever it dances.",
    ),
    CLOUD_WARRIOR(
        "Cloud Warrior", GameSprite.ENEMY_CLOUD_WARRIOR, EnemyBehaviour.CASTER,
        baseHealth = 110f, baseDamage = 13f, speed = 66f, radius = 30f, drawHeight = 108f,
        coinReward = 13,
        lore = "Storm made soldier, guarding the old sky roads.",
    ),
    CRYSTAL_GOLEM(
        "Crystal Golem", GameSprite.ENEMY_CRYSTAL_GOLEM, EnemyBehaviour.BRUTE,
        baseHealth = 260f, baseDamage = 22f, speed = 48f, radius = 38f, drawHeight = 130f,
        coinReward = 22,
        lore = "Its crystal shell drinks weak bolts. Hit it hard or not at all.",
    ),
    SHADOW_GUARDIAN(
        "Shadow Guardian", GameSprite.ENEMY_SHADOW_GUARDIAN, EnemyBehaviour.PHANTOM,
        baseHealth = 140f, baseDamage = 20f, speed = 84f, radius = 28f, drawHeight = 118f,
        coinReward = 24,
        lore = "Sworn to a temple that no longer stands. It fades between the columns.",
    ),
    ELECTRIC_SERPENT(
        "Electric Serpent", GameSprite.ENEMY_ELECTRIC_SERPENT, EnemyBehaviour.RUNNER,
        baseHealth = 105f, baseDamage = 15f, speed = 138f, radius = 26f, drawHeight = 108f,
        coinReward = 20,
        lore = "Fast enough to leave a trail of live current behind it.",
    ),
    ANCIENT_MINOTAUR(
        "Ancient Minotaur", GameSprite.ENEMY_ANCIENT_MINOTAUR, EnemyBehaviour.BRUTE,
        baseHealth = 330f, baseDamage = 30f, speed = 62f, radius = 36f, drawHeight = 128f,
        coinReward = 30,
        lore = "Older than the labyrinth, and far less forgiving.",
    ),
    LIGHTNING_TITAN(
        "Lightning Titan", GameSprite.BOSS_LIGHTNING_TITAN, EnemyBehaviour.TITAN,
        baseHealth = 2600f, baseDamage = 34f, speed = 46f, radius = 74f, drawHeight = 260f,
        coinReward = 400,
        lore = "The storm that broke the first source of Olympus.",
    ),
}

/** Blessings offered between stages. They last for the current run only. */
enum class Blessing(
    val displayName: String,
    val god: String,
    val sprite: GameSprite,
    val tint: Color,
    val lines: List<String>,
) {
    ZEUS(
        "Blessing of Zeus", "Zeus", GameSprite.CRYSTAL_LIGHTNING, OlympusColors.Lightning,
        listOf("Lightning damage +30%", "Chain lightning +1", "Attack range +10%"),
    ),
    ATHENA(
        "Blessing of Athena", "Athena", GameSprite.ARTIFACT_ZEUS_SEAL, OlympusColors.Gold,
        listOf("Defence +25%", "Damage taken -15%", "Crystal power +20%"),
    ),
    POSEIDON(
        "Blessing of Poseidon", "Poseidon", GameSprite.SPHERE_POWER, OlympusColors.Sky,
        listOf("Attacks release a water wave", "Enemies slowed by 30%"),
    ),
    HADES(
        "Blessing of Hades", "Hades", GameSprite.CRYSTAL_MAGIC, OlympusColors.Magic,
        listOf("Critical hits +25%", "Executes wounded elites"),
    ),
    ARTEMIS(
        "Blessing of Artemis", "Artemis", GameSprite.PLANT_GOLDEN_TREE, OlympusColors.Nature,
        listOf("Attack speed +25%", "Move speed +12%"),
    ),
    APOLLO(
        "Blessing of Apollo", "Apollo", GameSprite.SPHERE_HEALTH, OlympusColors.Success,
        listOf("Heal 20% of max health", "Health regeneration +2/s"),
    ),
    HERMES(
        "Blessing of Hermes", "Hermes", GameSprite.REWARD_COIN, OlympusColors.GoldBright,
        listOf("Pickup radius +80%", "Coin and crystal gain +35%"),
    ),
    HEPHAESTUS(
        "Blessing of Hephaestus", "Hephaestus", GameSprite.WEAPON_LIGHTNING_BLADE, OlympusColors.Ember,
        listOf("Bolts explode on impact", "Burning ground damage +50%"),
    ),
}

/** Permanent upgrade categories shown as tabs on the upgrades screen. */
enum class UpgradeBranch(val displayName: String) {
    OFFENSE("OFFENSE"),
    DEFENSE("DEFENSE"),
    UTILITY("UTILITY"),
    SPECIAL("SPECIAL"),
}

/**
 * A permanent upgrade node.
 *
 * @param perLevel how much one level adds, expressed in the unit named by [unit]
 */
enum class UpgradeNode(
    val branch: UpgradeBranch,
    val displayName: String,
    val sprite: GameSprite,
    val maxLevel: Int,
    val baseCost: Int,
    val perLevel: Float,
    val unit: String,
    val description: String,
) {
    LIGHTNING_POWER(
        UpgradeBranch.OFFENSE, "Lightning Power", GameSprite.CRYSTAL_LIGHTNING,
        maxLevel = 5, baseCost = 300, perLevel = 12f, unit = "%",
        description = "Increases the damage of every bolt you throw.",
    ),
    BOLT_SPEED(
        UpgradeBranch.OFFENSE, "Storm Cadence", GameSprite.WEAPON_LIGHTNING_BLADE,
        maxLevel = 5, baseCost = 340, perLevel = 8f, unit = "%",
        description = "Shortens the pause between attacks.",
    ),
    CHAIN_COUNT(
        UpgradeBranch.OFFENSE, "Arc Chain", GameSprite.THUNDERSTORM_CLOUD,
        maxLevel = 3, baseCost = 700, perLevel = 1f, unit = " target",
        description = "Chain lightning jumps to more enemies.",
    ),
    CRIT_CHANCE(
        UpgradeBranch.OFFENSE, "Divine Focus", GameSprite.ARTIFACT_SCEPTER,
        maxLevel = 5, baseCost = 420, perLevel = 3f, unit = "%",
        description = "Chance for a bolt to strike for double damage.",
    ),
    MAX_HEALTH(
        UpgradeBranch.DEFENSE, "Vital Essence", GameSprite.SPHERE_HEALTH,
        maxLevel = 5, baseCost = 300, perLevel = 90f, unit = " HP",
        description = "Raises the health you start each run with.",
    ),
    ARMOUR(
        UpgradeBranch.DEFENSE, "Marble Skin", GameSprite.STONE_RUNE_BLOCK,
        maxLevel = 5, baseCost = 380, perLevel = 4f, unit = "%",
        description = "Reduces all incoming damage.",
    ),
    SHIELD_STRENGTH(
        UpgradeBranch.DEFENSE, "Aegis", GameSprite.SHIELD_DIVINE,
        maxLevel = 4, baseCost = 520, perLevel = 1.2f, unit = "s",
        description = "Your shield ability lasts longer.",
    ),
    REGENERATION(
        UpgradeBranch.DEFENSE, "Ambrosia", GameSprite.PLANT_WHITE_LILY,
        maxLevel = 4, baseCost = 600, perLevel = 1f, unit = " HP/s",
        description = "Slowly restores health during a run.",
    ),
    MOVE_SPEED(
        UpgradeBranch.UTILITY, "Winged Sandals", GameSprite.CLOUD_WHITE,
        maxLevel = 5, baseCost = 320, perLevel = 5f, unit = "%",
        description = "The chosen one moves faster across the arena.",
    ),
    PICKUP_RANGE(
        UpgradeBranch.UTILITY, "Crystal Pull", GameSprite.CRYSTAL_MAGIC,
        maxLevel = 4, baseCost = 280, perLevel = 20f, unit = "%",
        description = "Draws crystals and coins in from further away.",
    ),
    COIN_GAIN(
        UpgradeBranch.UTILITY, "Golden Tribute", GameSprite.REWARD_COIN,
        maxLevel = 5, baseCost = 450, perLevel = 8f, unit = "%",
        description = "Every run pays out more coins.",
    ),
    FRUIT_DURATION(
        UpgradeBranch.UTILITY, "Sacred Orchard", GameSprite.FRUIT_GOLDEN_GRAPES,
        maxLevel = 4, baseCost = 400, perLevel = 15f, unit = "%",
        description = "Fruit buffs linger for longer.",
    ),
    ENERGY_POOL(
        UpgradeBranch.SPECIAL, "Storm Reservoir", GameSprite.SPHERE_POWER,
        maxLevel = 5, baseCost = 380, perLevel = 30f, unit = " EN",
        description = "Increases your maximum energy.",
    ),
    ENERGY_REGEN(
        UpgradeBranch.SPECIAL, "Sky Current", GameSprite.CRYSTAL_DIVINE,
        maxLevel = 5, baseCost = 460, perLevel = 1.4f, unit = "/s",
        description = "Energy returns faster between abilities.",
    ),
    ABILITY_POWER(
        UpgradeBranch.SPECIAL, "Wrath of Olympus", GameSprite.ALTAR_LIGHTNING,
        maxLevel = 5, baseCost = 640, perLevel = 12f, unit = "%",
        description = "All abilities deal more damage.",
    ),
    ULTIMATE_CHARGE(
        UpgradeBranch.SPECIAL, "Titan's Echo", GameSprite.ARTIFACT_ZEUS_SEAL,
        maxLevel = 4, baseCost = 800, perLevel = 12f, unit = "%",
        description = "The divine discharge charges faster.",
    ),
    ;

    fun costForLevel(level: Int): Int = (baseCost * (1f + level * 0.75f)).toInt()
}

/** Equipment the hero can find and wear. */
enum class EquipmentSlot(val displayName: String) { WEAPON("Weapon"), ARMOUR("Armour"), RELIC("Relic"), ARTIFACT("Artifact") }

enum class EquipmentItem(
    val slot: EquipmentSlot,
    val displayName: String,
    val sprite: GameSprite,
    val unlockZoneIndex: Int,
    val damageBonus: Float = 0f,
    val healthBonus: Float = 0f,
    val speedBonus: Float = 0f,
    val energyBonus: Float = 0f,
) {
    LIGHTNING_BLADE(
        EquipmentSlot.WEAPON, "Lightning Blade", GameSprite.WEAPON_LIGHTNING_BLADE,
        unlockZoneIndex = 0, damageBonus = 0.10f,
    ),
    STORM_SCEPTER(
        EquipmentSlot.WEAPON, "Storm Scepter", GameSprite.ARTIFACT_SCEPTER,
        unlockZoneIndex = 3, damageBonus = 0.22f, energyBonus = 20f,
    ),
    MARBLE_GUARD(
        EquipmentSlot.ARMOUR, "Marble Guard", GameSprite.STONE_BALUSTRADE,
        unlockZoneIndex = 1, healthBonus = 120f,
    ),
    STORMWEAVE(
        EquipmentSlot.ARMOUR, "Stormweave", GameSprite.CLOUD_STORM,
        unlockZoneIndex = 4, healthBonus = 220f, speedBonus = 0.06f,
    ),
    TEMPLE_KEY(
        EquipmentSlot.RELIC, "Heaven Temple Key", GameSprite.HEAVEN_TEMPLE_KEY,
        unlockZoneIndex = 2, energyBonus = 30f,
    ),
    DIVINE_SPHERE(
        EquipmentSlot.RELIC, "Divine Sphere", GameSprite.SPHERE_POWER,
        unlockZoneIndex = 5, energyBonus = 45f, damageBonus = 0.08f,
    ),
    SEAL_OF_ZEUS(
        EquipmentSlot.ARTIFACT, "Seal of Zeus", GameSprite.ARTIFACT_ZEUS_SEAL,
        unlockZoneIndex = 0, damageBonus = 0.06f, healthBonus = 60f,
    ),
    GOLDEN_CRYSTAL(
        EquipmentSlot.ARTIFACT, "Golden Crystal", GameSprite.CRYSTAL_DIVINE,
        unlockZoneIndex = 6, damageBonus = 0.15f, healthBonus = 150f, energyBonus = 25f,
    ),
}

/** The zones of Olympus, unlocked in order from the world map. */
enum class Zone(
    val displayName: String,
    val subtitle: String,
    val sprite: GameSprite,
    val waves: Int,
    val difficulty: Float,
    val hasBoss: Boolean,
    val enemies: List<EnemyType>,
) {
    ZEUS_TEMPLE(
        "Zeus Temple", "Where the first bolt was forged",
        GameSprite.ALTAR_LIGHTNING, waves = 6, difficulty = 1.0f, hasBoss = false,
        enemies = listOf(EnemyType.STONE_CYCLOPS, EnemyType.SKY_HARPY),
    ),
    ATHENA_GARDENS(
        "Athena Gardens", "Green energy under old olive trees",
        GameSprite.PLANT_GOLDEN_TREE, waves = 7, difficulty = 1.25f, hasBoss = false,
        enemies = listOf(EnemyType.STONE_CYCLOPS, EnemyType.SKY_HARPY, EnemyType.FIRE_SATYR),
    ),
    SKY_ISLANDS(
        "Sky Islands", "Floating stone above the clouds",
        GameSprite.OLYMPUS_PLATFORM, waves = 8, difficulty = 1.55f, hasBoss = false,
        enemies = listOf(EnemyType.SKY_HARPY, EnemyType.FIRE_SATYR, EnemyType.CLOUD_WARRIOR),
    ),
    STORM_PEAKS(
        "Storm Peaks", "Endless thunder, rare crystals",
        GameSprite.THUNDERSTORM_CLOUD, waves = 8, difficulty = 1.9f, hasBoss = true,
        enemies = listOf(EnemyType.CLOUD_WARRIOR, EnemyType.ELECTRIC_SERPENT, EnemyType.CRYSTAL_GOLEM),
    ),
    TITAN_RUINS(
        "Titan Ruins", "The sanctuary that fell first",
        GameSprite.OLYMPUS_RUINS, waves = 9, difficulty = 2.3f, hasBoss = false,
        enemies = listOf(EnemyType.CRYSTAL_GOLEM, EnemyType.SHADOW_GUARDIAN, EnemyType.ANCIENT_MINOTAUR),
    ),
    CRYSTAL_SOURCE(
        "Crystal Source", "The heart of divine energy",
        GameSprite.CRYSTAL_SOURCE, waves = 9, difficulty = 2.7f, hasBoss = false,
        enemies = listOf(
            EnemyType.SHADOW_GUARDIAN, EnemyType.ELECTRIC_SERPENT,
            EnemyType.CRYSTAL_GOLEM, EnemyType.ANCIENT_MINOTAUR,
        ),
    ),
    GOLDEN_PALACE(
        "Golden Palace", "Where Olympus is made whole again",
        GameSprite.OLYMPUS_PORTAL, waves = 10, difficulty = 3.2f, hasBoss = true,
        enemies = listOf(
            EnemyType.ANCIENT_MINOTAUR, EnemyType.SHADOW_GUARDIAN,
            EnemyType.CLOUD_WARRIOR, EnemyType.CRYSTAL_GOLEM,
        ),
    ),
    ;

    val index: Int get() = ordinal
}
