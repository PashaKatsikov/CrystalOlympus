package com.crystalolympus.crystalolympusgame.game

import androidx.compose.ui.graphics.Color
import com.crystalolympus.crystalolympusgame.engine.GameSprite
import com.crystalolympus.crystalolympusgame.game.model.CrystalType
import com.crystalolympus.crystalolympusgame.game.model.EnemyType
import com.crystalolympus.crystalolympusgame.game.model.FruitType

/** Live stats of the chosen one during a run. */
class Hero(
    var x: Float,
    var y: Float,
    var maxHealth: Float,
    var maxEnergy: Float,
) {
    var health: Float = maxHealth
    var energy: Float = maxEnergy
    var facing: Float = 1f
    var moveX: Float = 0f
    var moveY: Float = 0f
    var attackTimer: Float = 0f
    var shieldTimer: Float = 0f
    var hurtFlash: Float = 0f
    var ultimateCharge: Float = 0f
    var walkPhase: Float = 0f

    val isShielded: Boolean get() = shieldTimer > 0f
    val isAlive: Boolean get() = health > 0f
}

class Enemy(
    val type: EnemyType,
    var x: Float,
    var y: Float,
    var maxHealth: Float,
    val damage: Float,
    val speed: Float,
    val isElite: Boolean = false,
) {
    var health: Float = maxHealth
    var facing: Float = 1f
    var attackCooldown: Float = 0f
    var castCooldown: Float = 1.2f
    var hurtFlash: Float = 0f
    var slowTimer: Float = 0f
    var stunTimer: Float = 0f
    var phaseTimer: Float = 0f
    var visibility: Float = 1f
    var bobPhase: Float = (x + y) * 0.01f
    var spawnFade: Float = 0f
    var dead: Boolean = false

    /** Boss only: 1, 2 or 3. */
    var phase: Int = 1

    val radius: Float get() = type.radius * if (isElite) 1.25f else 1f
    val drawHeight: Float get() = type.drawHeight * if (isElite) 1.25f else 1f
    val healthFraction: Float get() = (health / maxHealth).coerceIn(0f, 1f)
}

enum class ProjectileOwner { HERO, ENEMY }

class Projectile(
    val owner: ProjectileOwner,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val radius: Float,
    val color: Color,
    val trailColor: Color,
    var life: Float,
    val explosive: Boolean = false,
    val leavesFire: Boolean = false,
    val chainsLeft: Int = 0,
    val homingTargetId: Int = -1,
) {
    var dead: Boolean = false
    var age: Float = 0f
}

/** A drawn lightning arc between two points. Purely cosmetic; damage is applied when it is created. */
class LightningArc(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val color: Color,
    val thickness: Float,
    var life: Float,
    val maxLife: Float = life,
    val seed: Int,
)

enum class ZoneKind { FIRE, ELECTRIC, HOLY, SHOCKWAVE }

/** Lingering ground effect: burning patches, live current, the titan's shockwave rings. */
class GroundZone(
    val kind: ZoneKind,
    val x: Float,
    val y: Float,
    var radius: Float,
    val maxRadius: Float,
    val damagePerSecond: Float,
    val hostile: Boolean,
    var life: Float,
    val maxLife: Float = life,
    val expandSpeed: Float = 0f,
) {
    var tickTimer: Float = 0f
}

enum class PickupKind { CRYSTAL, FRUIT, COIN, HEALTH }

class Pickup(
    val kind: PickupKind,
    var x: Float,
    var y: Float,
    val crystal: CrystalType? = null,
    val fruit: FruitType? = null,
    val amount: Int = 1,
) {
    var age: Float = 0f
    var life: Float = 26f
    var vx: Float = 0f
    var vy: Float = 0f
    var attracted: Boolean = false
    var collected: Boolean = false

    val sprite: GameSprite
        get() = when (kind) {
            PickupKind.CRYSTAL -> crystal?.sprite ?: GameSprite.CRYSTAL_LIGHTNING
            PickupKind.FRUIT -> fruit?.sprite ?: GameSprite.FRUIT_SKY_APPLE
            PickupKind.COIN -> GameSprite.REWARD_COIN
            PickupKind.HEALTH -> GameSprite.SPHERE_HEALTH
        }
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val size: Float,
    val color: Color,
    val gravity: Float = 0f,
)

class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Color,
    var life: Float,
    val maxLife: Float = life,
    val scale: Float = 1f,
)

/** Static scenery placed once when the arena is generated. */
class Prop(
    val sprite: GameSprite,
    val x: Float,
    val y: Float,
    val height: Float,
    val flip: Boolean,
    val alpha: Float = 1f,
)

/** Drifting cloud drawn above the arena floor for depth. */
class DriftingCloud(
    val sprite: GameSprite,
    var x: Float,
    var y: Float,
    val width: Float,
    val speed: Float,
    val alpha: Float,
)
