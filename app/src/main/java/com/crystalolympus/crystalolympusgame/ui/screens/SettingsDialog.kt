package com.crystalolympus.crystalolympusgame.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalolympus.crystalolympusgame.BuildConfig
import com.crystalolympus.crystalolympusgame.WebViewActivity
import com.crystalolympus.crystalolympusgame.core.AudioEngine
import com.crystalolympus.crystalolympusgame.core.GameSound
import com.crystalolympus.crystalolympusgame.data.PlayerProfile
import com.crystalolympus.crystalolympusgame.ui.GameViewModel
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButtonStyle
import com.crystalolympus.crystalolympusgame.ui.components.OlympusPanel
import com.crystalolympus.crystalolympusgame.ui.components.clickableNoRipple
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

@Composable
fun SettingsDialog(profile: PlayerProfile, viewModel: GameViewModel) {
    val context = LocalContext.current
    var confirmingReset by remember { mutableStateOf(false) }
    val dismissSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OlympusColors.Scrim)
            .clickableNoRipple(dismissSource) { viewModel.closeSettings() },
        contentAlignment = Alignment.Center,
    ) {
        val panelSource = remember { MutableInteractionSource() }
        OlympusPanel(
            modifier = Modifier
                .width(380.dp)
                .clickableNoRipple(panelSource) { },
            borderColor = OlympusColors.Gold,
            contentPadding = 16.dp,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SETTINGS",
                        color = OlympusColors.GoldBright,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "\u2715",
                        color = OlympusColors.TextSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier.clickableNoRipple(remember { MutableInteractionSource() }) {
                            viewModel.closeSettings()
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))

                SettingToggle("SOUND EFFECTS", profile.soundEnabled) { viewModel.setSoundEnabled(it) }
                SettingToggle("VIBRATION", profile.vibrationEnabled) { viewModel.setVibrationEnabled(it) }
                SettingToggle("HIGH QUALITY EFFECTS", profile.highQuality) { viewModel.setHighQuality(it) }

                Spacer(Modifier.height(6.dp))
                SettingRow("LANGUAGE") {
                    Text("ENGLISH", color = OlympusColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OlympusButton(
                        text = "PRIVACY POLICY",
                        onClick = {
                            AudioEngine.play(GameSound.BUTTON_CLICK)
                            WebViewActivity.open(context, "Privacy Policy", WebViewActivity.PRIVACY_POLICY_URL)
                        },
                        style = OlympusButtonStyle.Blue,
                        modifier = Modifier.weight(1f),
                    )
                    OlympusButton(
                        text = "SUPPORT",
                        onClick = {
                            AudioEngine.play(GameSound.BUTTON_CLICK)
                            WebViewActivity.open(context, "Support", WebViewActivity.SUPPORT_URL)
                        },
                        style = OlympusButtonStyle.Blue,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (confirmingReset) {
                    Text(
                        text = "This erases every upgrade, unlock and collected entry.",
                        color = OlympusColors.TextSecondary,
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OlympusButton(
                            text = "ERASE",
                            onClick = {
                                viewModel.resetProgress()
                                confirmingReset = false
                            },
                            style = OlympusButtonStyle.Danger,
                            modifier = Modifier.weight(1f),
                        )
                        OlympusButton(
                            text = "CANCEL",
                            onClick = { confirmingReset = false },
                            style = OlympusButtonStyle.Blue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    OlympusButton(
                        text = "RESET PROGRESS",
                        onClick = {
                            AudioEngine.play(GameSound.BUTTON_CLICK)
                            confirmingReset = true
                        },
                        style = OlympusButtonStyle.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Crystal Olympus  \u2022  version ${BuildConfig.VERSION_NAME}",
                    color = OlympusColors.TextMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = OlympusColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        control()
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(label) {
        val interactionSource = remember { MutableInteractionSource() }
        val shape = RoundedCornerShape(50)
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(26.dp)
                .clip(shape)
                .background(if (checked) OlympusColors.Success else Color(0xFF243250))
                .border(1.dp, OlympusColors.PanelBorder, shape)
                .clickableNoRipple(interactionSource) {
                    AudioEngine.play(GameSound.BUTTON_CLICK)
                    onChange(!checked)
                },
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(20.dp)
                    .clip(shape)
                    .background(Color.White),
            )
        }
    }
}
