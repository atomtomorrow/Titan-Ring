package com.titan.ringlight

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Служба спец. возможностей нужна ради окна TYPE_ACCESSIBILITY_OVERLAY:
 * оно рисуется поверх всех приложений, строки состояния и экрана блокировки.
 */
class RingService : AccessibilityService(), RingBus.Listener {

    private var wm: WindowManager? = null
    private var view: RingView? = null
    private val lp = WindowManager.LayoutParams()

    private var cameraManager: CameraManager? = null
    private val frontIds = HashSet<String>()
    private val busyIds = HashSet<String>()
    private var cameraBusy = false

    private val camCb = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(id: String) {
            if (id in frontIds) { busyIds.add(id); updateCam() }
        }
        override fun onCameraAvailable(id: String) {
            if (busyIds.remove(id)) updateCam()
        }
    }

    override fun onServiceConnected() {
        val w = getSystemService(WindowManager::class.java) ?: return
        wm = w
        val v = RingView(this)
        view = v
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        lp.format = PixelFormat.TRANSLUCENT
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        lp.gravity = Gravity.TOP or Gravity.START
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        lp.fitInsetsTypes = 0
        applyGeometry()
        try {
            w.addView(v, lp)
        } catch (e: Exception) {
            view = null
            return
        }

        // Прячем кольцо, когда работает фронтальная камера (чтобы не засвечивать селфи)
        try {
            val cm = getSystemService(CameraManager::class.java)
            cameraManager = cm
            cm?.cameraIdList?.forEach { id ->
                val facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) frontIds.add(id)
            }
            cm?.registerAvailabilityCallback(camCb, Handler(Looper.getMainLooper()))
        } catch (_: Exception) { }

        RingBus.add(this)
        NotifListener.refresh()
        onColors(RingBus.current(), false)
    }

    private fun applyGeometry() {
        val v = view ?: return
        val d = resources.displayMetrics.density
        val thick = Prefs.int(this, Prefs.K_THICK, 3) * d
        val gap = Prefs.int(this, Prefs.K_GAP, 2) * d
        val g = Geometry.cutout(this)
        val ringR = g.r + gap + thick / 2f
        val outer = ringR + thick * 1.4f + 2 * d
        val size = ceil(outer * 2).toInt()
        lp.width = size
        lp.height = size
        lp.x = (g.cx - size / 2f).roundToInt()
        lp.y = (g.cy - size / 2f).roundToInt()
        v.radius = ringR
        v.thickness = thick
        v.pulse = Prefs.bool(this, Prefs.K_PULSE, true)
        v.segments = Prefs.bool(this, Prefs.K_SEGMENTS, false)
        v.maxAlpha = Prefs.int(this, Prefs.K_ALPHA, 100) / 100f
        if (v.isAttachedToWindow) {
            try { wm?.updateViewLayout(v, lp) } catch (_: Exception) { }
        }
        v.kick()
    }

    override fun onColors(colors: List<Int>, newArrival: Boolean) {
        val v = view ?: return
        val testing = RingBus.testColors != null
        val show = colors.isNotEmpty() &&
            (testing || (Prefs.bool(this, Prefs.K_ENABLED, true) && !cameraBusy))
        v.visibility = if (show) View.VISIBLE else View.GONE
        v.colors = if (show) colors else emptyList()

        if (newArrival && show && !testing && Prefs.bool(this, Prefs.K_SCREEN_OFF, false)) {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isInteractive) {
                try {
                    startActivity(
                        Intent(this, NightActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    )
                } catch (_: Exception) { }
            }
        }
    }

    override fun onSettingsChanged() {
        applyGeometry()
        onColors(RingBus.current(), false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyGeometry() // поворот экрана
    }

    private fun updateCam() {
        cameraBusy = busyIds.isNotEmpty()
        onColors(RingBus.current(), false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        RingBus.remove(this)
        try { cameraManager?.unregisterAvailabilityCallback(camCb) } catch (_: Exception) { }
        view?.let { try { wm?.removeViewImmediate(it) } catch (_: Exception) { } }
        view = null
        super.onDestroy()
    }
}
