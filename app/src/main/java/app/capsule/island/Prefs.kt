package app.capsule.island

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    /** Größenfaktor der Island (1.0 = Standard, größer als Original). */
    val scale: Float = 1.15f,
    val offsetYdp: Float = 0f,
    val offsetXdp: Float = 0f,
    /** true = schwarz (verschmilzt mit Kamera), false = Material-You-Fläche. */
    val blackBackground: Boolean = true,
    /** Im Ruhezustand kleine Pille um die Kamera zeigen. */
    val idleVisible: Boolean = false,
    /** Verspielte, federnde Animationen (Material 3 Expressive). */
    val playful: Boolean = true,
    val haptics: Boolean = true,
    val media: Boolean = true,
    val notifications: Boolean = true,
    val timers: Boolean = true,
    val downloads: Boolean = true,
    val charging: Boolean = true,
    val doubleTapQuick: Boolean = true,
    val notificationSeconds: Int = 6,
    val hideInLandscape: Boolean = true,
    val shortcuts: List<String> = emptyList(),
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state
    val current: Settings get() = _state.value

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("capsule", Context.MODE_PRIVATE)
        _state.value = read()
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            scale = sp.getFloat("scale", d.scale),
            offsetYdp = sp.getFloat("offsetY", d.offsetYdp),
            offsetXdp = sp.getFloat("offsetX", d.offsetXdp),
            blackBackground = sp.getBoolean("black", d.blackBackground),
            idleVisible = sp.getBoolean("idleVisible", d.idleVisible),
            playful = sp.getBoolean("playful", d.playful),
            haptics = sp.getBoolean("haptics", d.haptics),
            media = sp.getBoolean("media", d.media),
            notifications = sp.getBoolean("notifications", d.notifications),
            timers = sp.getBoolean("timers", d.timers),
            downloads = sp.getBoolean("downloads", d.downloads),
            charging = sp.getBoolean("charging", d.charging),
            doubleTapQuick = sp.getBoolean("doubleTapQuick", d.doubleTapQuick),
            notificationSeconds = sp.getInt("notificationSeconds", d.notificationSeconds),
            hideInLandscape = sp.getBoolean("hideInLandscape", d.hideInLandscape),
            shortcuts = sp.getString("shortcuts", "")!!.split('|').filter { it.isNotBlank() },
        )
    }

    fun update(block: (Settings) -> Settings) {
        val n = block(_state.value)
        _state.value = n
        sp.edit()
            .putFloat("scale", n.scale)
            .putFloat("offsetY", n.offsetYdp)
            .putFloat("offsetX", n.offsetXdp)
            .putBoolean("black", n.blackBackground)
            .putBoolean("idleVisible", n.idleVisible)
            .putBoolean("playful", n.playful)
            .putBoolean("haptics", n.haptics)
            .putBoolean("media", n.media)
            .putBoolean("notifications", n.notifications)
            .putBoolean("timers", n.timers)
            .putBoolean("downloads", n.downloads)
            .putBoolean("charging", n.charging)
            .putBoolean("doubleTapQuick", n.doubleTapQuick)
            .putInt("notificationSeconds", n.notificationSeconds)
            .putBoolean("hideInLandscape", n.hideInLandscape)
            .putString("shortcuts", n.shortcuts.joinToString("|"))
            .apply()
    }
}
