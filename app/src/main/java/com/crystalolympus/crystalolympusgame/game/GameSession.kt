package com.crystalolympus.crystalolympusgame.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.core.GameAssets
import com.crystalolympus.crystalolympusgame.core.GameSound
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.game.model.Blessing
import com.crystalolympus.crystalolympusgame.game.model.CrystalType
import com.crystalolympus.crystalolympusgame.game.model.EnemyBehaviour
import com.crystalolympus.crystalolympusgame.game.model.EnemyType
import com.crystalolympus.crystalolympusgame.game.model.EquipmentSlot
import com.crystalolympus.crystalolympusgame.game.model.FruitType
import com.crystalolympus.crystalolympusgame.game.model.UpgradeNode
import com.crystalolympus.crystalolympusgame.game.model.Zone
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class GamePhase { INTRO, FIGHTING, WAVE_CLEARED, BLESSING, PAUSED, VICTORY, DEFEAT }

enum class Ability(
    val displayName: String,
    val sprite: GameSprite,
    val energyCost: Float,
    val cooldown: Float,
    val tint: Color,
) {
    CHAIN_LIGHTNING("Chain Lightning", GameSprite.WEAPON_LIGHTNING_BLADE, 25f, 4.5f, OlympusColors.Lightning),
    THUNDER_STRIKE("Thunder Strike", GameSprite.THUNDERSTORM_CLOUD, 40f, 8f, OlympusColors.Sky),
    DIVINE_SHIELD("Divine Shield", GameSprite.SHIELD_DIVINE, 30f, 13f, OlympusColors.Gold),
    DIVINE_DISCHARGE("Divine Discharge", GameSprite.ARTIFACT_ZEUS_SEAL, 0f, 0f, OlympusColors.Divine),
}

/** Everything the run screen shows outside the arena itself. */
class HudState {
    var health by mutableFloatStateOf(0f)
    var maxHealth by mutableFloatStateOf(1f)
    var energy by mutableFloatStateOf(0f)
    var maxEnergy by mutableFloatStateOf(1f)
    var ultimateCharge by mutableFloatStateOf(0f)
    var wave by mutableIntStateOf(1)
    var totalWaves by mutableIntStateOf(1)
    var enemiesLeft by mutableIntStateOf(0)
    var coins by mutableIntStateOf(0)
    var crystals by mutableIntStateOf(0)
    var bossHealth by mutableFloatStateOf(0f)
    var bossPhase by mutableIntStateOf(0)
    var bossActive by mutableStateOf(false)
    val abilityCooldowns = mutableStateListOf(0f, 0f, 0f, 0f)
    val crystalCharges = mutableStateListOf(0, 0, 0, 0, 0)
    val activeFruits = mutableStateListOf<ActiveFruit>()
    var banner by mutableStateOf<String?>(null)
}

data class ActiveFruit(val type: FruitType, val remaining: Float, val total: Float)

/** Totals handed to the results screen when a run ends. */
data class RunResult(
    val victory: Boolean,
    val zone: Zone,
    val waveReached: Int,
    val coins: Int,
    val crystals: Int,
    val gems: Int,
    val experience: Int,
    val crystalsByType: Map<CrystalType, Int>,
    val fruitsByType: Map<FruitType, Int>,
    val enemiesDefeated: Map<String, Int>,
)

/**
 * The simulation behind one run through a zone.
 *
 * The class owns mutable entity lists that the renderer reads directly every frame; only the values
 * the Compose HUD needs are mirrored into snapshot state, so combat never triggers recomposition on
 * its own.
 */
