package com.example.buzz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Keeps a streaming connection to ntfy.sh open and shows a notification
 * whenever the other phone sends a buzz.
 */
class ListenerService : Service() {

    @Volatile private var running = false

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Background listener", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_BUZZ, "Buzzes", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ongoing = Notification.Builder(this, CH_SERVICE)
            .setContentTitle("Listening for buzzes")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, ongoing)
        }

        if (!running) {
            running = true
            thread { listenLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun listenLoop() {
        val prefs = Prefs.get(this)
        val me = Prefs.deviceId(this)

        while (running) {
            val topic = prefs.getString("topic", null) ?: break
            try {
                // "since" replays anything we missed while disconnected
                val since = prefs.getString("lastId", null)
                val url = "https://ntfy.sh/$topic/json" + (if (since != null) "?since=$since" else "")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 90000 // ntfy sends keepalives every ~45s

                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!running) break
                        val j = JSONObject(line)
                        if (j.optString("event") != "message") continue
                        prefs.edit().putString("lastId", j.getString("id")).apply()

                        val tags = j.optJSONArray("tags")
                        val fromMe = tags != null && (0 until tags.length()).any { tags.getString(it) == me }
                        if (!fromMe) showBuzz(j.optString("title", "Update available"), j.optString("message"))
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // network dropped – fall through and reconnect
            }
            try { Thread.sleep(5000) } catch (_: InterruptedException) { }
        }
    }

    private fun showBuzz(title: String, text: String) {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH_BUZZ)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), n)
    }

    companion object {
        const val CH_SERVICE = "service"
        const val CH_BUZZ = "buzz"
    }
}
