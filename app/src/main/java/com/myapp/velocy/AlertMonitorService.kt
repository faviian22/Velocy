package com.myapp.velocy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// Service untuk memantau sensor kendaraan secara realtime
class AlertMonitorService : Service() {

    // Referensi ke Firebase Realtime Database
    private lateinit var database: DatabaseReference

    // Listener untuk membaca perubahan data Firebase
    private var sensorListener: ValueEventListener? = null

    // Menandai data pertama agar tidak langsung memunculkan notifikasi
    private var initialSnapshotLoaded = false

    // Menyimpan nilai sensor sebelumnya
    private var lastLidarDistance: Double? = null
    private var lastSpeedKmh: Double? = null
    private var lastMpuAz: Double? = null
    private var lastVibration: String? = null

    companion object {
        // ID channel notifikasi
        private const val MONITOR_CHANNEL_ID = "vehicle_monitor_channel_realtime_v1"
        private const val WARNING_CHANNEL_ID = "vehicle_warning_channel_realtime_v1"

        // ID notifikasi
        private const val MONITOR_NOTIFICATION_ID = 100
        private const val SAFE_DISTANCE_NOTIFICATION_ID = 101
        private const val MPU_AZ_NOTIFICATION_ID = 102
        private const val VIBRATION_NOTIFICATION_ID = 103

        // Batas LiDAR 800 = 8 meter
        private const val LIDAR_LIMIT_RAW = 800.0

        // Batas kecepatan minimum untuk peringatan
        private const val SPEED_LIMIT_KMH = 30.0

        // Batas nilai MPU AZ
        private const val MPU_AZ_MIN_LIMIT = 10.0
        private const val MPU_AZ_MAX_LIMIT = 20.0
    }

    override fun onCreate() {
        super.onCreate()

        // Hentikan service jika user belum login
        if (!SessionManager.isLoggedIn(this)) {
            stopSelf()
            return
        }

        // Membuat channel notifikasi
        createNotificationChannels()

        // Menjalankan service sebagai foreground service
        try {
            startForeground(
                MONITOR_NOTIFICATION_ID,
                buildMonitorNotification()
            )
        } catch (_: Exception) {
            stopSelf()
            return
        }

        // Menghubungkan ke Firebase
        database = FirebaseDatabase.getInstance().reference

        // Mulai membaca sensor
        startSensorMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Hentikan service jika user tidak login
        if (!SessionManager.isLoggedIn(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Inisialisasi database jika belum ada
        if (!::database.isInitialized) {
            database = FirebaseDatabase.getInstance().reference
        }

        // Jalankan monitoring jika listener belum aktif
        if (sensorListener == null) {
            startSensorMonitoring()
        }

        // Service tetap dijalankan ulang jika dihentikan sistem
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Jalankan ulang service jika aplikasi dihapus dari recent apps
        if (SessionManager.isLoggedIn(this)) {
            AlertServiceStarter.start(this)
        }
    }

    // Service ini tidak menggunakan binding
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Hapus listener Firebase saat service dihentikan
        if (::database.isInitialized) {
            sensorListener?.let { listener ->
                database.removeEventListener(listener)
            }
        }

        sensorListener = null
        super.onDestroy()
    }

    private fun startSensorMonitoring() {
        // Cek login sebelum monitoring
        if (!SessionManager.isLoggedIn(this)) {
            stopSelf()
            return
        }

        // Cegah listener dibuat dua kali
        if (sensorListener != null) return

        // Listener Firebase realtime
        sensorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Hentikan service jika user logout
                if (!SessionManager.isLoggedIn(this@AlertMonitorService)) {
                    stopSelf()
                    return
                }

                // Proses data terbaru dari Firebase
                handleRealtimeSnapshot(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                // Jika Firebase gagal dibaca, aplikasi tidak dibuat crash
            }
        }

        // Pasang listener ke Firebase
        database.addValueEventListener(sensorListener!!)
    }

