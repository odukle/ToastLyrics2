package com.odukle.toastlyrics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.odukle.toastlyrics.ui.home.START_ON_BOOT

private const val TAG = "AutoStart"

class AutoStart : BroadcastReceiver() {
    override fun onReceive(context: Context, arg1: Intent?) {
        when (arg1?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val sharedPref = context.getSharedPreferences("pref", Context.MODE_PRIVATE)
                val sob = sharedPref.getBoolean(START_ON_BOOT, false)

                if (sob) {
                    Log.d(TAG, "onReceive: AutoStart called")
                    val intent = Intent(context, MusicNotificationListener::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            }
        }
    }
}