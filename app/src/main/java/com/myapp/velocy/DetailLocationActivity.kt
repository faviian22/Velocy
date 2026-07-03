package com.myapp.velocy

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Activity untuk menampilkan detail lokasi dan rute perjalanan
class DetailLocationActivity : AppCompatActivity() {

    // Koordinat titik awal dan titik akhir
    private var startLat: Double = 0.0
    private var startLng: Double = 0.0
    private var endLat: Double = 0.0
    private var endLng: Double = 0.0

    // Data statistik perjalanan
    private var jarakKm: Double = 0.0
    private var kecepatanKmh: Double = 0.0
    private var durasiText: String = "0 menit"

    // Waktu mulai dan selesai perjalanan
    private var routeStartTime: Long = 0L
    private var routeEndTime: Long = 0L

    // Database Firestore
    private lateinit var db: FirebaseFirestore

    // Komponen tampilan
    private lateinit var mapContainer: FrameLayout
    private lateinit var cardDetail: CardView
    private var mapFragment: SupportMapFragment? = null

    private lateinit var tvWaktu: TextView
    private lateinit var btnMap: Button
    private lateinit var btnBack: View

    // Client untuk mengambil rute dari OSRM
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // Format tanggal untuk dokumen track
    private val formatTrackDoc = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cek sesi login
        if (SessionManager.redirectToLoginIfNeeded(this)) return

        setContentView(R.layout.activity_detail_location)

        // Inisialisasi Firestore
        db = FirebaseFirestore.getInstance()

        // Hubungkan view dari layout
        tvWaktu = findViewById(R.id.tvWaktu)
        btnMap = findViewById(R.id.btnMap)
        btnBack = findViewById(R.id.btnBack)

        cardDetail = findViewById(R.id.cardDetail)
        mapContainer = findViewById(R.id.mapContainer)

        // Ambil data koordinat dari Intent
        startLat = intent.getDoubleExtra("startLat", 0.0)
        startLng = intent.getDoubleExtra("startLng", 0.0)
        endLat = intent.getDoubleExtra("endLat", 0.0)
        endLng = intent.getDoubleExtra("endLng", 0.0)

        // Ambil waktu dari Intent
        val waktu = intent.getStringExtra("waktu") ?: "-"

        // Ambil statistik dari Intent
        jarakKm = intent.getDoubleExtra("jarakKm", 0.0)
        kecepatanKmh = intent.getDoubleExtra("kecepatanKmh", 0.0)
        durasiText = intent.getStringExtra("durasiText") ?: "0 menit"

        // Ambil waktu rute dari Intent
        routeStartTime = intent.getLongExtra("routeStartTime", 0L)
        routeEndTime = intent.getLongExtra("routeEndTime", 0L)

        // Tampilkan waktu ke layout
        tvWaktu.text = waktu

        // Tampilkan statistik awal
        tampilkanStatistikKeLayout(
            jarakKm = jarakKm,
            kecepatanKmh = kecepatanKmh,
            durasiText = durasiText
        )

        // Tombol untuk menampilkan map
        btnMap.setOnClickListener {
            cardDetail.visibility = View.GONE
            mapContainer.visibility = View.VISIBLE
            tampilkanRuteDiMap()
        }

