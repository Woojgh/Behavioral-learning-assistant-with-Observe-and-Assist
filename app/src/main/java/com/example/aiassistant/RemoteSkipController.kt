package com.example.aiassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

data class RemoteSkipRequest(
    val packageName: String,
    val stableState: String,
    val looseState: String,
    val command: ActionCommand,
    val createdAt: Long = System.currentTimeMillis()
)

object RemoteSkipController {
    const val ACTION_CONFIRM_REMOTE_SKIP = "com.example.aiassistant.action.CONFIRM_REMOTE_SKIP"
    const val ACTION_DISMISS_REMOTE_SKIP = "com.example.aiassistant.action.DISMISS_REMOTE_SKIP"
    private const val PREFS = "ai_assistant_prefs"
    private const val KEY_REMOTE_SKIP_CONFIRM_ENABLED = "remote_skip_confirm_enabled"

    private const val CHANNEL_ID = "remote_skip_channel"
    private const val NOTIFICATION_ID = 71_204
    private const val REQUEST_TIMEOUT_MS = 12_000L
    private const val SOURCE_WATCH = "WATCH"
    private const val SOURCE_EARBUD = "EARBUD"

    private val skipRegex = Regex("\\bskip\\b", RegexOption.IGNORE_CASE)
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingRequest: RemoteSkipRequest? = null
    private var timeoutRunnable: Runnable? = null
    private var mediaSession: MediaSession? = null
    fun isRemoteSkipEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMOTE_SKIP_CONFIRM_ENABLED, true)
    }

    fun setRemoteSkipEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMOTE_SKIP_CONFIRM_ENABLED, enabled)
            .apply()
        if (!enabled) {
            clearPending(context, "setting-disabled")
        }
    }

    fun shouldRequireRemoteConfirmation(context: Context, command: ActionCommand): Boolean {
        if (!isRemoteSkipEnabled(context)) return false
        return isSkipClickCommand(command)
    }

    private fun isSkipClickCommand(command: ActionCommand): Boolean {
        if (command.type != ActionType.CLICK) return false
        return skipRegex.containsMatchIn(command.target)
    }

    fun requestRemoteSkip(context: Context, snapshot: ScreenSnapshot, command: ActionCommand) {
        if (!shouldRequireRemoteConfirmation(context, command)) return
        val appContext = context.applicationContext

        synchronized(lock) {
            pendingRequest = RemoteSkipRequest(
                packageName = snapshot.packageName,
                stableState = snapshot.stableState,
                looseState = snapshot.looseState,
                command = command
            )
        }

        ensureNotificationChannel(appContext)
        showWatchNotification(appContext)
        enableEarbudGestureWindow(appContext)
        scheduleTimeout(appContext)
        OverlayService.updateStatus("Skip ready: watch button or earbuds next")
    }

    fun onScreenChanged(context: Context, snapshot: ScreenSnapshot) {
        val shouldClear = synchronized(lock) {
            val pending = pendingRequest
            pending != null &&
                (pending.packageName != snapshot.packageName || pending.looseState != snapshot.looseState)
        }
        if (shouldClear) {
            clearPending(context, "screen-changed")
        }
    }

    fun confirmFromWatch(context: Context) {
        confirmPending(context.applicationContext, SOURCE_WATCH)
    }

    fun confirmFromEarbud(context: Context) {
        confirmPending(context.applicationContext, SOURCE_EARBUD)
    }

    fun clearPending(context: Context, reason: String = "cleared") {
        val hadPending = synchronized(lock) {
            val hadValue = pendingRequest != null
            pendingRequest = null
            hadValue
        }
        if (!hadPending) return

        clearPromptSurface(context.applicationContext)
        if (reason != "confirmed") {
            OverlayService.updateStatus("Remote skip cancelled")
        }
    }

    fun release(context: Context) {
        clearPending(context, "release")
        mediaSession?.release()
        mediaSession = null
    }

    private fun confirmPending(context: Context, source: String) {
        val request = synchronized(lock) {
            val next = pendingRequest
            pendingRequest = null
            next
        } ?: return

        clearPromptSurface(context)

        if (System.currentTimeMillis() - request.createdAt > REQUEST_TIMEOUT_MS) {
            OverlayService.updateStatus("Remote skip expired")
            return
        }

        val service = AutoAccessibilityService.instance
        if (service == null) {
            Logger.logError("Remote skip confirmation dropped: accessibility service unavailable")
            return
        }
        service.executeRemoteSkip(request, source)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Remote Skip",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Remote confirmation actions for learned Skip clicks"
        }
        manager.createNotificationChannel(channel)
    }

    private fun showWatchNotification(context: Context) {
        val confirmIntent = Intent(context, RemoteSkipActionReceiver::class.java).apply {
            action = ACTION_CONFIRM_REMOTE_SKIP
        }
        val dismissIntent = Intent(context, RemoteSkipActionReceiver::class.java).apply {
            action = ACTION_DISMISS_REMOTE_SKIP
        }

        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            1_001,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            1_002,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_next)
            .setContentTitle("Skip detected")
            .setContentText("Tap Skip on your watch or use earbuds next gesture.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(confirmPendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Skip", confirmPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Logger.logError("Unable to post remote skip notification: ${e.message}")
        }
    }

    private fun clearPromptSurface(context: Context) {
        cancelTimeout()
        disableEarbudGestureWindow()
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: SecurityException) {
            Logger.logError("Unable to clear remote skip notification: ${e.message}")
        }
    }

    private fun scheduleTimeout(context: Context) {
        cancelTimeout()
        timeoutRunnable = Runnable {
            val hasPending = synchronized(lock) { pendingRequest != null }
            if (hasPending) {
                clearPending(context, "timeout")
            }
        }.also {
            mainHandler.postDelayed(it, REQUEST_TIMEOUT_MS)
        }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun enableEarbudGestureWindow(context: Context) {
        ensureMediaSession(context)
        val playbackState = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_SKIP_TO_NEXT)
            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
        mediaSession?.isActive = true
        playChime()
    }

    private fun disableEarbudGestureWindow() {
        mediaSession?.isActive = false
    }

    private fun ensureMediaSession(context: Context) {
        if (mediaSession != null) return
        val appContext = context.applicationContext
        mediaSession = MediaSession(appContext, "RemoteSkipSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onSkipToNext() {
                    confirmFromEarbud(appContext)
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (event?.action == KeyEvent.ACTION_DOWN &&
                        event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                    ) {
                        confirmFromEarbud(appContext)
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
        }
    }

    private fun playChime() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 220)
            mainHandler.postDelayed({ toneGenerator.release() }, 400L)
        } catch (e: Exception) {
            Logger.logError("Remote skip chime failed: ${e.message}")
        }
    }
}
