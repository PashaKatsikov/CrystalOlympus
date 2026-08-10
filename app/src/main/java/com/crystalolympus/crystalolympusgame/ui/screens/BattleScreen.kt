package com.crystalolympus.crystalolympusgame.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.core.GameSound
import com.crystalolympus.crystalolympusgame.core.GameSprite
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.game.Ability
import com.crystalolympus.crystalolympusgame.game.GamePhase
import com.crystalolympus.crystalolympusgame.game.GameRenderer
import com.crystalolympus.crystalolympusgame.game.GameSession
import com.crystalolympus.crystalolympusgame.game.renderGame
import com.crystalolympus.crystalolympusgame.game.model.CrystalType
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButtonStyle
import com.crystalolympus.crystalolympusgame.ui.components.OlympusIconButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.SpriteImage
import com.crystalolympus.crystalolympusgame.ui.components.StatBar
import com.crystalolympus.crystalolympusgame.ui.components.clickableNoRipple
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusBrushes
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun BattleScreen(profile: PlayerProfile, viewModel: GameViewModel) {
    val session = viewModel.session ?: return
    val textMeasurer = rememberTextMeasurer()
    val renderer = remember(textMeasurer) { GameRenderer(textMeasurer) }

    LaunchedEffect(session) {
        var previousNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val delta = (nowNanos - previousNanos) / 1_000_000_000f
            previousNanos = nowNanos
            session.update(delta)
        }
    }

    Box(Modifier.fillMaxSize().background(OlympusColors.Night)) {
        Canvas(Modifier.fillMaxSize()) {
            session.frameTick
            renderGame(renderer, session, profile.highQuality)
        }

        BattleHud(session, viewModel)

        if (session.phase == GamePhase.FIGHTING || session.phase == GamePhase.WAVE_CLEARED) {
            // Stops short of the top strip so dragging never starts on top of the HUD.
            MovementJoystick(session, Modifier.fillMaxHeight(0.74f).fillMaxWidth(0.45f).align(Alignment.BottomStart))
            AbilityCluster(session, Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }

        session.hud.banner?.let { banner ->
            Text(
                text = banner,
                color = OlympusColors.GoldBright,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.Center).padding(bottom = 60.dp),
            )
        }

        when (session.phase) {
            GamePhase.PAUSED -> PauseOverlay(session, viewModel)
            GamePhase.BLESSING -> BlessingOverlay(session)
            GamePhase.VICTORY, GamePhase.DEFEAT -> ResultOverlay(session, viewModel)
            else -> Unit
        }
    }
}

// --- HUD -------------------------------------------------------------------------------------------

