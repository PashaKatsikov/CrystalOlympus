package com.crystalolympus.crystalolympusgame.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

/**
 * Click handling without Material's ripple. Game buttons communicate presses through their own
 * scale animation, and a ripple on top of the painted gradients looks out of place.
 */
fun Modifier.clickableNoRipple(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
)
