package com.tvremote.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tvremote.app.theme.Palette

/** Soft radial light used behind otherwise-black screens. */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    tint: Color = Palette.Accent,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0C0C0F),
                    0.45f to Palette.Ink,
                    1f to Palette.Black,
                ),
            )
            .drawWithCache {
                val glow = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.5f, -size.height * 0.03f),
                    radius = size.width * 0.95f,
                )
                val floor = Brush.radialGradient(
                    colors = listOf(Color(0xFF2C2C33).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 1.02f),
                    radius = size.width * 0.9f,
                )
                onDrawBehind {
                    drawRect(glow)
                    drawRect(floor)
                }
            },
        content = content,
    )
}

/** Hairline border + dark glass fill used by the remote body and cards. */
fun Modifier.glassPanel(
    shape: Shape = RoundedCornerShape(28.dp),
    fillTop: Color = Color(0xFF232326),
    fillBottom: Color = Color(0xFF171719),
    border: Color = Palette.Hairline,
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(fillTop, fillBottom)), shape)
    .border(1.dp, border, shape)

@Composable
fun rememberBuzz(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        {
            runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            Unit
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoundKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
    iconSize: Dp = 24.dp,
    destructive: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(110),
        label = "keyScale",
    )
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF333336), Color(0xFF232325)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (destructive) Palette.Danger else Palette.TextPrimary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoftIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 19.dp,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(100), label = "softScale")
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.075f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) Color.White else Palette.TextSecondary,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** A tiny dot plus label describing the live connection. */
@Composable
fun ConnectionStatus(color: Color, label: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        androidx.compose.material3.Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
        )
    }
}

/** Very subtle one-pixel separator, inset from the left like iOS grouped lists. */
@Composable
fun Hairline(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            color = Color.White.copy(alpha = 0.07f),
            topLeft = Offset(inset.toPx(), 0f),
            size = androidx.compose.ui.geometry.Size(size.width - inset.toPx(), 1.dp.toPx()),
        )
    }
}
