package com.myapp.velocy

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

// Class pembantu untuk menjalankan dan menghentikan AlertMonitorService
object AlertServiceStarter {

    private const val TAG = "AlertServiceStarter"

    fun start(context: Context) {
        val appContext = context.applicationContext

        // Jika user belum login, service dihentikan
        if (!SessionManager.isLoggedIn(appContext)) {
            stop(appContext)
            return
        }

        try {
            // Intent untuk menjalankan service monitoring
            val serviceIntent = Intent(appContext, AlertMonitorService::class.java)

            // Android Oreo ke atas wajib memakai startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }

        } catch (e: Exception) {
            // Menampilkan error di Logcat agar mudah dicek
            Log.e(TAG, "Gagal menjalankan AlertMonitorService", e)
        }
    }

    fun stop(context: Context) {
        try {
            val appContext = context.applicationContext

            // Menghentikan service monitoring
            appContext.stopService(
                Intent(appContext, AlertMonitorService::class.java)
            )

        } catch (e: Exception) {
            // Menampilkan error jika service gagal dihentikan
            Log.e(TAG, "Gagal menghentikan AlertMonitorService", e)
        }
    }
}