package dev.companionremote.protocol.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackStateTest {
    @Test
    fun `pause allowed means the TV is playing`() {
        assertEquals(PlaybackState.Playing, playbackStateFromMediaFlags(2L))
    }

    @Test
    fun `play allowed means the TV is paused`() {
        assertEquals(PlaybackState.Paused, playbackStateFromMediaFlags(1L))
    }

    @Test
    fun `neither transport bit means stopped`() {
        assertEquals(PlaybackState.Stopped, playbackStateFromMediaFlags(0L))
        assertEquals(PlaybackState.Stopped, playbackStateFromMediaFlags(256L))
    }

    @Test
    fun `both transport bits prefer playing`() {
        assertEquals(PlaybackState.Playing, playbackStateFromMediaFlags(3L))
    }
}