@Composable
private fun BattleHud(session: GameSession, viewModel: GameViewModel) {
    val hud = session.hud

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            OlympusPanel(modifier = Modifier.width(230.dp), contentPadding = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(OlympusBrushes.PanelRaised)
                            .border(1.5.dp, OlympusColors.Gold, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Cropped to a portrait, so the sprite is pinned by its head rather than centred.
                        SpriteImage(
                            sprite = GameSprite.HERO_ZEUS_CHOSEN,
                            modifier = Modifier.size(38.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        StatBar(
                            fraction = hud.health / hud.maxHealth,
                            brush = OlympusBrushes.HealthBar,
                            modifier = Modifier.fillMaxWidth(),
                            height = 13.dp,
                            label = "${hud.health.toInt()} / ${hud.maxHealth.toInt()}",
                        )
                        Spacer(Modifier.height(4.dp))
                        StatBar(
                            fraction = hud.energy / hud.maxEnergy,
                            brush = OlympusBrushes.EnergyBar,
                            modifier = Modifier.fillMaxWidth(),
                            height = 11.dp,
                            label = "${hud.energy.toInt()} / ${hud.maxEnergy.toInt()}",
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))
            CrystalChargeStrip(session)

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OlympusPanel(contentPadding = 6.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpriteImage(GameSprite.REWARD_COIN, Modifier.size(20.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = hud.coins.toString(),
                            color = OlympusColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(10.dp))
                        SpriteImage(GameSprite.CRYSTAL_LIGHTNING, Modifier.size(20.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = hud.crystals.toString(),
                            color = OlympusColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                OlympusIconButton(onClick = { session.pause() }, size = 38.dp) {
                    Text("II", color = OlympusColors.GoldBright, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            ActiveFruitStrip(session)
            Spacer(Modifier.weight(1f))
            OlympusPanel(modifier = Modifier.width(84.dp), contentPadding = 6.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("WAVE", color = OlympusColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${session.hud.wave}/${session.hud.totalWaves}",
                        color = OlympusColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("ENEMIES", color = OlympusColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = session.hud.enemiesLeft.toString(),
                        color = OlympusColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }

        if (session.hud.bossActive) {
            Spacer(Modifier.height(8.dp))
            BossBar(session)
        }
    }
}

@Composable
private fun BossBar(session: GameSession) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "LIGHTNING TITAN  \u2022  PHASE ${session.hud.bossPhase}",
            color = OlympusColors.Danger,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(3.dp))
        StatBar(
            fraction = session.hud.bossHealth,
            brush = OlympusBrushes.HealthBar,
            modifier = Modifier.fillMaxWidth(),
            height = 14.dp,
        )
    }
}

@Composable
private fun CrystalChargeStrip(session: GameSession) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CrystalType.entries.forEachIndexed { index, type ->
            val charges = session.hud.crystalCharges[index]
            if (charges <= 0) return@forEachIndexed
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB3060D22))
                    .border(1.dp, type.color.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(start = 2.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpriteImage(type.sprite, Modifier.size(18.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text = charges.toString(),
                    color = type.color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun ActiveFruitStrip(session: GameSession) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        session.hud.activeFruits.forEach { active ->
            OlympusPanel(contentPadding = 5.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteImage(active.type.sprite, Modifier.size(20.dp))
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Text(
                            text = active.type.description,
                            color = OlympusColors.TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        StatBar(
                            fraction = active.remaining / active.total,
                            brush = OlympusBrushes.GoldButton,
                            modifier = Modifier.width(72.dp),
                            height = 4.dp,
                        )
                    }
                }
            }
        }
    }
}

// --- Controls ---------------------------------------------------------------------------------------

@Composable
private fun MovementJoystick(session: GameSession, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val maxRadiusPx = with(density) { 62.dp.toPx() }

    var basePosition by remember { mutableStateOf(Offset.Unspecified) }
    var knobPosition by remember { mutableStateOf(Offset.Unspecified) }

    Box(
        modifier = modifier.pointerInput(session) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent().changes.firstOrNull { it.pressed && it.previousPressed.not() }
                        ?: continue
                    basePosition = down.position
                    knobPosition = down.position
                    down.consume()

                    var pointer = down
                    while (pointer.pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pointer = change
                        knobPosition = change.position
                        change.consume()

                        val delta = knobPosition - basePosition
                        val distance = hypot(delta.x, delta.y)
                        val clamped = min(distance, maxRadiusPx)
                        if (distance > 6f) {
                            session.setMoveInput(delta.x / distance * (clamped / maxRadiusPx), delta.y / distance * (clamped / maxRadiusPx))
                        } else {
                            session.setMoveInput(0f, 0f)
                        }
                    }

                    session.setMoveInput(0f, 0f)
                    basePosition = Offset.Unspecified
                    knobPosition = Offset.Unspecified
                }
            }
        },
    ) {
        if (basePosition != Offset.Unspecified) {
            Canvas(Modifier.fillMaxSize()) {
                val delta = knobPosition - basePosition
                val distance = hypot(delta.x, delta.y)
                val knob = if (distance > maxRadiusPx) {
                    basePosition + delta * (maxRadiusPx / distance)
                } else {
                    knobPosition
                }

                drawCircle(
                    color = OlympusColors.Sky.copy(alpha = 0.12f),
                    radius = maxRadiusPx,
                    center = basePosition,
                )
                drawCircle(
                    color = OlympusColors.Sky.copy(alpha = 0.5f),
                    radius = maxRadiusPx,
                    center = basePosition,
                    style = Stroke(width = 3f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.85f), OlympusColors.Lightning.copy(alpha = 0.6f)),
                        center = knob,
                        radius = maxRadiusPx * 0.42f,
                    ),
                    radius = maxRadiusPx * 0.42f,
                    center = knob,
                )
            }
        }
    }
}

@Composable
private fun AbilityCluster(session: GameSession, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AbilityButton(session, Ability.DIVINE_SHIELD, 54.dp)
            AbilityButton(session, Ability.THUNDER_STRIKE, 54.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AbilityButton(session, Ability.CHAIN_LIGHTNING, 62.dp)
            AbilityButton(session, Ability.DIVINE_DISCHARGE, 76.dp)
        }
    }
}

@Composable
private fun AbilityButton(session: GameSession, ability: Ability, size: androidx.compose.ui.unit.Dp) {
    val interactionSource = remember { MutableInteractionSource() }
    val cooldown = session.hud.abilityCooldowns[ability.ordinal]
    val ultimate = ability == Ability.DIVINE_DISCHARGE
    val ready = if (ultimate) session.hud.ultimateCharge >= 1f else cooldown <= 0f && session.canAfford(ability)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ability.tint.copy(alpha = if (ready) 0.55f else 0.18f),
                        Color(0xE6060D22),
                    ),
                ),
            )
            .border(2.dp, if (ready) ability.tint else OlympusColors.PanelBorder, CircleShape)
            .clickableNoRipple(interactionSource) {
                AudioEngine.play(GameSound.BUTTON_CLICK, volume = 0.5f)
                session.useAbility(ability)
            },
        contentAlignment = Alignment.Center,
    ) {
        SpriteImage(
            ability.sprite,
            Modifier.size(size * 0.62f).scale(if (ready) 1f else 0.92f),
            alpha = if (ready) 1f else 0.4f,
        )

        if (ultimate) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5f
                drawArc(
                    color = OlympusColors.Divine,
                    startAngle = -90f,
                    sweepAngle = 360f * session.hud.ultimateCharge,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(width = stroke),
                )
            }
        } else if (cooldown > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(
                    color = Color.Black.copy(alpha = 0.55f),
                    startAngle = -90f,
                    sweepAngle = 360f * cooldown,
                    useCenter = true,
                )
            }
        }

        if (!ultimate) {
            Text(
                text = ability.energyCost.toInt().toString(),
                color = OlympusColors.Sky,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
    }
}

