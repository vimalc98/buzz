package com.example.buzz

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val prefs = Prefs.get(this)

        val topicInput = EditText(this).apply {
            hint = "Secret topic (same on both phones)"
            setText(prefs.getString("topic", ""))
        }
        val saveBtn = Button(this).apply { text = "Save & start listening" }
        val sendBtn = Button(this).apply {
            text = "🔔  Notify the other phone"
            textSize = 22f
        }
        val status = TextView(this).apply { textSize = 16f }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(48, 48, 48, 48)
            addView(topicInput)
            addView(saveBtn)
            addView(TextView(this@MainActivity).apply { text = "\n" })
            addView(sendBtn)
            addView(status)
        })

        saveBtn.setOnClickListener {
            val t = topicInput.text.toString().trim()
            if (t.length < 12 || !t.matches(Regex("[A-Za-z0-9_-]+"))) {
                Toast.makeText(this, "Use 12+ letters/numbers, e.g. buzz_k8f3mq92xz", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.edit().putString("topic", t).remove("lastId").apply()
            askToIgnoreBatteryOptimizations()
            startListener()
            status.text = "Listening ✓"
        }

        sendBtn.setOnClickListener {
            val topic = prefs.getString("topic", null)
            if (topic == null) {
                status.text = "Save a topic first"
                return@setOnClickListener
            }
            status.text = "Sending…"
            thread {
                val result = try {
                    val conn = URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.setRequestProperty("Title", "Update available")
                    conn.setRequestProperty("Tags", Prefs.deviceId(this))
                    conn.outputStream.use { it.write("A new version is ready".toByteArray()) }
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code == 200) "Sent ✓" else "Error: HTTP $code"
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                runOnUiThread { status.text = result }
            }
        }

        if (prefs.getString("topic", null) != null) {
            startListener()
            status.text = "Listening ✓"
        }
    }

    private fun startListener() {
        startForegroundService(Intent(this, ListenerService::class.java))
    }

    /** Without this, many phones cut the connection when the screen is off. */
    private fun askToIgnoreBatteryOptimizations() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) { }
        }
    }
}
