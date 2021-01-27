package com.odukle.toastlyrics

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.odukle.toastlyrics.ui.home.IS_SERVICE_STOPPED

private const val TAG = "StopService"

class StopService : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate: called")

        setContentView(R.layout.activity_stop_service)
        val sharedPref = getSharedPreferences("pref", Context.MODE_PRIVATE)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        sharedPref.edit().putBoolean(IS_SERVICE_STOPPED, true).apply()
        MusicNotificationListener.terminateService()
        notificationManager.cancel(1)
        finishAffinity()
    }
}