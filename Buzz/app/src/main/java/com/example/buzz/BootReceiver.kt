package com.example.buzz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the listener after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.get(context).getString("topic", null) != null
        ) {
            context.startForegroundService(Intent(context, ListenerService::class.java))
        }
    }
}
