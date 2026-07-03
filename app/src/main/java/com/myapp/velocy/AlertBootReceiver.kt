package com.myapp.velocy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Receiver untuk mendeteksi saat perangkat selesai boot
class AlertBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {

        // Ambil aksi dari intent
        val action = intent?.action ?: return

        // Cek apakah perangkat baru selesai boot atau aplikasi diperbarui
        val shouldStart = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON"

        // Jalankan service jika pengguna sudah login
        if (shouldStart && SessionManager.isLoggedIn(context)) {
            AlertServiceStarter.start(context)
        }
    }
}