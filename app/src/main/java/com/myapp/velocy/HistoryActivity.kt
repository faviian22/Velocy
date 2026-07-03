package com.myapp.velocy

import android.app.DatePickerDialog
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Activity untuk menampilkan riwayat perjalanan kendaraan
class HistoryActivity : AppCompatActivity() {

    // Menyimpan tanggal mulai dan selesai filter
    private var mulai: Long? = null
    private var selesai: Long? = null

    // Firebase Firestore
    private lateinit var db: FirebaseFirestore

    // View untuk memilih tanggal
    private lateinit var viewMulai: View
    private lateinit var viewSelesai: View

    // TextView di dalam view tanggal
    private var textMulai: TextView? = null
    private var textSelesai: TextView? = null

    // Komponen tampilan riwayat
    private lateinit var tvInfo: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var adapter: HistoryAdapter

    // List data riwayat
    private val list = mutableListOf<HistoryModel>()

    // Menyimpan statistik tiap rute
    private val routeStatsMap = mutableMapOf<Long, RouteStats>()

    // Format tanggal untuk tampilan
    private val formatDisplay = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

    // Format tanggal untuk pengelompokan data
    private val formatDateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Format nama dokumen track
    private val formatTrackDoc = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Batas jeda 10 menit untuk memisahkan rute
    private val routeIdleTimeoutMs = 10 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cek apakah user sudah login
        if (SessionManager.redirectToLoginIfNeeded(this)) return

        // Menghubungkan activity dengan layout XML
        setContentView(R.layout.activity_history)

        // Inisialisasi Firestore
        db = FirebaseFirestore.getInstance()

        // Menghubungkan view tanggal
        viewMulai = findViewById(R.id.tvMulai)
        viewSelesai = findViewById(R.id.tvSelesai)

        // Mencari TextView di dalam view tanggal
        textMulai = findTextViewInside(viewMulai)
        textSelesai = findTextViewInside(viewSelesai)

        // Menghubungkan komponen layout
        tvInfo = findViewById(R.id.tvInfo)
        rvHistory = findViewById(R.id.rvHistory)

        // Mengatur RecyclerView secara vertikal
        rvHistory.layoutManager = LinearLayoutManager(this)

        // Adapter untuk menampilkan list riwayat
        adapter = HistoryAdapter(list) { model, dateString ->
            val routeKey = model.timestamp?.toDate()?.time ?: 0L
            val stats = routeStatsMap[routeKey]

            // Intent menuju halaman detail lokasi
            val intent = Intent(this, DetailLocationActivity::class.java)
            intent.putExtra("startLat", model.startLat)
            intent.putExtra("startLng", model.startLng)
            intent.putExtra("endLat", model.endLat)
            intent.putExtra("endLng", model.endLng)
            intent.putExtra("waktu", dateString)
            intent.putExtra("jarakKm", stats?.totalDistanceKm ?: 0.0)
            intent.putExtra("kecepatanKmh", stats?.avgSpeedKmh ?: 0.0)
            intent.putExtra("durasiText", stats?.durationText ?: "0 menit")
            intent.putExtra("routeStartTime", stats?.startTime ?: 0L)
            intent.putExtra("routeEndTime", stats?.endTime ?: 0L)

            // Buka halaman detail
            startActivity(intent)
        }

        // Pasang adapter ke RecyclerView
        rvHistory.adapter = adapter

        // Pilih tanggal mulai
        viewMulai.setOnClickListener {
            pickDate { ts, label ->
                mulai = ts
                setDateText(viewMulai, textMulai, label)
            }
        }

        // Pilih tanggal selesai
        viewSelesai.setOnClickListener {
            pickDate { ts, label ->
                selesai = ts
                setDateText(viewSelesai, textSelesai, label)
            }
        }

        // Tombol cari berdasarkan tanggal
        findViewById<Button>(R.id.btnCari).setOnClickListener {
            if (mulai == null || selesai == null) {
                Toast.makeText(
                    this,
                    "Pilih tanggal mulai dan selesai terlebih dahulu",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            loadByRange(mulai!!, selesai!!)
        }

        // Tombol batal filter
        findViewById<Button>(R.id.btnBatal).setOnClickListener {
            mulai = null
            selesai = null

            setDateText(viewMulai, textMulai, "Mulai")
            setDateText(viewSelesai, textSelesai, "Selesai")

            loadAll()
        }

        // Tombol pindah ke halaman lokasi
        findViewById<View>(R.id.btnLokasi).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Tombol pindah ke halaman profil
        findViewById<View>(R.id.btnProfil).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }

        // Menampilkan semua riwayat saat halaman dibuka
        loadAll()
    }