// --- Overlays ------------------------------------------------------------------------------------------

@Composable
private fun PauseOverlay(session: GameSession, viewModel: GameViewModel) {
    Box(
        Modifier.fillMaxSize().background(OlympusColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        OlympusPanel(modifier = Modifier.width(260.dp), borderColor = OlympusColors.Gold, contentPadding = 18.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "PAUSED",
                    color = OlympusColors.GoldBright,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(16.dp))
                OlympusButton("RESUME", { session.resume() }, Modifier.fillMaxWidth(), OlympusButtonStyle.Blue)
                Spacer(Modifier.height(8.dp))
                OlympusButton("RESTART", { viewModel.restartRun() }, Modifier.fillMaxWidth(), OlympusButtonStyle.Blue)
                Spacer(Modifier.height(8.dp))
                OlympusButton("SETTINGS", { viewModel.openSettings() }, Modifier.fillMaxWidth(), OlympusButtonStyle.Blue)
                Spacer(Modifier.height(8.dp))
                OlympusButton("QUIT RUN", { viewModel.backToMenu() }, Modifier.fillMaxWidth(), OlympusButtonStyle.Danger)
            }
        }
    }
}

@Composable
private fun BlessingOverlay(session: GameSession) {
    Box(
        Modifier.fillMaxSize().background(OlympusColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "CHOOSE A BLESSING",
                color = OlympusColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                session.blessingChoices.forEach { blessing ->
                    val interactionSource = remember(blessing) { MutableInteractionSource() }
                    OlympusPanel(
                        modifier = Modifier
                            .width(168.dp)
                            .height(220.dp)
                            .clickableNoRipple(interactionSource) { session.chooseBlessing(blessing) },
                        borderColor = blessing.tint,
                        contentPadding = 12.dp,
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(blessing.tint.copy(alpha = 0.5f), Color.Transparent),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                SpriteImage(blessing.sprite, Modifier.size(56.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = blessing.displayName.uppercase(),
                                color = OlympusColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            blessing.lines.forEach { line ->
                                Text(
                                    text = line,
                                    color = OlympusColors.TextSecondary,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Choose one blessing", color = OlympusColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ResultOverlay(session: GameSession, viewModel: GameViewModel) {
    val result = session.result ?: return
    val victory = result.victory

    Box(
        Modifier.fillMaxSize().background(OlympusColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        OlympusPanel(
            modifier = Modifier.width(340.dp),
            borderColor = if (victory) OlympusColors.Gold else OlympusColors.Danger,
            contentPadding = 18.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (victory) "VICTORY!" else "DEFEAT",
                    color = if (victory) OlympusColors.GoldBright else OlympusColors.Danger,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (victory) {
                        "${result.zone.displayName} has been restored"
                    } else {
                        "You fell on wave ${result.waveReached}"
                    },
                    color = OlympusColors.TextSecondary,
                    fontSize = 11.sp,
                )

                Spacer(Modifier.height(14.dp))
                SectionTitle("REWARDS")
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RewardChip(GameSprite.REWARD_COIN, result.coins)
                    RewardChip(GameSprite.CRYSTAL_LIGHTNING, result.crystals)
                    if (result.gems > 0) RewardChip(GameSprite.REWARD_GEM, result.gems)
                    RewardChip(GameSprite.ARTIFACT_ZEUS_SEAL, result.experience)
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!victory) {
                        OlympusButton(
                            text = "TRY AGAIN",
                            onClick = { viewModel.restartRun() },
                            style = OlympusButtonStyle.Danger,
                        )
                    }
                    OlympusButton(
                        text = if (victory) "CLAIM REWARDS" else "COLLECT & LEAVE",
                        onClick = { viewModel.claimRun(result) },
                        style = if (victory) OlympusButtonStyle.Gold else OlympusButtonStyle.Blue,
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardChip(sprite: GameSprite, amount: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OlympusBrushes.PanelRaised)
                .border(1.dp, OlympusColors.PanelBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SpriteImage(sprite, Modifier.size(34.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatAmount(amount),
            color = OlympusColors.GoldBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
