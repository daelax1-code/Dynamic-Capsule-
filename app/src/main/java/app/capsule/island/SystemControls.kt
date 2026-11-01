package app.capsule.island

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings as SysSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow
import kotlin.math.roundToInt

/** Taschenlampe, Helligkeit, Lautstärke, Haptik und das Öffnen von Apps. */
object SystemControls {
    private lateinit var app: Context
    private var cameraManager: CameraManager? = null
    private var torchId: String? = null
    private var torchMaxLevel = 1

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn

    private val _torchLevel = MutableStateFlow(1f)
    val torchLevel: StateFlow<Float> = _torchLevel

    val torchAdjustable: Boolean get() = torchMaxLevel > 1 && Build.VERSION.SDK_INT >= 33

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        try {
            val cm = app.getSystemService(CameraManager::class.java)
            cameraManager = cm
            for (id in cm.cameraIdList) {
                val ch = cm.getCameraCharacteristics(id)
                val hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val back = ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                if (hasFlash && back) {
                    torchId = id
                    if (Build.VERSION.SDK_INT >= 33) {
                        torchMaxLevel = ch.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    }
                    break
                }
            }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) _torchOn.value = enabled
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }
    }

    fun toggleTorch() = setTorch(!_torchOn.value)

    fun setTorch(on: Boolean) {
        val id = torchId ?: return
        try {
            if (on && torchAdjustable && Build.VERSION.SDK_INT >= 33) {
                val lvl = (1 + (_torchLevel.value * (torchMaxLevel - 1))).roundToInt().coerceIn(1, torchMaxLevel)
                cameraManager?.turnOnTorchWithStrengthLevel(id, lvl)
            } else {
                cameraManager?.setTorchMode(id, on)
            }
        } catch (_: Exception) {
        }
    }

    fun setTorchLevel(fraction: Float) {
        _torchLevel.value = fraction.coerceIn(0f, 1f)
        if (_torchOn.value) setTorch(true)
    }

    // ---------- Helligkeit ----------
    fun canWriteSettings(): Boolean = SysSettings.System.canWrite(app)

    fun brightness(): Float = try {
        val v = SysSettings.System.getInt(app.contentResolver, SysSettings.System.SCREEN_BRIGHTNESS)
        (v / 255f).coerceIn(0f, 1f).pow(1f / 2.2f)
    } catch (_: Exception) {
        0.5f
    }

    fun setBrightness(fraction: Float) {
        if (!canWriteSettings()) return
        try {
            SysSettings.System.putInt(
                app.contentResolver,
                SysSettings.System.SCREEN_BRIGHTNESS_MODE,
                SysSettings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val v = (fraction.coerceIn(0f, 1f).pow(2.2f) * 255).roundToInt().coerceIn(1, 255)
            SysSettings.System.putInt(app.contentResolver, SysSettings.System.SCREEN_BRIGHTNESS, v)
        } catch (_: Exception) {
        }
    }

    // ---------- Lautstärke ----------
    private val audio: AudioManager get() = app.getSystemService(AudioManager::class.java)

    fun volume(): Float {
        val a = audio
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return a.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(fraction: Float) {
        try {
            val a = audio
            val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            a.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).roundToInt(), 0)
        } catch (_: Exception) {
        }
    }

    // ---------- Akku ----------
    fun batteryLevel(): Int = try {
        app.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: Exception) {
        0
    }

    fun isCharging(): Boolean = try {
        app.getSystemService(BatteryManager::class.java).isCharging
    } catch (_: Exception) {
        false
    }

    // ---------- Haptik ----------
    fun tick(strong: Boolean = false) {
        if (!Prefs.current.haptics) return
        try {
            val v: Vibrator = app.getSystemService(VibratorManager::class.java).defaultVibrator
            val effect = if (strong) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            else VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            v.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    // ---------- Apps & Intents ----------
    private fun activityOptions(): Bundle {
        val o = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION")
            o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        return o.toBundle()
    }

    fun send(pi: PendingIntent?): Boolean {
        if (pi == null) return false
        return try {
            pi.send(app, 0, null, null, null, null, activityOptions())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun reply(action: IslandAction, text: String): Boolean {
        val inputs = action.remoteInputs ?: return false
        val pi = action.intent ?: return false
        return try {
            val intent = Intent()
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(inputs, intent, results)
            pi.send(app, 0, intent, null, null, null, activityOptions())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchApp(pkg: String) {
        try {
            val i = app.packageManager.getLaunchIntentForPackage(pkg) ?: return
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            app.startActivity(i, activityOptions())
        } catch (_: Exception) {
        }
    }

    fun openSettingsApp() {
        try {
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                activityOptions()
            )
        } catch (_: Exception) {
        }
    }

    fun openWriteSettingsPermission() {
        try {
            app.startActivity(
                Intent(SysSettings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + app.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                activityOptions()
            )
        } catch (_: Exception) {
        }
    }

    /** Medien-App öffnen (oder die App-Startseite, falls keine Sitzungs-Aktivität existiert). */
    fun openMediaApp(pkg: String) {
        val pi = IslandStore.mediaController?.sessionActivity
        if (!send(pi)) launchApp(pkg)
    }
}
