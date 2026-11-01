package app.capsule.island

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.capsule.island.ui.IslandRoot

/** Bildschirm- und Kamera-Geometrie in Pixeln. */
data class ScreenGeometry(
    val screenWidth: Int,
    val screenHeight: Int,
    val cameraCenterX: Int,
    val cameraCenterY: Int,
    val cameraSize: Int,
    val statusBarHeight: Int,
    val landscape: Boolean,
)

/** Lifecycle für eine Compose-Ansicht außerhalb einer Activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/** Fängt Berührungen außerhalb des Fensters ab, damit die Island zuklappt. */
private class TouchFrame(context: Context) : FrameLayout(context) {
    var onOutsideTouch: (() -> Unit)? = null
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}

class OverlayController(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private var root: TouchFrame? = null
    private lateinit var params: WindowManager.LayoutParams

    val geometry = mutableStateOf(measure())
    /** Wird hochgezählt bei jeder Berührung außerhalb (die UI klappt dann zu). */
    val outsideTouches = mutableStateOf(0)

    fun attach() {
        if (root != null) return
        owner.start()
        params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "CapsuleIsland"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            x = 0
            y = 0
        }

        val frame = TouchFrame(context)
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.onOutsideTouch = { outsideTouches.value++ }

        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                IslandRoot(
                    geometry = geometry.value,
                    outsideTouches = outsideTouches.value,
                    onWindowChange = ::updateWindow,
                )
            }
        }
        frame.addView(
            compose,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        try {
            wm.addView(frame, params)
            root = frame
        } catch (_: Exception) {
            owner.stop()
        }
    }

    fun detach() {
        root?.let {
            try {
                wm.removeViewImmediate(it)
            } catch (_: Exception) {
            }
        }
        root = null
        owner.stop()
    }

    fun onConfigurationChanged() {
        geometry.value = measure()
    }

    /**
     * Passt das Fenster an die Island an, damit der Rest der Statusleiste bedienbar bleibt.
     * [offsetX] verschiebt das Fenster horizontal (relativ zur Bildschirmmitte).
     */
    private fun updateWindow(width: Int, height: Int, offsetX: Int, focusable: Boolean) {
        val r = root ?: return
        val flags = if (focusable) params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (params.width == width && params.height == height && params.x == offsetX && params.flags == flags) return
        params.width = width.coerceAtLeast(1)
        params.height = height.coerceAtLeast(1)
        params.x = offsetX
        params.flags = flags
        params.softInputMode = if (focusable)
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        try {
            wm.updateViewLayout(r, params)
        } catch (_: Exception) {
        }
    }

    private fun measure(): ScreenGeometry {
        val metrics = wm.currentWindowMetrics
        val bounds: Rect = metrics.bounds
        val insets = metrics.windowInsets
        val density = context.resources.displayMetrics.density
        val status = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
        val cutout = insets.displayCutout
        val rect: Rect? = cutout?.boundingRectTop?.takeIf { !it.isEmpty }
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val fallbackSize = (26 * density).toInt()
        return if (rect != null && rect.width() < bounds.width() / 3) {
            ScreenGeometry(
                screenWidth = bounds.width(),
                screenHeight = bounds.height(),
                cameraCenterX = rect.centerX(),
                cameraCenterY = rect.centerY(),
                cameraSize = maxOf(rect.width(), rect.height()),
                statusBarHeight = status,
                landscape = landscape,
            )
        } else {
            ScreenGeometry(
                screenWidth = bounds.width(),
                screenHeight = bounds.height(),
                cameraCenterX = bounds.width() / 2,
                cameraCenterY = if (status > 0) status / 2 else (16 * density).toInt(),
                cameraSize = fallbackSize,
                statusBarHeight = status,
                landscape = landscape,
            )
        }
    }
}
