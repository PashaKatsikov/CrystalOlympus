package com.crystalolympus.crystalolympusgame.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.engine.GameAssets
import com.crystalolympus.crystalolympusgame.engine.GameSprite
import com.crystalolympus.crystalolympusgame.game.model.EnemyBehaviour
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws a [GameSession] onto a Compose canvas.
 *
 * The arena is simulated in world units and projected with a fixed 3/4 top-down camera that follows
 * the hero. Everything standing on the ground is drawn as an upright billboard anchored at its feet
 * and sorted by depth, which keeps the isometric artwork readable without a real 3D pipeline.
 */
class GameRenderer(private val textMeasurer: TextMeasurer) {

    private var cameraX = 0f
    private var cameraY = 0f
    private var scale = 1f

    private val depthBuffer = ArrayList<Drawable>(256)

    private sealed interface Drawable {
        val depth: Float

        class PropItem(val prop: Prop) : Drawable {
            override val depth get() = prop.y
        }

        class EnemyItem(val enemy: Enemy) : Drawable {
            override val depth get() = enemy.y
        }

        class HeroItem(val hero: Hero) : Drawable {
            override val depth get() = hero.y
        }
    }

    fun DrawScope.render(session: GameSession, highQuality: Boolean) {
        scale = size.height / VIEW_HEIGHT_WORLD
        val viewWidth = size.width / scale
        val viewHeight = VIEW_HEIGHT_WORLD

        cameraX = if (viewWidth >= session.arenaWidth) {
            (session.arenaWidth - viewWidth) / 2f
        } else {
            (session.hero.x - viewWidth / 2f).coerceIn(0f, session.arenaWidth - viewWidth)
        }
        cameraY = if (viewHeight >= session.arenaHeight) {
            (session.arenaHeight - viewHeight) / 2f
        } else {
            (session.hero.y - viewHeight / 2f).coerceIn(0f, session.arenaHeight - viewHeight)
        }

        drawGround(session)
        drawGroundZones(session)
        drawPickupHalos(session)
        drawDepthSortedEntities(session, highQuality)
        drawPickups(session)
        drawProjectiles(session)
        drawArcs(session)
        if (highQuality) drawParticles(session)
        drawClouds(session)
        drawFloatingTexts(session)
        drawArenaEdge(session)
    }

    // --- Projection --------------------------------------------------------------------------------

    private fun sx(worldX: Float) = (worldX - cameraX) * scale
    private fun sy(worldY: Float) = (worldY - cameraY) * scale

    // --- Layers ------------------------------------------------------------------------------------

