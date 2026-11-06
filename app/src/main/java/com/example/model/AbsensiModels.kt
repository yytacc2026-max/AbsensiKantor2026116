package com.example.model

/**
 * =========================================================================
 * MODEL DATA & SUMBER DATA CONTOH (DUMMY) UNTUK APLIKASI ABSENSI KANTOR
 * =========================================================================
 */

enum class PeranPengguna {
  BELUM_PILIH,
  ADMIN,
  KARYAWAN
}

enum class AuthMethod(val label: String) {
  NONE("Belum Diatur"),
  PIN("PIN 6-Digit"),
  FINGERPRINT("Sidik Jari (Fingerprint)"),
  FACE("Pengenalan Wajah (Face Biometric)")
}

enum class StatusAbsensi(val label: String) {
  TEPAT_WAKTU("Tepat Waktu"),
  TERLAMBAT("Terlambat"),
  IZIN("Izin"),
  BELUM_ABSEN("Belum Absen")
}

data class DataKaryawan(
  val id: String,                   // Contoh: "K-001"
  val nip: String,                  // Nomor Induk Pegawai kantor
  val nama: String,                 // Contoh: "Ahmad Fauzi"
  val jenisKelamin: String = "-",         // Contoh: "Laki-laki" atau "Perempuan"
  val tempatTanggalLahir: String = "-",   // Contoh: "Jakarta, 15 Januari 1998"
  val alamat: String = "-",               // Contoh: "Jl. Pemuda No. 12, Surabaya"
  val departemen: String,           // Contoh: "Produksi Pabrik"
  val avatarInisial: String = "AF"
)

data class RiwayatAbsensiItem(
  val id: String,
  val hariTanggal: String,
  val jamMasuk: String,
  val jamPulang: String,
  val status: StatusAbsensi,
  val catatan: String = ""
)

data class AbsensiHariIni(
  val jamMasuk: String? = null,
  val jamPulang: String? = null,
  val status: StatusAbsensi? = null,
  val sudahAbsenMasuk: Boolean = false,
  val sudahAbsenPulang: Boolean = false
)

data class BarisAbsensiKaryawan(
  val idKaryawan: String,
  val nama: String,
  val departemen: String,
  val jamMasuk: String,
  val jamPulang: String,
  val status: StatusAbsensi,
  val mapsUrl: String = "",
  val fotoMasukUrl: String = "",
  val fotoPulangUrl: String = "",
  val recordId: Int = 0,
  val nip: String = "",
  val catatan: String = "",
  val mapsUrlPulang: String = ""
)

data class KonfigurasiAdminDashboard(
  val jamMasukKerja: String = "08:00",
  val jamPulangKerja: String = "17:00",
  val batasTerlambat: String = "08:00",
  val pengumuman: String = "Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.",
  val totalKaryawan: Int = 0,
  val totalHadir: Int = 0,
  val totalTerlambat: Int = 0,
  val totalBelumAbsen: Int = 0,
  val lokasiNama: String = "Kantor Pusat PT Fadiya Kencana",
  val lokasiLatitude: Double = -7.58854,
  val lokasiLongitude: Double = 110.79566,
  val radiusMeter: Int = 100,
  val wajibDalamRadius: Boolean = false
)

data class ItemPengajuanIzinAdmin(
  val id: Int,
  val idKaryawan: String,
  val nip: String = "",
  val nama: String,
  val jenisIzin: String,
  val alasan: String,
  val tanggalDiajukan: String,
  val statusPersetujuan: String,
  val buktiFotoUrl: String = ""
)

data class DashboardAdminData(
  val config: KonfigurasiAdminDashboard = KonfigurasiAdminDashboard(),
  val totalKaryawanMaster: Int = 0,
  val daftarAbsensi: List<BarisAbsensiKaryawan> = emptyList(),
  val totalHadir: Int = 0,
  val totalTerlambat: Int = 0,
  val totalBelumAbsen: Int = 0
)

object DummyDataKantor {
  // Master data karyawan awal kosong: hanya bertambah saat karyawan registrasi di aplikasi
  val daftarKaryawanResmi = emptyList<DataKaryawan>()

  // Karyawan default fallback jika belum ada pendaftaran
  val karyawanAktifDefault = DataKaryawan(
    id = "K-001",
    nip = "19940115001",
    nama = "Karyawan Baru",
    jenisKelamin = "Laki-laki",
    tempatTanggalLahir = "Jakarta, 15 Januari 1994",
    departemen = "Produksi Pabrik",
    avatarInisial = "KB"
  )

  // Riwayat awal: kosong untuk kondisi penggunaan pertama kali
  val riwayatAwal: List<RiwayatAbsensiItem> = emptyList()

  // Data baris tabel absensi hari ini untuk Admin
  val absensiHariIniAdmin: List<BarisAbsensiKaryawan> = emptyList()
}