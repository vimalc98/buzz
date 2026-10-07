package com.example.buzz

import android.content.Context
import java.util.UUID

object Prefs {
    fun get(c: Context) = c.getSharedPreferences("buzz", Context.MODE_PRIVATE)

    /** Random ID for this phone, so it can ignore its own buzzes. */
    fun deviceId(c: Context): String {
        val p = get(c)
        return p.getString("id", null)
            ?: ("dev" + UUID.randomUUID().toString().replace("-", "").take(12))
                .also { p.edit().putString("id", it).apply() }
    }
}
