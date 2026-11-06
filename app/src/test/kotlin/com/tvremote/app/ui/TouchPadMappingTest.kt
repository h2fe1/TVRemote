package com.tvremote.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TouchPadMappingTest {

    @Test
    fun `edges center and clamping are anchored`() {
        assertEquals(0L, touchPadCoordinate(0f, 1000f))
        assertEquals(500L, touchPadCoordinate(500f, 1000f))
        assertEquals(1000L, touchPadCoordinate(1000f, 1000f))

        assertEquals(0L, touchPadCoordinate(-120f, 1000f))
        assertEquals(1000L, touchPadCoordinate(1200f, 1000f))
        assertEquals(0L, touchPadCoordinate(0f, 0f))
    }

    @Test
    fun `mapping is monotonic and never pins an edge inside the pad`() {
        var previous = -1L
        for (i in 0..1000) {
            val value = touchPadCoordinate(i.toFloat(), 1000f)
            assertTrue("coordinate went backwards at $i", value >= previous)
            previous = value
        }
        // A finger that is still inside the pad must not already be reported
        // at the edge. This is the regression: the old accumulator clamped a
        // normal swipe to y=0 halfway through and then kept sending 0.
        assertTrue(touchPadCoordinate(10f, 1000f) > 0L)
        assertTrue(touchPadCoordinate(990f, 1000f) < 1000L)
    }

    @Test
    fun `short swipes get more travel than a linear mapping`() {
        val quarter = touchPadCoordinate(750f, 1000f) - 500L
        assertTrue("quarter-pad swipe only moved $quarter", quarter > 250L)

        val eighth = touchPadCoordinate(625f, 1000f) - 500L
        assertTrue("eighth-pad swipe only moved $eighth", eighth > 125L)
    }

    @Test
    fun `response is symmetric around the centre`() {
        for (i in 0..500) {
            val left = touchPadCoordinate(i.toFloat(), 1000f)
            val right = touchPadCoordinate((1000 - i).toFloat(), 1000f)
            assertTrue(abs((left + right) - 1000L) <= 1L)
        }
    }

    @Test
    fun `x and y use the same curve for their own extents`() {
        val point = touchPadPoint(x = 750f, y = 250f, width = 1000f, height = 1000f)
        assertEquals(touchPadCoordinate(750f, 1000f), point.x)
        assertEquals(touchPadCoordinate(250f, 1000f), point.y)
        assertTrue(point.x > 500L)
        assertTrue(point.y < 500L)
    }
}
