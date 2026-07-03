package com.myapp.velocy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

// Activity untuk menampilkan profil pengguna
class ProfileActivity : AppCompatActivity() {

    // TextView untuk menampilkan email user
    private lateinit var tvEmailDisplay: TextView

    // EditText untuk menampilkan email dan password
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    // Firebase Authentication
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cek apakah user sudah login
        if (SessionManager.redirectToLoginIfNeeded(this)) return

        // Menghubungkan activity dengan layout XML
        setContentView(R.layout.activity_profile)

        // Menghubungkan komponen layout dengan variabel
        tvEmailDisplay = findViewById(R.id.tvEmailDisplay)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)

        // Inisialisasi Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Email dan password hanya ditampilkan, tidak bisa diedit di halaman ini
        etEmail.isEnabled = false
        etPassword.isEnabled = false

        // Mengambil data user dari Firebase
        loadUserData()

        // Tombol untuk masuk ke halaman edit profil
        findViewById<Button>(R.id.btnEdit).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // Tombol logout
        findViewById<Button>(R.id.btnLogout).setOnClickListener {

            // Menghentikan service monitoring sensor
            stopService(Intent(this, AlertMonitorService::class.java))

            // Logout dari Firebase Auth
            auth.signOut()

            // Tandai sesi user sebagai logout
            SessionManager.markLoggedOut(this)

            // Pastikan service benar-benar berhenti
            AlertServiceStarter.stop(this)

            // Pindah ke halaman login dan hapus riwayat activity
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // Tombol navigasi ke halaman lokasi
        findViewById<android.view.View>(R.id.btnLokasi).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Tombol navigasi ke halaman histori
        findViewById<android.view.View>(R.id.btnHistori).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            finish()
        }

        // Tombol profil tidak melakukan apa-apa karena sudah di halaman profil
        findViewById<android.view.View>(R.id.btnProfil).setOnClickListener {
            // Sudah di halaman profil
        }
    }

    override fun onResume() {
        super.onResume()

        // Memuat ulang data saat kembali dari halaman edit profil
        loadUserData()
    }

    // Mengambil data user dari Firebase Auth
    private fun loadUserData() {

        // Mengambil user yang sedang login
        val currentUser = auth.currentUser

        // Jika tidak ada user, arahkan kembali ke login
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Memuat ulang data user dari Firebase Auth
        currentUser.reload()

        // Mengambil email dari Firebase Auth
        val email = currentUser.email ?: ""

        // Menampilkan email ke layout
        tvEmailDisplay.text = email
        etEmail.setText(email)

        // Password tidak lagi disimpan di database untuk keamanan
        etPassword.setText("******")
    }
}