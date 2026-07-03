package com.myapp.velocy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

// Activity untuk proses login pengguna
class LoginActivity : AppCompatActivity() {

    // Firebase Authentication
    private lateinit var auth: FirebaseAuth

    // Status apakah password sedang terlihat atau disembunyikan
    private var isPasswordVisible = false

    // Meminta izin notifikasi untuk Android 13 ke atas
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Setelah izin dijawab, service dijalankan dan masuk ke halaman utama
            startServiceAndGoToMain()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inisialisasi Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Saat di halaman login, user dianggap logout
        SessionManager.markLoggedOut(this)

        // Matikan service monitoring agar tidak aktif sebelum login
        AlertServiceStarter.stop(this)

        // Menghubungkan activity dengan layout XML
        setContentView(R.layout.activity_login)

        // Menghubungkan komponen layout dengan variabel
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnTogglePassword = findViewById<ImageView>(R.id.btnTogglePassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        // Password disembunyikan secara default
        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        btnTogglePassword.setImageResource(R.drawable.ic_eye_off)

        // Tombol untuk menampilkan atau menyembunyikan password
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible

            if (isPasswordVisible) {
                // Menampilkan password
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye)
                etPassword.contentDescription = "Kata sandi ditampilkan"
            } else {
                // Menyembunyikan password
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye_off)
                etPassword.contentDescription = "Kata sandi disembunyikan"
            }

            // Kursor tetap berada di akhir teks
            etPassword.setSelection(etPassword.text.length)
        }

        // Tombol login
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validasi input kosong
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    "Email dan kata sandi wajib diisi",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Nonaktifkan tombol saat proses login
            btnLogin.isEnabled = false
            btnLogin.text = "Memproses..."

            // Login menggunakan Firebase Authentication
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    // Tandai user sudah login
                    SessionManager.markLoggedIn(this)

                    Toast.makeText(
                        this,
                        "Berhasil masuk",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Minta izin notifikasi jika diperlukan
                    requestNotificationPermissionIfNeeded()
                }
                .addOnFailureListener { e ->
                    // Jika login gagal, user tetap dianggap logout
                    SessionManager.markLoggedOut(this)

                    // Pastikan service monitoring berhenti
                    AlertServiceStarter.stop(this)

                    // Aktifkan kembali tombol login
                    btnLogin.isEnabled = true
                    btnLogin.text = "Masuk"

                    Toast.makeText(
                        this,
                        "Login gagal: Email atau Password salah",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        // Pindah ke halaman register
        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // Mengecek dan meminta izin notifikasi jika Android 13 ke atas
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            // Jika izin belum diberikan, minta izin terlebih dahulu
            if (!isGranted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // Jika izin sudah ada atau Android di bawah 13, langsung lanjut
        startServiceAndGoToMain()
    }

    // Menjalankan service monitoring lalu masuk ke MainActivity
    private fun startServiceAndGoToMain() {
        // Cek apakah sesi login valid
        if (!SessionManager.isLoggedIn(this)) {
            Toast.makeText(
                this,
                "Sesi login tidak valid. Silakan masuk ulang.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Jalankan service monitoring sensor
        AlertServiceStarter.start(this)

        // Pindah ke halaman utama
        goToMain()
    }

    // Pindah ke MainActivity dan menghapus stack halaman sebelumnya
    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)

        // Supaya user tidak bisa kembali ke halaman login dengan tombol back
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }
}