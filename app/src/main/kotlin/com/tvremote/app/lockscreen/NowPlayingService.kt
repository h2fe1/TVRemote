package com.tvremote.app.lockscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import com.tvremote.app.MainActivity
import com.tvremote.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that mirrors the TV's now-playing state into a
 * MediaSession. The system uses it to render the lock screen / notification
 * shade media card (Android 17 uses the card layout there).
 *
 * The service only exists while the TV is connected with an active media item;
 * AppViewModel stops it as soon as playback disappears or the TV sleeps.
 */
class NowPlayingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private lateinit var notificationManager: NotificationManager
    private var foreground = false
    private var lastState: LockScreenState? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()
        session = MediaSession(this, SESSION_TAG).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = dispatch(LockScreenCommand.Play)
                override fun onPause() = dispatch(LockScreenCommand.Pause)
                override fun onSeekTo(pos: Long) = dispatch(LockScreenCommand.SeekTo(pos))
                override fun onCustomAction(action: String, extras: Bundle?) {
                    when (action) {
                        ACTION_REWIND -> dispatch(LockScreenCommand.Rewind15)
                        ACTION_FORWARD -> dispatch(LockScreenCommand.Forward15)
                        ACTION_MUTE -> dispatch(LockScreenCommand.ToggleMute)
                    }
                }
            })
            isActive = true
        }

        scope.launch {
            LockScreenMedia.state.collectLatest { state ->
                lastState = state
                if (state == null) showIdle() else update(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> dispatch(
                if (lastState?.isPlaying == true) LockScreenCommand.Pause else LockScreenCommand.Play,
            )
            ACTION_REWIND -> dispatch(LockScreenCommand.Rewind15)
            ACTION_FORWARD -> dispatch(LockScreenCommand.Forward15)
            ACTION_MUTE -> dispatch(LockScreenCommand.ToggleMute)
        }
        val state = LockScreenMedia.state.value
        if (state == null) showIdle() else update(state)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    private fun dispatch(command: LockScreenCommand) {
        LockScreenMedia.commandHandler?.invoke(command)
    }

    private fun showIdle() {
        session.isActive = false
        session.setMetadata(null)
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_NONE, 0L, 0f)
                .build(),
        )
        val notification = buildIdleNotification()
        if (!foreground) {
            startForeground(NOTIFICATION_ID, notification)
            foreground = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun update(state: LockScreenState) {
        session.isActive = true
        val art = artwork(state.artwork)
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.artist ?: "Apple TV")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
            .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }
            .build()
        session.setMetadata(metadata)
        session.setPlaybackState(playbackState(state))

        val notification = buildNotification(state, art)
        if (!foreground) {
            startForeground(NOTIFICATION_ID, notification)
            foreground = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun playbackState(state: LockScreenState): PlaybackState {
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SEEK_TO
        return PlaybackState.Builder()
            .setActions(actions)
            .setState(
                if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                state.positionMs,
                if (state.isPlaying) 1f else 0f,
                SystemClock.elapsedRealtime(),
            )
            .addCustomAction(customAction(ACTION_REWIND, "后退 15 秒", R.drawable.ic_rewind_15))
            .addCustomAction(customAction(ACTION_FORWARD, "前进 15 秒", R.drawable.ic_forward_15))
            .addCustomAction(
                customAction(
                    ACTION_MUTE,
                    if (state.muted) "取消静音" else "静音",
                    if (state.muted) R.drawable.ic_volume_up else R.drawable.ic_volume_off,
                ),
            )
            .build()
    }

    private fun customAction(action: String, title: String, iconRes: Int): PlaybackState.CustomAction =
        PlaybackState.CustomAction.Builder(action, title, iconRes).build()

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildIdleNotification(): Notification {
        val contentIntent = contentIntent()
        return Notification.Builder(this, CHANNEL_IDLE)
            .setSmallIcon(R.drawable.ic_stat_tv)
            .setContentTitle("TV Remote")
            .setContentText("已连接 ${LockScreenMedia.deviceName.value ?: "Apple TV"}")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun buildNotification(state: LockScreenState, art: Bitmap?): Notification {
        val contentIntent = contentIntent()
        session.setSessionActivity(contentIntent)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tv)
            .setContentTitle(state.title)
            .setContentText(state.artist ?: "Apple TV")
            .setSubText("TV Remote")
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(action(R.drawable.ic_rewind_15, "后退 15 秒", ACTION_REWIND))
            .addAction(
                action(
                    if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (state.isPlaying) "暂停" else "播放",
                    ACTION_PLAY_PAUSE,
                ),
            )
            .addAction(action(R.drawable.ic_forward_15, "前进 15 秒", ACTION_FORWARD))
            .addAction(
                action(
                    if (state.muted) R.drawable.ic_volume_up else R.drawable.ic_volume_off,
                    if (state.muted) "取消静音" else "静音",
                    ACTION_MUTE,
                ),
            )
        if (art != null) builder.setLargeIcon(art)
        return builder.build()
    }

    private fun action(iconRes: Int, title: String, action: String): Notification.Action {
        val pending = PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, NowPlayingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, iconRes),
            title,
            pending,
        ).build()
    }

    private fun createChannel() {
        val playing = NotificationChannel(
            CHANNEL_ID,
            "正在播放",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "锁屏和通知栏里的 Apple TV 播放控制"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(playing)

        val connection = NotificationChannel(
            CHANNEL_IDLE,
            "遥控器连接",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "保持与 Apple TV 的连接，播放开始时自动切换为媒体卡"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        notificationManager.createNotificationChannel(connection)
    }

    private fun artwork(bytes: ByteArray?): Bitmap? {
        if (bytes == null) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 720) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull()
    }

    companion object {
        private const val SESSION_TAG = "TVRemoteNowPlaying"
        private const val CHANNEL_ID = "tvremote_now_playing"
        private const val CHANNEL_IDLE = "tvremote_connection"
        private const val NOTIFICATION_ID = 4210
        private const val ACTION_PLAY_PAUSE = "com.tvremote.app.PLAY_PAUSE"
        private const val ACTION_REWIND = "com.tvremote.app.REWIND15"
        private const val ACTION_FORWARD = "com.tvremote.app.FORWARD15"
        private const val ACTION_MUTE = "com.tvremote.app.MUTE"
    }
}
