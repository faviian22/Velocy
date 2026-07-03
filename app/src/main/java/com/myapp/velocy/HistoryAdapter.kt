package com.myapp.velocy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

// Adapter untuk menampilkan data riwayat ke RecyclerView
class HistoryAdapter(
    private val list: List<HistoryModel>,
    private val onItemClick: (HistoryModel, String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // ViewHolder untuk menampung komponen pada item_history.xml
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLokasi: TextView = itemView.findViewById(R.id.tvLokasi)
        val tvWaktu: TextView = itemView.findViewById(R.id.tvWaktu)
    }

    // Membuat tampilan item dari layout item_history.xml
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)

        return ViewHolder(view)
    }

    // Mengembalikan jumlah data riwayat
    override fun getItemCount(): Int = list.size

    // Mengisi data ke setiap item RecyclerView
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = list[position]

        // Menampilkan lokasi awal dan lokasi akhir
        holder.tvLokasi.text = "${data.lokasiAwal} → ${data.lokasiAkhir}"

        // Format tanggal lengkap untuk ditampilkan
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Format tanggal saja untuk dikirim ke halaman detail
        val dateOnlySdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // Mengambil timestamp dari data
        val date = data.timestamp?.toDate()

        // Mengubah timestamp menjadi teks tanggal dan jam
        val formattedDate = date?.let { sdf.format(it) } ?: "-"

        // Mengubah timestamp menjadi tanggal saja
        val dateOnly = date?.let { dateOnlySdf.format(it) } ?: ""

        // Menampilkan waktu ke item riwayat
        holder.tvWaktu.text = formattedDate

        // Aksi saat item riwayat diklik
        holder.itemView.setOnClickListener {
            onItemClick(data, dateOnly)
        }
    }
}