class GameSession(
    val zone: Zone,
    private val profile: PlayerProfile,
    private val random: Random = Random(System.nanoTime()),
) {

    // --- Arena ----------------------------------------------------------------------------------
    val arenaWidth = 2000f
    val arenaHeight = 1250f

    val groundSprite: GameSprite = when (zone) {
        Zone.SKY_ISLANDS, Zone.STORM_PEAKS -> GameSprite.BG_CLOUD_ISLANDS
        Zone.GOLDEN_PALACE, Zone.CRYSTAL_SOURCE -> GameSprite.BG_OLYMPUS_SKY
        else -> GameSprite.BG_TEMPLE_FLOOR
    }

    val props = mutableListOf<Prop>()
    val clouds = mutableListOf<DriftingCloud>()
    val traps = mutableListOf<GroundZone>()

    // --- Entities -------------------------------------------------------------------------------
    val hero: Hero
    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val arcs = mutableListOf<LightningArc>()
    val zones = mutableListOf<GroundZone>()
    val pickups = mutableListOf<Pickup>()
    val particles = mutableListOf<Particle>()
    val floatingTexts = mutableListOf<FloatingText>()

    var boss: Enemy? = null
        private set

    // --- Observable state -----------------------------------------------------------------------
    var phase by mutableStateOf(GamePhase.INTRO)
        private set
    var frameTick by mutableIntStateOf(0)
        private set
    var blessingChoices by mutableStateOf<List<Blessing>>(emptyList())
        private set
    var chosenBlessings by mutableStateOf<List<Blessing>>(emptyList())
        private set
    var result by mutableStateOf<RunResult?>(null)
        private set

    val hud = HudState()

    // --- Derived hero stats ---------------------------------------------------------------------
    private val equipDamage: Float
    private val equipHealth: Float
    private val equipSpeed: Float
    private val equipEnergy: Float

    private var baseDamage: Float
    private var attackInterval: Float
    private var moveSpeed: Float
    private var pickupRadius: Float
    private var armour: Float
    private var chainTargets: Int
    private var critChance: Float
    private var abilityPower: Float
    private var healthRegen: Float
    private var energyRegen: Float
    private var attackRange: Float = 430f
    private var coinMultiplier: Float
    private var fruitDurationMultiplier: Float
    private var shieldDuration: Float
    private var ultimateRate: Float

    // --- Run bookkeeping -------------------------------------------------------------------------
    private val crystalCharges = IntArray(CrystalType.entries.size)
    private val fruitTimers = HashMap<FruitType, Float>()
    private val abilityTimers = FloatArray(Ability.entries.size)
    private val crystalsCollected = HashMap<CrystalType, Int>()
    private val fruitsCollected = HashMap<FruitType, Int>()
    private val defeated = HashMap<String, Int>()

    private var earnedCoins = 0
    private var earnedCrystals = 0
    private var earnedExperience = 0

    private var wave = 0
    private var spawnQueue = mutableListOf<EnemyType>()
    private var spawnTimer = 0f
    private var waveBreakTimer = 0f
    private var fruitSpawnTimer = 12f
    private var introTimer = 1.8f
    private var elapsed = 0f
    private var nextBlessingWave = 3
    private var pendingBlessingAfterWave = false

    init {
        val equipped = EquipmentSlot.entries.mapNotNull { profile.equippedIn(it) }
        equipDamage = equipped.sumOf { it.damageBonus.toDouble() }.toFloat()
        equipHealth = equipped.sumOf { it.healthBonus.toDouble() }.toFloat()
        equipSpeed = equipped.sumOf { it.speedBonus.toDouble() }.toFloat()
        equipEnergy = equipped.sumOf { it.energyBonus.toDouble() }.toFloat()

        val levelBonus = 1f + (profile.level - 1) * 0.04f

        val maxHealth = (520f + profile.bonusOf(UpgradeNode.MAX_HEALTH) + equipHealth) * levelBonus
        val maxEnergy = 100f + profile.bonusOf(UpgradeNode.ENERGY_POOL) + equipEnergy

        baseDamage = 34f * (1f + profile.bonusOf(UpgradeNode.LIGHTNING_POWER) / 100f) *
            (1f + equipDamage) * levelBonus
        attackInterval = 0.62f / (1f + profile.bonusOf(UpgradeNode.BOLT_SPEED) / 100f)
        moveSpeed = 205f * (1f + profile.bonusOf(UpgradeNode.MOVE_SPEED) / 100f) * (1f + equipSpeed)
        pickupRadius = 110f * (1f + profile.bonusOf(UpgradeNode.PICKUP_RANGE) / 100f)
        armour = profile.bonusOf(UpgradeNode.ARMOUR) / 100f
        chainTargets = profile.levelOf(UpgradeNode.CHAIN_COUNT)
        critChance = 0.05f + profile.bonusOf(UpgradeNode.CRIT_CHANCE) / 100f
        abilityPower = 1f + profile.bonusOf(UpgradeNode.ABILITY_POWER) / 100f
        healthRegen = profile.bonusOf(UpgradeNode.REGENERATION)
        energyRegen = 7.5f + profile.bonusOf(UpgradeNode.ENERGY_REGEN)
        coinMultiplier = 1f + profile.bonusOf(UpgradeNode.COIN_GAIN) / 100f
        fruitDurationMultiplier = 1f + profile.bonusOf(UpgradeNode.FRUIT_DURATION) / 100f
        shieldDuration = 4f + profile.bonusOf(UpgradeNode.SHIELD_STRENGTH)
        ultimateRate = 1f + profile.bonusOf(UpgradeNode.ULTIMATE_CHARGE) / 100f

        hero = Hero(arenaWidth / 2f, arenaHeight / 2f, maxHealth, maxEnergy)

        buildArena()
        hud.totalWaves = zone.waves
        hud.maxHealth = maxHealth
        hud.maxEnergy = maxEnergy
        syncHud()
    }

    // --- Input ------------------------------------------------------------------------------------

    fun setMoveInput(dx: Float, dy: Float) {
        hero.moveX = dx
        hero.moveY = dy
    }

    fun useAbility(ability: Ability) {
        if (phase != GamePhase.FIGHTING || !hero.isAlive) return
        val index = ability.ordinal
        if (abilityTimers[index] > 0f) return

        if (ability == Ability.DIVINE_DISCHARGE) {
            if (hero.ultimateCharge < 1f) return
            hero.ultimateCharge = 0f
            castDivineDischarge()
            return
        }

        if (hero.energy < ability.energyCost) return
        hero.energy -= ability.energyCost
        abilityTimers[index] = ability.cooldown

        when (ability) {
            Ability.CHAIN_LIGHTNING -> castChainLightning()
            Ability.THUNDER_STRIKE -> castThunderStrike()
            Ability.DIVINE_SHIELD -> castDivineShield()
            Ability.DIVINE_DISCHARGE -> Unit
        }
    }

    fun pause() {
        if (phase == GamePhase.FIGHTING || phase == GamePhase.WAVE_CLEARED || phase == GamePhase.INTRO) {
            phase = GamePhase.PAUSED
            AudioEngine.play(GameSound.MENU_OPEN)
        }
    }

    fun resume() {
        if (phase == GamePhase.PAUSED) {
            phase = if (wave == 0) GamePhase.INTRO else GamePhase.FIGHTING
            AudioEngine.play(GameSound.MENU_CLOSE)
        }
    }

    fun chooseBlessing(blessing: Blessing) {
        if (phase != GamePhase.BLESSING) return
        applyBlessing(blessing)
        chosenBlessings = chosenBlessings + blessing
        blessingChoices = emptyList()
        AudioEngine.play(GameSound.ALTAR_ACTIVATE)
        startNextWave()
    }

    // --- Main loop ---------------------------------------------------------------------------------

    fun update(deltaSeconds: Float) {
        frameTick++
        val dt = deltaSeconds.coerceIn(0f, 0.05f)

        when (phase) {
            GamePhase.INTRO -> {
                introTimer -= dt
                hud.banner = "GET READY"
                advanceCosmetics(dt)
                if (introTimer <= 0f) {
                    hud.banner = null
                    startNextWave()
                }
            }

            GamePhase.FIGHTING -> {
                elapsed += dt
                stepSimulation(dt)
                checkWaveProgress(dt)
            }

            GamePhase.WAVE_CLEARED -> {
                stepSimulation(dt)
                waveBreakTimer -= dt
                if (waveBreakTimer <= 0f) {
                    hud.banner = null
                    if (pendingBlessingAfterWave) {
                        pendingBlessingAfterWave = false
                        offerBlessing()
                    } else {
                        startNextWave()
                    }
                }
            }

            GamePhase.BLESSING, GamePhase.PAUSED -> advanceCosmetics(dt)

            GamePhase.VICTORY, GamePhase.DEFEAT -> advanceCosmetics(dt)
        }

        syncHud()
    }

    private fun advanceCosmetics(dt: Float) {
        clouds.forEach { cloud ->
            cloud.x += cloud.speed * dt
            if (cloud.x - cloud.width > arenaWidth + 400f) cloud.x = -cloud.width - 400f
        }
        updateParticles(dt)
        updateArcs(dt)
    }

    private fun stepSimulation(dt: Float) {
        advanceCosmetics(dt)
        updateHero(dt)
        updateEnemies(dt)
        updateProjectiles(dt)
        updateGroundZones(dt)
        updateTraps(dt)
        updatePickups(dt)
        updateFloatingTexts(dt)
        updateFruitTimers(dt)
        updateSpawning(dt)

        for (index in abilityTimers.indices) {
            if (abilityTimers[index] > 0f) abilityTimers[index] = max(0f, abilityTimers[index] - dt)
        }

        if (!hero.isAlive) finishRun(victory = false)
    }

    // --- Hero ---------------------------------------------------------------------------------------

    private fun updateHero(dt: Float) {
        val speedBuff = if (fruitTimers.containsKey(FruitType.SUN_BERRY)) 1.3f else 1f
        val speed = moveSpeed * speedBuff

        val length = hypot(hero.moveX, hero.moveY)
        if (length > 0.01f) {
            val nx = hero.moveX / max(length, 1f)
            val ny = hero.moveY / max(length, 1f)
            hero.x += nx * speed * dt
            hero.y += ny * speed * dt
            hero.walkPhase += dt * 9f
            if (abs(nx) > 0.05f) hero.facing = if (nx < 0f) -1f else 1f
        } else {
            hero.walkPhase += dt * 2.4f
        }

        hero.x = hero.x.coerceIn(HERO_RADIUS, arenaWidth - HERO_RADIUS)
        hero.y = hero.y.coerceIn(HERO_RADIUS, arenaHeight - HERO_RADIUS)

        val magicCharge = crystalCharges[CrystalType.MAGIC.ordinal] * divineAmplifier()
        hero.energy = min(hero.maxEnergy, hero.energy + (energyRegen + magicCharge * 0.4f) * dt)

        val natureCharge = crystalCharges[CrystalType.NATURE.ordinal] * divineAmplifier()
        val regen = healthRegen + natureCharge * 0.35f
        if (regen > 0f && hero.health < hero.maxHealth) {
            hero.health = min(hero.maxHealth, hero.health + regen * dt)
        }

        if (hero.shieldTimer > 0f) hero.shieldTimer -= dt
        if (hero.hurtFlash > 0f) hero.hurtFlash -= dt

        hero.attackTimer -= dt
        if (hero.attackTimer <= 0f) {
            val target = findAttackTarget()
            if (target != null) {
                hero.attackTimer = attackInterval / attackSpeedMultiplier()
                fireBasicBolt(target)
            }
        }
    }

    private fun attackSpeedMultiplier(): Float = if (fruitTimers.containsKey(FruitType.SUN_BERRY)) 1.3f else 1f

    private fun attackPowerMultiplier(): Float {
        var multiplier = 1f
        if (fruitTimers.containsKey(FruitType.SKY_APPLE)) multiplier *= 1.35f
        multiplier *= 1f + crystalCharges[CrystalType.LIGHTNING.ordinal] * 0.04f * divineAmplifier()
        return multiplier
    }

    /** Divine crystals make every other crystal stronger; pomegranate doubles the whole effect. */
    private fun divineAmplifier(): Float {
        val divine = 1f + crystalCharges[CrystalType.DIVINE.ordinal] * 0.05f
        val pomegranate = if (fruitTimers.containsKey(FruitType.CRYSTAL_POMEGRANATE)) 2f else 1f
        return divine * pomegranate
    }

    private fun findAttackTarget(): Enemy? {
        var best: Enemy? = null
        var bestDistance = attackRange * (1f + crystalCharges[CrystalType.LIGHTNING.ordinal] * 0.02f)
        for (enemy in enemies) {
            if (enemy.dead || enemy.visibility < 0.4f) continue
            val distance = hypot(enemy.x - hero.x, enemy.y - hero.y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = enemy
            }
        }
        return best
    }

    private fun fireBasicBolt(target: Enemy) {
        val damage = baseDamage * attackPowerMultiplier()
        val extraChains = chainTargets + crystalCharges[CrystalType.LIGHTNING.ordinal] / 3
        strikeChain(target, damage, extraChains, OlympusColors.Lightning, thickness = 5f)
        AudioEngine.play(GameSound.LIGHTNING_STRIKE, volume = 0.35f, rate = 0.92f + random.nextFloat() * 0.2f)
    }

    /** Applies a bolt to [first] and lets it arc onwards to the closest untouched enemies. */
    private fun strikeChain(
        first: Enemy,
        damage: Float,
        chains: Int,
        color: Color,
        thickness: Float,
        falloff: Float = 0.78f,
    ) {
        var sourceX = hero.x
        var sourceY = hero.y - HERO_HEIGHT * 0.45f
        var current: Enemy? = first
        var currentDamage = damage
        val hit = HashSet<Enemy>()

        var jumps = 0
        while (current != null && jumps <= chains) {
            val enemy = current
            arcs += LightningArc(
                fromX = sourceX,
                fromY = sourceY,
                toX = enemy.x,
                toY = enemy.y - enemy.drawHeight * 0.45f,
                color = color,
                thickness = thickness,
                life = 0.16f,
                seed = random.nextInt(),
            )
            damageEnemy(enemy, currentDamage)
            hit += enemy

            if (crystalCharges[CrystalType.FIRE.ordinal] > 0) {
                explodeAt(
                    enemy.x, enemy.y,
                    radius = 70f + crystalCharges[CrystalType.FIRE.ordinal] * 6f * divineAmplifier(),
                    damage = currentDamage * 0.35f * divineAmplifier(),
                    leaveFire = crystalCharges[CrystalType.FIRE.ordinal] >= 4,
                )
            }

            sourceX = enemy.x
            sourceY = enemy.y - enemy.drawHeight * 0.45f
            currentDamage *= falloff
            jumps++
            current = nearestEnemyExcept(sourceX, sourceY, hit, 260f)
        }
    }

    private fun nearestEnemyExcept(x: Float, y: Float, exclude: Set<Enemy>, range: Float): Enemy? {
        var best: Enemy? = null
        var bestDistance = range
        for (enemy in enemies) {
            if (enemy.dead || enemy in exclude || enemy.visibility < 0.4f) continue
            val distance = hypot(enemy.x - x, enemy.y - y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = enemy
            }
        }
        return best
    }

    // --- Abilities ------------------------------------------------------------------------------------

    private fun castChainLightning() {
        val target = findAttackTarget() ?: nearestEnemyExcept(hero.x, hero.y, emptySet(), 900f) ?: return
        val damage = baseDamage * 2.1f * abilityPower * attackPowerMultiplier() * magicAmplifier()
        strikeChain(target, damage, 4 + chainTargets, OlympusColors.Sky, thickness = 8f, falloff = 0.88f)
        AudioEngine.play(GameSound.CHAIN_LIGHTNING, volume = 0.8f)
        AudioEngine.vibrate(20)
    }

    private fun castThunderStrike() {
        val centre = densestEnemyCluster() ?: return
        val radius = 230f
        val damage = baseDamage * 3.4f * abilityPower * magicAmplifier()

        zones += GroundZone(
            kind = ZoneKind.SHOCKWAVE,
            x = centre.first,
            y = centre.second,
            radius = 20f,
            maxRadius = radius,
            damagePerSecond = 0f,
            hostile = false,
            life = 0.45f,
            expandSpeed = radius / 0.45f,
        )

        for (enemy in enemies) {
            if (enemy.dead) continue
            val distance = hypot(enemy.x - centre.first, enemy.y - centre.second)
            if (distance <= radius) {
                damageEnemy(enemy, damage * (1f - distance / radius * 0.4f))
                enemy.stunTimer = max(enemy.stunTimer, 0.9f)
                arcs += LightningArc(
                    fromX = centre.first,
                    fromY = centre.second - 460f,
                    toX = enemy.x,
                    toY = enemy.y - enemy.drawHeight * 0.4f,
                    color = OlympusColors.Sky,
                    thickness = 7f,
                    life = 0.22f,
                    seed = random.nextInt(),
                )
            }
        }
        spawnBurst(centre.first, centre.second, 26, OlympusColors.Sky)
        AudioEngine.play(GameSound.LIGHTNING_STRIKE, volume = 1f, rate = 0.75f)
        AudioEngine.vibrate(35)
    }

    private fun castDivineShield() {
        hero.shieldTimer = shieldDuration * (1f + crystalCharges[CrystalType.NATURE.ordinal] * 0.04f)
        spawnBurst(hero.x, hero.y - HERO_HEIGHT * 0.4f, 22, OlympusColors.GoldBright)
        for (enemy in enemies) {
            val distance = hypot(enemy.x - hero.x, enemy.y - hero.y)
            if (distance < 220f && distance > 1f) {
                val push = (220f - distance) * 0.9f
                enemy.x += (enemy.x - hero.x) / distance * push
                enemy.y += (enemy.y - hero.y) / distance * push
            }
        }
        AudioEngine.play(GameSound.BARRIER_DESTROY, volume = 0.7f)
    }

    private fun castDivineDischarge() {
        val damage = baseDamage * 5.5f * abilityPower * magicAmplifier()
        for (enemy in enemies.toList()) {
            if (enemy.dead) continue
            arcs += LightningArc(
                fromX = enemy.x + random.nextFloat() * 60f - 30f,
                fromY = enemy.y - 700f,
                toX = enemy.x,
                toY = enemy.y - enemy.drawHeight * 0.4f,
                color = OlympusColors.Divine,
                thickness = 10f,
                life = 0.4f,
                seed = random.nextInt(),
            )
            damageEnemy(enemy, damage)
            enemy.stunTimer = max(enemy.stunTimer, 1.2f)
        }
        zones += GroundZone(
            kind = ZoneKind.HOLY,
            x = hero.x,
            y = hero.y,
            radius = 40f,
            maxRadius = 900f,
            damagePerSecond = 0f,
            hostile = false,
            life = 0.8f,
            expandSpeed = 900f / 0.8f,
        )
        hero.health = min(hero.maxHealth, hero.health + hero.maxHealth * 0.15f)
        spawnBurst(hero.x, hero.y, 60, OlympusColors.Divine)
        floatingTexts += FloatingText(hero.x, hero.y - HERO_HEIGHT, "DIVINE DISCHARGE", OlympusColors.Divine, 1.6f, scale = 1.4f)
        AudioEngine.play(GameSound.ALTAR_ACTIVATE, volume = 1f)
        AudioEngine.vibrate(60)
    }

    private fun magicAmplifier(): Float =
        1f + crystalCharges[CrystalType.MAGIC.ordinal] * 0.05f * divineAmplifier()

    private fun densestEnemyCluster(): Pair<Float, Float>? {
        if (enemies.isEmpty()) return null
        var bestEnemy: Enemy? = null
        var bestScore = -1
        for (candidate in enemies) {
            if (candidate.dead) continue
            if (hypot(candidate.x - hero.x, candidate.y - hero.y) > 620f) continue
            var score = 0
            for (other in enemies) {
                if (other.dead) continue
                if (hypot(other.x - candidate.x, other.y - candidate.y) < 230f) score++
            }
            if (score > bestScore) {
                bestScore = score
                bestEnemy = candidate
            }
        }
        return bestEnemy?.let { it.x to it.y }
    }

    private fun explodeAt(x: Float, y: Float, radius: Float, damage: Float, leaveFire: Boolean) {
        for (enemy in enemies) {
            if (enemy.dead) continue
            if (hypot(enemy.x - x, enemy.y - y) <= radius) damageEnemy(enemy, damage)
        }
        spawnBurst(x, y, 10, OlympusColors.Ember)
        if (leaveFire) {
            zones += GroundZone(
                kind = ZoneKind.FIRE,
                x = x,
                y = y,
                radius = radius * 0.8f,
                maxRadius = radius * 0.8f,
                damagePerSecond = damage * 0.5f,
                hostile = false,
                life = 3.5f,
            )
        }
    }

    // --- Enemies ---------------------------------------------------------------------------------------

    private fun updateEnemies(dt: Float) {
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemy = iterator.next()
            if (enemy.dead) {
                iterator.remove()
                if (enemy === boss) boss = null
                continue
            }

            enemy.spawnFade = min(1f, enemy.spawnFade + dt * 2.5f)
            enemy.bobPhase += dt * 3.2f
            if (enemy.hurtFlash > 0f) enemy.hurtFlash -= dt
            if (enemy.slowTimer > 0f) enemy.slowTimer -= dt
            if (enemy.stunTimer > 0f) {
                enemy.stunTimer -= dt
                continue
            }

            val slowFactor = if (enemy.slowTimer > 0f) 0.55f else 1f
            val toHeroX = hero.x - enemy.x
            val toHeroY = hero.y - enemy.y
            val distance = max(1f, hypot(toHeroX, toHeroY))
            if (abs(toHeroX) > 4f) enemy.facing = if (toHeroX < 0f) -1f else 1f

            when (enemy.type.behaviour) {
                EnemyBehaviour.BRUTE, EnemyBehaviour.RUNNER -> {
                    moveTowards(enemy, toHeroX / distance, toHeroY / distance, enemy.speed * slowFactor, dt)
                    if (enemy.type.behaviour == EnemyBehaviour.RUNNER) {
                        enemy.castCooldown -= dt
                        if (enemy.castCooldown <= 0f) {
                            enemy.castCooldown = 1.6f
                            zones += GroundZone(
                                kind = ZoneKind.ELECTRIC,
                                x = enemy.x,
                                y = enemy.y,
                                radius = 54f,
                                maxRadius = 54f,
                                damagePerSecond = enemy.damage * 0.5f,
                                hostile = true,
                                life = 2.6f,
                            )
                        }
                    }
                    tryMelee(enemy, distance, dt)
                }

                EnemyBehaviour.FLYER -> {
                    val preferred = 250f
                    val direction = if (distance > preferred) 1f else -0.7f
                    moveTowards(
                        enemy,
                        toHeroX / distance * direction,
                        toHeroY / distance * direction,
                        enemy.speed * slowFactor,
                        dt,
                    )
                    enemy.castCooldown -= dt
                    if (enemy.castCooldown <= 0f && distance < 520f) {
                        enemy.castCooldown = 2.2f
                        shootAtHero(enemy, speed = 320f, color = OlympusColors.Sky, leavesFire = false)
                    }
                }

                EnemyBehaviour.CASTER -> {
                    val preferred = 310f
                    val direction = if (distance > preferred) 1f else -0.6f
                    moveTowards(
                        enemy,
                        toHeroX / distance * direction,
                        toHeroY / distance * direction,
                        enemy.speed * slowFactor,
                        dt,
                    )
                    enemy.castCooldown -= dt
                    if (enemy.castCooldown <= 0f && distance < 560f) {
                        enemy.castCooldown = 2.6f
                        val fiery = enemy.type == EnemyType.FIRE_SATYR
                        shootAtHero(
                            enemy,
                            speed = 290f,
                            color = if (fiery) OlympusColors.Ember else OlympusColors.Lightning,
                            leavesFire = fiery,
                        )
                    }
                    tryMelee(enemy, distance, dt)
                }

                EnemyBehaviour.PHANTOM -> {
                    enemy.phaseTimer -= dt
                    if (enemy.phaseTimer <= 0f) {
                        enemy.phaseTimer = 5.5f
                        enemy.visibility = 0.22f
                    }
                    enemy.visibility = if (enemy.phaseTimer > 4f) {
                        0.22f
                    } else {
                        min(1f, enemy.visibility + dt * 1.6f)
                    }
                    val dash = if (enemy.visibility < 0.5f) 1.9f else 1f
                    moveTowards(enemy, toHeroX / distance, toHeroY / distance, enemy.speed * slowFactor * dash, dt)
                    tryMelee(enemy, distance, dt)
                }

                EnemyBehaviour.TITAN -> updateBoss(enemy, toHeroX / distance, toHeroY / distance, distance, dt)
            }
        }
    }

    private fun moveTowards(enemy: Enemy, nx: Float, ny: Float, speed: Float, dt: Float) {
        enemy.x = (enemy.x + nx * speed * dt).coerceIn(enemy.radius, arenaWidth - enemy.radius)
        enemy.y = (enemy.y + ny * speed * dt).coerceIn(enemy.radius, arenaHeight - enemy.radius)
    }

    private fun tryMelee(enemy: Enemy, distance: Float, dt: Float) {
        enemy.attackCooldown -= dt
        if (distance <= enemy.radius + HERO_RADIUS + 12f && enemy.attackCooldown <= 0f) {
            enemy.attackCooldown = 1.15f
            damageHero(enemy.damage)
        }
    }

    private fun shootAtHero(enemy: Enemy, speed: Float, color: Color, leavesFire: Boolean) {
        val angle = atan2(hero.y - enemy.y, hero.x - enemy.x)
        projectiles += Projectile(
            owner = ProjectileOwner.ENEMY,
            x = enemy.x,
            y = enemy.y - enemy.drawHeight * 0.4f,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            damage = enemy.damage,
            radius = 14f,
            color = color,
            trailColor = color.copy(alpha = 0.4f),
            life = 3.2f,
            leavesFire = leavesFire,
        )
    }

    private fun updateBoss(boss: Enemy, nx: Float, ny: Float, distance: Float, dt: Float) {
        boss.phase = when {
            boss.healthFraction > 0.66f -> 1
            boss.healthFraction > 0.33f -> 2
            else -> 3
        }

        if (distance > 200f) {
            moveTowards(boss, nx, ny, boss.speed * (0.8f + boss.phase * 0.15f), dt)
        }
        tryMelee(boss, distance, dt)

        boss.castCooldown -= dt
        if (boss.castCooldown > 0f) return

        boss.castCooldown = when (boss.phase) {
            1 -> 4.2f
            2 -> 3.2f
            else -> 2.4f
        }

        when (random.nextInt(if (boss.phase >= 2) 3 else 2)) {
            0 -> {
                // Expanding shockwave ring.
                zones += GroundZone(
                    kind = ZoneKind.SHOCKWAVE,
                    x = boss.x,
                    y = boss.y,
                    radius = 60f,
                    maxRadius = 620f,
                    damagePerSecond = boss.damage * 2.4f,
                    hostile = true,
                    life = 1.5f,
                    expandSpeed = 560f / 1.5f,
                )
                AudioEngine.play(GameSound.LIGHTNING_STRIKE, volume = 0.9f, rate = 0.7f)
            }

            1 -> {
                // Lightning barrage spread around the hero.
                repeat(3 + boss.phase) { index ->
                    val angle = atan2(hero.y - boss.y, hero.x - boss.x) + (index - 2) * 0.22f
                    projectiles += Projectile(
                        owner = ProjectileOwner.ENEMY,
                        x = boss.x,
                        y = boss.y - boss.drawHeight * 0.45f,
                        vx = cos(angle) * 300f,
                        vy = sin(angle) * 300f,
                        damage = boss.damage * 0.7f,
                        radius = 18f,
                        color = OlympusColors.Lightning,
                        trailColor = OlympusColors.Sky.copy(alpha = 0.35f),
                        life = 3.5f,
                    )
                }
            }

            else -> {
                // Summon reinforcements.
                val minion = zone.enemies.random(random)
                repeat(2) { spawnEnemy(minion, nearBoss = true) }
                AudioEngine.play(GameSound.PORTAL_OPEN, volume = 0.8f)
            }
        }
    }

    // --- Damage ------------------------------------------------------------------------------------------

    private fun damageEnemy(enemy: Enemy, rawDamage: Float) {
        if (enemy.dead) return
        val critical = random.nextFloat() < critChance
        var damage = rawDamage * if (critical) 2f else 1f
        if (enemy.type == EnemyType.CRYSTAL_GOLEM) damage *= 0.75f

        enemy.health -= damage
        enemy.hurtFlash = 0.14f

        floatingTexts += FloatingText(
            x = enemy.x + random.nextFloat() * 30f - 15f,
            y = enemy.y - enemy.drawHeight * 0.8f,
            text = damage.toInt().toString(),
            color = if (critical) OlympusColors.GoldBright else OlympusColors.TextPrimary,
            life = 0.8f,
            scale = if (critical) 1.35f else 1f,
        )

        if (enemy.health <= 0f) killEnemy(enemy)
    }

    private fun killEnemy(enemy: Enemy) {
        enemy.dead = true
        spawnBurst(enemy.x, enemy.y - enemy.drawHeight * 0.35f, 14, OlympusColors.Sky)

        defeated[enemy.type.name] = (defeated[enemy.type.name] ?: 0) + 1
        earnedExperience += (enemy.type.coinReward * 1.4f).toInt()

        val coinGain = (enemy.type.coinReward * coinMultiplier * goldMultiplier()).toInt().coerceAtLeast(1)
        pickups += Pickup(PickupKind.COIN, enemy.x, enemy.y, amount = coinGain)

        if (random.nextFloat() < 0.55f) {
            pickups += Pickup(
                PickupKind.CRYSTAL,
                enemy.x + random.nextFloat() * 40f - 20f,
                enemy.y + random.nextFloat() * 40f - 20f,
                crystal = rollCrystal(),
            )
        }
        if (random.nextFloat() < 0.08f) {
            pickups += Pickup(PickupKind.HEALTH, enemy.x, enemy.y)
        }

        hero.ultimateCharge = min(1f, hero.ultimateCharge + 0.035f * ultimateRate)

        if (enemy === boss) {
            boss = null
            spawnBurst(enemy.x, enemy.y - 120f, 90, OlympusColors.Divine)
            repeat(6) {
                pickups += Pickup(
                    PickupKind.CRYSTAL,
                    enemy.x + random.nextFloat() * 220f - 110f,
                    enemy.y + random.nextFloat() * 160f - 80f,
                    crystal = CrystalType.DIVINE,
                )
            }
        }
    }

    private fun rollCrystal(): CrystalType {
        val roll = random.nextFloat()
        return when {
            roll < 0.32f -> CrystalType.LIGHTNING
            roll < 0.56f -> CrystalType.FIRE
            roll < 0.78f -> CrystalType.NATURE
            roll < 0.95f -> CrystalType.MAGIC
            else -> CrystalType.DIVINE
        }
    }

    private fun goldMultiplier(): Float = if (fruitTimers.containsKey(FruitType.GOLDEN_GRAPES)) 2f else 1f

    private fun damageHero(rawDamage: Float) {
        if (!hero.isAlive) return
        if (hero.isShielded) {
            floatingTexts += FloatingText(hero.x, hero.y - HERO_HEIGHT, "BLOCKED", OlympusColors.Gold, 0.7f)
            return
        }

        val natureReduction = min(0.35f, crystalCharges[CrystalType.NATURE.ordinal] * 0.02f * divineAmplifier())
        val damage = rawDamage * (1f - armour - natureReduction).coerceIn(0.2f, 1f)
        hero.health -= damage
        hero.hurtFlash = 0.25f

        floatingTexts += FloatingText(
            x = hero.x + random.nextFloat() * 24f - 12f,
            y = hero.y - HERO_HEIGHT * 0.9f,
            text = "-${damage.toInt()}",
            color = OlympusColors.Danger,
            life = 0.9f,
        )
        AudioEngine.vibrate(18)
    }

    // --- Projectiles, zones, traps ---------------------------------------------------------------------------

    private fun updateProjectiles(dt: Float) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val projectile = iterator.next()
            projectile.x += projectile.vx * dt
            projectile.y += projectile.vy * dt
            projectile.life -= dt
            projectile.age += dt

            val outOfBounds = projectile.x < -80f || projectile.x > arenaWidth + 80f ||
                projectile.y < -80f || projectile.y > arenaHeight + 80f

            var consumed = false
            if (projectile.owner == ProjectileOwner.ENEMY) {
                if (hypot(projectile.x - hero.x, projectile.y - (hero.y - HERO_HEIGHT * 0.4f)) < projectile.radius + HERO_RADIUS) {
                    damageHero(projectile.damage)
                    consumed = true
                }
            }

            if (consumed || projectile.life <= 0f || outOfBounds) {
                if (consumed || projectile.life <= 0f) {
                    spawnBurst(projectile.x, projectile.y, 6, projectile.color)
                    if (projectile.leavesFire) {
                        zones += GroundZone(
                            kind = ZoneKind.FIRE,
                            x = projectile.x,
                            y = projectile.y,
                            radius = 62f,
                            maxRadius = 62f,
                            damagePerSecond = projectile.damage * 0.55f,
                            hostile = true,
                            life = 4f,
                        )
                    }
                }
                iterator.remove()
            }
        }
    }

    private fun updateGroundZones(dt: Float) {
        val iterator = zones.iterator()
        while (iterator.hasNext()) {
            val zoneEffect = iterator.next()
            zoneEffect.life -= dt
            if (zoneEffect.expandSpeed > 0f) {
                zoneEffect.radius = min(zoneEffect.maxRadius, zoneEffect.radius + zoneEffect.expandSpeed * dt)
            }

            if (zoneEffect.damagePerSecond > 0f) {
                zoneEffect.tickTimer -= dt
                if (zoneEffect.tickTimer <= 0f) {
                    zoneEffect.tickTimer = 0.5f
                    applyZoneDamage(zoneEffect)
                }
            }

            if (zoneEffect.life <= 0f) iterator.remove()
        }
    }

    private fun applyZoneDamage(zoneEffect: GroundZone) {
        val tickDamage = zoneEffect.damagePerSecond * 0.5f
        if (zoneEffect.hostile) {
            val distance = hypot(hero.x - zoneEffect.x, hero.y - zoneEffect.y)
            val inRing = if (zoneEffect.kind == ZoneKind.SHOCKWAVE) {
                abs(distance - zoneEffect.radius) < 60f
            } else {
                distance < zoneEffect.radius
            }
            if (inRing) damageHero(tickDamage)
        } else {
            for (enemy in enemies) {
                if (enemy.dead) continue
                if (hypot(enemy.x - zoneEffect.x, enemy.y - zoneEffect.y) < zoneEffect.radius) {
                    damageEnemy(enemy, tickDamage)
                }
            }
        }
    }

    private fun updateTraps(dt: Float) {
        for (trap in traps) {
            trap.tickTimer -= dt
            trap.life = (trap.life + dt) % TRAP_CYCLE
            val armed = trap.life > TRAP_CYCLE * 0.6f
            if (armed && trap.tickTimer <= 0f) {
                trap.tickTimer = 0.5f
                if (hypot(hero.x - trap.x, hero.y - trap.y) < trap.radius) {
                    damageHero(trap.damagePerSecond * 0.5f)
                }
                for (enemy in enemies) {
                    if (!enemy.dead && hypot(enemy.x - trap.x, enemy.y - trap.y) < trap.radius) {
                        damageEnemy(enemy, trap.damagePerSecond * 0.35f)
                    }
                }
            }
        }
    }

    private fun updatePickups(dt: Float) {
        val radius = pickupRadius * (1f + crystalCharges[CrystalType.MAGIC.ordinal] * 0.02f)
        val iterator = pickups.iterator()
        while (iterator.hasNext()) {
            val pickup = iterator.next()
            pickup.age += dt
            pickup.life -= dt

            val dx = hero.x - pickup.x
            val dy = hero.y - pickup.y
            val distance = hypot(dx, dy)

            if (distance < radius) pickup.attracted = true
            if (pickup.attracted && distance > 1f) {
                val pull = 460f * dt
                pickup.x += dx / distance * pull
                pickup.y += dy / distance * pull
            }

            if (distance < 44f) {
                collect(pickup)
                iterator.remove()
                continue
            }
            if (pickup.life <= 0f) iterator.remove()
        }
    }

    private fun collect(pickup: Pickup) {
        when (pickup.kind) {
            PickupKind.COIN -> {
                earnedCoins += pickup.amount
                floatingTexts += FloatingText(pickup.x, pickup.y, "+${pickup.amount}", OlympusColors.Gold, 0.7f)
            }

            PickupKind.HEALTH -> {
                hero.health = min(hero.maxHealth, hero.health + hero.maxHealth * 0.12f)
                floatingTexts += FloatingText(pickup.x, pickup.y, "+HEALTH", OlympusColors.Success, 0.8f)
            }

            PickupKind.CRYSTAL -> {
                val type = pickup.crystal ?: CrystalType.LIGHTNING
                val index = type.ordinal
                if (crystalCharges[index] < MAX_CRYSTAL_CHARGES) crystalCharges[index]++
                crystalsCollected[type] = (crystalsCollected[type] ?: 0) + 1
                earnedCrystals++
                hero.ultimateCharge = min(1f, hero.ultimateCharge + 0.02f * ultimateRate)
                if (type == CrystalType.NATURE) {
                    hero.health = min(hero.maxHealth, hero.health + hero.maxHealth * 0.02f)
                }
                floatingTexts += FloatingText(pickup.x, pickup.y, type.displayName.substringBefore(' '), type.color, 0.8f)
                AudioEngine.play(GameSound.CRYSTAL_PICKUP, volume = 0.5f)
            }

            PickupKind.FRUIT -> {
                val type = pickup.fruit ?: FruitType.SKY_APPLE
                fruitTimers[type] = type.durationSeconds * fruitDurationMultiplier
                fruitsCollected[type] = (fruitsCollected[type] ?: 0) + 1
                floatingTexts += FloatingText(pickup.x, pickup.y - 20f, type.displayName, OlympusColors.GoldBright, 1.2f, scale = 1.15f)
                AudioEngine.play(GameSound.REWARD, volume = 0.7f)
            }
        }
    }

    private fun updateFruitTimers(dt: Float) {
        if (fruitTimers.isEmpty()) return
        val expired = mutableListOf<FruitType>()
        for (entry in fruitTimers.entries) {
            entry.setValue(entry.value - dt)
            if (entry.value <= 0f) expired += entry.key
        }
        expired.forEach { fruitTimers.remove(it) }
    }

    // --- Waves --------------------------------------------------------------------------------------------

    private fun updateSpawning(dt: Float) {
        if (spawnQueue.isNotEmpty()) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnTimer = 0.42f
                spawnEnemy(spawnQueue.removeAt(spawnQueue.lastIndex))
            }
        }

        fruitSpawnTimer -= dt
        if (fruitSpawnTimer <= 0f) {
            fruitSpawnTimer = 22f + random.nextFloat() * 12f
            pickups += Pickup(
                kind = PickupKind.FRUIT,
                x = 200f + random.nextFloat() * (arenaWidth - 400f),
                y = 200f + random.nextFloat() * (arenaHeight - 400f),
                fruit = FruitType.entries.random(random),
            ).also { it.life = 40f }
        }
    }

    private fun checkWaveProgress(dt: Float) {
        if (enemies.isNotEmpty() || spawnQueue.isNotEmpty()) return

        if (wave >= zone.waves) {
            finishRun(victory = true)
            return
        }

        phase = GamePhase.WAVE_CLEARED
        waveBreakTimer = 2.2f
        hud.banner = "WAVE $wave CLEARED"
        pendingBlessingAfterWave = wave >= nextBlessingWave
        AudioEngine.play(GameSound.REWARD, volume = 0.6f)
    }

    private fun startNextWave() {
        wave++
        if (wave > zone.waves) {
            finishRun(victory = true)
            return
        }

        phase = GamePhase.FIGHTING
        hud.wave = wave
        hud.banner = null

        val isFinalWave = wave == zone.waves
        if (isFinalWave && zone.hasBoss) {
            spawnBoss()
            return
        }

        val count = (4 + wave * 2 + zone.difficulty * 2).toInt()
        spawnQueue = MutableList(count) { zone.enemies.random(random) }
        spawnTimer = 0.3f
    }

    private fun offerBlessing() {
        nextBlessingWave = wave + 3
        val taken = chosenBlessings.toSet()
        val pool = Blessing.entries.filter { it !in taken }.ifEmpty { Blessing.entries }
        blessingChoices = pool.shuffled(random).take(3)
        phase = GamePhase.BLESSING
        AudioEngine.play(GameSound.MENU_OPEN)
    }

    private fun applyBlessing(blessing: Blessing) {
        when (blessing) {
            Blessing.ZEUS -> {
                baseDamage *= 1.3f
                chainTargets += 1
                attackRange *= 1.1f
            }

            Blessing.ATHENA -> {
                armour = min(0.6f, armour + 0.15f)
                hero.maxHealth *= 1.1f
                hero.health = min(hero.maxHealth, hero.health * 1.1f)
                hud.maxHealth = hero.maxHealth
            }

            Blessing.POSEIDON -> {
                enemies.forEach { it.slowTimer = 9999f }
                poseidonWave = true
            }

            Blessing.HADES -> critChance = min(0.75f, critChance + 0.25f)

            Blessing.ARTEMIS -> {
                attackInterval *= 0.8f
                moveSpeed *= 1.12f
            }

            Blessing.APOLLO -> {
                hero.health = min(hero.maxHealth, hero.health + hero.maxHealth * 0.2f)
                healthRegen += 2f
            }

            Blessing.HERMES -> {
                pickupRadius *= 1.8f
                coinMultiplier *= 1.35f
            }

            Blessing.HEPHAESTUS -> {
                if (crystalCharges[CrystalType.FIRE.ordinal] < 4) crystalCharges[CrystalType.FIRE.ordinal] = 4
                abilityPower *= 1.15f
            }
        }
    }

    private var poseidonWave = false

    private fun spawnEnemy(type: EnemyType, nearBoss: Boolean = false) {
        val scale = zone.difficulty * (1f + (wave - 1) * 0.13f)
        val elite = wave >= 4 && random.nextFloat() < 0.12f

        val position = if (nearBoss && boss != null) {
            val angle = random.nextFloat() * TWO_PI
            (boss!!.x + cos(angle) * 180f) to (boss!!.y + sin(angle) * 180f)
        } else {
            spawnPointAwayFromHero()
        }

        val enemy = Enemy(
            type = type,
            x = position.first.coerceIn(60f, arenaWidth - 60f),
            y = position.second.coerceIn(60f, arenaHeight - 60f),
            maxHealth = type.baseHealth * scale * if (elite) 2.2f else 1f,
            damage = type.baseDamage * scale * if (elite) 1.4f else 1f,
            speed = type.speed * if (elite) 0.9f else 1f,
            isElite = elite,
        )
        if (poseidonWave) enemy.slowTimer = 9999f
        enemies += enemy
    }

    private fun spawnBoss() {
        val scale = zone.difficulty * (1f + zone.waves * 0.08f)
        val titan = Enemy(
            type = EnemyType.LIGHTNING_TITAN,
            x = arenaWidth / 2f,
            y = 220f,
            maxHealth = EnemyType.LIGHTNING_TITAN.baseHealth * scale,
            damage = EnemyType.LIGHTNING_TITAN.baseDamage * scale * 0.6f,
            speed = EnemyType.LIGHTNING_TITAN.speed,
        )
        enemies += titan
        boss = titan
        AudioEngine.play(GameSound.PORTAL_OPEN, volume = 1f)
        AudioEngine.vibrate(80)
    }

    private fun spawnPointAwayFromHero(): Pair<Float, Float> {
        repeat(12) {
            val x = 80f + random.nextFloat() * (arenaWidth - 160f)
            val y = 80f + random.nextFloat() * (arenaHeight - 160f)
            if (hypot(x - hero.x, y - hero.y) > 480f) return x to y
        }
        val angle = random.nextFloat() * TWO_PI
        return (hero.x + cos(angle) * 520f) to (hero.y + sin(angle) * 520f)
    }

    // --- Run end -----------------------------------------------------------------------------------------

    private fun finishRun(victory: Boolean) {
        if (phase == GamePhase.VICTORY || phase == GamePhase.DEFEAT) return
        phase = if (victory) GamePhase.VICTORY else GamePhase.DEFEAT
        hud.banner = null

        val gems = when {
            victory && zone.hasBoss -> 3
            victory -> 1
            else -> 0
        }

        result = RunResult(
            victory = victory,
            zone = zone,
            waveReached = wave.coerceAtLeast(1),
            coins = if (victory) (earnedCoins * 1.35f).toInt() else (earnedCoins * 0.6f).toInt(),
            crystals = if (victory) earnedCrystals + 10 else earnedCrystals,
            gems = gems,
            experience = if (victory) (earnedExperience * 1.5f).toInt() else earnedExperience,
            crystalsByType = crystalsCollected.toMap(),
            fruitsByType = fruitsCollected.toMap(),
            enemiesDefeated = defeated.toMap(),
        )

        AudioEngine.play(if (victory) GameSound.LEVEL_COMPLETE else GameSound.LEVEL_FAILED, volume = 1f)
    }

    // --- Cosmetics ----------------------------------------------------------------------------------------

    private fun updateArcs(dt: Float) {
        val iterator = arcs.iterator()
        while (iterator.hasNext()) {
            val arc = iterator.next()
            arc.life -= dt
            if (arc.life <= 0f) iterator.remove()
        }
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.life -= dt
            particle.x += particle.vx * dt
            particle.y += particle.vy * dt
            particle.vy += particle.gravity * dt
            particle.vx *= 0.97f
            particle.vy *= 0.97f
            if (particle.life <= 0f) iterator.remove()
        }
    }

    private fun updateFloatingTexts(dt: Float) {
        val iterator = floatingTexts.iterator()
        while (iterator.hasNext()) {
            val text = iterator.next()
            text.life -= dt
            text.y -= 42f * dt
            if (text.life <= 0f) iterator.remove()
        }
    }

    private fun spawnBurst(x: Float, y: Float, count: Int, color: Color) {
        repeat(count) {
            val angle = random.nextFloat() * TWO_PI
            val speed = 60f + random.nextFloat() * 210f
            particles += Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed * 0.65f,
                life = 0.4f + random.nextFloat() * 0.5f,
                maxLife = 0.9f,
                size = 3f + random.nextFloat() * 5f,
                color = color,
                gravity = 120f,
            )
        }
    }

    // --- Arena generation ----------------------------------------------------------------------------------

    private fun buildArena() {
        val columnSprites = listOf(
            GameSprite.COLUMN_MARBLE,
            GameSprite.COLUMN_SHATTERED,
            GameSprite.COLUMN_GOLDEN,
            GameSprite.COLUMN_CRYSTAL,
        )
        val stoneSprites = listOf(
            GameSprite.STONE_BOULDER,
            GameSprite.STONE_RUNE_BLOCK,
            GameSprite.STONE_SLAB,
            GameSprite.STONE_BALUSTRADE,
        )
        val plantSprites = listOf(
            GameSprite.PLANT_GOLDEN_TREE,
            GameSprite.PLANT_CRYSTAL_BUSH,
            GameSprite.PLANT_WHITE_LILY,
            GameSprite.PLANT_VIOLET_BLOOM,
        )

        // Columns line the edges so the arena reads as a temple courtyard.
        repeat(10) { index ->
            val t = index / 9f
            val near = columnSprites.random(random)
            val far = columnSprites.random(random)
            props += Prop(
                sprite = far,
                x = 120f + t * (arenaWidth - 240f),
                y = 96f,
                height = sceneryHeight(far, COLUMN_SHEET_HEIGHT),
                flip = random.nextBoolean(),
            )
            props += Prop(
                sprite = near,
                x = 120f + t * (arenaWidth - 240f),
                y = arenaHeight - 60f,
                height = sceneryHeight(near, COLUMN_SHEET_HEIGHT),
                flip = random.nextBoolean(),
            )
        }

        repeat(9) {
            val sprite = stoneSprites.random(random)
            props += Prop(
                sprite = sprite,
                x = 220f + random.nextFloat() * (arenaWidth - 440f),
                y = 240f + random.nextFloat() * (arenaHeight - 480f),
                height = sceneryHeight(sprite, STONE_SHEET_HEIGHT),
                flip = random.nextBoolean(),
                alpha = 0.96f,
            )
        }

        repeat(8) {
            val sprite = plantSprites.random(random)
            props += Prop(
                sprite = sprite,
                x = 180f + random.nextFloat() * (arenaWidth - 360f),
                y = 200f + random.nextFloat() * (arenaHeight - 400f),
                height = sceneryHeight(sprite, PLANT_SHEET_HEIGHT),
                flip = random.nextBoolean(),
            )
        }

        val landmark = when (zone) {
            Zone.ZEUS_TEMPLE -> GameSprite.ZEUS_STATUE
            Zone.ATHENA_GARDENS -> GameSprite.ALTAR_UPGRADE
            Zone.SKY_ISLANDS -> GameSprite.OLYMPUS_BRIDGE
            Zone.STORM_PEAKS -> GameSprite.ALTAR_LIGHTNING
            Zone.TITAN_RUINS -> GameSprite.OLYMPUS_RUINS
            Zone.CRYSTAL_SOURCE -> GameSprite.CRYSTAL_SOURCE
            Zone.GOLDEN_PALACE -> GameSprite.OLYMPUS_PORTAL
        }
        props += Prop(landmark, arenaWidth / 2f, 210f, height = 250f, flip = false)

        val trapSprites = listOf(
            GameSprite.TRAP_LIGHTNING,
            GameSprite.TRAP_FIRE,
            GameSprite.TRAP_CRYSTAL_SPIKES,
            GameSprite.TRAP_FALLING_STONES,
        )
        repeat(4 + zone.index / 2) { index ->
            val x = 300f + random.nextFloat() * (arenaWidth - 600f)
            val y = 300f + random.nextFloat() * (arenaHeight - 600f)
            traps += GroundZone(
                kind = ZoneKind.ELECTRIC,
                x = x,
                y = y,
                radius = 74f,
                maxRadius = 74f,
                damagePerSecond = 26f * zone.difficulty,
                hostile = true,
                life = index * 0.8f % TRAP_CYCLE,
            )
            val trapSprite = trapSprites[index % trapSprites.size]
            props += Prop(
                sprite = trapSprite,
                x = x,
                y = y + 24f,
                height = sceneryHeight(trapSprite, TRAP_SHEET_HEIGHT),
                flip = false,
                alpha = 0.9f,
            )
        }

        val cloudSprites = listOf(
            GameSprite.CLOUD_WHITE,
            GameSprite.CLOUD_GOLDEN,
            GameSprite.CLOUD_MYSTIC,
            GameSprite.CLOUD_STORM,
        )
        repeat(5) {
            clouds += DriftingCloud(
                sprite = cloudSprites.random(random),
                x = random.nextFloat() * arenaWidth,
                y = random.nextFloat() * arenaHeight,
                width = 340f + random.nextFloat() * 260f,
                speed = 8f + random.nextFloat() * 16f,
                alpha = 0.16f + random.nextFloat() * 0.12f,
            )
        }

        props.sortBy { it.y }
    }

    /**
     * World height for a piece of scenery, taken from how much of its source sheet the artwork fills.
     * Objects sharing a sheet were drawn to one scale, so this preserves their size relative to each
     * other instead of stretching every silhouette to the same height.
     */
    private fun sceneryHeight(sprite: GameSprite, sheetWorldHeight: Float): Float =
        sheetWorldHeight * GameAssets.heightFraction(sprite)

    // --- HUD mirroring ----------------------------------------------------------------------------------------

    private fun syncHud() {
        hud.health = hero.health.coerceAtLeast(0f)
        hud.maxHealth = hero.maxHealth
        hud.energy = hero.energy
        hud.maxEnergy = hero.maxEnergy
        hud.ultimateCharge = hero.ultimateCharge
        hud.enemiesLeft = enemies.size + spawnQueue.size
        hud.coins = earnedCoins
        hud.crystals = earnedCrystals

        for (index in abilityTimers.indices) {
            val fraction = if (Ability.entries[index].cooldown > 0f) {
                abilityTimers[index] / Ability.entries[index].cooldown
            } else {
                0f
            }
            if (hud.abilityCooldowns[index] != fraction) hud.abilityCooldowns[index] = fraction
        }

        for (index in crystalCharges.indices) {
            if (hud.crystalCharges[index] != crystalCharges[index]) {
                hud.crystalCharges[index] = crystalCharges[index]
            }
        }

        val activeBoss = boss
        hud.bossActive = activeBoss != null
        hud.bossHealth = activeBoss?.healthFraction ?: 0f
        hud.bossPhase = activeBoss?.phase ?: 0

        if (hud.activeFruits.size != fruitTimers.size ||
            hud.activeFruits.any { fruitTimers[it.type] == null }
        ) {
            hud.activeFruits.clear()
            fruitTimers.forEach { (type, remaining) ->
                hud.activeFruits += ActiveFruit(type, remaining, type.durationSeconds * fruitDurationMultiplier)
            }
        } else {
            for (index in hud.activeFruits.indices) {
                val entry = hud.activeFruits[index]
                val remaining = fruitTimers[entry.type] ?: continue
                if (abs(entry.remaining - remaining) > 0.2f) {
                    hud.activeFruits[index] = entry.copy(remaining = remaining)
                }
            }
        }
    }

    fun canAfford(ability: Ability): Boolean = when (ability) {
        Ability.DIVINE_DISCHARGE -> hero.ultimateCharge >= 1f
        else -> hero.energy >= ability.energyCost
    }

    companion object {
        const val HERO_RADIUS = 30f
        const val HERO_HEIGHT = 130f
        const val MAX_CRYSTAL_CHARGES = 10

        // World height a sprite would get if it filled its source sheet from top to bottom.
        private const val COLUMN_SHEET_HEIGHT = 205f
        private const val STONE_SHEET_HEIGHT = 190f
        private const val PLANT_SHEET_HEIGHT = 135f
        private const val TRAP_SHEET_HEIGHT = 190f

        private const val TRAP_CYCLE = 4f
        private const val TWO_PI = 6.2831855f
    }
}
