package com.myapp.velocy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// Activity utama untuk menampilkan lokasi kendaraan pada Google Maps
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // Objek Google Maps
    private lateinit var mMap: GoogleMap

    // Referensi Firebase Realtime Database
    private lateinit var database: DatabaseReference

    // Marker lokasi kendaraan di peta
    private var marker: Marker? = null

    // Tombol navigasi dan tombol center map
    private lateinit var btnLokasi: View
    private lateinit var btnHistori: View
    private lateinit var btnProfil: View
    private lateinit var btnCenterMap: View

    // TextView status kendaraan dan kecepatan
    private lateinit var tvStatusKendaraan: TextView
    private lateinit var tvSpeed: TextView

    // Badge status live atau offline
    private lateinit var badgeStatusView: View
    private var badgeStatusText: TextView? = null

    // Waktu terakhir data GPS diterima
    private var lastUpdateTime: Long = 0L

    // Handler untuk mengecek status GPS secara berkala
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        // Request code untuk izin lokasi
        private const val LOCATION_PERMISSION_REQUEST = 100

        // Request code untuk izin notifikasi
        private const val NOTIFICATION_PERMISSION_REQUEST = 200

        // Batas waktu kendaraan dianggap offline
        private const val TIMEOUT_OFFLINE = 3000L

        // Kecepatan di bawah 2.5 km/jam dianggap noise dan ditampilkan 0
        private const val SPEED_NOISE_THRESHOLD_KMH = 2.5
    }

    // Runnable untuk mengecek apakah kendaraan masih online
    private val statusChecker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            // Jika tidak ada update selama batas waktu, status menjadi offline
            if (lastUpdateTime == 0L || now - lastUpdateTime > TIMEOUT_OFFLINE) {
                setOfflineStatus()
            }

            // Jalankan ulang pengecekan setiap 3 detik
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cek apakah user sudah login
        if (SessionManager.redirectToLoginIfNeeded(this)) return

        // Menghubungkan activity dengan layout XML
        setContentView(R.layout.activity_main)

        // Menjalankan service monitoring sensor
        AlertServiceStarter.start(this)

        // Menghubungkan tombol dari layout
        btnLokasi = findViewById(R.id.btnLokasi)
        btnHistori = findViewById(R.id.btnHistori)
        btnProfil = findViewById(R.id.btnProfil)
        btnCenterMap = findViewById(R.id.btnCenterMap)

        // Menghubungkan TextView status dan speed
        tvStatusKendaraan = findViewById(R.id.tvStatusKendaraan)
        tvSpeed = findViewById(R.id.tvSpeed)

        // Menghubungkan badge status
        badgeStatusView = findViewById(R.id.tvBadgeStatus)
        badgeStatusText = findTextViewInside(badgeStatusView)

        // Mengambil data GPS dari node "gps"
        database = FirebaseDatabase.getInstance().getReference("gps")

        // Tombol lokasi me-refresh halaman utama
        btnLokasi.setOnClickListener {
            recreate()
        }

        // Tombol menuju halaman histori
        btnHistori.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Tombol menuju halaman profil
        btnProfil.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Tombol untuk memusatkan kamera ke posisi kendaraan
        btnCenterMap.setOnClickListener {
            val currentPosition = marker?.position

            if (currentPosition != null && ::mMap.isInitialized) {
                mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(currentPosition, 16f)
                )
            } else {
                Toast.makeText(
                    this,
                    "Lokasi kendaraan belum tersedia",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Mengambil fragment Google Maps dari layout
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment

        // Jika map tidak ditemukan, tampilkan status offline
        if (mapFragment == null) {
            Toast.makeText(
                this,
                "Peta tidak ditemukan pada layout activity_main",
                Toast.LENGTH_SHORT
            ).show()

            setOfflineStatus()
        } else {
            // Menyiapkan Google Maps
            mapFragment.getMapAsync(this)
        }

        // Meminta izin notifikasi jika diperlukan
        requestNotificationPermissionIfNeeded()

        // Meminta pengecualian optimasi baterai agar monitoring lebih stabil
        requestIgnoreBatteryOptimizationIfNeeded()

        // Mulai cek status kendaraan secara berkala
        startCheckingStatus()
    }

    // Dipanggil saat Google Maps sudah siap digunakan
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Menampilkan tombol zoom di map
        mMap.uiSettings.isZoomControlsEnabled = true

        // Cek izin lokasi sebelum mengaktifkan lokasi pengguna
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }

        // Mulai membaca data GPS dari Firebase
        listenGPS()
    }

    // Membaca data GPS secara realtime dari Firebase
    private fun listenGPS() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                // Membaca latitude dari beberapa kemungkinan nama field
                val lat = snapshot.child("latitude").value?.toString()?.toDoubleOrNull()
                    ?: snapshot.child("lat").value?.toString()?.toDoubleOrNull()

                // Membaca longitude dari beberapa kemungkinan nama field
                val lng = snapshot.child("longitude").value?.toString()?.toDoubleOrNull()
                    ?: snapshot.child("lng").value?.toString()?.toDoubleOrNull()

                // Jika koordinat kosong, kendaraan dianggap offline
                if (lat == null || lng == null) {
                    setOfflineStatus()
                    return
                }

                // Simpan waktu update terakhir
                lastUpdateTime = System.currentTimeMillis()

                // Ubah status menjadi online
                setOnlineStatus()

                // Membaca kecepatan kendaraan
                val rawSpeedKmh = readSpeedKmh(snapshot) ?: 0.0

                // Jika speed terlalu kecil, dianggap noise
                val displaySpeed = if (rawSpeedKmh < SPEED_NOISE_THRESHOLD_KMH) {
                    0.0
                } else {
                    rawSpeedKmh
                }

                // Menampilkan speed tanpa angka desimal
                tvSpeed.text = "%.0f".format(displaySpeed)

                // Menampilkan status kendaraan
                tvStatusKendaraan.text = "Lokasi kendaraan diperbarui"

                // Membuat objek posisi dari latitude dan longitude
                val posisi = LatLng(lat, lng)

                if (::mMap.isInitialized) {
                    if (marker == null) {
                        // Jika marker belum ada, buat marker baru
                        marker = mMap.addMarker(
                            MarkerOptions().position(posisi).title("Lokasi Kendaraan")
                        )

                        // Arahkan kamera ke posisi kendaraan
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(posisi, 16f))
                    } else {
                        // Jika marker sudah ada, update posisinya
                        marker?.position = posisi

                        // Gerakkan kamera mengikuti kendaraan
                        mMap.animateCamera(CameraUpdateFactory.newLatLng(posisi))
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Jika gagal membaca Firebase, status menjadi offline
                setOfflineStatus()
            }
        })
    }

    // Memulai pengecekan status kendaraan
    private fun startCheckingStatus() {
        handler.removeCallbacks(statusChecker)
        handler.post(statusChecker)
    }

    // Mengubah tampilan status menjadi online
    private fun setOnlineStatus() {
        badgeStatusText?.text = "LIVE"

        try {
            badgeStatusView.setBackgroundResource(R.drawable.bg_badge_live)
        } catch (_: Exception) {
        }
    }

    // Mengubah tampilan status menjadi offline
    private fun setOfflineStatus() {
        badgeStatusText?.text = "Offline"

        try {
            badgeStatusView.setBackgroundResource(R.drawable.bg_badge_red)
        } catch (_: Exception) {
        }

        tvSpeed.text = "0"
        tvStatusKendaraan.text = "GPS tidak aktif"
    }

    // Meminta izin notifikasi untuk Android 13 ke atas
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }

    // Menjalankan service monitoring sensor
    private fun startAlertMonitorService() {
        AlertServiceStarter.start(this)
    }

    // Meminta agar aplikasi tidak dibatasi optimasi baterai
    private fun requestIgnoreBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager

            // Jika aplikasi belum dikecualikan dari optimasi baterai
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }

                startActivity(intent)
            }
        } catch (_: Exception) {
        }
    }

    // Membaca kecepatan dalam km/jam
    private fun readSpeedKmh(snapshot: DataSnapshot): Double? {
        // Jika speed dari Firebase berupa meter per detik, ubah ke km/jam
        val speedMps = readDouble(snapshot, listOf("speed_mps", "speedMps", "gps/speed_mps"))

        if (speedMps != null) return speedMps * 3.6

        // Jika speed sudah berupa km/jam
        return readDouble(
            snapshot,
            listOf("speedKmh", "kecepatanKmh", "kecepatan", "kmh", "speed", "gps/speed")
        )
    }

    // Membaca nilai Double dari beberapa path Firebase
    private fun readDouble(snapshot: DataSnapshot, paths: List<String>): Double? {
        for (path in paths) {
            var current = snapshot

            // Membaca child bertingkat berdasarkan tanda "/"
            for (part in path.split("/")) {
                current = current.child(part)
            }

            val result = when (val value = current.value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }

            if (result != null) return result
        }

        return null
    }

    // Mencari TextView di dalam View atau ViewGroup
    private fun findTextViewInside(view: View): TextView? {
        if (view is TextView) return view

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findTextViewInside(view.getChildAt(i))

                if (result != null) return result
            }
        }

        return null
    }

    // Dipanggil saat activity dihancurkan
    override fun onDestroy() {
        super.onDestroy()

        // Hentikan pengecekan status agar tidak memory leak
        handler.removeCallbacks(statusChecker)
    }

    // Menangani hasil request permission
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Jika izin lokasi diberikan, aktifkan fitur lokasi di map
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (::mMap.isInitialized &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                mMap.isMyLocationEnabled = true
            }
        }
    }
}