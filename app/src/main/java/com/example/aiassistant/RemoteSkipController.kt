package com.example.aiassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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
    const val ACTION_CONFIRM_REMOTE_SKIP_TEST = "com.example.aiassistant.action.CONFIRM_REMOTE_SKIP_TEST"
    const val ACTION_DISMISS_REMOTE_SKIP_TEST = "com.example.aiassistant.action.DISMISS_REMOTE_SKIP_TEST"
    private const val PREFS = "ai_assistant_prefs"
    private const val KEY_REMOTE_SKIP_CONFIRM_ENABLED = "remote_skip_confirm_enabled"

    private const val CHANNEL_ID = "remote_skip_watch_channel_v2"
    private const val NOTIFICATION_ID = 71_204
    private const val TEST_NOTIFICATION_ID = 71_205
    private const val TEST_NOTIFICATION_TIMEOUT_MS = 20_000L
    private const val REQUEST_TIMEOUT_MS = 20_000L
    private const val DISMISSAL_ID_REMOTE_SKIP = "remote_skip_prompt"
    private const val DISMISSAL_ID_REMOTE_SKIP_TEST = "remote_skip_prompt_test"
    private const val SOURCE_WATCH = "WATCH"
    private const val SOURCE_EARBUD = "EARBUD"
    private const val SKIP_TOKEN = "skip"

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

    /**
     * Sends a standalone watch prompt test notification so watch mirroring can
     * be verified independently from skip-detection logic.
     */
    fun showWatchPromptTest(context: Context) {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            OverlayService.updateStatus("Enable app notifications for watch skip prompt")
            return
        }

        val testIntent = Intent(appContext, RemoteSkipActionReceiver::class.java).apply {
            action = ACTION_CONFIRM_REMOTE_SKIP_TEST
        }
        val dismissIntent = Intent(appContext, RemoteSkipActionReceiver::class.java).apply {
            action = ACTION_DISMISS_REMOTE_SKIP_TEST
        }

        val testPendingIntent = PendingIntent.getBroadcast(
            appContext,
            1_101,
            testIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPendingIntent = PendingIntent.getBroadcast(
            appContext,
            1_102,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val testAction = buildWearInlineAction(
            iconRes = android.R.drawable.ic_media_play,
            title = "SKIP",
            pendingIntent = testPendingIntent
        )
        val dismissAction = buildWearInlineAction(
            iconRes = android.R.drawable.ic_menu_close_clear_cancel,
            title = "Dismiss",
            pendingIntent = dismissPendingIntent
        )
        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(testAction)
            .setContentAction(0)
            .setDismissalId(DISMISSAL_ID_REMOTE_SKIP_TEST)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SKIP TEST")
            .setContentText("Tap SKIP")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(TEST_NOTIFICATION_TIMEOUT_MS)
            .setContentIntent(testPendingIntent)
            .addAction(testAction)
            .addAction(dismissAction)
            .extend(wearableExtender)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(TEST_NOTIFICATION_ID, notification)
            OverlayService.updateStatus("Sent watch prompt test")
        } catch (e: SecurityException) {
            Logger.logError("Unable to post watch test notification: ${e.message}")
            OverlayService.updateStatus("Grant notification permission for watch prompt")
        }
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
    /**
     * Finds a likely skip target directly from the current screen text.
     * Used by REMOTE_SKIP mode, which bypasses observe/assist pattern matching.
     */
    fun findSkipCommand(snapshot: ScreenSnapshot): ActionCommand? {
        val target = snapshot.textElements
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { containsSkipKeyword(it) }
            .minByOrNull { it.length }
            ?: return null
        return ActionCommand(type = ActionType.CLICK, target = target)
    }

    private fun isSkipClickCommand(command: ActionCommand): Boolean {
        if (command.type != ActionType.CLICK) return false
        return containsSkipKeyword(command.target)
    }

    private fun containsSkipKeyword(text: String): Boolean {
        return text.contains(SKIP_TOKEN, ignoreCase = true)
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
        OverlayService.updateStatus("Skip ready on watch / earbud next gesture")
    }

    fun onScreenChanged(context: Context, snapshot: ScreenSnapshot) {
        val shouldClear = synchronized(lock) {
            val pending = pendingRequest
            pending != null && pending.packageName != snapshot.packageName
        }
        if (shouldClear) {
            clearPending(context, "screen-changed")
        }
    }

    fun confirmFromWatch(context: Context) {
        confirmPending(context.applicationContext, SOURCE_WATCH)
    }

    fun onWatchTestAction(context: Context) {
        try {
            NotificationManagerCompat.from(context.applicationContext).cancel(TEST_NOTIFICATION_ID)
        } catch (_: Exception) {}
        OverlayService.updateStatus("Watch action received")
    }

    fun dismissWatchTest(context: Context) {
        try {
            NotificationManagerCompat.from(context.applicationContext).cancel(TEST_NOTIFICATION_ID)
        } catch (_: Exception) {}
        OverlayService.updateStatus("Watch prompt test dismissed")
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
            "Remote Skip Watch Prompt",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Remote confirmation actions for learned Skip clicks"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun showWatchNotification(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            OverlayService.updateStatus("Enable app notifications for watch skip prompt")
            return
        }
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

        val skipAction = buildWearInlineAction(
            iconRes = android.R.drawable.ic_media_next,
            title = "SKIP",
            pendingIntent = confirmPendingIntent
        )
        val dismissAction = buildWearInlineAction(
            iconRes = android.R.drawable.ic_menu_close_clear_cancel,
            title = "Dismiss",
            pendingIntent = dismissPendingIntent
        )
        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(skipAction)
            .setContentAction(0)
            .setDismissalId(DISMISSAL_ID_REMOTE_SKIP)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_next)
            .setContentTitle("SKIP")
            .setContentText("Tap SKIP")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(REQUEST_TIMEOUT_MS)
            .setContentIntent(confirmPendingIntent)
            .addAction(skipAction)
            .addAction(dismissAction)
            .extend(wearableExtender)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Logger.logError("Unable to post remote skip notification: ${e.message}")
            OverlayService.updateStatus("Grant notification permission for watch skip prompt")
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
            .setActions(
                PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_PLAY_PAUSE
            )
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
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setCallback(object : MediaSession.Callback() {
                override fun onSkipToNext() {
                    confirmFromEarbud(appContext)
                }
                override fun onFastForward() {
                    confirmFromEarbud(appContext)
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = extractMediaButtonEvent(mediaButtonIntent)
                    if (event?.action == KeyEvent.ACTION_DOWN && isEarbudNextGesture(event.keyCode)) {
                        confirmFromEarbud(appContext)
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
        }
    }

    private fun extractMediaButtonEvent(mediaButtonIntent: Intent): KeyEvent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }

    private fun isEarbudNextGesture(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    }

    private fun playChime() {
        if (playToneOnStream(AudioManager.STREAM_MUSIC)) return
        if (playToneOnStream(AudioManager.STREAM_NOTIFICATION)) return
        Logger.logError("Remote skip chime failed: unable to play tone on available streams")
    }

    private fun playToneOnStream(streamType: Int): Boolean {
        return try {
            val toneGenerator = ToneGenerator(streamType, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 170)
            mainHandler.postDelayed({
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 170)
            }, 220L)
            mainHandler.postDelayed({ toneGenerator.release() }, 550L)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildWearInlineAction(
        iconRes: Int,
        title: String,
        pendingIntent: PendingIntent
    ): NotificationCompat.Action {
        val actionExtender = NotificationCompat.Action.WearableExtender()
            .setHintDisplayActionInline(true)
            .setAvailableOffline(true)
        return NotificationCompat.Action.Builder(iconRes, title, pendingIntent)
            .extend(actionExtender)
            .build()
    }
}
