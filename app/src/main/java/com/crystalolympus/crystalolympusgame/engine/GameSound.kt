package com.crystalolympus.crystalolympusgame.engine

/** Short one-shot effects played through a [android.media.SoundPool]. */
enum class GameSound(val file: String) {
    BUTTON_CLICK("Button_Click_asset.mp3"),
    MENU_OPEN("Menu_Open_asset.mp3"),
    MENU_CLOSE("Menu_Close_asset.mp3"),
    LIGHTNING_STRIKE("Zeus_Lightning_Strike_asset.mp3"),
    CHAIN_LIGHTNING("Chain_Lightning_asset.mp3"),
    CRYSTAL_PICKUP("Crystal_Activation_asset.mp3"),
    ALTAR_ACTIVATE("Altar_Activation_asset.mp3"),
    BARRIER_DESTROY("Energy_Barrier_Destroy_asset.mp3"),
    PORTAL_OPEN("Portal_Opening_asset.mp3"),
    REWARD("Reward_Receive_asset.mp3"),
    UPGRADE("Lightning_Ability_Upgrade_asset.mp3"),
    LEVEL_COMPLETE("Level_Complete_asset.mp3"),
    LEVEL_FAILED("Level_Failed_asset.mp3"),
}
