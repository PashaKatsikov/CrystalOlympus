package com.crystalolympus.crystalolympusgame.game.model

import com.crystalolympus.crystalolympusgame.engine.GameSprite
import java.util.Calendar

/** One reward in the seven-day login cycle. */
data class DailyReward(val day: Int, val coins: Int, val gems: Int, val sprite: GameSprite)

/**
 * The seven-day login-streak table, plus the local-calendar clock the streak is measured against.
 *
 * The streak itself lives on [com.crystalolympus.crystalolympusgame.data.PlayerProfile] as two plain
 * fields (`dailyStreak`, `lastDailyClaimEpochDay`) rather than here, so it persists with the rest of
 * the profile through the existing JSON save file with no extra wiring.
 */
object DailyRewards {

    val schedule: List<DailyReward> = listOf(
        DailyReward(day = 1, coins = 100, gems = 0, sprite = GameSprite.REWARD_COIN),
        DailyReward(day = 2, coins = 150, gems = 0, sprite = GameSprite.REWARD_COIN),
        DailyReward(day = 3, coins = 200, gems = 1, sprite = GameSprite.CRYSTAL_MAGIC),
        DailyReward(day = 4, coins = 250, gems = 1, sprite = GameSprite.REWARD_CRYSTAL_SHARD),
        DailyReward(day = 5, coins = 300, gems = 1, sprite = GameSprite.CRYSTAL_DIVINE),
        DailyReward(day = 6, coins = 400, gems = 2, sprite = GameSprite.TREASURE_CHEST),
        DailyReward(day = 7, coins = 600, gems = 3, sprite = GameSprite.ARTIFACT_ZEUS_SEAL),
    )

    fun rewardForDay(day: Int): DailyReward = schedule[(day - 1).mod(schedule.size)]

    /**
     * A day counter that only advances at local midnight. Used purely to gate "once per calendar
     * day" - it does not need to agree with any server or with [System.currentTimeMillis]'s epoch,
     * only to be monotonic and to roll over exactly once per local day.
     */
    fun currentEpochDay(): Long {
        val calendar = Calendar.getInstance()
        val localMillis = calendar.timeInMillis + calendar.timeZone.getOffset(calendar.timeInMillis)
        return localMillis / MILLIS_PER_DAY
    }

    private const val MILLIS_PER_DAY = 86_400_000L
}