        // Tombol kembali
        btnBack.setOnClickListener {
            if (mapContainer.visibility == View.VISIBLE) {
                mapContainer.visibility = View.GONE
                cardDetail.visibility = View.VISIBLE
            } else {
                finish()
            }
        }
    }

    // Menampilkan statistik perjalanan ke TextView
    private fun tampilkanStatistikKeLayout(
        jarakKm: Double,
        kecepatanKmh: Double,
        durasiText: String
    ) {
        setOptionalText(
            listOf("tvJarak", "tvSheetJarak", "tvDistance", "tvJarakTempuh"),
            "%.2f km".format(jarakKm)
        )

        setOptionalText(
            listOf("tvSpeed", "tvSheetSpeed", "tvKecepatan"),
            "%.1f km/jam".format(kecepatanKmh)
        )

        setOptionalText(
            listOf("tvDuration", "tvSheetDuration", "tvDurasi", "tvWaktuTempuh"),
            durasiText
        )
    }

    // Mengisi TextView berdasarkan ID yang tersedia
    private fun setOptionalText(possibleIds: List<String>, value: String) {
        for (idName in possibleIds) {
            val id = resources.getIdentifier(idName, "id", packageName)

            if (id != 0) {
                val view = findViewById<View>(id)

                if (view is TextView) {
                    view.text = value
                    return
                }
            }
        }
    }

    // Menampilkan rute pada Google Maps
    private fun tampilkanRuteDiMap() {
        if (startLat == 0.0 || startLng == 0.0 || endLat == 0.0 || endLng == 0.0) {
            Toast.makeText(
                this,
                "Data titik awal atau titik akhir tidak lengkap",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Membuat fragment map jika belum ada
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance()

            supportFragmentManager.beginTransaction()
                .replace(R.id.mapContainer, mapFragment!!)
                .commitNowAllowingStateLoss()
        }

        // Proses map setelah siap
        mapFragment?.getMapAsync { googleMap ->
            googleMap.clear()
            googleMap.uiSettings.isZoomControlsEnabled = true

            val titikAwal = LatLng(startLat, startLng)
            val titikAkhir = LatLng(endLat, endLng)

            // Marker lokasi awal
            googleMap.addMarker(
                MarkerOptions()
                    .position(titikAwal)
                    .title("Lokasi Awal")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )

            // Marker lokasi akhir
            googleMap.addMarker(
                MarkerOptions()
                    .position(titikAkhir)
                    .title("Lokasi Akhir")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            // Fokuskan kamera ke titik awal dan akhir
            fokuskanKamera(googleMap, listOf(titikAwal, titikAkhir))

            // Jika waktu rute tersedia, ambil data GPS aktual
            if (routeStartTime > 0L && routeEndTime > 0L) {
                ambilRuteAktualFirestore(
                    onSuccess = { gpsPoints ->
                        if (gpsPoints.size < 2) {
                            tampilkanRuteOSRMFallback(googleMap)
                            return@ambilRuteAktualFirestore
                        }

                        // Buat rute jalan dari titik GPS
                        ambilRuteJalanDariOSRMMelaluiTitik(
                            gpsPoints = gpsPoints,
                            onSuccess = { roadPoints ->
                                runOnUiThread {
                                    if (roadPoints.size >= 2) {
                                        gambarPolylineRute(googleMap, roadPoints, Color.BLUE)
                                        fokuskanKamera(googleMap, roadPoints)
                                        perbaruiStatistikDariJalurJalan(roadPoints)

                                        Toast.makeText(
                                            this,
                                            "Rute GPS ditampilkan mengikuti jalan",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        tampilkanRuteOSRMFallback(googleMap)
                                    }
                                }
                            },
                            onError = {
                                runOnUiThread {
                                    gambarPolylineRute(googleMap, gpsPoints, Color.BLUE)
                                    fokuskanKamera(googleMap, gpsPoints)

                                    Toast.makeText(
                                        this,
                                        "Rute jalan gagal dibuat. Ditampilkan rute GPS aktual.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    },
                    onError = {
                        runOnUiThread {
                            tampilkanRuteOSRMFallback(googleMap)
                        }
                    }
                )
            } else {
                tampilkanRuteOSRMFallback(googleMap)
            }
        }
    }

    // Mengambil titik GPS aktual dari Firestore
    private fun ambilRuteAktualFirestore(
        onSuccess: (List<LatLng>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collectionGroup("points")
            .get()
            .addOnSuccessListener { pointDocs ->
                val points = mutableListOf<GpsPoint>()

                // Ambil semua titik yang waktunya sesuai rute
                for (pointDoc in pointDocs) {
                    val trackDate = pointDoc.reference.parent.parent?.id ?: ""
                    val point = getGpsPointFromTrackPoint(pointDoc, trackDate)

                    if (point != null && point.timestamp in routeStartTime..routeEndTime) {
                        points.add(point)
                    }
                }

                val sortedPoints = points.sortedBy { it.timestamp }

                if (sortedPoints.isEmpty()) {
                    onError("Data rute aktual tidak ditemukan")
                    return@addOnSuccessListener
                }

                // Ubah data menjadi LatLng
                val pathList = sortedPoints.map {
                    LatLng(it.lat, it.lng)
                }

                // Hitung statistik dari titik GPS
                val stats = hitungStatistikRute(sortedPoints)

                jarakKm = stats.jarakKm
                kecepatanKmh = stats.kecepatanKmh
                durasiText = stats.durasiText

                tampilkanStatistikKeLayout(
                    jarakKm = jarakKm,
                    kecepatanKmh = kecepatanKmh,
                    durasiText = durasiText
                )

                onSuccess(pathList)
            }
            .addOnFailureListener { error ->
                onError("Gagal mengambil rute aktual: ${error.message}")
            }
    }

    // Mengubah titik GPS menjadi rute jalan OSRM
    private fun ambilRuteJalanDariOSRMMelaluiTitik(
        gpsPoints: List<LatLng>,
        onSuccess: (List<LatLng>) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanedPoints = rapikanTitikGps(gpsPoints)

        if (cleanedPoints.size < 2) {
            onError("Titik GPS tidak cukup")
            return
        }

        // OSRM diproses per bagian agar tidak terlalu banyak waypoint
        val chunks = buatChunkRute(cleanedPoints, 20)
        val hasilAkhir = mutableListOf<LatLng>()

        var index = 0

        fun prosesChunk() {
            if (index >= chunks.size) {
                onSuccess(hasilAkhir)
                return
            }

            val chunk = chunks[index]

            ambilRuteOSRMUntukWaypoint(
                waypoints = chunk,
                onSuccess = { segmentPoints ->
                    if (segmentPoints.isEmpty()) {
                        onError("Rute OSRM kosong")
                        return@ambilRuteOSRMUntukWaypoint
                    }

                    // Gabungkan hasil setiap chunk
                    if (hasilAkhir.isNotEmpty()) {
                        hasilAkhir.addAll(segmentPoints.drop(1))
                    } else {
                        hasilAkhir.addAll(segmentPoints)
                    }

                    index++
                    prosesChunk()
                },
                onError = { message ->
                    onError(message)
                }
            )
        }

        prosesChunk()
    }

    // Membersihkan titik GPS yang tidak valid atau terlalu dekat
    private fun rapikanTitikGps(points: List<LatLng>): List<LatLng> {
        val result = mutableListOf<LatLng>()

        for (point in points) {
            val validLat = point.latitude in -90.0..90.0
            val validLng = point.longitude in -180.0..180.0

            if (!validLat || !validLng) continue

            val lastPoint = result.lastOrNull()

            if (lastPoint == null) {
                result.add(point)
            } else {
                val jarakMeter = distance(lastPoint, point) * 1000.0

                // Titik yang terlalu dekat tidak dimasukkan
                if (jarakMeter >= 5.0) {
                    result.add(point)
                }
            }
        }

        return result
    }

    // Membagi titik rute menjadi beberapa chunk
    private fun buatChunkRute(points: List<LatLng>, maxPointPerChunk: Int): List<List<LatLng>> {
        val chunks = mutableListOf<List<LatLng>>()

        if (points.size <= maxPointPerChunk) {
            chunks.add(points)
            return chunks
        }

        var startIndex = 0

        while (startIndex < points.lastIndex) {
            val endIndex = minOf(startIndex + maxPointPerChunk - 1, points.lastIndex)
            chunks.add(points.subList(startIndex, endIndex + 1))
            startIndex = endIndex
        }

        return chunks
    }

    // Mengambil rute OSRM berdasarkan banyak waypoint
    private fun ambilRuteOSRMUntukWaypoint(
        waypoints: List<LatLng>,
        onSuccess: (List<LatLng>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (waypoints.size < 2) {
            onError("Titik rute tidak cukup")
            return
        }

        // Format koordinat OSRM: longitude,latitude
        val coordinates = waypoints.joinToString(";") {
            "${it.longitude},${it.latitude}"
        }

        val url =
            "https://router.project-osrm.org/route/v1/driving/$coordinates" +
                    "?overview=full&geometries=polyline&steps=false"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        // Request ke server OSRM
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Gagal terhubung ke OSRM: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onError("Gagal mengambil rute OSRM. Kode: ${response.code}")
                        return
                    }

                    val responseBody = response.body?.string()

                    if (responseBody.isNullOrEmpty()) {
                        onError("Respons OSRM kosong")
                        return
                    }

                    try {
                        val json = JSONObject(responseBody)
                        val code = json.optString("code")

                        if (code != "Ok") {
                            onError("OSRM tidak menemukan rute")
                            return
                        }

                        val routes = json.getJSONArray("routes")

                        if (routes.length() == 0) {
                            onError("Data rute OSRM kosong")
                            return
                        }

                        val route = routes.getJSONObject(0)
                        val geometry = route.getString("geometry")

                        // Decode polyline menjadi titik LatLng
                        val routePoints = PolyUtil.decode(geometry)

                        onSuccess(routePoints)
                    } catch (e: Exception) {
                        onError("Gagal membaca respons OSRM: ${e.message}")
                    }
                }
            }
        })
    }

    // Fallback jika rute aktual tidak tersedia
    private fun tampilkanRuteOSRMFallback(googleMap: GoogleMap) {
        val titikAwal = LatLng(startLat, startLng)
        val titikAkhir = LatLng(endLat, endLng)

        ambilRuteOSRM(
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng,
            onSuccess = { routePoints, distanceKm, durationMinute ->
                runOnUiThread {
                    if (routePoints.isEmpty()) {
                        Toast.makeText(
                            this,
                            "Rute OSRM kosong",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@runOnUiThread
                    }

                    // Gambar rute OSRM
                    gambarPolylineRute(googleMap, routePoints, Color.BLUE)
                    fokuskanKamera(googleMap, routePoints)

                    // Hitung durasi
                    val durationMs = if (routeEndTime > routeStartTime) {
                        routeEndTime - routeStartTime
                    } else {
                        (durationMinute * 60.0 * 1000.0).toLong()
                    }

                    perbaruiStatistikDariJarak(distanceKm, durationMs)

                    Toast.makeText(
                        this,
                        "Rute ditampilkan mengikuti jalan",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                    // Jika OSRM gagal, gambar garis lurus
                    googleMap.addPolyline(
                        PolylineOptions()
                            .add(titikAwal, titikAkhir)
                            .width(8f)
                            .color(Color.GRAY)
                    )

                    fokuskanKamera(googleMap, listOf(titikAwal, titikAkhir))
                }
            }
        )
    }

    // Menggambar garis rute di map
    private fun gambarPolylineRute(
        googleMap: GoogleMap,
        routePoints: List<LatLng>,
        color: Int
    ) {
        if (routePoints.size < 2) return

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .width(12f)
                .color(color)
                .jointType(JointType.ROUND)
        )
    }

    // Mengarahkan kamera map agar semua titik terlihat
    private fun fokuskanKamera(
        googleMap: GoogleMap,
        points: List<LatLng>
    ) {
        if (points.isEmpty()) return

        mapContainer.post {
            if (points.size == 1) {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(points.first(), 17f)
                )
                return@post
            }

            try {
                val boundsBuilder = LatLngBounds.Builder()

                for (point in points) {
                    boundsBuilder.include(point)
                }

                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
                )
            } catch (_: Exception) {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(points.last(), 16f)
                )
            }
        }
    }

    // Update statistik berdasarkan jalur jalan
    private fun perbaruiStatistikDariJalurJalan(routePoints: List<LatLng>) {
        val totalDistanceKm = hitungJarakPolyline(routePoints)
        val durationMs = if (routeEndTime > routeStartTime) {
            routeEndTime - routeStartTime
        } else {
            0L
        }

        perbaruiStatistikDariJarak(totalDistanceKm, durationMs)
    }

    // Update jarak, durasi, dan kecepatan
    private fun perbaruiStatistikDariJarak(distanceKm: Double, durationMs: Long) {
        val durationHours = durationMs.toDouble() / (1000.0 * 60.0 * 60.0)

        jarakKm = distanceKm
        durasiText = formatDuration(durationMs)

        kecepatanKmh = if (durationHours > 0.0) {
            distanceKm / durationHours
        } else {
            0.0
        }

        tampilkanStatistikKeLayout(
            jarakKm = jarakKm,
            kecepatanKmh = kecepatanKmh,
            durasiText = durasiText
        )
    }

    // Menghitung total jarak dari banyak titik polyline
    private fun hitungJarakPolyline(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0

        var total = 0.0

        for (i in 0 until points.size - 1) {
            total += distance(points[i], points[i + 1])
        }

        return total
    }

    // Menghitung statistik dari data GPS
    private fun hitungStatistikRute(points: List<GpsPoint>): RouteStats {
        if (points.isEmpty()) {
            return RouteStats(
                jarakKm = 0.0,
                kecepatanKmh = 0.0,
                durasiText = "0 menit"
            )
        }

        val sortedPoints = points.sortedBy { it.timestamp }

        var totalDistanceKm = 0.0

        for (i in 0 until sortedPoints.size - 1) {
            val start = LatLng(sortedPoints[i].lat, sortedPoints[i].lng)
            val end = LatLng(sortedPoints[i + 1].lat, sortedPoints[i + 1].lng)

            totalDistanceKm += distance(start, end)
        }

        val startTime = sortedPoints.first().timestamp
        val endTime = sortedPoints.last().timestamp

        val durationMs = if (endTime > startTime) {
            endTime - startTime
        } else {
            0L
        }

        val durationHours = durationMs.toDouble() / (1000.0 * 60.0 * 60.0)

        val avgSpeedKmh = if (durationHours > 0.0) {
            totalDistanceKm / durationHours
        } else {
            0.0
        }

        return RouteStats(
            jarakKm = totalDistanceKm,
            kecepatanKmh = avgSpeedKmh,
            durasiText = formatDuration(durationMs)
        )
    }

    // Mengubah durasi milidetik menjadi teks
    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) {
            return "0 menit"
        }

        val totalMinutes = durationMs / (1000 * 60)
        val jam = totalMinutes / 60
        val menit = totalMinutes % 60

        return if (jam > 0) {
            "%d jam %d menit".format(jam, menit)
        } else {
            "%d menit".format(menit)
        }
    }

    // Menghitung jarak dua koordinat menggunakan rumus haversine
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

    // Mengambil data GPS dari dokumen Firestore
    private fun getGpsPointFromTrackPoint(doc: DocumentSnapshot, trackDate: String): GpsPoint? {
        val lat = getDoubleField(doc, "lat", "latitude")
        val lng = getDoubleField(doc, "lng", "longitude")
        val speed = getDoubleField(doc, "speed") ?: 0.0

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

        // Jika timestamp masih detik, ubah ke milidetik
        if (timestamp > 0 && timestamp < 10000000000L) {
            timestamp *= 1000
        }

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

    // Mengambil field angka dari Firestore
    private fun getDoubleField(doc: DocumentSnapshot, vararg fieldNames: String): Double? {
        for (fieldName in fieldNames) {
            val value = doc.get(fieldName)

            val result = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }

            if (result != null) {
                return result
            }
        }

        return null
    }

    // Mengambil field waktu dari Firestore
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

    // Mengubah teks waktu menjadi timestamp
    private fun parseTimestampString(value: String): Long {
        val cleanValue = value.trim()

        cleanValue.toLongOrNull()?.let {
            return it
        }

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

                if (date != null) {
                    return date.time
                }
            } catch (_: Exception) {
            }
        }

        return 0L
    }

    // Mengubah tanggal dokumen track menjadi timestamp
    private fun parseTrackDateToMillis(trackDate: String): Long {
        return try {
            formatTrackDoc.parse(trackDate)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // Mengambil rute OSRM dari titik awal ke titik akhir
    private fun ambilRuteOSRM(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        onSuccess: (List<LatLng>, Double, Double) -> Unit,
        onError: (String) -> Unit
    ) {
        val url =
            "https://router.project-osrm.org/route/v1/driving/" +
                    "$startLng,$startLat;$endLng,$endLat" +
                    "?overview=full&geometries=polyline&steps=false"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        // Request ke OSRM
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Gagal terhubung ke OSRM: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onError("Gagal mengambil rute OSRM. Kode: ${response.code}")
                        return
                    }

                    val responseBody = response.body?.string()

                    if (responseBody.isNullOrEmpty()) {
                        onError("Respons OSRM kosong")
                        return
                    }

                    try {
                        val json = JSONObject(responseBody)
                        val code = json.optString("code")

                        if (code != "Ok") {
                            onError("OSRM tidak menemukan rute")
                            return
                        }

                        val routes = json.getJSONArray("routes")

                        if (routes.length() == 0) {
                            onError("Data rute OSRM kosong")
                            return
                        }

                        val route = routes.getJSONObject(0)

                        val geometry = route.getString("geometry")
                        val distanceMeter = route.optDouble("distance", 0.0)
                        val durationSecond = route.optDouble("duration", 0.0)

                        // Decode rute dan hitung statistik OSRM
                        val routePoints = PolyUtil.decode(geometry)

                        val distanceKm = distanceMeter / 1000.0
                        val durationMinute = durationSecond / 60.0

                        onSuccess(routePoints, distanceKm, durationMinute)
                    } catch (e: Exception) {
                        onError("Gagal membaca respons OSRM: ${e.message}")
                    }
                }
            }
        })
    }

    // Model data titik GPS
    data class GpsPoint(
        val lat: Double,
        val lng: Double,
        val timestamp: Long,
        val speed: Double = 0.0
    )

    // Model data statistik rute
    data class RouteStats(
        val jarakKm: Double,
        val kecepatanKmh: Double,
        val durasiText: String
    )
}