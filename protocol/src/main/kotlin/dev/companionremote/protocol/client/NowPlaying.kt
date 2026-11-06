package dev.companionremote.protocol.client

import dev.companionremote.protocol.plist.KeyedArchiver

/**
 * Now-playing metadata pushed by the Apple TV in `NowPlayingInfo` events.
 *
 * Updates can be partial: the TV only sends the fields that changed, so use
 * [merge] to fold an update into the last known snapshot.
 */
data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationSeconds: Double? = null,
    val elapsedSeconds: Double? = null,
    val playbackRate: Double? = null,
    val artwork: ByteArray? = null,
) {
    fun merge(update: NowPlaying): NowPlaying = NowPlaying(
        title = update.title ?: title,
        artist = update.artist ?: artist,
        album = update.album ?: album,
        durationSeconds = update.durationSeconds?.takeIf { it > 0.0 } ?: durationSeconds,
        // Volume / transport commands can trigger a partial NowPlayingInfo
        // with elapsedTime = 0 but no title. Do not let that rewind the bar.
        elapsedSeconds = when {
            update.elapsedSeconds == null -> elapsedSeconds
            update.elapsedSeconds == 0.0 &&
                (update.title == null || update.title == title) &&
                (elapsedSeconds ?: 0.0) > 1.0 -> elapsedSeconds
            else -> update.elapsedSeconds
        },
        playbackRate = update.playbackRate ?: playbackRate,
        artwork = update.artwork ?: artwork,
    )
}

/**
 * Decodes the `NowPlayingInfoKey` NSKeyedArchiver payload.
 *
 * Both the short keys used by the companion service (`title`, `artist`,
 * `duration`, `elapsedTime`, ...) and their `kMRMediaRemoteNowPlayingInfo*`
 * spellings are accepted, because tvOS versions differ in what they archive.
 * Returns null when the payload carries no recognisable metadata (a partial
 * play/pause update, for example).
 */
object NowPlayingParser {
    private val TITLE_KEYS = arrayOf("title", "kMRMediaRemoteNowPlayingInfoTitle")
    private val ARTIST_KEYS = arrayOf("artist", "kMRMediaRemoteNowPlayingInfoArtist")
    private val ALBUM_KEYS = arrayOf("album", "kMRMediaRemoteNowPlayingInfoAlbum")
    private val DURATION_KEYS = arrayOf("duration", "kMRMediaRemoteNowPlayingInfoDuration")
    private val ELAPSED_KEYS = arrayOf(
        "elapsedTime",
        "elapsed",
        "kMRMediaRemoteNowPlayingInfoElapsedTime",
    )
    private val RATE_KEYS = arrayOf("playbackRate", "rate", "kMRMediaRemoteNowPlayingInfoPlaybackRate")
    private val ARTWORK_KEYS = arrayOf(
        "artworkData",
        "artwork",
        "kMRMediaRemoteNowPlayingInfoArtworkData",
    )

    fun parse(archive: ByteArray): NowPlaying? {
        val root = KeyedArchiver.decodeArchive(archive) as? Map<*, *> ?: return null
        val metadata = root["metadata"] as? Map<*, *> ?: return null

        val title = metadata.string(*TITLE_KEYS)
        val artist = metadata.string(*ARTIST_KEYS)
        val album = metadata.string(*ALBUM_KEYS)
        val duration = metadata.number(*DURATION_KEYS)?.takeIf { it > 0 }
        val elapsed = metadata.number(*ELAPSED_KEYS)?.takeIf { it >= 0 }
        val rate = metadata.number(*RATE_KEYS)
        val artwork = metadata.bytes(*ARTWORK_KEYS)

        if (title == null && artist == null && album == null &&
            duration == null && elapsed == null && artwork == null
        ) {
            return null
        }
        return NowPlaying(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            elapsedSeconds = elapsed,
            playbackRate = rate,
            artwork = artwork,
        )
    }

    private fun Map<*, *>.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? String)?.takeIf { it.isNotBlank() }
        }

    private fun Map<*, *>.number(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? Number)?.toDouble() }

    private fun Map<*, *>.bytes(vararg keys: String): ByteArray? {
        for (key in keys) {
            when (val value = this[key]) {
                is ByteArray -> return value
                is Map<*, *> -> (value["NS.data"] as? ByteArray)?.let { return it }
            }
        }
        return null
    }
}