package com.titan.ringlight

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * «Ночник»: при новом уведомлении на выключенном экране показывает чёрный экран.
 * На AMOLED чёрные пиксели не светятся, видно только кольцо (оно рисуется службой поверх).
 */
class NightActivity : Activity(), RingBus.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val finishR = Runnable { finish() }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        setContentView(View(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { finish() }
        })
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val f = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(screenOff, f)
        handler.postDelayed(finishR, Prefs.int(this, Prefs.K_SCREEN_MIN, 2) * 60_000L)
        RingBus.add(this)
    }

    override fun onColors(colors: List<Int>, newArrival: Boolean) {
        if (colors.isEmpty()) finish()
    }

    override fun onSettingsChanged() {}

    override fun onDestroy() {
        handler.removeCallbacks(finishR)
        RingBus.remove(this)
        try { unregisterReceiver(screenOff) } catch (_: Exception) { }
        super.onDestroy()
    }
}
