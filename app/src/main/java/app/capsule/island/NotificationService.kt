package app.capsule.island

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/**
 * Liest Benachrichtigungen und Medien-Sitzungen und übersetzt sie in Island-Ereignisse.
 */
class NotificationService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val iconCache = HashMap<String, Bitmap?>()
    private val labelCache = HashMap<String, String>()
    private var lastArt: Bitmap? = null
    private var lastAccent: Int? = null
    private val recentPopups = HashMap<String, Long>()

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onSessionDestroyed() {
            setController(null)
            refreshSessions()
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    private val hidePausedMedia = Runnable { IslandStore.remove(MEDIA_KEY) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Prefs.init(this)
        instance = this
        val sm = getSystemService(MediaSessionManager::class.java)
        sessionManager = sm
        try {
            sm.addOnActiveSessionsChangedListener(sessionsListener, component(), handler)
        } catch (_: SecurityException) {
        }
        refreshSessions()
        try {
            activeNotifications?.forEach { handle(it, initial = true) }
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        setController(null)
        instance = null
    }

    private fun component() = ComponentName(this, NotificationService::class.java)

    private fun refreshSessions() {
        try {
            pickController(sessionManager?.getActiveSessions(component()))
        } catch (_: SecurityException) {
        }
    }

    private fun pickController(list: List<MediaController>?) {
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val chosen = playing ?: list?.firstOrNull { it.sessionToken == controller?.sessionToken } ?: list?.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) setController(chosen) else publishMedia()
    }

    private fun setController(c: MediaController?) {
        try {
            controller?.unregisterCallback(mediaCallback)
        } catch (_: Exception) {
        }
        controller = c
        c?.registerCallback(mediaCallback, handler)
        IslandStore.mediaController = c
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        val md = c?.metadata
        val st = c?.playbackState
        if (!Prefs.current.media || c == null || md == null || st == null ||
            st.state == PlaybackState.STATE_NONE || st.state == PlaybackState.STATE_STOPPED ||
            st.state == PlaybackState.STATE_ERROR
        ) {
            handler.removeCallbacks(hidePausedMedia)
            IslandStore.remove(MEDIA_KEY)
            return
        }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        if (title.isBlank()) {
            IslandStore.remove(MEDIA_KEY); return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art !== lastArt) {
            lastArt = art
            lastAccent = art?.let { accentOf(it) }
        }
        val playing = st.state == PlaybackState.STATE_PLAYING || st.state == PlaybackState.STATE_BUFFERING
        IslandStore.put(
            MediaEvent(
                pkg = c.packageName,
                title = title,
                artist = artist,
                art = art,
                accent = lastAccent,
                playing = playing,
                position = st.position,
                duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                positionUpdateTime = if (st.lastPositionUpdateTime > 0) st.lastPositionUpdateTime else SystemClock.elapsedRealtime(),
                speed = if (st.playbackSpeed == 0f) 1f else st.playbackSpeed,
                appIcon = appIcon(c.packageName),
            )
        )
        // Pausierte Musik verschwindet nach einer Weile wieder aus der Island.
        handler.removeCallbacks(hidePausedMedia)
        if (!playing) handler.postDelayed(hidePausedMedia, 45_000)
    }

    private fun accentOf(bmp: Bitmap): Int? = try {
        val small = if (bmp.width > 160) Bitmap.createScaledBitmap(bmp, 160, 160 * bmp.height / bmp.width.coerceAtLeast(1), true) else bmp
        val p = Palette.from(small).generate()
        val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch
        sw?.rgb
    } catch (_: Exception) {
        null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn, initial = false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        IslandStore.remove(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val ex = n.extras ?: return
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            refreshSessions(); return
        }
        val s = Prefs.current
        val pkg = sbn.packageName
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val ongoing = sbn.isOngoing
        val isClock = pkg.contains("clock", ignoreCase = true) || pkg.contains("deskclock", ignoreCase = true)
        val stopwatchCategory = n.category == Notification.CATEGORY_STOPWATCH

        when {
            s.timers && ongoing && (chrono || stopwatchCategory || (isClock && n.category != Notification.CATEGORY_ALARM)) -> {
                IslandStore.put(
                    TimerEvent(
                        key = sbn.key,
                        pkg = pkg,
                        appName = appLabel(pkg),
                        title = title,
                        base = if (chrono) n.`when` else null,
                        countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
                        fallbackText = text.ifBlank { title },
                        appIcon = appIcon(pkg),
                        actions = actionsOf(n),
                        contentIntent = n.contentIntent,
                    )
                )
            }

            s.downloads && ongoing && (max > 0 || indeterminate) -> {
                if (max > 0 && progress >= max) {
                    IslandStore.remove(sbn.key)
                } else {
                    IslandStore.put(
                        DownloadEvent(
                            key = sbn.key,
                            pkg = pkg,
                            appName = appLabel(pkg),
                            title = title.ifBlank { text },
                            progress = progress,
                            max = max,
                            indeterminate = indeterminate,
                            appIcon = appIcon(pkg),
                            actions = actionsOf(n),
                            contentIntent = n.contentIntent,
                        )
                    )
                }
            }

            else -> {
                IslandStore.remove(sbn.key)
                if (initial || !s.notifications || ongoing) return
                if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
                if (title.isBlank() && text.isBlank()) return
                if (!isAlerting(sbn)) return
                val now = System.currentTimeMillis()
                // Nicht dieselbe Benachrichtigung mehrmals hintereinander aufpoppen lassen.
                val last = recentPopups[sbn.key]
                if (last != null && now - last < 2500) return
                if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && last != null) return
                recentPopups[sbn.key] = now
                if (recentPopups.size > 200) recentPopups.clear()

                val large = try {
                    n.getLargeIcon()?.loadDrawable(this)?.toBitmap(128, 128)
                } catch (_: Exception) {
                    null
                }
                IslandStore.showTransient(
                    NotificationEvent(
                        key = sbn.key,
                        pkg = pkg,
                        appName = appLabel(pkg),
                        title = title,
                        text = text,
                        appIcon = appIcon(pkg),
                        largeIcon = large,
                        contentIntent = n.contentIntent,
                        actions = actionsOf(n),
                        postTime = sbn.postTime,
                    )
                )
            }
        }
    }

    private fun isAlerting(sbn: StatusBarNotification): Boolean = try {
        val r = Ranking()
        if (currentRanking.getRanking(sbn.key, r)) {
            r.importance >= NotificationManager.IMPORTANCE_DEFAULT && !r.isAmbient
        } else true
    } catch (_: Exception) {
        true
    }

    private fun actionsOf(n: Notification): List<IslandAction> =
        n.actions?.mapNotNull { a ->
            val t = a.title?.toString() ?: return@mapNotNull null
            IslandAction(t, a.actionIntent, a.remoteInputs)
        }?.take(3) ?: emptyList()

    private fun appIcon(pkg: String): Bitmap? = iconCache.getOrPut(pkg) {
        try {
            packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
        } catch (_: Exception) {
            null
        }
    }

    private fun appLabel(pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    /** Wird aufgerufen, wenn sich Einstellungen ändern (z. B. Medien an/aus). */
    fun refreshAll() {
        publishMedia()
    }

    companion object {
        @Volatile
        var instance: NotificationService? = null
    }
}
