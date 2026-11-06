package dev.companionremote.protocol.client

/** Transport state reported by the Apple TV through `_iMC` / MediaControlStatus. */
enum class PlaybackState {
    /** No status has arrived yet (or the connection is new). */
    Unknown,
    Playing,
    Paused,
    Stopped,
}

/**
 * Decode the allowed-control flags (`_iMC._mcF`, or
 * `MediaControlStatus.MediaControlFlags`).
 *
 * The flags describe what the user is allowed to do next, not a mode:
 * bit 0 = play is allowed (so playback is paused), bit 1 = pause is allowed
 * (so playback is running). Remaining bits are capability flags.
 *
 * Same interpretation as the pyatv-derived Home Assistant integrations:
 * pause allowed wins when both bits are set, and neither bit means idle.
 */
fun playbackStateFromMediaFlags(flags: Long): PlaybackState = when {
    flags and (1L shl 1) != 0L -> PlaybackState.Playing
    flags and (1L shl 0) != 0L -> PlaybackState.Paused
    else -> PlaybackState.Stopped
}