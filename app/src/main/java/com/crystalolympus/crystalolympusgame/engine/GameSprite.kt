package com.crystalolympus.crystalolympusgame.engine

/**
 * Every drawable piece of art the game can show.
 *
 * Several sheets carry more than one object, and those objects are not spaced evenly across the
 * canvas, so each sprite carries the exact slice of its sheet that belongs to it. The bounds are
 * fractions of the sheet, which keeps them valid no matter how far the decoder downsamples. They
 * were measured from the gutters between the objects by `tools/slice_sheets.ps1`; only the gutter
 * matters here, because the tight silhouette is trimmed on device where the alpha channel is real.
 *
 * @param file name of the sheet inside `assets/images`
 * @param left fraction of the sheet width where this sprite's slice starts
 * @param top fraction of the sheet height where this sprite's slice starts
 * @param right fraction of the sheet width where this sprite's slice ends
 * @param bottom fraction of the sheet height where this sprite's slice ends
 * @param maxSize cap on the longest edge, in pixels, of the decoded sprite
 * @param trim whether fully transparent borders are cut away after slicing
 * @param preload whether [GameAssets.preloadAll] decodes this sprite up front
 */
enum class GameSprite(
    val file: String,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val maxSize: Int = 512,
    val trim: Boolean = true,
    val preload: Boolean = true,
) {
    // --- Loading artwork, decoded by the loading screen itself ----------------------------------
    LOADING_LANDSCAPE("Horizontal_Loading_Screen.webp", maxSize = 2048, trim = false, preload = false),
    LOADING_PORTRAIT("Vertical_Loading_Screen.webp", maxSize = 2048, trim = false, preload = false),

    LOGO("Game_Name.webp", maxSize = 512),

    // --- Backgrounds ---------------------------------------------------------------------------
    BG_TEMPLE_FLOOR("Ancient_Temple_Background_asset.webp", maxSize = 1024, trim = false),
    BG_CLOUD_ISLANDS("Cloud_Islands_Background_asset.webp", maxSize = 1024, trim = false),
    BG_OLYMPUS_SKY("Olympus_Sky_Background_asset.webp", maxSize = 1024, trim = false),

    // --- Heroes and bosses ---------------------------------------------------------------------
    // The shipped file names are shuffled: the "energy crystals" sheet holds the titan, and the
    // "titan boss" sheet holds the crystals. The pairing below follows the artwork, not the name.
    HERO_ZEUS_CHOSEN("Chosen_Zeus_Hero_asset.webp", maxSize = 640),
    ZEUS_MENTOR("Zeus_Divine_Mentor_asset.webp", maxSize = 640),
    BOSS_LIGHTNING_TITAN("Energy_Crystals_Set_asset.webp", maxSize = 768),

    // --- Enemy set 1 ---------------------------------------------------------------------------
    ENEMY_STONE_CYCLOPS("Mythical_Creatures_Set_1_asset.webp", right = 0.2929f, maxSize = 320),
    ENEMY_SKY_HARPY("Mythical_Creatures_Set_1_asset.webp", left = 0.2929f, right = 0.5278f, maxSize = 320),
    ENEMY_FIRE_SATYR("Mythical_Creatures_Set_1_asset.webp", left = 0.5278f, right = 0.7348f, maxSize = 320),
    ENEMY_CLOUD_WARRIOR("Mythical_Creatures_Set_1_asset.webp", left = 0.7348f, maxSize = 320),

    // --- Enemy set 2 ---------------------------------------------------------------------------
    ENEMY_CRYSTAL_GOLEM("Mythical_Creatures_Set_2_asset.webp", right = 0.2778f, maxSize = 320),
    ENEMY_SHADOW_GUARDIAN("Mythical_Creatures_Set_2_asset.webp", left = 0.2778f, right = 0.5227f, maxSize = 320),
    ENEMY_ELECTRIC_SERPENT("Mythical_Creatures_Set_2_asset.webp", left = 0.5227f, right = 0.7374f, maxSize = 320),
    ENEMY_ANCIENT_MINOTAUR("Mythical_Creatures_Set_2_asset.webp", left = 0.7374f, maxSize = 320),

    // --- Crystals ------------------------------------------------------------------------------
    CRYSTAL_LIGHTNING("Lightning_Titan_Boss_asset.webp", right = 0.2702f, maxSize = 256),
    CRYSTAL_FIRE("Lightning_Titan_Boss_asset.webp", left = 0.2702f, right = 0.5025f, maxSize = 256),
    CRYSTAL_NATURE("Lightning_Titan_Boss_asset.webp", left = 0.5025f, right = 0.7348f, maxSize = 256),
    CRYSTAL_MAGIC("Lightning_Titan_Boss_asset.webp", left = 0.7348f, maxSize = 256),
    CRYSTAL_DIVINE("Divine_Golden_Crystal_asset.webp", maxSize = 256),

    // --- Fruits --------------------------------------------------------------------------------
    FRUIT_SKY_APPLE("Olympus_Magic_Fruits_Set_asset.webp", right = 0.2626f, maxSize = 224),
    FRUIT_SUN_BERRY("Olympus_Magic_Fruits_Set_asset.webp", left = 0.2626f, right = 0.4773f, maxSize = 224),
    FRUIT_GOLDEN_GRAPES("Olympus_Magic_Fruits_Set_asset.webp", left = 0.4773f, right = 0.7121f, maxSize = 224),
    FRUIT_CRYSTAL_POMEGRANATE("Olympus_Magic_Fruits_Set_asset.webp", left = 0.7121f, maxSize = 224),

    // --- Rewards and currencies ----------------------------------------------------------------
    REWARD_COIN("Divine_Energy_Coin_asset.webp", right = 0.2652f, maxSize = 224),
    REWARD_CRYSTAL_SHARD("Divine_Energy_Coin_asset.webp", left = 0.2652f, right = 0.4798f, maxSize = 224),
    REWARD_AMPHORA("Divine_Energy_Coin_asset.webp", left = 0.4798f, right = 0.7096f, maxSize = 224),
    REWARD_GEM("Divine_Energy_Coin_asset.webp", left = 0.7096f, maxSize = 224),

    // --- Artifacts and equipment ---------------------------------------------------------------
    ARTIFACT_ZEUS_SEAL("Olympus_Rewards_Set_asset.webp", maxSize = 320),
    ARTIFACT_SCEPTER("Zeus_Artifact_asset.webp", maxSize = 320),
    WEAPON_LIGHTNING_BLADE("Zeus_Lightning_Weapon_asset.webp", maxSize = 320),
    HEAVEN_TEMPLE_KEY("Power_Energy_Sphere_asset.webp", maxSize = 320),

    // --- Spheres, shields, barriers --------------------------------------------------------------
    SPHERE_HEALTH("Health_Energy_Sphere_asset.webp", maxSize = 256),
    SPHERE_POWER("Heaven_Temple_Key_asset.webp", maxSize = 256),
    SHIELD_DIVINE("Protective_Energy_Shield_asset.webp", maxSize = 384),
    ENERGY_BARRIER("Energy_Barrier_asset.webp", maxSize = 320),
    THUNDERSTORM_CLOUD("Thunderstorm_Cloud_asset.webp", maxSize = 384),

    // --- Structures ----------------------------------------------------------------------------
    ALTAR_LIGHTNING("Lightning_Altar_asset.webp", maxSize = 512),
    ALTAR_UPGRADE("Upgrade_Altar_asset.webp", maxSize = 512),
    CRYSTAL_SOURCE("Ancient_God_Statue_asset.webp", maxSize = 512),
    ZEUS_STATUE("Crystal_Energy_Source_asset.webp", maxSize = 448),
    OLYMPUS_BRIDGE("Ancient_Olympus_Bridge_asset.webp", maxSize = 640),
    OLYMPUS_RUINS("Destroyed_Olympus_Structure_asset.webp", maxSize = 640),
    OLYMPUS_PLATFORM("Floating_Olympus_Platform_asset.webp", maxSize = 768),
    OLYMPUS_PORTAL("Olympus_Magic_Portal_asset.webp", maxSize = 512),
    TREASURE_CHEST("Divine_Treasure_Chest_asset.webp", maxSize = 448),

    // --- Clouds --------------------------------------------------------------------------------
    CLOUD_WHITE("Olympus_Clouds_Set_asset.webp", right = 0.4975f, bottom = 0.4762f, maxSize = 384),
    CLOUD_STORM("Olympus_Clouds_Set_asset.webp", left = 0.4975f, bottom = 0.4940f, maxSize = 384),
    CLOUD_GOLDEN("Olympus_Clouds_Set_asset.webp", right = 0.4975f, top = 0.4762f, maxSize = 384),
    CLOUD_MYSTIC("Olympus_Clouds_Set_asset.webp", left = 0.4975f, top = 0.4940f, maxSize = 384),

    // --- Columns -------------------------------------------------------------------------------
    COLUMN_MARBLE("Olympus_Columns_Set_asset.webp", right = 0.2475f, maxSize = 320),
    COLUMN_SHATTERED("Olympus_Columns_Set_asset.webp", left = 0.2475f, right = 0.4949f, maxSize = 320),
    COLUMN_GOLDEN("Olympus_Columns_Set_asset.webp", left = 0.4949f, right = 0.7323f, maxSize = 320),
    COLUMN_CRYSTAL("Olympus_Columns_Set_asset.webp", left = 0.7323f, maxSize = 320),

    // --- Plants --------------------------------------------------------------------------------
    PLANT_GOLDEN_TREE("Olympus_Magical_Plants_Set_asset.webp", right = 0.3182f, maxSize = 320),
    PLANT_CRYSTAL_BUSH("Olympus_Magical_Plants_Set_asset.webp", left = 0.3182f, right = 0.5455f, maxSize = 320),
    PLANT_WHITE_LILY("Olympus_Magical_Plants_Set_asset.webp", left = 0.5455f, right = 0.7525f, maxSize = 320),
    PLANT_VIOLET_BLOOM("Olympus_Magical_Plants_Set_asset.webp", left = 0.7525f, maxSize = 320),

    // --- Stone props ---------------------------------------------------------------------------
    STONE_BOULDER("Olympus_Stone_Elements_Set_asset.webp", right = 0.4823f, bottom = 0.5238f, maxSize = 288),
    STONE_RUNE_BLOCK("Olympus_Stone_Elements_Set_asset.webp", left = 0.4823f, bottom = 0.4732f, maxSize = 288),
    STONE_SLAB("Olympus_Stone_Elements_Set_asset.webp", right = 0.4823f, top = 0.5238f, maxSize = 288),
    STONE_BALUSTRADE("Olympus_Stone_Elements_Set_asset.webp", left = 0.4823f, top = 0.4732f, maxSize = 288),

    // --- Traps ---------------------------------------------------------------------------------
    TRAP_LIGHTNING("Olympus_Traps_Set_asset.webp", right = 0.5025f, bottom = 0.4940f, maxSize = 288),
    TRAP_FIRE("Olympus_Traps_Set_asset.webp", left = 0.5025f, bottom = 0.4821f, maxSize = 288),
    TRAP_CRYSTAL_SPIKES("Olympus_Traps_Set_asset.webp", right = 0.5025f, top = 0.4940f, maxSize = 288),
    TRAP_FALLING_STONES("Olympus_Traps_Set_asset.webp", left = 0.5025f, top = 0.4821f, maxSize = 288),
    ;

    /** Fraction of the sheet, along each axis, that this sprite's slice covers. */
    val widthFraction: Float get() = right - left
    val heightFraction: Float get() = bottom - top
}
