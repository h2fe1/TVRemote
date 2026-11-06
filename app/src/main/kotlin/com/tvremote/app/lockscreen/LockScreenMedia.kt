package com.tvremote.app.lockscreen

import kotlinx.coroutines.flow.MutableStateFlow

/** Snapshot rendered by the system media player on the lock screen / in the shade. */
data class LockScreenState(
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val muted: Boolean,
    val artwork: ByteArray?,
)

sealed interface LockScreenCommand {
    data object Play : LockScreenCommand
    data object Pause : LockScreenCommand
    data object Rewind15 : LockScreenCommand
    data object Forward15 : LockScreenCommand
    data object ToggleMute : LockScreenCommand
    data class SeekTo(val positionMs: Long) : LockScreenCommand
}

/**
 * Process-wide bridge between the Companion connection (owned by the
 * ViewModel) and the foreground service that owns the MediaSession.
 *
 * A MediaSession is the only supported way to put media controls on the lock
 * screen; the system draws its own player card from this state, so this is not
 * the in-app Compose card.
 */
object LockScreenMedia {
    val state = MutableStateFlow<LockScreenState?>(null)
    val deviceName = MutableStateFlow<String?>(null)
    var commandHandler: ((LockScreenCommand) -> Unit)? = null
}
