package com.myapp.velocy

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.HideReturnsTransformationMethod
import android.text.method.LinkMovementMethod
import android.text.method.PasswordTransformationMethod
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

// Activity untuk proses pendaftaran akun baru
class RegisterActivity : AppCompatActivity() {

    // Firebase Authentication untuk membuat akun
    private lateinit var auth: FirebaseAuth

    // Status tampil/sembunyi password
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Menghubungkan activity dengan layout XML
        setContentView(R.layout.activity_register)

        // Inisialisasi Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Menghubungkan komponen layout dengan variabel
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnTogglePassword = findViewById<ImageView>(R.id.btnTogglePassword)
        val btnToggleConfirm = findViewById<ImageView>(R.id.btnToggleConfirm)
        val btnDaftar = findViewById<Button>(R.id.btnDaftar)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        val btnBack = findViewById<android.view.View>(R.id.btnBack)

        // Tombol kembali ke halaman sebelumnya
        btnBack.setOnClickListener { finish() }

        // Password disembunyikan secara default
        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        btnTogglePassword.setImageResource(R.drawable.ic_eye_off)
        btnToggleConfirm.setImageResource(R.drawable.ic_eye_off)

        // Tombol tampil/sembunyi password
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            setPasswordVisibility(etPassword, btnTogglePassword, isPasswordVisible)
        }

        // Tombol tampil/sembunyi konfirmasi password
        btnToggleConfirm.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            setPasswordVisibility(etConfirmPassword, btnToggleConfirm, isConfirmPasswordVisible)
        }

        // Membuat teks "Masuk" berwarna biru
        val spannable = SpannableString("Masuk")
        val start = spannable.indexOf("Masuk")

        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#2563EB")),
            start,
            spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Menampilkan teks login
        tvLogin.text = spannable
        tvLogin.movementMethod = LinkMovementMethod.getInstance()

        // Tombol daftar akun
        btnDaftar.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            // Validasi input kosong
            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Semua data harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validasi panjang password
            if (password.length < 6) {
                Toast.makeText(this, "Kata sandi minimal 6 karakter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validasi password dan konfirmasi password
            if (password != confirmPassword) {
                Toast.makeText(this, "Kata sandi tidak sama", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Membuat akun baru di Firebase Authentication
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    // Jika pendaftaran berhasil
                    if (task.isSuccessful) {

                        // Setelah daftar, user diarahkan untuk login ulang
                        SessionManager.markLoggedOut(this)

                        Toast.makeText(
                            this,
                            "Akun berhasil dibuat, silakan masuk",
                            Toast.LENGTH_SHORT
                        ).show()

                        finish()

                    } else {

                        // Jika pendaftaran gagal
                        Toast.makeText(
                            this,
                            task.exception?.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        // Kembali ke halaman login
        tvLogin.setOnClickListener { finish() }
    }

    // Mengatur password agar bisa ditampilkan atau disembunyikan
    private fun setPasswordVisibility(
        editText: EditText,
        toggleButton: ImageView,
        visible: Boolean
    ) {
        if (visible) {
            // Menampilkan password
            editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
            toggleButton.setImageResource(R.drawable.ic_eye)
        } else {
            // Menyembunyikan password
            editText.transformationMethod = PasswordTransformationMethod.getInstance()
            toggleButton.setImageResource(R.drawable.ic_eye_off)
        }

        // Kursor tetap di akhir teks
        editText.setSelection(editText.text.length)
    }
}