package com.myapp.velocy

import com.google.firebase.Timestamp

data class HistoryModel(
    val lokasiAwal: String = "",
    val lokasiAkhir: String = "",
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    val timestamp: Timestamp? = null,

    val routeStartTime: Long = 0L,
    val routeEndTime: Long = 0L
)