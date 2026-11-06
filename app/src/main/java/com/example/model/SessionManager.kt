package com.example.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

class SessionManager(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("absensi_session_pref", Context.MODE_PRIVATE)

  companion object {
    private const val KEY_IS_REGISTERED = "key_is_registered"
    private const val KEY_KARYAWAN_ID = "key_karyawan_id"
    private const val KEY_KARYAWAN_NIP = "key_karyawan_nip"
    private const val KEY_KARYAWAN_NAMA = "key_karyawan_nama"
    private const val KEY_KARYAWAN_JENIS_KELAMIN = "key_karyawan_jenis_kelamin"
    private const val KEY_KARYAWAN_TTL = "key_karyawan_ttl"
    private const val KEY_KARYAWAN_ALAMAT = "key_karyawan_alamat"
    private const val KEY_KARYAWAN_DEPARTEMEN = "key_karyawan_departemen"
    private const val KEY_DEVICE_TEST_ID = "key_device_test_id"

    private const val KEY_LAST_NIP = "key_last_nip"
    private const val KEY_LAST_NAMA = "key_last_nama"
    private const val KEY_LAST_DEPARTEMEN = "key_last_departemen"

    private const val KEY_ABSENSI_MASUK = "key_absensi_masuk"
    private const val KEY_ABSENSI_PULANG = "key_absensi_pulang"
    private const val KEY_ABSENSI_STATUS = "key_absensi_status"
    private const val KEY_ABSENSI_SUDAH_MASUK = "key_absensi_sudah_masuk"
    private const val KEY_ABSENSI_SUDAH_PULANG = "key_absensi_sudah_pulang"
    private const val KEY_ABSENSI_TANGGAL = "key_absensi_tanggal"

    private const val KEY_RIWAYAT_JSON = "key_riwayat_json"
    private const val KEY_ADMIN_ABSENSI_JSON = "key_admin_absensi_json"
    private const val KEY_MASTER_KARYAWAN_JSON = "key_master_karyawan_json"
    private const val KEY_JWT_TOKEN = "key_jwt_token"

    private const val KEY_CFG_JAM_MASUK = "key_cfg_jam_masuk"
    private const val KEY_CFG_JAM_PULANG = "key_cfg_jam_pulang"
    private const val KEY_CFG_BATAS_TERLAMBAT = "key_cfg_batas_terlambat"
    private const val KEY_CFG_PENGUMUMAN = "key_cfg_pengumuman"
    private const val KEY_CFG_TOTAL_KARYAWAN = "key_cfg_total_karyawan"
    private const val KEY_CFG_TOTAL_HADIR = "key_cfg_total_hadir"
    private const val KEY_CFG_TOTAL_TERLAMBAT = "key_cfg_total_terlambat"
    private const val KEY_CFG_TOTAL_BELUM_ABSEN = "key_cfg_total_belum_absen"
    private const val KEY_CFG_LOKASI_NAMA = "key_cfg_lokasi_nama"
    private const val KEY_CFG_LOKASI_LAT = "key_cfg_lokasi_lat"
    private const val KEY_CFG_LOKASI_LNG = "key_cfg_lokasi_lng"
    private const val KEY_CFG_RADIUS_METER = "key_cfg_radius_meter"
    private const val KEY_CFG_WAJIB_RADIUS = "key_cfg_wajib_radius"
    private const val KEY_SECURITY_PIN = "key_security_pin"
    private const val KEY_SECURITY_PIN_HASH = "key_security_pin_hash"
    private const val KEY_AUTH_METHOD = "key_auth_method"
    private const val KEY_FAILED_PIN_ATTEMPTS = "key_failed_pin_attempts"
    private const val KEY_LOCKOUT_TIMESTAMP = "key_lockout_timestamp"

    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L // 30 Detik Kunci Sementara
    private const val DEFAULT_INITIAL_PIN = "123456"
  }

  private fun getTodayDateKey(): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
  }

  // --- Keamanan PIN & Hashing SHA-256 ---
  private fun hashPin(pin: String): String {
    val salt = "FadiyaKencana_SecureSalt_2026"
    val bytes = (salt + pin).toByteArray(Charsets.UTF_8)
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun simpanSecurityPin(pin: String) {
    val hash = hashPin(pin)
    prefs.edit()
      .putString(KEY_SECURITY_PIN_HASH, hash)
      .remove(KEY_SECURITY_PIN) // Hapus penyimpanan plaintext jika pernah ada
      .apply()
  }

  fun verifyPin(inputPin: String): Boolean {
    val storedHash = prefs.getString(KEY_SECURITY_PIN_HASH, null)
    if (storedHash != null) {
      val isMatch = storedHash == hashPin(inputPin)
      if (isMatch) resetFailedPinAttempts()
      return isMatch
    }
    // Migrasi otomatis satu arah jika belum ada hash tersimpan
    val legacyPin = prefs.getString(KEY_SECURITY_PIN, DEFAULT_INITIAL_PIN) ?: DEFAULT_INITIAL_PIN
    if (inputPin == legacyPin) {
      simpanSecurityPin(inputPin)
      resetFailedPinAttempts()
      return true
    }
    return false
  }

  fun isSecurityPinSet(): Boolean {
    return prefs.contains(KEY_SECURITY_PIN_HASH) || prefs.contains(KEY_SECURITY_PIN)
  }

  // --- Penyimpanan JWT Token (Akses API Server) ---
  fun getJwtToken(): String? {
    return prefs.getString(KEY_JWT_TOKEN, null)
  }

  fun simpanJwtToken(token: String) {
    prefs.edit().putString(KEY_JWT_TOKEN, token).apply()
  }

  fun hapusJwtToken() {
    prefs.edit().remove(KEY_JWT_TOKEN).apply()
  }

  // --- Konfigurasi Metode Login (Sidik Jari, Wajah, PIN) ---
  fun getAuthMethod(): AuthMethod {
    val raw = prefs.getString(KEY_AUTH_METHOD, null) ?: return AuthMethod.NONE
    return try {
      AuthMethod.valueOf(raw)
    } catch (_: Exception) {
      AuthMethod.NONE
    }
  }

  fun simpanAuthMethod(method: AuthMethod) {
    prefs.edit().putString(KEY_AUTH_METHOD, method.name).apply()
  }

  fun isAuthMethodConfigured(): Boolean {
    return getAuthMethod() != AuthMethod.NONE
  }

  fun resetAuthMethod() {
    prefs.edit().remove(KEY_AUTH_METHOD).apply()
  }

  // --- Perlindungan Percobaan PIN Berulang & Lockout 30 Detik ---
  fun getFailedPinAttempts(): Int {
    return prefs.getInt(KEY_FAILED_PIN_ATTEMPTS, 0)
  }

  fun getLockoutRemainingSeconds(): Long {
    val lockoutTimestamp = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
    if (lockoutTimestamp == 0L) return 0L
    val now = System.currentTimeMillis()
    val elapsed = now - lockoutTimestamp
    return if (elapsed < LOCKOUT_DURATION_MS) {
      ((LOCKOUT_DURATION_MS - elapsed) / 1000L) + 1L
    } else {
      resetFailedPinAttempts()
      0L
    }
  }

  fun recordFailedPinAttempt(): Boolean {
    val current = getFailedPinAttempts() + 1
    val editor = prefs.edit().putInt(KEY_FAILED_PIN_ATTEMPTS, current)
    val isLocked = if (current >= MAX_FAILED_ATTEMPTS) {
      editor.putLong(KEY_LOCKOUT_TIMESTAMP, System.currentTimeMillis())
      true
    } else {
      false
    }
    editor.apply()
    return isLocked
  }

  fun resetFailedPinAttempts() {
    prefs.edit()
      .remove(KEY_FAILED_PIN_ATTEMPTS)
      .remove(KEY_LOCKOUT_TIMESTAMP)
      .apply()
  }

  // --- ID Perangkat ---
  fun getOrCreateDeviceTestId(): String {
    var id = prefs.getString(KEY_DEVICE_TEST_ID, null)
    if (id.isNullOrBlank()) {
      id = "DEV-" + UUID.randomUUID().toString().take(8).uppercase()
      prefs.edit().putString(KEY_DEVICE_TEST_ID, id).apply()
    }
    return id
  }

  // --- Registrasi / Karyawan ---
  fun isSudahTerdaftar(): Boolean {
    return prefs.getBoolean(KEY_IS_REGISTERED, false)
  }

  fun getKaryawanTerdaftar(): DataKaryawan? {
    if (!isSudahTerdaftar()) return null
    val id = prefs.getString(KEY_KARYAWAN_ID, "") ?: ""
    val nip = prefs.getString(KEY_KARYAWAN_NIP, "") ?: ""
    val nama = prefs.getString(KEY_KARYAWAN_NAMA, "") ?: ""
    val jk = prefs.getString(KEY_KARYAWAN_JENIS_KELAMIN, "-") ?: "-"
    val ttl = prefs.getString(KEY_KARYAWAN_TTL, "-") ?: "-"
    val alamat = prefs.getString(KEY_KARYAWAN_ALAMAT, "-") ?: "-"
    val dept = prefs.getString(KEY_KARYAWAN_DEPARTEMEN, "") ?: ""
    return DataKaryawan(
      id = id,
      nip = nip,
      nama = nama,
      jenisKelamin = jk,
      tempatTanggalLahir = ttl,
      alamat = alamat,
      departemen = dept,
      avatarInisial = if (nama.length >= 2) nama.take(2).uppercase() else "KA"
    )
  }

  fun simpanPendaftaran(karyawan: DataKaryawan) {
    prefs.edit()
      .putBoolean(KEY_IS_REGISTERED, true)
      .putString(KEY_KARYAWAN_ID, karyawan.id)
      .putString(KEY_KARYAWAN_NIP, karyawan.nip)
      .putString(KEY_KARYAWAN_NAMA, karyawan.nama)
      .putString(KEY_KARYAWAN_JENIS_KELAMIN, karyawan.jenisKelamin)
      .putString(KEY_KARYAWAN_TTL, karyawan.tempatTanggalLahir)
      .putString(KEY_KARYAWAN_ALAMAT, karyawan.alamat)
      .putString(KEY_KARYAWAN_DEPARTEMEN, karyawan.departemen)
      .apply()
  }

  fun simpanKaryawanTerdaftar(karyawan: DataKaryawan) {
    simpanPendaftaran(karyawan)
  }

  fun hapusPendaftaran() {
    prefs.edit()
      .putBoolean(KEY_IS_REGISTERED, false)
      .remove(KEY_KARYAWAN_ID)
      .remove(KEY_KARYAWAN_NIP)
      .remove(KEY_KARYAWAN_NAMA)
      .remove(KEY_KARYAWAN_JENIS_KELAMIN)
      .remove(KEY_KARYAWAN_TTL)
      .remove(KEY_KARYAWAN_ALAMAT)
      .remove(KEY_KARYAWAN_DEPARTEMEN)
      .remove(KEY_ABSENSI_MASUK)
      .remove(KEY_ABSENSI_PULANG)
      .remove(KEY_ABSENSI_STATUS)
      .remove(KEY_ABSENSI_SUDAH_MASUK)
      .remove(KEY_ABSENSI_SUDAH_PULANG)
      .remove(KEY_ABSENSI_TANGGAL)
      .remove(KEY_RIWAYAT_JSON)
      .remove(KEY_JWT_TOKEN)
      .apply()
  }

  // --- Input Terakhir ---
  fun getInputTerakhir(): DataKaryawan? {
    val nip = prefs.getString(KEY_LAST_NIP, null) ?: return null
    val nama = prefs.getString(KEY_LAST_NAMA, "") ?: ""
    val dept = prefs.getString(KEY_LAST_DEPARTEMEN, "") ?: ""
    return DataKaryawan(
      id = "",
      nip = nip,
      nama = nama,
      jenisKelamin = "-",
      tempatTanggalLahir = "-",
      departemen = dept
    )
  }

  fun simpanInputTerakhir(nip: String, nama: String, departemen: String) {
    prefs.edit()
      .putString(KEY_LAST_NIP, nip)
      .putString(KEY_LAST_NAMA, nama)
      .putString(KEY_LAST_DEPARTEMEN, departemen)
      .apply()
  }

  // --- Absensi Hari Ini (Otomatis Reset Saat Berganti Hari) ---
  fun getAbsensiHariIni(): AbsensiHariIni {
    val savedDate = prefs.getString(KEY_ABSENSI_TANGGAL, null)
    val today = getTodayDateKey()
    if (savedDate != null && savedDate != today) {
      // Sudah ganti hari: bersihkan status absensi hari sebelumnya
      prefs.edit()
        .remove(KEY_ABSENSI_MASUK)
        .remove(KEY_ABSENSI_PULANG)
        .remove(KEY_ABSENSI_STATUS)
        .remove(KEY_ABSENSI_SUDAH_MASUK)
        .remove(KEY_ABSENSI_SUDAH_PULANG)
        .putString(KEY_ABSENSI_TANGGAL, today)
        .apply()
      return AbsensiHariIni()
    }

    val jamMasuk = prefs.getString(KEY_ABSENSI_MASUK, null)
    val jamPulang = prefs.getString(KEY_ABSENSI_PULANG, null)
    val statusStr = prefs.getString(KEY_ABSENSI_STATUS, null)
    val status = statusStr?.let {
      try { StatusAbsensi.valueOf(it) } catch (_: Exception) { null }
    }
    val sudahMasuk = prefs.getBoolean(KEY_ABSENSI_SUDAH_MASUK, false)
    val sudahPulang = prefs.getBoolean(KEY_ABSENSI_SUDAH_PULANG, false)

    return AbsensiHariIni(
      jamMasuk = jamMasuk,
      jamPulang = jamPulang,
      status = status,
      sudahAbsenMasuk = sudahMasuk,
      sudahAbsenPulang = sudahPulang
    )
  }

  fun simpanAbsensiHariIni(absensi: AbsensiHariIni) {
    prefs.edit()
      .putString(KEY_ABSENSI_TANGGAL, getTodayDateKey())
      .putString(KEY_ABSENSI_MASUK, absensi.jamMasuk)
      .putString(KEY_ABSENSI_PULANG, absensi.jamPulang)
      .putString(KEY_ABSENSI_STATUS, absensi.status?.name)
      .putBoolean(KEY_ABSENSI_SUDAH_MASUK, absensi.sudahAbsenMasuk)
      .putBoolean(KEY_ABSENSI_SUDAH_PULANG, absensi.sudahAbsenPulang)
      .apply()
  }

  // --- Riwayat Absensi ---
  fun getRiwayat(): List<RiwayatAbsensiItem> {
    val jsonStr = prefs.getString(KEY_RIWAYAT_JSON, null) ?: return emptyList()
    val result = mutableListOf<RiwayatAbsensiItem>()
    try {
      val jsonArray = JSONArray(jsonStr)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val statusStr = obj.optString("status", StatusAbsensi.TEPAT_WAKTU.name)
        val status = try { StatusAbsensi.valueOf(statusStr) } catch (e: Exception) { StatusAbsensi.TEPAT_WAKTU }
        result.add(
          RiwayatAbsensiItem(
            id = obj.optString("id", UUID.randomUUID().toString()),
            hariTanggal = obj.optString("hariTanggal", ""),
            jamMasuk = obj.optString("jamMasuk", "-"),
            jamPulang = obj.optString("jamPulang", "-"),
            status = status,
            catatan = obj.optString("catatan", "")
          )
        )
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return result
  }

  fun simpanRiwayat(list: List<RiwayatAbsensiItem>) {
    try {
      val jsonArray = JSONArray()
      // Batasi maksimal 60 riwayat terakhir agar memori penyimpanan lokal HP tetap ringan
      for (item in list.take(60)) {
        val obj = JSONObject().apply {
          put("id", item.id)
          put("hariTanggal", item.hariTanggal)
          put("jamMasuk", item.jamMasuk)
          put("jamPulang", item.jamPulang)
          put("status", item.status.name)
          put("catatan", item.catatan)
        }
        jsonArray.put(obj)
      }
      prefs.edit().putString(KEY_RIWAYAT_JSON, jsonArray.toString()).apply()
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  // --- Konfigurasi Admin ---
  fun getKonfigurasiAdmin(): KonfigurasiAdminDashboard {
    return KonfigurasiAdminDashboard(
      jamMasukKerja = prefs.getString(KEY_CFG_JAM_MASUK, "08:00") ?: "08:00",
      jamPulangKerja = prefs.getString(KEY_CFG_JAM_PULANG, "17:00") ?: "17:00",
      batasTerlambat = prefs.getString(KEY_CFG_BATAS_TERLAMBAT, "08:00") ?: "08:00",
      pengumuman = prefs.getString(KEY_CFG_PENGUMUMAN, "Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.")
        ?: "Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.",
      totalKaryawan = prefs.getInt(KEY_CFG_TOTAL_KARYAWAN, 0),
      totalHadir = prefs.getInt(KEY_CFG_TOTAL_HADIR, 0),
      totalTerlambat = prefs.getInt(KEY_CFG_TOTAL_TERLAMBAT, 0),
      totalBelumAbsen = prefs.getInt(KEY_CFG_TOTAL_BELUM_ABSEN, 0),
      lokasiNama = prefs.getString(KEY_CFG_LOKASI_NAMA, "Kantor Pusat PT Fadiya Kencana") ?: "Kantor Pusat PT Fadiya Kencana",
      lokasiLatitude = (prefs.getString(KEY_CFG_LOKASI_LAT, "-7.58854") ?: "-7.58854").toDoubleOrNull() ?: -7.58854,
      lokasiLongitude = (prefs.getString(KEY_CFG_LOKASI_LNG, "110.79566") ?: "110.79566").toDoubleOrNull() ?: 110.79566,
      radiusMeter = prefs.getInt(KEY_CFG_RADIUS_METER, 100),
      wajibDalamRadius = prefs.getBoolean(KEY_CFG_WAJIB_RADIUS, false)
    )
  }

  fun simpanKonfigurasiAdmin(config: KonfigurasiAdminDashboard) {
    prefs.edit()
      .putString(KEY_CFG_JAM_MASUK, config.jamMasukKerja)
      .putString(KEY_CFG_JAM_PULANG, config.jamPulangKerja)
      .putString(KEY_CFG_BATAS_TERLAMBAT, config.batasTerlambat)
      .putString(KEY_CFG_PENGUMUMAN, config.pengumuman)
      .putInt(KEY_CFG_TOTAL_KARYAWAN, config.totalKaryawan)
      .putInt(KEY_CFG_TOTAL_HADIR, config.totalHadir)
      .putInt(KEY_CFG_TOTAL_TERLAMBAT, config.totalTerlambat)
      .putInt(KEY_CFG_TOTAL_BELUM_ABSEN, config.totalBelumAbsen)
      .putString(KEY_CFG_LOKASI_NAMA, config.lokasiNama)
      .putString(KEY_CFG_LOKASI_LAT, config.lokasiLatitude.toString())
      .putString(KEY_CFG_LOKASI_LNG, config.lokasiLongitude.toString())
      .putInt(KEY_CFG_RADIUS_METER, config.radiusMeter)
      .putBoolean(KEY_CFG_WAJIB_RADIUS, config.wajibDalamRadius)
      .apply()
  }

  // --- Admin Daftar Absensi ---
  fun getAdminDaftarAbsensi(): List<BarisAbsensiKaryawan> {
    val jsonStr = prefs.getString(KEY_ADMIN_ABSENSI_JSON, null) ?: return emptyList()
    val result = mutableListOf<BarisAbsensiKaryawan>()
    try {
      val jsonArray = JSONArray(jsonStr)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val statusStr = obj.optString("status", StatusAbsensi.TEPAT_WAKTU.name)
        val status = try { StatusAbsensi.valueOf(statusStr) } catch (e: Exception) { StatusAbsensi.TEPAT_WAKTU }
        result.add(
          BarisAbsensiKaryawan(
            idKaryawan = obj.optString("idKaryawan", ""),
            nama = obj.optString("nama", ""),
            departemen = obj.optString("departemen", ""),
            jamMasuk = obj.optString("jamMasuk", "-"),
            jamPulang = obj.optString("jamPulang", "-"),
            status = status,
            mapsUrl = obj.optString("mapsUrl", "")
          )
        )
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return result
  }

  fun simpanAdminDaftarAbsensi(list: List<BarisAbsensiKaryawan>) {
    try {
      val jsonArray = JSONArray()
      for (item in list) {
        val obj = JSONObject().apply {
          put("idKaryawan", item.idKaryawan)
          put("nama", item.nama)
          put("departemen", item.departemen)
          put("jamMasuk", item.jamMasuk)
          put("jamPulang", item.jamPulang)
          put("status", item.status.name)
          put("mapsUrl", item.mapsUrl)
        }
        jsonArray.put(obj)
      }
      prefs.edit().putString(KEY_ADMIN_ABSENSI_JSON, jsonArray.toString()).apply()
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun tambahAtauUpdateAbsensiKaryawan(baris: BarisAbsensiKaryawan) {
    val list = getAdminDaftarAbsensi().toMutableList()
    val index = list.indexOfFirst { it.idKaryawan == baris.idKaryawan }
    if (index >= 0) {
      list[index] = baris
    } else {
      list.add(baris)
    }
    simpanAdminDaftarAbsensi(list)
  }

  fun resetAdminAbsensiHariIni() {
    prefs.edit().remove(KEY_ADMIN_ABSENSI_JSON).apply()
  }

  // --- Master Karyawan ---
  fun getMasterKaryawanList(): List<DataKaryawan> {
    val jsonStr = prefs.getString(KEY_MASTER_KARYAWAN_JSON, null) ?: return emptyList()
    val result = mutableListOf<DataKaryawan>()
    try {
      val jsonArray = JSONArray(jsonStr)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val nama = obj.optString("nama", "")
        result.add(
          DataKaryawan(
            id = obj.optString("id", ""),
            nip = obj.optString("nip", ""),
            nama = nama,
            jenisKelamin = obj.optString("jenisKelamin", "-"),
            tempatTanggalLahir = obj.optString("tempatTanggalLahir", "-"),
            alamat = obj.optString("alamat", "-"),
            departemen = obj.optString("departemen", ""),
            avatarInisial = obj.optString("avatarInisial", if (nama.length >= 2) nama.take(2).uppercase() else "KA")
          )
        )
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return result
  }

  fun simpanMasterKaryawan(list: List<DataKaryawan>) {
    try {
      val jsonArray = JSONArray()
      for (item in list) {
        val obj = JSONObject().apply {
          put("id", item.id)
          put("nip", item.nip)
          put("nama", item.nama)
          put("jenisKelamin", item.jenisKelamin)
          put("tempatTanggalLahir", item.tempatTanggalLahir)
          put("alamat", item.alamat)
          put("departemen", item.departemen)
          put("avatarInisial", item.avatarInisial)
        }
        jsonArray.put(obj)
      }
      prefs.edit().putString(KEY_MASTER_KARYAWAN_JSON, jsonArray.toString()).apply()
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