    // Mengambil semua data titik GPS dari Firestore
    private fun loadAll() {
        db.collectionGroup("points")
            .get()
            .addOnSuccessListener { pointDocs ->
                val allPoints = mutableListOf<GpsPoint>()

                // Ubah dokumen Firestore menjadi data GPS
                for (pointDoc in pointDocs) {
                    val trackDate = pointDoc.reference.parent.parent?.id ?: ""
                    val point = getGpsPointFromTrackPoint(pointDoc, trackDate)

                    if (point != null) {
                        allPoints.add(point)
                    }
                }

                // Tampilkan data riwayat
                tampilkanRiwayat(allPoints)
            }
            .addOnFailureListener { error ->
                list.clear()
                routeStatsMap.clear()
                adapter.notifyDataSetChanged()
                tvInfo.text = "Menampilkan 0 riwayat"

                Toast.makeText(
                    this,
                    "Gagal mengambil data points: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Mengambil data GPS berdasarkan rentang tanggal
    private fun loadByRange(st: Long, en: Long) {
        // Tanggal selesai dibuat sampai jam 23:59:59
        val enCal = Calendar.getInstance().apply {
            timeInMillis = en
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val startTime = st
        val endTime = enCal.timeInMillis

        db.collectionGroup("points")
            .get()
            .addOnSuccessListener { pointDocs ->
                val allPoints = mutableListOf<GpsPoint>()

                // Ambil data yang masuk rentang waktu
                for (pointDoc in pointDocs) {
                    val trackDate = pointDoc.reference.parent.parent?.id ?: ""
                    val point = getGpsPointFromTrackPoint(pointDoc, trackDate)

                    if (point != null && point.timestamp in startTime..endTime) {
                        allPoints.add(point)
                    }
                }

                tampilkanRiwayat(allPoints)
            }
            .addOnFailureListener { error ->
                list.clear()
                routeStatsMap.clear()
                adapter.notifyDataSetChanged()
                tvInfo.text = "Menampilkan 0 riwayat"

                Toast.makeText(
                    this,
                    "Gagal mengambil data points: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Mengolah dan menampilkan data riwayat
    private fun tampilkanRiwayat(points: List<GpsPoint>) {
        list.clear()
        routeStatsMap.clear()

        // Mengelompokkan titik GPS berdasarkan tanggal
        val groupedData = mutableMapOf<String, MutableList<GpsPoint>>()

        for (point in points) {
            val tanggal = formatDateOnly.format(Date(point.timestamp))

            if (!groupedData.containsKey(tanggal)) {
                groupedData[tanggal] = mutableListOf()
            }

            groupedData[tanggal]?.add(point)
        }

        // Memproses setiap kelompok tanggal
        for ((_, groupPoints) in groupedData) {
            val sortedPoints = groupPoints.sortedBy { it.timestamp }

            // Pecah rute berdasarkan jeda waktu
            val routeGroups = kelompokkanRutePerJeda(sortedPoints)

            for (routePoints in routeGroups) {
                if (routePoints.isEmpty()) continue

                val sortedRoutePoints = routePoints.sortedBy { it.timestamp }
                val start = sortedRoutePoints.first()
                val end = sortedRoutePoints.last()

                // Mengambil nama tempat dari koordinat
                val namaAwal = getNamaTempat(start.lat, start.lng)
                val namaAkhir = getNamaTempat(end.lat, end.lng)

                val routeKey = end.timestamp

                // Menghitung statistik rute
                val stats = hitungStatistikRute(sortedRoutePoints)

                // Simpan statistik berdasarkan timestamp akhir
                routeStatsMap[routeKey] = stats

                // Tambahkan data ke list riwayat
                list.add(
                    HistoryModel(
                        lokasiAwal = "Lokasi Awal: $namaAwal",
                        lokasiAkhir = "Lokasi Akhir: $namaAkhir",
                        startLat = start.lat,
                        startLng = start.lng,
                        endLat = end.lat,
                        endLng = end.lng,
                        timestamp = Timestamp(Date(end.timestamp))
                    )
                )
            }
        }

        // Urutkan riwayat terbaru di atas
        list.sortByDescending { it.timestamp?.toDate()?.time ?: 0L }

        // Update info jumlah riwayat
        tvInfo.text = "Menampilkan ${list.size} riwayat"

        // Refresh RecyclerView
        adapter.notifyDataSetChanged()
    }

    // Mengelompokkan titik GPS menjadi beberapa rute berdasarkan jeda waktu
    private fun kelompokkanRutePerJeda(points: List<GpsPoint>): List<List<GpsPoint>> {
        if (points.isEmpty()) return emptyList()

        val result = mutableListOf<List<GpsPoint>>()
        val currentRoute = mutableListOf<GpsPoint>()

        for (point in points) {
            val lastPoint = currentRoute.lastOrNull()

            if (lastPoint != null) {
                val selisihWaktu = point.timestamp - lastPoint.timestamp

                // Jika jeda terlalu lama, dianggap rute baru
                if (selisihWaktu >= routeIdleTimeoutMs) {
                    result.add(currentRoute.toList())
                    currentRoute.clear()
                }
            }

            currentRoute.add(point)
        }

        // Tambahkan rute terakhir
        if (currentRoute.isNotEmpty()) {
            result.add(currentRoute.toList())
        }

        return result
    }

    // Menghitung jarak, kecepatan rata-rata, dan durasi rute
    private fun hitungStatistikRute(points: List<GpsPoint>): RouteStats {
        if (points.isEmpty()) {
            return RouteStats(
                totalDistanceKm = 0.0,
                avgSpeedKmh = 0.0,
                durationText = "0 menit",
                startTime = 0L,
                endTime = 0L
            )
        }

        val sortedPoints = points.sortedBy { it.timestamp }
        var totalDistanceKm = 0.0

        // Hitung total jarak antar titik GPS
        for (i in 0 until sortedPoints.size - 1) {
            val start = LatLng(sortedPoints[i].lat, sortedPoints[i].lng)
            val end = LatLng(sortedPoints[i + 1].lat, sortedPoints[i + 1].lng)

            totalDistanceKm += distance(start, end)
        }

        val startTime = sortedPoints.first().timestamp
        val endTime = sortedPoints.last().timestamp

        // Hitung durasi perjalanan
        val durationMs = if (endTime > startTime) endTime - startTime else 0L

        val durationHours = durationMs.toDouble() / (1000.0 * 60.0 * 60.0)

        // Hitung kecepatan rata-rata
        val avgSpeedKmh = if (durationHours > 0.0) {
            totalDistanceKm / durationHours
        } else {
            0.0
        }

        return RouteStats(
            totalDistanceKm = totalDistanceKm,
            avgSpeedKmh = avgSpeedKmh,
            durationText = formatDuration(durationMs),
            startTime = startTime,
            endTime = endTime
        )
    }

    // Mengubah durasi milidetik menjadi teks
    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0 menit"

        val totalMinutes = durationMs / (1000 * 60)
        val jam = totalMinutes / 60
        val menit = totalMinutes % 60

        return if (jam > 0) {
            "%d jam %d menit".format(jam, menit)
        } else {
            "%d menit".format(menit)
        }
    }

    // Mengambil data GPS dari dokumen Firestore
    private fun getGpsPointFromTrackPoint(doc: DocumentSnapshot, trackDate: String): GpsPoint? {
        val lat = getDoubleField(doc, "lat", "latitude")
        val lng = getDoubleField(doc, "lng", "longitude")
        val speed = getDoubleField(doc, "speed") ?: 0.0

        // Ambil timestamp dari beberapa kemungkinan field
        var timestamp = getTimestampField(doc, "timestamp")

        if (timestamp == 0L) {
            timestamp = getTimestampField(doc, "time")
        }

        if (timestamp == 0L) {
            timestamp = getTimestampField(doc, "createdAt")
        }

        if (timestamp == 0L) {
            timestamp = parseTrackDateToMillis(trackDate)
        }

        // Jika timestamp dalam detik, ubah ke milidetik
        if (timestamp > 0 && timestamp < 10000000000L) {
            timestamp *= 1000
        }

        // Data tidak valid jika lat/lng/timestamp kosong
        if (lat == null || lng == null || timestamp == 0L) {
            return null
        }

        return GpsPoint(
            lat = lat,
            lng = lng,
            timestamp = timestamp,
            speed = speed
        )
    }

    // Mengambil nilai Double dari beberapa nama field
    private fun getDoubleField(doc: DocumentSnapshot, vararg fieldNames: String): Double? {
        for (fieldName in fieldNames) {
            val value = doc.get(fieldName)

            val result = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }

            if (result != null) return result
        }

        return null
    }

    // Mengambil timestamp dari field Firestore
    private fun getTimestampField(doc: DocumentSnapshot, fieldName: String): Long {
        val value = doc.get(fieldName)

        return when (value) {
            is Timestamp -> value.toDate().time
            is Date -> value.time
            is Number -> value.toLong()
            is String -> parseTimestampString(value)
            else -> 0L
        }
    }

    // Mengubah timestamp String menjadi Long
    private fun parseTimestampString(value: String): Long {
        val cleanValue = value.trim()

        // Jika isinya angka, langsung dikembalikan
        cleanValue.toLongOrNull()?.let { return it }

        // Beberapa format tanggal yang didukung
        val patterns = listOf(
            "dd/MM/yyyy HH:mm",
            "dd-MM-yyyy HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val date = sdf.parse(cleanValue)

                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        return 0L
    }

    // Mengubah nama dokumen tanggal menjadi timestamp
    private fun parseTrackDateToMillis(trackDate: String): Long {
        return try {
            formatTrackDoc.parse(trackDate)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // Mengubah koordinat menjadi nama tempat
    private fun getNamaTempat(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale("id", "ID"))

            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]

                val desa = address.subLocality
                val kecamatan = address.locality
                val kabupaten = address.subAdminArea
                val provinsi = address.adminArea
                val alamatLengkap = address.getAddressLine(0)

                // Pilih nama lokasi yang paling informatif
                when {
                    !desa.isNullOrEmpty() &&
                            !kecamatan.isNullOrEmpty() &&
                            !kabupaten.isNullOrEmpty() ->
                        "$desa, $kecamatan, $kabupaten"

                    !kecamatan.isNullOrEmpty() &&
                            !kabupaten.isNullOrEmpty() ->
                        "$kecamatan, $kabupaten"

                    !kabupaten.isNullOrEmpty() &&
                            !provinsi.isNullOrEmpty() ->
                        "$kabupaten, $provinsi"

                    !alamatLengkap.isNullOrEmpty() ->
                        alamatLengkap

                    else ->
                        "Nama lokasi tidak tersedia"
                }
            } else {
                "Nama lokasi tidak tersedia"
            }
        } catch (e: Exception) {
            "Nama lokasi tidak tersedia"
        }
    }

    // Menghitung jarak dua koordinat dengan rumus haversine
    private fun distance(start: LatLng, end: LatLng): Double {
        val radius = 6371.0

        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLng = Math.toRadians(end.longitude - start.longitude)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(start.latitude)) *
                cos(Math.toRadians(end.latitude)) *
                sin(dLng / 2).pow(2)

        return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // Menampilkan DatePicker untuk memilih tanggal
    private fun pickDate(onPicked: (Long, String) -> Unit) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                // Kirim hasil tanggal dalam timestamp dan teks
                onPicked(
                    calendar.timeInMillis,
                    formatDisplay.format(calendar.time)
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
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

    // Mengubah teks tanggal di view
    private fun setDateText(view: View, textView: TextView?, value: String) {
        if (view is TextView) {
            view.text = value
        } else {
            textView?.text = value
        }
    }

    // Model data titik GPS
    data class GpsPoint(
        val lat: Double,
        val lng: Double,
        val timestamp: Long,
        val speed: Double = 0.0
    )

    // Model statistik rute
    data class RouteStats(
        val totalDistanceKm: Double,
        val avgSpeedKmh: Double,
        val durationText: String,
        val startTime: Long,
        val endTime: Long
    )
}