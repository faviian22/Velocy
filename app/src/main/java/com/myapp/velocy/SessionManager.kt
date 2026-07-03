package com.myapp.velocy

import android.content.Context
import android.content.Intent

// Object untuk mengatur status sesi login pengguna
object SessionManager {

    // Nama file SharedPreferences
    private const val PREF_NAME = "user_session"

    // Key untuk menyimpan status login
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    // Menandai user sudah login
    fun markLoggedIn(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    // Menandai user sudah logout
    fun markLoggedOut(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
    }

    // Mengecek apakah user sedang login
    fun isLoggedIn(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // Mengarahkan user ke LoginActivity jika belum login
    fun redirectToLoginIfNeeded(context: Context): Boolean {
        if (!isLoggedIn(context)) {

            // Hentikan service monitoring jika user belum login
            AlertServiceStarter.stop(context)

            // Intent untuk membuka halaman login
            val intent = Intent(context, LoginActivity::class.java)

            // Menghapus semua activity sebelumnya dari stack
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // Pindah ke halaman login
            context.startActivity(intent)

            // Return true berarti user dialihkan ke login
            return true
        }

        // Return false berarti user masih login
        return false
    }
}