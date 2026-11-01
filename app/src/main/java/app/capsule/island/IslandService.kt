package app.capsule.island

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * Bedienungshilfe-Dienst: wird nur benutzt, um die Island über der Statusleiste
 * und dem Kameraloch zu zeichnen. Es werden keine Bildschirminhalte gelesen.
 */
class IslandService : AccessibilityService() {

    private var overlay: OverlayController? = null
    private var receiverRegistered = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!Prefs.current.charging) return
            val charging = intent.action == Intent.ACTION_POWER_CONNECTED
            val level = SystemControls.batteryLevel()
            IslandStore.showTransient(ChargingEvent(level = level, charging = charging))
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Prefs.init(this)
        SystemControls.init(this)
        overlay?.detach()
        overlay = OverlayController(this).also { it.attach() }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        running = true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        running = false
        overlay?.detach()
        overlay = null
        if (receiverRegistered) {
            try {
                unregisterReceiver(powerReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }

    companion object {
        @Volatile
        var running = false
    }
}
