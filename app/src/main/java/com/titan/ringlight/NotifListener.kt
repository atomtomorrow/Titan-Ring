package com.titan.ringlight

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {

    companion object {
        @Volatile var instance: NotifListener? = null
        fun refresh() { instance?.recompute(false) }
    }

    private val ranking = Ranking()

    override fun onListenerConnected() {
        instance = this
        recompute(false)
    }

    override fun onListenerDisconnected() {
        instance = null
        RingBus.publish(emptyList(), false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, map: RankingMap?) {
        val rel = relevant(sbn, map)
        if (rel) Prefs.addSeen(this, sbn.packageName)
        recompute(rel && Prefs.colorFor(this, sbn.packageName) != Prefs.OFF)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, map: RankingMap?) {
        recompute(false)
    }

    private fun relevant(sbn: StatusBarNotification, map: RankingMap?): Boolean {
        if (sbn.packageName == packageName) return false
        if (sbn.isOngoing || !sbn.isClearable) return false          // музыка, загрузки, навигация
        val f = sbn.notification.flags
        if (f and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (f and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        if (map != null && map.getRanking(sbn.key, ranking)) {
            if (ranking.importance <= NotificationManager.IMPORTANCE_MIN) return false
        }
        return true
    }

    fun recompute(newArrival: Boolean) {
        val all = try { activeNotifications } catch (e: Exception) { null } ?: return
        val map = try { currentRanking } catch (e: Exception) { null }
        val colors = LinkedHashSet<Int>()
        all.filter { relevant(it, map) }
            .sortedByDescending { it.postTime }
            .forEach { val c = Prefs.colorFor(this, it.packageName); if (c != Prefs.OFF) colors.add(c) }
        RingBus.publish(colors.toList(), newArrival)
    }
}
