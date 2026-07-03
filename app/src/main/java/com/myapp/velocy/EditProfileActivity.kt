package com.myapp.velocy
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

// Activity untuk mengubah email dan password pengguna
class EditProfileActivity : AppCompatActivity() {

    // Komponen input email dan password
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    // Tombol untuk menampilkan atau menyembunyikan password
    private lateinit var btnTogglePassword: ImageView

    // Tombol kembali
    private lateinit var btnBack: View

    // Firebase Authentication
    private lateinit var auth: FirebaseAuth

    // Status apakah password sedang terlihat atau disembunyikan
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cek apakah user sudah login
        if (SessionManager.redirectToLoginIfNeeded(this)) return

        // Menghubungkan activity dengan layout XML
        setContentView(R.layout.activity_edit_profile)

        // Menghubungkan komponen layout dengan variabel
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        btnBack = findViewById(R.id.btnBack)

        // Inisialisasi Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Mengambil data user dari Firebase Auth
        loadUserData()

        // Tombol untuk show/hide password
        btnTogglePassword.setOnClickListener {
            togglePasswordVisibility()
        }

        // Tombol simpan perubahan profil
        findViewById<Button>(R.id.btnSimpan).setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validasi email kosong
            if (email.isEmpty()) {
                Toast.makeText(
                    this,
                    "Email tidak boleh kosong",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Validasi panjang password
            if (password.length < 6) {
                Toast.makeText(
                    this,
                    "Kata sandi minimal 6 karakter",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Update email dan password
            updateProfile(email, password)
        }

        // Tombol batal perubahan
        findViewById<Button>(R.id.btnBatal).setOnClickListener {
            showBatalDialog()
        }

        // Tombol back juga menampilkan dialog batal
        btnBack.setOnClickListener {
            showBatalDialog()
        }
    }

    // Mengambil data user dari Firebase Auth
    private fun loadUserData() {
        val user = auth.currentUser

        // Jika user kosong, user dianggap belum login
        if (user == null) {
            Toast.makeText(
                this,
                "Pengguna belum masuk",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        // Mengambil email dari Firebase Auth
        val email = user.email ?: ""

        // Menampilkan data ke input
        etEmail.setText(email)
        etPassword.setText("") // Password tidak ditampilkan demi keamanan

        // Password disembunyikan secara default
        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        btnTogglePassword.setImageResource(R.drawable.ic_eye_off)
    }

    // Menampilkan atau menyembunyikan password
    private fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible

        if (passwordVisible) {
            // Menampilkan password
            etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            btnTogglePassword.setImageResource(R.drawable.ic_eye)
        } else {
            // Menyembunyikan password
            etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            btnTogglePassword.setImageResource(R.drawable.ic_eye_off)
        }

        // Kursor tetap berada di akhir teks
        etPassword.setSelection(etPassword.text.length)
    }

    // Mengubah email dan password user
    private fun updateProfile(email: String, password: String) {
        val user = auth.currentUser

        // Cek apakah user masih login
        if (user == null) {
            Toast.makeText(
                this,
                "Pengguna belum masuk",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Update email di Firebase Authentication
        user.updateEmail(email)
            .addOnSuccessListener {

                // Update password di Firebase Authentication
                user.updatePassword(password)
                    .addOnSuccessListener {

                        // Jika semua berhasil
                        auth.signOut()
                        SessionManager.markLoggedOut(this)

                        Toast.makeText(
                            this,
                            "Profil berhasil diperbarui. Silakan login kembali.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Arahkan ke LoginActivity dan hapus tumpukan activity
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            this,
                            "Gagal memperbarui kata sandi: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Gagal memperbarui email: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // Dialog konfirmasi saat user batal mengedit profil
    private fun showBatalDialog() {
        AlertDialog.Builder(this)
            .setTitle("Batalkan perubahan?")
            .setMessage("Perubahan tidak akan disimpan")
            .setPositiveButton("Ya") { _, _ ->
                finish()
            }
            .setNegativeButton("Tidak", null)
            .show()
    }
}