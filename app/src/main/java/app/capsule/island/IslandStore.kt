package app.capsule.island

import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.Bitmap
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Eine Schaltfläche aus einer Benachrichtigung (z. B. „Pause“, „Antworten“). */
data class IslandAction(
    val title: String,
    val intent: PendingIntent?,
    val remoteInputs: Array<RemoteInput>? = null,
) {
    val isReply: Boolean get() = !remoteInputs.isNullOrEmpty()
    override fun equals(other: Any?) = other is IslandAction && other.title == title && other.intent == intent
    override fun hashCode() = title.hashCode() * 31 + (intent?.hashCode() ?: 0)
}

sealed interface IslandEvent {
    val key: String
    /** Höher = wichtiger. */
    val priority: Int
}

data class MediaEvent(
    override val key: String = MEDIA_KEY,
    val pkg: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val accent: Int?,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    val positionUpdateTime: Long,
    val speed: Float,
    val appIcon: Bitmap?,
) : IslandEvent {
    override val priority get() = if (playing) 50 else 20
}

data class TimerEvent(
    override val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    /** Wanduhr-Zeit (System.currentTimeMillis) als Basis des Chronometers, oder null. */
    val base: Long?,
    val countDown: Boolean,
    val fallbackText: String,
    val appIcon: Bitmap?,
    val actions: List<IslandAction>,
    val contentIntent: PendingIntent?,
) : IslandEvent {
    override val priority get() = 60
}

data class DownloadEvent(
    override val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val progress: Int,
    val max: Int,
    val indeterminate: Boolean,
    val appIcon: Bitmap?,
    val actions: List<IslandAction>,
    val contentIntent: PendingIntent?,
) : IslandEvent {
    override val priority get() = 40
    val fraction: Float get() = if (max <= 0) 0f else (progress.toFloat() / max).coerceIn(0f, 1f)
}

data class ChargingEvent(
    override val key: String = CHARGING_KEY,
    val level: Int,
    val charging: Boolean,
) : IslandEvent {
    override val priority get() = 70
}

data class NotificationEvent(
    override val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val appIcon: Bitmap?,
    val largeIcon: Bitmap?,
    val contentIntent: PendingIntent?,
    val actions: List<IslandAction>,
    val postTime: Long,
) : IslandEvent {
    override val priority get() = 90
    val replyAction: IslandAction? get() = actions.firstOrNull { it.isReply }
}

const val MEDIA_KEY = "media"
const val CHARGING_KEY = "charging"

/** Zentrale Stelle, an der alle Dienste ihre Ereignisse ablegen und die UI sie liest. */
object IslandStore {
    private val _ongoing = MutableStateFlow<Map<String, IslandEvent>>(emptyMap())
    val ongoing: StateFlow<Map<String, IslandEvent>> = _ongoing

    private val _transient = MutableStateFlow<IslandEvent?>(null)
    val transient: StateFlow<IslandEvent?> = _transient

    /** Wird hochgezählt, wenn jemand (z. B. die Einstellungs-App) das Schnellmenü öffnen will. */
    private val _quickRequests = MutableStateFlow(0)
    val quickRequests: StateFlow<Int> = _quickRequests

    @Volatile
    var mediaController: MediaController? = null

    fun put(event: IslandEvent) = _ongoing.update { it + (event.key to event) }

    fun remove(key: String) {
        _ongoing.update { if (key in it) it - key else it }
        if (_transient.value?.key == key) _transient.value = null
    }

    fun showTransient(event: IslandEvent) {
        _transient.value = event
    }

    fun dismissTransient(event: IslandEvent? = null) {
        if (event == null || _transient.value?.key == event.key) _transient.value = null
    }

    fun requestQuickPanel() = _quickRequests.update { it + 1 }
}