    private fun DrawScope.drawGround(session: GameSession) {
        val ground = GameAssets[session.groundSprite]
        val left = sx(0f)
        val top = sy(0f)
        val width = session.arenaWidth * scale
        val height = session.arenaHeight * scale

        drawRect(color = Color(0xFF0A1024))

        if (ground != null) {
            // The background is an illustration, not a seamless texture. Draw one centred copy and
            // crop its source to the arena ratio, preserving proportions without visible seams.
            val destinationRatio = width / height
            val sourceRatio = ground.width.toFloat() / ground.height
            val sourceWidth: Int
            val sourceHeight: Int
            val sourceLeft: Int
            val sourceTop: Int

            if (sourceRatio > destinationRatio) {
                sourceHeight = ground.height
                sourceWidth = (sourceHeight * destinationRatio).roundToInt().coerceAtMost(ground.width)
                sourceLeft = (ground.width - sourceWidth) / 2
                sourceTop = 0
            } else {
                sourceWidth = ground.width
                sourceHeight = (sourceWidth / destinationRatio).roundToInt().coerceAtMost(ground.height)
                sourceLeft = 0
                sourceTop = (ground.height - sourceHeight) / 2
            }

            drawImage(
                image = ground,
                srcOffset = IntOffset(sourceLeft, sourceTop),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }

        // Vignette keeps attention on the middle of the arena.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0x66000B1E), Color(0xB3000714)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * 0.72f,
            ),
        )
    }

    private fun DrawScope.drawArenaEdge(session: GameSession) {
        val rect = Rect(sx(0f), sy(0f), sx(session.arenaWidth), sy(session.arenaHeight))
        drawRect(
            color = OlympusColors.Sky.copy(alpha = 0.18f),
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height),
            style = Stroke(width = 3f),
        )
    }

    private fun DrawScope.drawGroundZones(session: GameSession) {
        for (trap in session.traps) {
            val armed = trap.life > TRAP_ARMED_FROM
            val pulse = if (armed) 0.55f else 0.18f
            drawCircle(
                color = OlympusColors.Lightning.copy(alpha = pulse * 0.5f),
                radius = trap.radius * scale,
                center = Offset(sx(trap.x), sy(trap.y)),
            )
            drawCircle(
                color = OlympusColors.Sky.copy(alpha = pulse),
                radius = trap.radius * scale,
                center = Offset(sx(trap.x), sy(trap.y)),
                style = Stroke(width = 2.5f),
            )
        }

        for (zone in session.zones) {
            val centre = Offset(sx(zone.x), sy(zone.y))
            val radius = zone.radius * scale
            val fade = (zone.life / zone.maxLife).coerceIn(0f, 1f)

            when (zone.kind) {
                ZoneKind.FIRE -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                OlympusColors.Divine.copy(alpha = 0.55f * fade),
                                OlympusColors.Ember.copy(alpha = 0.35f * fade),
                                Color.Transparent,
                            ),
                            center = centre,
                            radius = radius,
                        ),
                        radius = radius,
                        center = centre,
                    )
                }

                ZoneKind.ELECTRIC -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                OlympusColors.Sky.copy(alpha = 0.45f * fade),
                                OlympusColors.LightningDeep.copy(alpha = 0.28f * fade),
                                Color.Transparent,
                            ),
                            center = centre,
                            radius = radius,
                        ),
                        radius = radius,
                        center = centre,
                    )
                }

                ZoneKind.SHOCKWAVE -> {
                    drawCircle(
                        color = OlympusColors.Sky.copy(alpha = 0.75f * fade),
                        radius = radius,
                        center = centre,
                        style = Stroke(width = 8f * fade + 2f),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f * fade),
                        radius = radius * 0.92f,
                        center = centre,
                        style = Stroke(width = 3f),
                    )
                }

                ZoneKind.HOLY -> {
                    drawCircle(
                        color = OlympusColors.Divine.copy(alpha = 0.6f * fade),
                        radius = radius,
                        center = centre,
                        style = Stroke(width = 10f * fade + 2f),
                    )
                }
            }
        }
    }

    private fun DrawScope.drawPickupHalos(session: GameSession) {
        for (pickup in session.pickups) {
            val colour = when (pickup.kind) {
                PickupKind.CRYSTAL -> pickup.crystal?.color ?: OlympusColors.Lightning
                PickupKind.FRUIT -> OlympusColors.GoldBright
                PickupKind.COIN -> OlympusColors.Gold
                PickupKind.HEALTH -> OlympusColors.Success
            }
            val centre = Offset(sx(pickup.x), sy(pickup.y))
            val radius = 30f * scale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colour.copy(alpha = 0.5f), Color.Transparent),
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }
    }

    private fun DrawScope.drawDepthSortedEntities(session: GameSession, highQuality: Boolean) {
        depthBuffer.clear()
        session.props.forEach { depthBuffer += Drawable.PropItem(it) }
        session.enemies.forEach { if (!it.dead) depthBuffer += Drawable.EnemyItem(it) }
        depthBuffer += Drawable.HeroItem(session.hero)
        depthBuffer.sortBy { it.depth }

        for (item in depthBuffer) {
            when (item) {
                is Drawable.PropItem -> drawProp(item.prop)
                is Drawable.EnemyItem -> drawEnemy(item.enemy, highQuality)
                is Drawable.HeroItem -> drawHero(session, item.hero)
            }
        }
    }

    private fun DrawScope.drawProp(prop: Prop) {
        val image = GameAssets[prop.sprite] ?: return
        drawShadow(prop.x, prop.y, prop.height * 0.34f, 0.25f)
        drawBillboard(image, prop.x, prop.y, prop.height, prop.flip, prop.alpha)
    }

    private fun DrawScope.drawEnemy(enemy: Enemy, highQuality: Boolean) {
        val image = GameAssets[enemy.type.sprite] ?: return
        val alpha = enemy.visibility * enemy.spawnFade

        val bob = if (enemy.type.behaviour == EnemyBehaviour.FLYER) sin(enemy.bobPhase) * 12f else 0f

        drawShadow(enemy.x, enemy.y, enemy.radius, 0.32f * alpha)

        if (enemy.isElite && highQuality) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OlympusColors.Magic.copy(alpha = 0.4f * alpha), Color.Transparent),
                    center = Offset(sx(enemy.x), sy(enemy.y)),
                    radius = enemy.radius * 2.2f * scale,
                ),
                radius = enemy.radius * 2.2f * scale,
                center = Offset(sx(enemy.x), sy(enemy.y)),
            )
        }

        drawBillboard(image, enemy.x, enemy.y + bob, enemy.drawHeight, enemy.facing < 0f, alpha)

        if (enemy.hurtFlash > 0f) {
            drawBillboard(
                image = image,
                worldX = enemy.x,
                worldFootY = enemy.y + bob,
                height = enemy.drawHeight,
                flip = enemy.facing < 0f,
                alpha = (enemy.hurtFlash / 0.14f).coerceIn(0f, 1f) * 0.8f,
                colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
            )
        }

        if (enemy.type.behaviour != EnemyBehaviour.TITAN) {
            drawEnemyHealthBar(enemy, alpha)
        }
    }

    private fun DrawScope.drawEnemyHealthBar(enemy: Enemy, alpha: Float) {
        if (enemy.healthFraction >= 0.999f) return
        val width = enemy.radius * 2.1f * scale
        val height = 5f
        val left = sx(enemy.x) - width / 2f
        val top = sy(enemy.y) - (enemy.drawHeight + 16f) * scale

        drawRect(
            color = Color(0xCC060B1C).copy(alpha = 0.8f * alpha),
            topLeft = Offset(left, top),
            size = Size(width, height),
        )
        drawRect(
            color = (if (enemy.isElite) OlympusColors.Magic else OlympusColors.Danger).copy(alpha = alpha),
            topLeft = Offset(left, top),
            size = Size(width * enemy.healthFraction, height),
        )
    }

    private fun DrawScope.drawHero(session: GameSession, hero: Hero) {
        val image = GameAssets[GameSprite.HERO_ZEUS_CHOSEN] ?: return
        val bob = sin(hero.walkPhase) * 3.5f

        drawShadow(hero.x, hero.y, GameSession.HERO_RADIUS, 0.34f)

        // Standing aura so the hero never gets lost in a crowd.
        val centre = Offset(sx(hero.x), sy(hero.y))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(OlympusColors.Lightning.copy(alpha = 0.35f), Color.Transparent),
                center = centre,
                radius = 70f * scale,
            ),
            radius = 70f * scale,
            center = centre,
        )

        drawBillboard(image, hero.x, hero.y + bob, GameSession.HERO_HEIGHT, hero.facing < 0f, 1f)

        if (hero.hurtFlash > 0f) {
            drawBillboard(
                image = image,
                worldX = hero.x,
                worldFootY = hero.y + bob,
                height = GameSession.HERO_HEIGHT,
                flip = hero.facing < 0f,
                alpha = (hero.hurtFlash / 0.25f).coerceIn(0f, 1f) * 0.7f,
                colorFilter = ColorFilter.tint(OlympusColors.Danger, BlendMode.SrcIn),
            )
        }

        if (hero.isShielded) {
            val shield = GameAssets[GameSprite.SHIELD_DIVINE]
            if (shield != null) {
                val pulse = 0.6f + 0.25f * sin(session.frameTick * 0.25f)
                drawBillboard(
                    image = shield,
                    worldX = hero.x,
                    worldFootY = hero.y + 26f,
                    height = GameSession.HERO_HEIGHT * 1.5f,
                    flip = false,
                    alpha = pulse,
                )
            }
        }
    }

    private fun DrawScope.drawPickups(session: GameSession) {
        for (pickup in session.pickups) {
            val image = GameAssets[pickup.sprite] ?: continue
            val bob = sin(pickup.age * 3.4f) * 6f
            val fade = if (pickup.life < 3f) (pickup.life / 3f).coerceIn(0f, 1f) else 1f
            val height = when (pickup.kind) {
                PickupKind.COIN -> 34f
                PickupKind.CRYSTAL -> 46f
                PickupKind.HEALTH -> 44f
                PickupKind.FRUIT -> 56f
            }
            drawBillboard(image, pickup.x, pickup.y + bob, height, flip = false, alpha = fade)
        }
    }

    private fun DrawScope.drawProjectiles(session: GameSession) {
        for (projectile in session.projectiles) {
            val centre = Offset(sx(projectile.x), sy(projectile.y))
            val radius = projectile.radius * scale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, projectile.color, Color.Transparent),
                    center = centre,
                    radius = radius * 2.1f,
                ),
                radius = radius * 2.1f,
                center = centre,
            )
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = radius * 0.5f, center = centre)
        }
    }

    private fun DrawScope.drawArcs(session: GameSession) {
        for (arc in session.arcs) {
            val fade = (arc.life / arc.maxLife).coerceIn(0f, 1f)
            val path = buildLightningPath(
                startX = sx(arc.fromX),
                startY = sy(arc.fromY),
                endX = sx(arc.toX),
                endY = sy(arc.toY),
                seed = arc.seed,
            )

            drawPath(
                path = path,
                color = arc.color.copy(alpha = 0.28f * fade),
                style = Stroke(width = arc.thickness * 3.4f),
            )
            drawPath(
                path = path,
                color = arc.color.copy(alpha = 0.85f * fade),
                style = Stroke(width = arc.thickness * 1.6f),
            )
            drawPath(
                path = path,
                color = Color.White.copy(alpha = fade),
                style = Stroke(width = arc.thickness * 0.55f),
            )
        }
    }

    private fun buildLightningPath(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        seed: Int,
    ): Path {
        val random = Random(seed)
        val path = Path()
        val length = hypot(endX - startX, endY - startY)
        val segments = (length / 26f).toInt().coerceIn(3, 14)
        val spread = (length * 0.09f).coerceAtMost(28f)

        path.moveTo(startX, startY)
        for (index in 1 until segments) {
            val t = index.toFloat() / segments
            val baseX = startX + (endX - startX) * t
            val baseY = startY + (endY - startY) * t
            val jitter = (random.nextFloat() - 0.5f) * spread * 2f
            val normalX = -(endY - startY) / length
            val normalY = (endX - startX) / length
            path.lineTo(baseX + normalX * jitter, baseY + normalY * jitter)
        }
        path.lineTo(endX, endY)
        return path
    }

    private fun DrawScope.drawParticles(session: GameSession) {
        for (particle in session.particles) {
            val alpha = (particle.life / particle.maxLife).coerceIn(0f, 1f)
            drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = particle.size * scale * alpha,
                center = Offset(sx(particle.x), sy(particle.y)),
            )
        }
    }

    private fun DrawScope.drawClouds(session: GameSession) {
        for (cloud in session.clouds) {
            val image = GameAssets[cloud.sprite] ?: continue
            val width = cloud.width * scale
            val height = width * image.height / image.width
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset((sx(cloud.x) - width / 2f).roundToInt(), (sy(cloud.y) - height / 2f).roundToInt()),
                dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                alpha = cloud.alpha,
                filterQuality = FilterQuality.Low,
            )
        }
    }

    private fun DrawScope.drawFloatingTexts(session: GameSession) {
        for (text in session.floatingTexts) {
            val alpha = (text.life / text.maxLife).coerceIn(0f, 1f)
            val layout = textMeasurer.measure(
                text = text.text,
                style = TextStyle(
                    color = text.color.copy(alpha = alpha),
                    fontSize = (13f * text.scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(sx(text.x) - layout.size.width / 2f, sy(text.y) - layout.size.height),
            )
        }
    }

    // --- Primitives ---------------------------------------------------------------------------------

    private fun DrawScope.drawShadow(worldX: Float, worldFootY: Float, radius: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val width = radius * 2.2f * scale
        val height = width * 0.42f
        drawOval(
            color = Color.Black.copy(alpha = alpha),
            topLeft = Offset(sx(worldX) - width / 2f, sy(worldFootY) - height / 2f),
            size = Size(width, height),
        )
    }

    private fun DrawScope.drawBillboard(
        image: ImageBitmap,
        worldX: Float,
        worldFootY: Float,
        height: Float,
        flip: Boolean,
        alpha: Float,
        colorFilter: ColorFilter? = null,
    ) {
        if (alpha <= 0.01f) return
        val drawHeight = height * scale
        val drawWidth = drawHeight * image.width / image.height
        val left = sx(worldX) - drawWidth / 2f
        val top = sy(worldFootY) - drawHeight

        // Skip anything outside the viewport; crowded waves push a lot of sprites through here.
        if (left > size.width || left + drawWidth < 0f || top > size.height || top + drawHeight < 0f) return

        val paint: DrawScope.() -> Unit = {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(drawWidth.roundToInt().coerceAtLeast(1), drawHeight.roundToInt().coerceAtLeast(1)),
                alpha = alpha.coerceIn(0f, 1f),
                colorFilter = colorFilter,
                filterQuality = FilterQuality.Medium,
            )
        }

        if (flip) {
            withTransform({ scale(-1f, 1f, Offset(sx(worldX), sy(worldFootY))) }) { paint() }
        } else {
            paint()
        }
    }

    companion object {
        const val VIEW_HEIGHT_WORLD = 640f
        private const val TRAP_ARMED_FROM = 2.4f
    }
}

/** Convenience so callers do not need to import the renderer's receiver scope explicitly. */
fun DrawScope.renderGame(renderer: GameRenderer, session: GameSession, highQuality: Boolean) {
    with(renderer) { render(session, highQuality) }
}
