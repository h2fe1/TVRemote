package com.tvremote.app.ui

import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin

/** A point in the Companion touchpad's 0..1000 coordinate space. */
internal data class TouchPadPoint(val x: Long, val y: Long)

private const val TOUCH_RANGE = 1000f

/**
 * Strength of the response curve. 0 is linear; 0.6 boosts the middle of the
 * pad by roughly 1.6x while keeping the edges and centre anchored.
 */
private const val CURVE_STRENGTH = 0.6f

/**
 * Map a finger position on the on-screen touch area to the 0..1000 space the
 * Apple TV uses for its touchpad.
 *
 * The mapping is absolute and monotonic: the top/left edge is 0, the centre
 * is 500, the bottom/right edge is 1000. A gentle S-curve boosts travel
 * around the middle of the pad (where most swipes start) while still mapping
 * the physical pad edge exactly onto the virtual edge; it can never reach an
 * edge before the finger does. Previously a normal vertical swipe hit y=0
 * about halfway through the gesture and every remaining event was clamped to
 * 0, so tvOS saw a finger that had stopped moving while the real finger was
 * still sliding. That is what made scrolling feel unresponsive.
 */
internal fun touchPadCoordinate(positionPx: Float, extentPx: Float): Long {
    val extent = extentPx.coerceAtLeast(1f)
    val t = (positionPx / extent).coerceIn(0f, 1f)
    // f(t) = t - a*sin(2*pi*t)/(2*pi)
    // f(0)=0, f(1/2)=1/2, f(1)=1
    // f'(0)=f'(1)=1-a, f'(1/2)=1+a
    val angle = 2f * PI.toFloat() * t
    val curved = t - CURVE_STRENGTH * sin(angle) / (2f * PI.toFloat())
    return (curved * TOUCH_RANGE).roundToLong().coerceIn(0L, 1000L)
}

/** Map a touch coordinate pair on a [width] x [height] surface to 0..1000. */
internal fun touchPadPoint(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): TouchPadPoint = TouchPadPoint(
    x = touchPadCoordinate(x, width),
    y = touchPadCoordinate(y, height),
)
