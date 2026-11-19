package com.titan.ringlight

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/** Связывает слушатель уведомлений, службу кольца и экран настроек. */
object RingBus {
    interface Listener {
        fun onColors(colors: List<Int>, newArrival: Boolean)
        fun onSettingsChanged()
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()

    @Volatile var colors: List<Int> = emptyList(); private set
    @Volatile var testColors: List<Int>? = null; private set

    private val endTest = Runnable { testColors = null; dispatch(false) }

    fun add(l: Listener) { listeners.add(l) }
    fun remove(l: Listener) { listeners.remove(l) }

    fun current(): List<Int> = testColors ?: colors

    fun publish(c: List<Int>, newArrival: Boolean) {
        colors = c
        dispatch(newArrival)
    }

    fun test(c: List<Int>, ms: Long = 6000) {
        main.removeCallbacks(endTest)
        testColors = c
        dispatch(false)
        main.postDelayed(endTest, ms)
    }

    fun settingsChanged() {
        main.post { listeners.forEach { it.onSettingsChanged() } }
    }

    private fun dispatch(newArrival: Boolean) {
        main.post {
            val c = current()
            listeners.forEach { it.onColors(c, newArrival) }
        }
    }
}