    private fun handleRealtimeSnapshot(snapshot: DataSnapshot) {
        // Membaca nilai LiDAR dari beberapa kemungkinan path Firebase
        val currentLidar = readDouble(
            snapshot,
            listOf(
                "lidar/distance",
                "lidar",
                "sensor/lidar",
                "sensor/lidar/distance",
                "gps/lidar",
                "gps/lidar/distance",
                "gps/sensor/lidar",
                "gps/sensor/lidar/distance",
                "distance",
                "jarak",
                "Jarak",
                "gps/jarak",
                "gps/Jarak",
                "sensor/jarak"
            )
        )

        // Membaca kecepatan kendaraan
        val currentSpeed = readSpeedKmh(snapshot)

        // Membaca nilai MPU AZ
        val currentAz = readDouble(
            snapshot,
            listOf(
                "sensor/mpu/az",
                "mpu/az",
                "gps/mpu/az",
                "gps/sensor/mpu/az",
                "az",
                "AZ"
            )
        )

        // Membaca status getaran
        val currentVibration = readString(
            snapshot,
            listOf(
                "sensor/vibration",
                "vibration",
                "gps/vibration",
                "gps/sensor/vibration",
                "sensor/getar",
                "getar",
                "gps/getar",
                "Getar"
            )
        )

        // Data pertama hanya disimpan, belum menampilkan notifikasi
        if (!initialSnapshotLoaded) {
            lastLidarDistance = currentLidar
            lastSpeedKmh = currentSpeed
            lastMpuAz = currentAz
            lastVibration = currentVibration
            initialSnapshotLoaded = true
            return
        }

        // Cek kondisi jarak aman
        checkSafeDistanceRealtime(
            currentDistance = currentLidar,
            currentSpeed = currentSpeed
        )

        // Cek kondisi kemiringan motor
        checkMpuAzRealtime(
            currentAz = currentAz
        )

        // Cek kondisi getaran
        checkVibrationRealtime(
            currentVibration = currentVibration
        )

        // Simpan nilai terbaru untuk perbandingan berikutnya
        lastLidarDistance = currentLidar
        lastSpeedKmh = currentSpeed
        lastMpuAz = currentAz
        lastVibration = currentVibration
    }

    private fun checkSafeDistanceRealtime(
        currentDistance: Double?,
        currentSpeed: Double?
    ) {
        // Jika data kosong, tidak diproses
        if (currentDistance == null || currentSpeed == null) return

        // Cek apakah jarak atau kecepatan berubah
        val lidarChanged = lastLidarDistance == null || currentDistance != lastLidarDistance
        val speedChanged = lastSpeedKmh == null || currentSpeed != lastSpeedKmh

        // Jika tidak berubah, tidak perlu notifikasi
        if (!lidarChanged && !speedChanged) return

        // Konversi nilai LiDAR ke meter
        val distanceMeter = currentDistance / 100.0

        // Kondisi bahaya jarak aman
        val isDanger =
            currentDistance > 0.0 &&
                    currentDistance <= LIDAR_LIMIT_RAW &&
                    currentSpeed >= SPEED_LIMIT_KMH

        // Tampilkan notifikasi jika bahaya
        if (isDanger) {
            showWarningNotification(
                notificationId = SAFE_DISTANCE_NOTIFICATION_ID,
                title = "⚠️ Peringatan Jarak Aman",
                message = "Jarak objek %.1f meter dan kecepatan %.1f km/jam. Jaga jarak aman."
                    .format(distanceMeter, currentSpeed)
            )
        }
    }

    private fun checkMpuAzRealtime(currentAz: Double?) {
        // Jika data kosong, tidak diproses
        if (currentAz == null) return

        // Cek apakah nilai AZ berubah
        val azChanged = lastMpuAz == null || currentAz != lastMpuAz
        if (!azChanged) return

        // Kondisi bahaya kemiringan
        val isDanger =
            currentAz >= MPU_AZ_MIN_LIMIT &&
                    currentAz <= MPU_AZ_MAX_LIMIT

        // Tampilkan notifikasi jika motor berpotensi miring
        if (isDanger) {
            showWarningNotification(
                notificationId = MPU_AZ_NOTIFICATION_ID,
                title = "⚠️ Peringatan Kemiringan Motor",
                message = "Nilai MPU AZ = %.2f. Motor terindikasi miring berlebih atau berpotensi roboh."
                    .format(currentAz)
            )
        }
    }

    private fun checkVibrationRealtime(currentVibration: String?) {
        // Jika data kosong, tidak diproses
        if (currentVibration == null) return

        // Cek apakah status getaran berubah
        val vibrationChanged = lastVibration == null || currentVibration != lastVibration
        if (!vibrationChanged) return

        // Tampilkan notifikasi jika getaran terdeteksi
        if (isVibrationDetected(currentVibration)) {
            showWarningNotification(
                notificationId = VIBRATION_NOTIFICATION_ID,
                title = "⚠️ Peringatan Getaran",
                message = "Getaran terdeteksi pada kendaraan."
            )
        }
    }

