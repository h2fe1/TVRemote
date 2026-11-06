package dev.companionremote.protocol.client

import dev.companionremote.protocol.plist.BinaryPlist
import dev.companionremote.protocol.plist.PlistUid
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NowPlayingTest {
    private fun archive(artwork: Any? = null, metadata: Map<String, Any?>? = null): ByteArray {
        val fields = metadata ?: linkedMapOf(
            "title" to PlistUid(3),
            "artist" to PlistUid(4),
            "duration" to 3600L,
            "elapsedTime" to 1466.0,
            "playbackRate" to 1L,
            "artworkData" to PlistUid(5),
        )
        return BinaryPlist.encode(
            linkedMapOf(
                "\$version" to 100000L,
                "\$archiver" to "NSKeyedArchiver",
                "\$top" to linkedMapOf("root" to PlistUid(1)),
                "\$objects" to listOf(
                    "\$null",
                    linkedMapOf("metadata" to PlistUid(2)),
                    fields,
                    "甄嬛传",
                    "电视剧",
                    artwork ?: ByteArray(0),
                ),
            ),
        )
    }

    @Test
    fun `decodes title artist duration and elapsed time`() {
        val nowPlaying = NowPlayingParser.parse(archive())

        assertEquals("甄嬛传", nowPlaying?.title)
        assertEquals("电视剧", nowPlaying?.artist)
        assertEquals(3600.0, nowPlaying?.durationSeconds)
        assertEquals(1466.0, nowPlaying?.elapsedSeconds)
        assertEquals(1.0, nowPlaying?.playbackRate)
    }

    @Test
    fun `decodes NSData artwork wrapper`() {
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01)
        val encoded = archive(artwork = linkedMapOf("NS.data" to image))

        assertArrayEquals(image, NowPlayingParser.parse(encoded)?.artwork)
    }

    @Test
    fun `partial update keeps previous values`() {
        val base = NowPlaying(
            title = "甄嬛传",
            artist = "电视剧",
            durationSeconds = 3600.0,
            elapsedSeconds = 10.0,
        )

        val merged = base.merge(NowPlaying(elapsedSeconds = 25.0))

        assertEquals("甄嬛传", merged.title)
        assertEquals("电视剧", merged.artist)
        assertEquals(3600.0, merged.durationSeconds)
        assertEquals(25.0, merged.elapsedSeconds)
    }

    @Test
    fun `partial volume update with zero elapsed does not rewind`() {
        val base = NowPlaying(
            title = "甄嬛传",
            durationSeconds = 3600.0,
            elapsedSeconds = 1466.0,
            playbackRate = 1.0,
        )

        val merged = base.merge(NowPlaying(elapsedSeconds = 0.0, durationSeconds = 0.0))

        assertEquals(1466.0, merged.elapsedSeconds)
        assertEquals(3600.0, merged.durationSeconds)
    }

    @Test
    fun `same title zero elapsed after mute does not rewind`() {
        val base = NowPlaying(
            title = "甄嬛传",
            durationSeconds = 3600.0,
            elapsedSeconds = 1466.0,
            playbackRate = 1.0,
        )

        val merged = base.merge(
            NowPlaying(title = "甄嬛传", elapsedSeconds = 0.0, playbackRate = 1.0),
        )

        assertEquals(1466.0, merged.elapsedSeconds)
    }

    @Test
    fun `full snapshot may restart at zero`() {
        val base = NowPlaying(title = "旧", durationSeconds = 3600.0, elapsedSeconds = 1466.0)
        val merged = base.merge(NowPlaying(title = "新", durationSeconds = 100.0, elapsedSeconds = 0.0))

        assertEquals("新", merged.title)
        assertEquals(0.0, merged.elapsedSeconds)
        assertEquals(100.0, merged.durationSeconds)
    }

    @Test
    fun `unknown payload is ignored`() {
        assertNull(NowPlayingParser.parse(byteArrayOf(1, 2, 3)))
        assertNull(NowPlayingParser.parse(archive(metadata = linkedMapOf("volume" to 4L))))
    }
}