    private fun readSpeedKmh(snapshot: DataSnapshot): Double? {
        // Membaca speed dalam satuan meter per detik
        val speedMps = readDouble(
            snapshot,
            listOf(
                "speed_mps",
                "speedMps",
                "gps/speed_mps",
                "gps/speedMps"
            )
        )

        // Konversi m/s ke km/jam
        if (speedMps != null) {
            return speedMps * 3.6
        }

        // Membaca speed jika sudah dalam km/jam
        return readDouble(
            snapshot,
            listOf(
                "speedKmh",
                "kecepatanKmh",
                "kecepatan",
                "kmh",
                "speed",
                "Speed",
                "gps/speed",
                "gps/Speed",
                "gps/speedKmh",
                "gps/kecepatan",
                "gps/kecepatanKmh"
            )
        )
    }

    private fun isVibrationDetected(value: String): Boolean {
        // Normalisasi teks agar mudah dibandingkan
        val normalized = value.trim().lowercase()

        // Jika kosong, dianggap tidak ada getaran
        if (normalized.isEmpty()) return false

        // Jika nilainya bukan status aman, berarti getaran terdeteksi
        return normalized !in listOf(
            "stable",
            "stabil",
            "normal",
            "aman",
            "safe",
            "false",
            "0",
            "off",
            "tidak",
            "tidak mendeteksi",
            "tidak terdeteksi",
            "none",
            "null",
            "-"
        )
    }

    private fun showWarningNotification(
        notificationId: Int,
        title: String,
        message: String
    ) {
        // Jangan tampilkan notifikasi jika user logout
        if (!SessionManager.isLoggedIn(this)) return

        // Intent untuk membuka MainActivity saat notifikasi diklik
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Membuat ID unik agar notifikasi tidak saling menimpa
        val uniqueNotificationId =
            notificationId + (System.currentTimeMillis() % 100000).toInt()

        // PendingIntent untuk aksi klik notifikasi
        val pendingIntent = PendingIntent.getActivity(
            this,
            uniqueNotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Suara default notifikasi
        val defaultSoundUri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Membuat notifikasi peringatan
        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_directions_bike_24)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 450, 180, 450, 180, 650))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setTicker(title)
            .setContentIntent(pendingIntent)
            .build()

        try {
            // Menampilkan notifikasi
            NotificationManagerCompat.from(this)
                .notify(uniqueNotificationId, notification)
        } catch (_: SecurityException) {
            // Android 13+ membutuhkan izin notifikasi
        }
    }

    private fun buildMonitorNotification(): Notification {
        // Notifikasi tetap untuk foreground service
        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_directions_bike_24)
            .setContentTitle("Pemantauan kendaraan aktif")
            .setContentText("Sensor LiDAR, MPU, dan getaran sedang dipantau.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        // Notification channel hanya untuk Android Oreo ke atas
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // Mengambil NotificationManager
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel untuk notifikasi monitoring
        val monitorChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "Pemantauan Kendaraan",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifikasi status pemantauan sensor kendaraan"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Suara default untuk notifikasi warning
        val defaultSoundUri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Pengaturan audio notifikasi
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Channel untuk notifikasi peringatan
        val warningChannel = NotificationChannel(
            WARNING_CHANNEL_ID,
            "Peringatan Kendaraan Prioritas Tinggi",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Peringatan jarak aman, kemiringan, dan getaran kendaraan"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 450, 180, 450, 180, 650)
            enableLights(true)
            setSound(defaultSoundUri, audioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Mendaftarkan channel ke sistem Android
        notificationManager.createNotificationChannel(monitorChannel)
        notificationManager.createNotificationChannel(warningChannel)
    }

    private fun readDouble(snapshot: DataSnapshot, paths: List<String>): Double? {
        // Mencari nilai Double dari beberapa path Firebase
        for (path in paths) {
            val value = getChildByPath(snapshot, path).value

            val result = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }

            // Kembalikan nilai pertama yang ditemukan
            if (result != null) return result
        }

        return null
    }

    private fun readString(snapshot: DataSnapshot, paths: List<String>): String? {
        // Mencari nilai String dari beberapa path Firebase
        for (path in paths) {
            val value = getChildByPath(snapshot, path).value

            // Kembalikan nilai pertama yang ditemukan
            if (value != null) return value.toString()
        }

        return null
    }

    private fun getChildByPath(snapshot: DataSnapshot, path: String): DataSnapshot {
        // Mengambil child Firebase berdasarkan path bertingkat
        var current = snapshot

        for (part in path.split("/")) {
            current = current.child(part)
        }

        return current
    }
}