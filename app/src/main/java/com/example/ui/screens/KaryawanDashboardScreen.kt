package com.example.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.model.BarisAbsensiKaryawan
import com.example.model.DataKaryawan
import com.example.model.JaringanKantorManager
import com.example.model.RiwayatAbsensiItem
import com.example.model.SessionManager
import com.example.model.StatusAbsensi
import com.example.network.XamppApiClient
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaryawanDashboardScreen(
  karyawan: DataKaryawan,
  onLogout: () -> Unit,
  onBack: () -> Unit,
  jaringanManager: JaringanKantorManager? = null,
  onChangeAuthMethod: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val sessionManager = remember { SessionManager(context) }
  var showSecurityDialog by remember { mutableStateOf(false) }
  var showSecurityMenuDialog by remember { mutableStateOf(false) }
  var showUbahPinDialog by remember { mutableStateOf(false) }
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }
  var showJadwalDialog by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Beranda Presensi, 1 = Riwayat Simpel
  var profilKaryawan by remember(karyawan) { mutableStateOf(karyawan) }
  var isRefreshingServer by remember { mutableStateOf(false) }
  var isMasuk by remember { mutableStateOf(false) }
  var isSelesai by remember { mutableStateOf(false) }
  var isSedangIzinHariIni by remember { mutableStateOf(false) }
  var jamMasukHariIni by remember { mutableStateOf<String?>(null) }
  var jamPulangHariIni by remember { mutableStateOf<String?>(null) }
  var isProsesAbsen by remember { mutableStateOf(false) }
  var statusPesan by remember { mutableStateOf<String?>(null) }
  var isError by remember { mutableStateOf(false) }
  var capturedSelfieBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var pendingSelfieBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var catatanPresensiInput by remember { mutableStateOf("") }
  var riwayatList by remember { mutableStateOf(sessionManager.getRiwayat()) }
  var configKantor by remember { mutableStateOf(sessionManager.getKonfigurasiAdmin()) }

  // Otomatis sembunyikan pesan sukses setelah 5 detik agar halaman tetap lega
  LaunchedEffect(statusPesan, isError) {
    if (statusPesan != null && !isError) {
      delay(5000L)
      statusPesan = null
    }
  }

  // Helper evaluasi keterlambatan berbasis menit yang akurat terhadap batasTerlambat kantor
  fun isMelewatiBatasTerlambat(jamInput: String, batasInput: String): Boolean {
    val cleanJam = jamInput.replace("WIB", "").replace(".", ":").trim()
    val cleanBatas = batasInput.replace("WIB", "").replace(".", ":").trim()
    val jamParts = cleanJam.split(":")
    val batasParts = cleanBatas.split(":")
    val hA = jamParts.getOrNull(0)?.toIntOrNull() ?: 8
    val mA = jamParts.getOrNull(1)?.toIntOrNull() ?: 0
    val hB = batasParts.getOrNull(0)?.toIntOrNull() ?: 8
    val mB = batasParts.getOrNull(1)?.toIntOrNull() ?: 0
    return (hA * 60 + mA) > (hB * 60 + mB)
  }

  // State Dialog Pengajuan Izin & Riwayat Izin Karyawan
  var showIzinDialog by remember { mutableStateOf(false) }
  var jenisIzinInput by remember { mutableStateOf("Sakit") }
  var tanggalMulaiInput by remember { mutableStateOf("") }
  var tanggalSelesaiInput by remember { mutableStateOf("") }
  var alasanInput by remember { mutableStateOf("") }
  var buktiFotoIzinBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var buktiFotoIzinBase64 by remember { mutableStateOf("") }
  var isSubmittingIzin by remember { mutableStateOf(false) }
  var popupHasilIzinJudul by remember { mutableStateOf<String?>(null) }
  var popupHasilIzinPesan by remember { mutableStateOf("") }
  var popupHasilIzinSukses by remember { mutableStateOf(true) }
  var daftarIzinSaya by remember { mutableStateOf<List<com.example.model.ItemPengajuanIzinAdmin>>(emptyList()) }

  // State Pemilih Bulan untuk Ringkasan Mandiri & Filter Riwayat Karyawan
  var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
  val namaBulanTahun = remember(selectedCalendar) {
    val raw = SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(selectedCalendar.time)
    raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  }
  val isBulanSekarang = remember(selectedCalendar) {
    val now = Calendar.getInstance()
    now.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
      now.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)
  }
  val slashPattern = remember(selectedCalendar) {
    SimpleDateFormat("/MM/yyyy", Locale.getDefault()).format(selectedCalendar.time)
  }
  val isoPattern = remember(selectedCalendar) {
    SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(selectedCalendar.time)
  }

  // Riwayat terfilter khusus bulan yang dipilih
  val riwayatBulanTerpilih = remember(riwayatList, slashPattern, isoPattern) {
    riwayatList.filter { item ->
      item.hariTanggal.contains(slashPattern) || item.hariTanggal.contains(isoPattern)
    }
  }

  // 3 Angka Ringkasan Kedisiplinan Bulan Terpilih (Hadir, Terlambat, Izin)
  val totalHadirBulan = remember(riwayatBulanTerpilih) {
    riwayatBulanTerpilih.count {
      it.status == StatusAbsensi.TEPAT_WAKTU || it.status == StatusAbsensi.TERLAMBAT
    }
  }
  val totalTerlambatBulan = remember(riwayatBulanTerpilih) {
    riwayatBulanTerpilih.count {
      it.status == StatusAbsensi.TERLAMBAT
    }
  }
  val totalIzinBulan = remember(daftarIzinSaya, isoPattern, slashPattern) {
    daftarIzinSaya.count {
      (it.tanggalDiajukan.contains(isoPattern) || it.tanggalDiajukan.contains(slashPattern)) &&
        it.statusPersetujuan.startsWith("Disetujui", ignoreCase = true)
    }
  }

  // Ringkasan khusus bulan berjalan (untuk preview di Beranda)
  val nowCal = remember { Calendar.getInstance() }
  val slashNow = remember(nowCal) { SimpleDateFormat("/MM/yyyy", Locale.getDefault()).format(nowCal.time) }
  val isoNow = remember(nowCal) { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(nowCal.time) }
  val namaBulanNow = remember(nowCal) {
    val r = SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(nowCal.time)
    r.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  }
  val hadirNowCount = remember(riwayatList, slashNow, isoNow) {
    riwayatList.filter { it.hariTanggal.contains(slashNow) || it.hariTanggal.contains(isoNow) }
      .count { it.status == StatusAbsensi.TEPAT_WAKTU || it.status == StatusAbsensi.TERLAMBAT }
  }
  val telatNowCount = remember(riwayatList, slashNow, isoNow) {
    riwayatList.filter { it.hariTanggal.contains(slashNow) || it.hariTanggal.contains(isoNow) }
      .count { it.status == StatusAbsensi.TERLAMBAT }
  }
  val izinNowCount = remember(daftarIzinSaya, isoNow, slashNow) {
    daftarIzinSaya.count {
      (it.tanggalDiajukan.contains(isoNow) || it.tanggalDiajukan.contains(slashNow)) &&
        it.statusPersetujuan.startsWith("Disetujui", ignoreCase = true)
    }
  }

  fun muatDaftarIzinSaya() {
    coroutineScope.launch {
      // 1. Sinkronkan log riwayat kehadiran karyawan dari server MySQL (termasuk hasil koreksi/izin dari Web Admin)
      val riwayatRes = XamppApiClient.getRiwayatAbsensiKaryawan(karyawan.nip, karyawan.id)
      if (riwayatRes.isSuccess) {
        val serverRiwayat = riwayatRes.getOrNull().orEmpty()
        riwayatList = serverRiwayat
        sessionManager.simpanRiwayat(serverRiwayat)
      }

      // 2. Sinkronkan daftar permohonan izin milik karyawan ini dari server MySQL
      val izinRes = XamppApiClient.getDaftarPengajuanIzin()
      if (izinRes.isSuccess) {
        val semua = izinRes.getOrNull().orEmpty()
        daftarIzinSaya = semua.filter {
          it.nip.equals(profilKaryawan.nip, ignoreCase = true) ||
            it.idKaryawan.equals(profilKaryawan.id, ignoreCase = true) ||
            it.nama.equals(profilKaryawan.nama, ignoreCase = true)
        }
      }
    }
  }

  // Fungsi Segarkan Seluruh Informasi dari Server / Web Admin (Pengumuman, Jam Kerja, Profil, Status Presensi & Riwayat)
  fun segarkanDataDariServer(tampilkanToast: Boolean = false) {
    coroutineScope.launch {
      isRefreshingServer = true

      // 1. Segarkan Pengaturan Jam Kerja, Batas Terlambat & Pengumuman Kantor
      val cfgRes = XamppApiClient.getPengaturanKantor()
      if (cfgRes.isSuccess) {
        cfgRes.getOrNull()?.let { serverCfg ->
          configKantor = serverCfg
          sessionManager.simpanKonfigurasiAdmin(serverCfg)
          jaringanManager?.updatePengaturanGpsKantor(
            lat = serverCfg.lokasiLatitude,
            lng = serverCfg.lokasiLongitude,
            radius = serverCfg.radiusMeter,
            bypass = !serverCfg.wajibDalamRadius
          )
        }
      }

      // 2. Segarkan Profil Karyawan (Sinkronisasi dua arah Web Admin <-> HP)
      val currentLocal = sessionManager.getKaryawanTerdaftar()
      val localAlamat = currentLocal?.alamat?.takeIf { it != "-" } ?: profilKaryawan.alamat.takeIf { it != "-" }.orEmpty()
      val localTtl = currentLocal?.tempatTanggalLahir?.takeIf { it != "-" && it != "-, -" } ?: profilKaryawan.tempatTanggalLahir.takeIf { it != "-" && it != "-, -" }.orEmpty()
      val localJk = currentLocal?.jenisKelamin?.takeIf { it != "-" } ?: profilKaryawan.jenisKelamin.takeIf { it != "-" }.orEmpty()

      val profilRes = XamppApiClient.cekAtauLoginKaryawan(
        nip = profilKaryawan.nip,
        deviceId = sessionManager.getOrCreateDeviceTestId(),
        alamat = localAlamat,
        ttl = localTtl,
        jk = localJk
      )
      if (profilRes.isSuccess) {
        profilRes.getOrNull()?.let { updatedKaryawan ->
          val cleanServerTtl = updatedKaryawan.tempatTanggalLahir.replace("-, -", "-").trim()
          val mergedTtl = if (cleanServerTtl != "-" && cleanServerTtl.isNotBlank()) cleanServerTtl else (if (localTtl.isNotBlank()) localTtl else "-")
          val mergedAlamat = if (updatedKaryawan.alamat != "-" && updatedKaryawan.alamat.isNotBlank()) updatedKaryawan.alamat else (if (localAlamat.isNotBlank()) localAlamat else "-")
          val mergedJk = if (updatedKaryawan.jenisKelamin != "-" && updatedKaryawan.jenisKelamin.isNotBlank()) updatedKaryawan.jenisKelamin else (if (localJk.isNotBlank()) localJk else "-")

          val finalMerged = updatedKaryawan.copy(
            tempatTanggalLahir = mergedTtl,
            alamat = mergedAlamat,
            jenisKelamin = mergedJk
          )
          profilKaryawan = finalMerged
          sessionManager.simpanKaryawanTerdaftar(finalMerged)
        }
        if (!XamppApiClient.jwtToken.isNullOrBlank()) {
          sessionManager.simpanJwtToken(XamppApiClient.jwtToken!!)
        }
      }

      // 3. Segarkan Status Presensi Hari Ini
      val resStatus = XamppApiClient.cekStatusAbsensiKaryawan(profilKaryawan.nip, profilKaryawan.id)
      if (resStatus.isSuccess) {
        val absensiState = resStatus.getOrNull()
        if (absensiState != null) {
          val sedangIzin = (absensiState.status == StatusAbsensi.IZIN)
          isSedangIzinHariIni = sedangIzin
          jamMasukHariIni = if (sedangIzin) null else absensiState.jamMasuk
          jamPulangHariIni = if (sedangIzin) null else absensiState.jamPulang
          isMasuk = if (sedangIzin) false else absensiState.sudahAbsenMasuk
          isSelesai = if (sedangIzin) false else absensiState.sudahAbsenPulang
          sessionManager.simpanAbsensiHariIni(absensiState)
        }
      }

      // 4. Segarkan Riwayat Kehadiran & Daftar Izin
      muatDaftarIzinSaya()

      isRefreshingServer = false
      if (tampilkanToast) {
        Toast.makeText(context, "Data & pengumuman kantor berhasil diperbarui", Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Launcher Izin Lokasi GPS (Realtime Tracking)
  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
    if (fine || coarse) {
      jaringanManager?.mulaiPelacakanGpsRealtime()
    }
  }

  // Minta izin lokasi & kelola GPS secara pintar (Otomatis mati saat presensi selesai / pindah ke tab Riwayat)
  LaunchedEffect(isSelesai, isSedangIzinHariIni, selectedTab) {
    if (jaringanManager != null) {
      if (isSelesai || isSedangIzinHariIni || selectedTab != 0) {
        // Hemat baterai: matikan pembaruan GPS jika sudah selesai absen pulang, sedang izin, atau di tab Riwayat
        jaringanManager.hentikanPelacakanGps()
      } else if (!jaringanManager.hasLocationPermission()) {
        locationPermissionLauncher.launch(
          arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      } else {
        jaringanManager.mulaiPelacakanGpsRealtime()
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      jaringanManager?.hentikanPelacakanGps()
    }
  }

  // Sinkronisasi status absensi hari ini (Local offline-first + Server)
  LaunchedEffect(karyawan.nip) {
    // 1. Cek data lokal tersimpan terlebih dahulu
    val localAbsensi = sessionManager.getAbsensiHariIni()
    jamMasukHariIni = localAbsensi.jamMasuk
    jamPulangHariIni = localAbsensi.jamPulang
    if (localAbsensi.sudahAbsenPulang) {
      isMasuk = true
      isSelesai = true
      isError = false
    } else if (localAbsensi.sudahAbsenMasuk) {
      isMasuk = true
      isSelesai = false
      isError = false
    }

    // 2. Pastikan Token JWT tersedia (Auto-Refresh jika sesi dibuka ulang)
    if (XamppApiClient.jwtToken.isNullOrBlank()) {
      val savedToken = sessionManager.getJwtToken()
      if (!savedToken.isNullOrBlank()) {
        XamppApiClient.jwtToken = savedToken
      } else {
        val loginRefresh = XamppApiClient.cekAtauLoginKaryawan(
          karyawan.nip,
          sessionManager.getOrCreateDeviceTestId()
        )
        if (loginRefresh.isSuccess && !XamppApiClient.jwtToken.isNullOrBlank()) {
          sessionManager.simpanJwtToken(XamppApiClient.jwtToken!!)
        }
      }
    }

    // 3. Sinkronkan pengaturan jam kerja & pengumuman kantor dari server
    val cfgRes = XamppApiClient.getPengaturanKantor()
    if (cfgRes.isSuccess) {
      cfgRes.getOrNull()?.let { serverCfg ->
        configKantor = serverCfg
        sessionManager.simpanKonfigurasiAdmin(serverCfg)
      }
    }

    // 4. Sinkronkan status kehadiran dengan server backend (Sumber Kebenaran Utama)
    val resStatus = XamppApiClient.cekStatusAbsensiKaryawan(karyawan.nip, karyawan.id)
    if (resStatus.isSuccess) {
      val absensiState = resStatus.getOrNull()
      if (absensiState != null) {
        val sedangIzin = (absensiState.status == StatusAbsensi.IZIN)
        isSedangIzinHariIni = sedangIzin
        jamMasukHariIni = if (sedangIzin) null else absensiState.jamMasuk
        jamPulangHariIni = if (sedangIzin) null else absensiState.jamPulang
        isMasuk = if (sedangIzin) false else absensiState.sudahAbsenMasuk
        isSelesai = if (sedangIzin) false else absensiState.sudahAbsenPulang
        sessionManager.simpanAbsensiHariIni(absensiState)
        isError = false
      }
    }

    // 5. Muat daftar riwayat kehadiran & status pengajuan izin milik karyawan ini dari server
    muatDaftarIzinSaya()
  }

  // Fungsi Kecilkan & Tambahkan Watermark pada Bitmap (Hemat RAM HP)
  fun applyWatermarkToBitmap(originalBitmap: Bitmap, karyawanNama: String, lat: Double, lng: Double): Bitmap {
    val maxDim = 720
    val baseBitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
      val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
      val targetW = if (ratio > 1f) maxDim else (maxDim * ratio).toInt()
      val targetH = if (ratio > 1f) (maxDim / ratio).toInt() else maxDim
      Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
    } else {
      originalBitmap
    }
    val resultBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(resultBitmap)

    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale("id", "ID"))
    val currentDateTime = dateFormat.format(Date())

    val paint = android.graphics.Paint().apply {
      color = android.graphics.Color.WHITE
      textSize = resultBitmap.height * 0.04f
      isAntiAlias = true
      setShadowLayer(5f, 2f, 2f, android.graphics.Color.BLACK)
    }

    val padding = 20f
    var yPos = resultBitmap.height - padding - (paint.textSize * 3)

    canvas.drawText("FADIYA KENCANA - HRIS", padding, yPos, paint)
    yPos += paint.textSize + 5f
    canvas.drawText("Waktu  : $currentDateTime WIB", padding, yPos, paint)
    yPos += paint.textSize + 5f
    canvas.drawText("Lokasi : $lat, $lng", padding, yPos, paint)
    yPos += paint.textSize + 5f
    canvas.drawText("User   : $karyawanNama", padding, yPos, paint)

    return resultBitmap
  }

  // Fungsi Proses Kirim Absensi dengan Foto Swafoto yang sudah di-preview & diberi catatan lokasi
  val prosesKirimAbsensi: (Bitmap, String) -> Unit = { watermarkedBitmap, catatanInput ->
    val realLat = jaringanManager?.currentLatitude ?: -7.250445
    val realLng = jaringanManager?.currentLongitude ?: 112.768845
    capturedSelfieBitmap = watermarkedBitmap

    val outputStream = ByteArrayOutputStream()
    watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 72, outputStream)
    val base64Foto = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)

    val tipeAbsen = if (isMasuk) "keluar" else "masuk"
    val jamFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) + " WIB"
    val tanggalFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("id", "ID")).format(Date())

    val mapsUrl = jaringanManager?.mapsUrl ?: "https://www.google.com/maps?q=$realLat,$realLng"

    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
    val currentHourMin = sdfTime.format(Date())
    val statusAbsen = if (tipeAbsen == "masuk") {
      if (isMelewatiBatasTerlambat(currentHourMin, configKantor.batasTerlambat)) {
        StatusAbsensi.TERLAMBAT
      } else {
        StatusAbsensi.TEPAT_WAKTU
      }
    } else {
      StatusAbsensi.TEPAT_WAKTU
    }

    coroutineScope.launch {
      isProsesAbsen = true

      // Pastikan token tersedia sebelum mengirim absen
      if (XamppApiClient.jwtToken.isNullOrBlank()) {
        val refreshRes = XamppApiClient.cekAtauLoginKaryawan(
          karyawan.nip,
          sessionManager.getOrCreateDeviceTestId()
        )
        if (refreshRes.isSuccess && !XamppApiClient.jwtToken.isNullOrBlank()) {
          sessionManager.simpanJwtToken(XamppApiClient.jwtToken!!)
        }
      }

      // Siapkan data profil lengkap untuk sinkronisasi otomatis ke server saat presensi
      val currentActive = sessionManager.getKaryawanTerdaftar()
      val activeTtl = currentActive?.tempatTanggalLahir ?: profilKaryawan.tempatTanggalLahir
      val activeAlamat = currentActive?.alamat ?: profilKaryawan.alamat
      val activeJk = currentActive?.jenisKelamin ?: profilKaryawan.jenisKelamin

      val (tLahir, tglLahir) = if (activeTtl.contains(",")) {
        val p = activeTtl.split(",")
        Pair(p[0].trim(), p.drop(1).joinToString(",").trim())
      } else {
        Pair(activeTtl.trim(), "")
      }

      // Kirim ke server terlebih dahulu agar status terjamin masuk ke database MySQL
      var res = XamppApiClient.kirimAbsen(
        idKaryawan = karyawan.id,
        nip = karyawan.nip,
        nama = karyawan.nama,
        departemen = karyawan.departemen,
        tipe = tipeAbsen,
        jam = currentHourMin,
        statusAbsen = statusAbsen.name,
        fotoSelfie = base64Foto,
        latitude = realLat,
        longitude = realLng,
        mapsUrl = mapsUrl,
        catatan = catatanInput.trim(),
        jenisKelamin = activeJk,
        tempatLahir = tLahir,
        tanggalLahir = tglLahir,
        alamat = activeAlamat,
        ttl = activeTtl
      )

      // Jika gagal karena sesi token kedaluwarsa, perbarui token 1x lalu coba kirim ulang otomatis
      if (res.isFailure && (res.exceptionOrNull()?.message?.contains("Token", ignoreCase = true) == true ||
          res.exceptionOrNull()?.message?.contains("Sesi", ignoreCase = true) == true)) {
        val relogin = XamppApiClient.cekAtauLoginKaryawan(
          nip = karyawan.nip,
          deviceId = sessionManager.getOrCreateDeviceTestId(),
          alamat = if (activeAlamat != "-") activeAlamat else "",
          ttl = if (activeTtl != "-") activeTtl else "",
          jk = if (activeJk != "-") activeJk else ""
        )
        if (relogin.isSuccess && !XamppApiClient.jwtToken.isNullOrBlank()) {
          sessionManager.simpanJwtToken(XamppApiClient.jwtToken!!)
          res = XamppApiClient.kirimAbsen(
            idKaryawan = karyawan.id,
            nip = karyawan.nip,
            nama = karyawan.nama,
            departemen = karyawan.departemen,
            tipe = tipeAbsen,
            jam = currentHourMin,
            statusAbsen = statusAbsen.name,
            fotoSelfie = base64Foto,
            latitude = realLat,
            longitude = realLng,
            mapsUrl = mapsUrl,
            catatan = catatanInput.trim(),
            jenisKelamin = activeJk,
            tempatLahir = tLahir,
            tanggalLahir = tglLahir,
            alamat = activeAlamat,
            ttl = activeTtl
          )
        }
      }

      isProsesAbsen = false

      if (res.isSuccess) {
        // Hanya kunci status absen jika berhasil diterima oleh server
        val currentAbsensi = sessionManager.getAbsensiHariIni()
        val updatedAbsensi = if (tipeAbsen == "masuk") {
          currentAbsensi.copy(
            jamMasuk = jamFormat,
            status = statusAbsen,
            sudahAbsenMasuk = true
          )
        } else {
          currentAbsensi.copy(
            jamPulang = jamFormat,
            sudahAbsenPulang = true
          )
        }
        sessionManager.simpanAbsensiHariIni(updatedAbsensi)

        val riwayatBaru = sessionManager.getRiwayat().toMutableList()
        val todayKey = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val existingIdx = riwayatBaru.indexOfFirst { it.hariTanggal.contains(todayKey) }
        val catatanRiwayat = catatanInput.trim().ifEmpty { "Swafoto ($tipeAbsen)" }
        if (existingIdx >= 0) {
          val existing = riwayatBaru[existingIdx]
          riwayatBaru[existingIdx] = existing.copy(
            jamPulang = if (tipeAbsen == "keluar") jamFormat else existing.jamPulang,
            catatan = catatanInput.trim().ifEmpty { existing.catatan }
          )
        } else {
          riwayatBaru.add(
            0,
            RiwayatAbsensiItem(
              id = UUID.randomUUID().toString(),
              hariTanggal = tanggalFormat,
              jamMasuk = if (tipeAbsen == "masuk") jamFormat else "-",
              jamPulang = if (tipeAbsen == "keluar") jamFormat else "-",
              status = statusAbsen,
              catatan = catatanRiwayat
            )
          )
        }
        sessionManager.simpanRiwayat(riwayatBaru)
        riwayatList = riwayatBaru

        sessionManager.tambahAtauUpdateAbsensiKaryawan(
          BarisAbsensiKaryawan(
            idKaryawan = karyawan.id,
            nama = karyawan.nama,
            departemen = karyawan.departemen,
            jamMasuk = updatedAbsensi.jamMasuk ?: "-",
            jamPulang = updatedAbsensi.jamPulang ?: "-",
            status = statusAbsen,
            mapsUrl = mapsUrl
          )
        )

        if (tipeAbsen == "masuk") {
          isMasuk = true
          jamMasukHariIni = jamFormat
        } else {
          isSelesai = true
          jamPulangHariIni = jamFormat
        }

        statusPesan = if (tipeAbsen == "masuk") {
          "Presensi masuk berhasil dicatat pada pukul $jamFormat."
        } else {
          "Presensi pulang berhasil dicatat pada pukul $jamFormat."
        }
        isError = false
      } else {
        val errMsg = res.exceptionOrNull()?.message ?: "Tidak dapat terhubung ke server."
        statusPesan = "Gagal mengirim presensi: $errMsg"
        isError = true
      }
    }
  }

  // Launcher Kamera Swafoto -> Tampilkan Dialog Preview & Catatan Lokasi terlebih dahulu
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap: Bitmap? ->
    if (bitmap != null) {
      val realLat = jaringanManager?.currentLatitude ?: -7.250445
      val realLng = jaringanManager?.currentLongitude ?: 112.768845
      pendingSelfieBitmap = applyWatermarkToBitmap(bitmap, karyawan.nama, realLat, realLng)
      catatanPresensiInput = ""
    } else {
      statusPesan = "Pengambilan foto swafoto dibatalkan."
      isError = true
    }
  }

  // Helper kompres Bitmap untuk bukti surat/izin (Opsional)
  val simpanBitmapBuktiIzin: (Bitmap) -> Unit = { bitmap ->
    val maxDim = 720
    val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
      val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
      val w = if (ratio > 1f) maxDim else (maxDim * ratio).toInt()
      val h = if (ratio > 1f) (maxDim / ratio).toInt() else maxDim
      Bitmap.createScaledBitmap(bitmap, w, h, true)
    } else {
      bitmap
    }
    buktiFotoIzinBitmap = scaled
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
    buktiFotoIzinBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
  }

  // Launcher Kamera untuk Bukti Surat Dokter / Izin (Opsional)
  val izinFotoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap: Bitmap? ->
    if (bitmap != null) {
      simpanBitmapBuktiIzin(bitmap)
    }
  }

  // Launcher Galeri untuk Bukti Surat Dokter / Izin (Opsional)
  val izinGaleriLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val source = ImageDecoder.createSource(context.contentResolver, uri)
          ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = true
          }
        } else {
          @Suppress("DEPRECATION")
          MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        if (bmp != null) {
          simpanBitmapBuktiIzin(bmp)
        }
      } catch (e: Exception) {
        Toast.makeText(context, "Gagal memuat gambar dari galeri", Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Launcher Izin Kamera khusus untuk Foto Bukti Izin (Opsional)
  val izinCameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      try {
        izinFotoLauncher.launch(null)
      } catch (e: Exception) {
        Toast.makeText(context, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    } else {
      Toast.makeText(context, "Izin kamera ditolak. Anda tetap bisa memilih foto dari Galeri atau mengosongkannya.", Toast.LENGTH_SHORT).show()
    }
  }

  // Launcher Izin Akses Kamera Runtime
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      try {
        cameraLauncher.launch(null)
      } catch (e: ActivityNotFoundException) {
        statusPesan = "Aplikasi kamera tidak ditemukan di perangkat ini."
        isError = true
      } catch (e: Exception) {
        statusPesan = "Gagal membuka kamera: ${e.message}"
        isError = true
      }
    } else {
      statusPesan = "Izin akses kamera diperlukan untuk melakukan presensi swafoto."
      isError = true
    }
  }

  val bukaKameraSafely: () -> Unit = {
    if (jaringanManager != null && jaringanManager.isFakeGpsDetected) {
      statusPesan = "Presensi ditolak: Terdeteksi penggunaan lokasi palsu (Fake GPS). Matikan aplikasi Mock Location terlebih dahulu."
      isError = true
    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      try {
        cameraLauncher.launch(null)
      } catch (e: ActivityNotFoundException) {
        statusPesan = "Aplikasi kamera tidak ditemukan di perangkat ini."
        isError = true
      } catch (e: SecurityException) {
        statusPesan = "Izin kamera diperlukan: ${e.message}"
        isError = true
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
      } catch (e: Exception) {
        statusPesan = "Gagal membuka kamera: ${e.message}"
        isError = true
      }
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  val sapaanWaktu = remember {
    val jam = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    when (jam) {
      in 4..10 -> "Selamat pagi"
      in 11..14 -> "Selamat siang"
      in 15..17 -> "Selamat sore"
      else -> "Selamat malam"
    }
  }
  val namaDepan = remember(profilKaryawan.nama) {
    val raw = profilKaryawan.nama.trim().substringBefore(" ").ifEmpty { profilKaryawan.nama }
    raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  }

  Scaffold(
    containerColor = BgLight,
    topBar = {
      TopAppBar(
        title = {
          if (selectedTab == 0) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(vertical = 2.dp)
            ) {
              Surface(
                shape = CircleShape,
                color = CharcoalCard,
                border = BorderStroke(1.5.dp, SunsetOrange.copy(alpha = 0.7f)),
                modifier = Modifier.size(38.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = profilKaryawan.avatarInisial.ifEmpty { "K" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "$sapaanWaktu, $namaDepan 👋",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TextDark
                )
                Text(
                  text = "${profilKaryawan.departemen} • NIP ${profilKaryawan.nip}",
                  fontSize = 11.sp,
                  color = TextMuted,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          } else {
            Column {
              Text(
                text = "Riwayat Kehadiran",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
              )
              Text(
                text = "${profilKaryawan.nama} • NIP ${profilKaryawan.nip}",
                fontSize = 11.sp,
                color = TextMuted
              )
            }
          }
        },
        actions = {
          IconButton(
            onClick = { segarkanDataDariServer(tampilkanToast = true) },
            enabled = !isRefreshingServer,
            modifier = Modifier.testTag("btn_top_refresh_dashboard")
          ) {
            if (isRefreshingServer) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = SunsetOrange
              )
            } else {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Segarkan Data",
                tint = TextDark
              )
            }
          }
          IconButton(
            onClick = { showSecurityMenuDialog = true },
            modifier = Modifier.testTag("btn_top_security_settings")
          ) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = "Keamanan",
              tint = TealPrimary
            )
          }
          IconButton(
            onClick = { showLogoutConfirmDialog = true },
            modifier = Modifier.testTag("karyawan_dashboard_logout_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Logout,
              contentDescription = "Keluar",
              tint = StatusRed
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgLight)
      )
    },
    bottomBar = {
      // OPSI 4: NAVIGATION TABS V2 — FLOATING CAPSULE PILL BAR + POP-OUT CENTER CAMERA BUTTON
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.Transparent)
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
      ) {
        val isLokasiSiapNav = jaringanManager == null || jaringanManager.isDalamJaringanKantor
        // Kapsul Putih Melayang
        Surface(
          shape = RoundedCornerShape(34.dp),
          color = CardWhite,
          shadowElevation = 10.dp,
          border = BorderStroke(1.dp, BorderMuted),
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .height(64.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Tab Kiri: Beranda
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .then(
                  if (selectedTab == 0) Modifier.background(FreshGreenButtonBrush)
                  else Modifier.background(Color.Transparent)
                )
                .clickable {
                  selectedTab = 0
                  segarkanDataDariServer(tampilkanToast = false)
                }
                .padding(horizontal = 16.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Beranda",
                tint = if (selectedTab == 0) Color.White else TextMuted,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Beranda",
                fontSize = 12.sp,
                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                color = if (selectedTab == 0) Color.White else TextMuted
              )
            }

            Spacer(modifier = Modifier.width(56.dp))

            // Tab Kanan: Riwayat
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .then(
                  if (selectedTab == 1) Modifier.background(FreshGreenButtonBrush)
                  else Modifier.background(Color.Transparent)
                )
                .clickable {
                  selectedTab = 1
                  muatDaftarIzinSaya()
                }
                .padding(horizontal = 16.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.History,
                contentDescription = "Riwayat",
                tint = if (selectedTab == 1) Color.White else TextMuted,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Riwayat",
                fontSize = 12.sp,
                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                color = if (selectedTab == 1) Color.White else TextMuted
              )
            }
          }
        }

        // Tombol Kamera Bulat Menonjol di Tengah (Pop-Out Center Button)
        Surface(
          onClick = {
            if (!isProsesAbsen && !isSelesai && !isSedangIzinHariIni && isLokasiSiapNav) {
              bukaKameraSafely()
            } else {
              selectedTab = 0
            }
          },
          shape = CircleShape,
          color = when {
            isSedangIzinHariIni -> SunsetAmber
            isSelesai -> StatusGreen
            isMasuk -> StatusRed
            else -> SunsetOrange
          },
          shadowElevation = 12.dp,
          border = BorderStroke(3.dp, CardWhite),
          modifier = Modifier
            .offset(y = (-16).dp)
            .size(58.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (isProsesAbsen) {
              CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
              )
            } else {
              Icon(
                imageVector = when {
                  isSedangIzinHariIni -> Icons.Default.EventAvailable
                  isSelesai -> Icons.Default.Check
                  else -> Icons.Default.CameraAlt
                },
                contentDescription = "Ambil Swafoto Presensi",
                tint = Color.White,
                modifier = Modifier.size(25.dp)
              )
            }
          }
        }
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp)
          .verticalScroll(scrollState)
          .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (selectedTab == 0) {
          // ========================================================
          // TAB 1: BERANDA PRESENSI HARI INI (DESAIN BERSIH, FOKUS, TANPA REDUNDANSI)
          // ========================================================
          val isLokasiSiap = jaringanManager == null || jaringanManager.isDalamJaringanKantor

          // 1. PESAN FEEDBACK STATUS (OTOMATIS HILANG 5 DETIK)
          if (statusPesan != null) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(
                containerColor = if (isError) StatusRedBg else StatusGreenBg
              ),
              border = BorderStroke(
                1.dp,
                if (isError) StatusRed.copy(alpha = 0.35f) else StatusGreen.copy(alpha = 0.35f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = if (isError) StatusRed else StatusGreen,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = statusPesan ?: "",
                  fontSize = 12.sp,
                  color = if (isError) StatusRed else StatusGreen,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.weight(1f)
                )
              }
            }
            Spacer(modifier = Modifier.height(12.dp))
          }

          // 2. KARTU UTAMA LENGKUNG BERGRADASI HIJAU SEGAR (DENGAN ORNAMEN GELOMBANG & DEKORASI MODERN)
          Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(26.dp))
              .background(FreshGreenBannerBrush)
          ) {
            Box(modifier = Modifier.fillMaxWidth()) {

              // LAPISAN 1: ORNAMEN DEKORATIF LATAR BELAKANG (TEGAS & MENYALA SESUAI MOCKUP)
              // A. Watermark Ikon Waktu Elegan di Pojok Kanan Atas
              Box(
                modifier = Modifier
                  .matchParentSize()
                  .clipToBounds(),
                contentAlignment = Alignment.TopEnd
              ) {
                Icon(
                  imageVector = Icons.Default.Schedule,
                  contentDescription = null,
                  tint = Color.White.copy(alpha = 0.12f),
                  modifier = Modifier
                    .size(190.dp)
                    .offset(x = 45.dp, y = 20.dp)
                )
              }

              // B. Cincin Frosted Glass Translucent di Sudut Kanan Atas
              Box(
                modifier = Modifier
                  .matchParentSize()
                  .clipToBounds(),
                contentAlignment = Alignment.TopEnd
              ) {
                Box(
                  modifier = Modifier
                    .size(250.dp)
                    .offset(x = 85.dp, y = (-75).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34D399).copy(alpha = 0.05f))
                    .border(BorderStroke(1.5.dp, Color(0xFF6EE7B7).copy(alpha = 0.28f)), CircleShape)
                )
                Box(
                  modifier = Modifier
                    .size(170.dp)
                    .offset(x = 55.dp, y = (-45).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.07f))
                    .border(BorderStroke(1.5.dp, Color(0xFFA7F3D0).copy(alpha = 0.38f)), CircleShape)
                )
                Box(
                  modifier = Modifier
                    .size(95.dp)
                    .offset(x = 25.dp, y = (-20).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF86EFAC).copy(alpha = 0.10f))
                    .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)), CircleShape)
                )
              }

              // C. Garis Kontur Topografi Digital & Wave Mesh (Jelas, Tebal, dan Menyala)
              Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height

                // Shading gradasi bayangan gelombang medan 3D
                val waveFill = Path().apply {
                  moveTo(0f, h * 0.65f)
                  cubicTo(w * 0.35f, h * 0.50f, w * 0.65f, h * 0.75f, w, h * 0.48f)
                  lineTo(w, h)
                  lineTo(0f, h)
                  close()
                }
                drawPath(
                  waveFill,
                  androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFF34D399).copy(alpha = 0.16f), Color(0xFF042F24).copy(alpha = 0.02f)),
                    startY = h * 0.45f,
                    endY = h
                  )
                )

                // Garis Kontur 1 (Atas)
                val p1 = Path().apply {
                  moveTo(0f, h * 0.38f)
                  cubicTo(w * 0.32f, h * 0.18f, w * 0.68f, h * 0.45f, w, h * 0.15f)
                }
                drawPath(p1, Color(0xFF6EE7B7).copy(alpha = 0.50f), style = Stroke(width = 2.2.dp.toPx()))

                // Garis Kontur 2 (Tengah-Atas)
                val p2 = Path().apply {
                  moveTo(0f, h * 0.48f)
                  cubicTo(w * 0.35f, h * 0.30f, w * 0.65f, h * 0.58f, w, h * 0.28f)
                }
                drawPath(p2, Color(0xFFA7F3D0).copy(alpha = 0.58f), style = Stroke(width = 2.dp.toPx()))

                // Garis Kontur 3 (Tengah Menyapu ke Kanan)
                val p3 = Path().apply {
                  moveTo(0f, h * 0.62f)
                  cubicTo(w * 0.38f, h * 0.44f, w * 0.65f, h * 0.72f, w, h * 0.45f)
                }
                drawPath(p3, Color(0xFF34D399).copy(alpha = 0.45f), style = Stroke(width = 2.dp.toPx()))

                // Garis Kontur 4 (Bawah)
                val p4 = Path().apply {
                  moveTo(w * 0.08f, h * 0.76f)
                  cubicTo(w * 0.42f, h * 0.62f, w * 0.75f, h * 0.84f, w, h * 0.62f)
                }
                drawPath(p4, Color(0xFF6EE7B7).copy(alpha = 0.40f), style = Stroke(width = 1.8.dp.toPx()))

                // Cincin Resonansi Topografi di Bagian Kanan Kartu (Menyala & Terlihat Jelas)
                val ringCenter = Offset(w * 0.88f, h * 0.44f)
                drawCircle(
                  color = Color(0xFF6EE7B7).copy(alpha = 0.45f),
                  radius = 65.dp.toPx(),
                  center = ringCenter,
                  style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                  color = Color(0xFF34D399).copy(alpha = 0.55f),
                  radius = 42.dp.toPx(),
                  center = ringCenter,
                  style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                  color = Color(0xFF10B981).copy(alpha = 0.15f),
                  radius = 22.dp.toPx(),
                  center = ringCenter
                )
                drawCircle(
                  color = Color(0xFFA7F3D0).copy(alpha = 0.70f),
                  radius = 22.dp.toPx(),
                  center = ringCenter,
                  style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                  color = Color(0xFF34D399).copy(alpha = 0.60f),
                  radius = 9.dp.toPx(),
                  center = ringCenter
                )
                drawCircle(
                  color = Color.White.copy(alpha = 0.95f),
                  radius = 4.dp.toPx(),
                  center = ringCenter
                )

                // Titik Konstelasi Cahaya Digital (Sparkles)
                val sparkles = listOf(
                  Offset(w * 0.82f, h * 0.16f),
                  Offset(w * 0.94f, h * 0.24f),
                  Offset(w * 0.68f, h * 0.34f),
                  Offset(w * 0.52f, h * 0.22f),
                  Offset(w * 0.74f, h * 0.66f)
                )
                for (sparkle in sparkles) {
                  drawCircle(Color(0xFF34D399).copy(alpha = 0.50f), 5.5.dp.toPx(), sparkle)
                  drawCircle(Color.White.copy(alpha = 0.95f), 2.2.dp.toPx(), sparkle)
                }
              }

              // LAPISAN 2: KONTEN UTAMA KARTU
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(20.dp)
              ) {
              // Baris Atas: Tanggal Hari Ini + Badge Status Kehadiran
              val badgeBg = when {
                isSedangIzinHariIni -> SunsetAmber.copy(alpha = 0.25f)
                isSelesai -> Color(0xFF4ADE80).copy(alpha = 0.25f)
                isMasuk -> Color.White.copy(alpha = 0.22f)
                else -> Color.White.copy(alpha = 0.18f)
              }
              val badgeText = when {
                isSedangIzinHariIni -> "Sedang Izin / Cuti"
                isSelesai -> "Presensi Selesai"
                isMasuk -> "Sedang Bekerja"
                else -> "Belum Absen"
              }
              val badgeColor = when {
                isSedangIzinHariIni -> SunsetAmber
                isSelesai -> Color(0xFF4ADE80)
                isMasuk -> Color.White
                else -> Color.White.copy(alpha = 0.85f)
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID")).format(Date()),
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color.White.copy(alpha = 0.7f)
                )

                Surface(
                  shape = RoundedCornerShape(20.dp),
                  color = Color(0xFF03261D).copy(alpha = 0.65f),
                  border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.35f))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = badgeText,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = badgeColor
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Baris Jam Digital Besar & Jadwal Shift
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
              ) {
                Column {
                  Text(
                    text = "Waktu Server Saat Ini",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.70f)
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  LiveClockText()
                }

                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = Color(0xFF03261D).copy(alpha = 0.55f),
                  border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.30f))
                ) {
                  Text(
                    text = "Shift ${configKantor.jamMasukKerja} - ${configKantor.jamPulangKerja}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(18.dp))

              if (isSedangIzinHariIni) {
                // TAMPILAN KHUSUS SAAT KARYAWAN SEDANG IZIN / SAKIT / CUTI (TIDAK BENTROK DENGAN MASUK/PULANG)
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = Color(0xFF03261D).copy(alpha = 0.65f),
                  border = BorderStroke(1.dp, SunsetAmber.copy(alpha = 0.45f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SunsetAmber.copy(alpha = 0.18f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.EventAvailable,
                        contentDescription = null,
                        tint = SunsetAmber,
                        modifier = Modifier.size(24.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Izin / Sakit / Cuti Disetujui HR",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                      )
                      Spacer(modifier = Modifier.height(3.dp))
                      Text(
                        text = "Anda tercatat sedang izin hari ini dan tidak perlu melakukan presensi masuk maupun pulang.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 15.sp
                      )
                    }
                  }
                }
              } else {
                // DUA KOTak RINGKASAN JAM MASUK & JAM PULANG
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF03261D).copy(alpha = 0.60f),
                    border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.30f)),
                    modifier = Modifier.weight(1f)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(20.dp)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Column {
                        Text(
                          text = "Jam Masuk",
                          fontSize = 11.sp,
                          color = Color.White.copy(alpha = 0.70f)
                        )
                        Text(
                          text = jamMasukHariIni ?: "--:--",
                          fontSize = 15.sp,
                          fontWeight = FontWeight.ExtraBold,
                          color = Color.White
                        )
                      }
                    }
                  }

                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF03261D).copy(alpha = 0.60f),
                    border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.30f)),
                    modifier = Modifier.weight(1f)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = Color(0xFF6EE7B7),
                        modifier = Modifier.size(20.dp)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Column {
                        Text(
                          text = "Jam Pulang",
                          fontSize = 11.sp,
                          color = Color.White.copy(alpha = 0.70f)
                        )
                        Text(
                          text = jamPulangHariIni ?: "--:--",
                          fontSize = 15.sp,
                          fontWeight = FontWeight.ExtraBold,
                          color = Color.White
                        )
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(14.dp))

              // RADAR / INDIKATOR AKURASI GPS SEBELUM FOTO SELFIE
              if (jaringanManager != null) {
                val isFetching = jaringanManager.isFetchingGps || !jaringanManager.isGpsAktif
                val isMock = jaringanManager.isFakeGpsDetected
                val noPermission = !jaringanManager.hasLocationPermission()
                val diLuarRadius = !jaringanManager.isBypassRadius && !jaringanManager.isDalamJaringanKantor

                val pillBgColor = when {
                  isMock || diLuarRadius -> Color(0xFF2B0B0B).copy(alpha = 0.75f)
                  noPermission || isFetching -> Color(0xFF2B1D0A).copy(alpha = 0.75f)
                  else -> Color(0xFF022018).copy(alpha = 0.70f)
                }
                val pillBorderColor = when {
                  isMock || diLuarRadius -> Color(0xFFEF4444).copy(alpha = 0.50f)
                  noPermission || isFetching -> Color(0xFFF59E0B).copy(alpha = 0.50f)
                  else -> Color(0xFF34D399).copy(alpha = 0.40f)
                }
                val pillTextColor = when {
                  isMock || diLuarRadius -> Color(0xFFFCA5A5)
                  noPermission || isFetching -> Color(0xFFFDE68A)
                  else -> Color(0xFF6EE7B7)
                }
                val pillText = when {
                  isMock -> "Peringatan: Terdeteksi Lokasi Tiruan (Fake GPS)"
                  noPermission -> "Izin Lokasi GPS Belum Aktif"
                  isFetching -> "Mencari Sinyal GPS... Harap tunggu beberapa detik"
                  diLuarRadius -> "Di luar radius kantor (Jarak ${jaringanManager.jarakDariKantorMeter}m, maks ${jaringanManager.radiusMaksimalKantorMeter}m)"
                  jaringanManager.isBypassRadius -> "GPS Terkunci (Mode Bebas Lokasi Aktif) - Siap Absen"
                  else -> "GPS Terkunci (Jarak ${jaringanManager.jarakDariKantorMeter}m dari kantor) - Siap Absen"
                }

                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = pillBgColor,
                  border = BorderStroke(1.dp, pillBorderColor),
                  shadowElevation = 2.dp,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.weight(1f)
                    ) {
                      if (isFetching) {
                        CircularProgressIndicator(
                          color = Color(0xFFF59E0B),
                          strokeWidth = 2.dp,
                          modifier = Modifier.size(13.dp)
                        )
                      } else {
                        // Animasi Gelombang Radar GPS (Pulsing Ripple)
                        val radarPulse = rememberInfiniteTransition(label = "gps_radar_dot")
                        val dotScale by radarPulse.animateFloat(
                          initialValue = 1f,
                          targetValue = 2.2f,
                          animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                          ),
                          label = "dot_scale"
                        )
                        val dotAlpha by radarPulse.animateFloat(
                          initialValue = 0.75f,
                          targetValue = 0f,
                          animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                          ),
                          label = "dot_alpha"
                        )
                        val activeDotColor = when {
                          isMock || diLuarRadius -> Color(0xFFEF4444)
                          noPermission -> Color(0xFFF59E0B)
                          else -> Color(0xFF22C55E)
                        }

                        Box(
                          contentAlignment = Alignment.Center,
                          modifier = Modifier.size(14.dp)
                        ) {
                          // Gelombang radar memancar keluar (Radar ripple)
                          Box(
                            modifier = Modifier
                              .size(10.dp)
                              .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                                alpha = dotAlpha
                              }
                              .background(activeDotColor, CircleShape)
                          )
                          // Inti titik radar (Core solid dot)
                          Box(
                            modifier = Modifier
                              .size(7.dp)
                              .background(activeDotColor, CircleShape)
                          )
                        }
                      }
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = pillText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillTextColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                      )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Perbarui ↻",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = pillTextColor,
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { jaringanManager.refreshGps() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                  }
                }
              }

              if (capturedSelfieBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CharcoalSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Image(
                    bitmap = capturedSelfieBitmap!!.asImageBitmap(),
                    contentDescription = "Foto Presensi",
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text("Swafoto Presensi Tercatat", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Terverifikasi dengan waktu & titik lokasi GPS", fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f))
                  }
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              Button(
                onClick = { bukaKameraSafely() },
                enabled = !isProsesAbsen && !isSelesai && isLokasiSiap,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isSelesai || !isLokasiSiap) Color(0xFF03261D).copy(alpha = 0.85f) else if (isMasuk) StatusRed else Color(0xFF059669),
                  disabledContainerColor = Color(0xFF03261D).copy(alpha = 0.85f)
                ),
                border = if (isSelesai || !isLokasiSiap) BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.35f)) else null,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("karyawan_absen_button")
              ) {
                if (isProsesAbsen) {
                  CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                  )
                } else {
                  Icon(
                    imageVector = if (isSelesai) Icons.Default.CheckCircle else if (!isLokasiSiap) Icons.Default.LocationOff else if (isMasuk) Icons.AutoMirrored.Filled.Logout else Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (isSelesai) "Presensi Hari Ini Selesai" else if (!isLokasiSiap) "Lokasi GPS Belum Siap" else if (isMasuk) "Ambil Swafoto & Absen Pulang" else "Ambil Swafoto & Absen Masuk",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            }
          }
        }

          Spacer(modifier = Modifier.height(18.dp))

          // 3. DERETAN 4 MENU LAYANAN MANDIRI (TIDAK ADA TOMBOL GANDA)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Menu 1: Ajukan Izin / Sakit / Cuti
            Column(
              modifier = Modifier.weight(1f),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Surface(
                onClick = {
                  tanggalMulaiInput = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                  tanggalSelesaiInput = tanggalMulaiInput
                  alasanInput = ""
                  buktiFotoIzinBitmap = null
                  buktiFotoIzinBase64 = ""
                  showIzinDialog = true
                },
                shape = RoundedCornerShape(18.dp),
                color = SunsetOrangeBg,
                border = BorderStroke(1.dp, SunsetOrange.copy(alpha = 0.25f)),
                shadowElevation = 1.dp,
                modifier = Modifier.size(56.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.AssignmentTurnedIn,
                    contentDescription = "Ajukan Izin",
                    tint = SunsetOrange,
                    modifier = Modifier.size(23.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Ajukan Izin",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
              )
            }

            // Menu 2: Riwayat Kehadiran
            Column(
              modifier = Modifier.weight(1f),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Surface(
                onClick = {
                  selectedTab = 1
                  muatDaftarIzinSaya()
                },
                shape = RoundedCornerShape(18.dp),
                color = StatusBlueBg,
                border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.22f)),
                shadowElevation = 1.dp,
                modifier = Modifier.size(56.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Riwayat",
                    tint = StatusBlue,
                    modifier = Modifier.size(23.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Riwayat",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
              )
            }

            // Menu 3: Info Jadwal & Jam Kerja
            Column(
              modifier = Modifier.weight(1f),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Surface(
                onClick = { showJadwalDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = StatusAmberBg,
                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.25f)),
                shadowElevation = 1.dp,
                modifier = Modifier.size(56.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Jadwal Shift",
                    tint = StatusAmber,
                    modifier = Modifier.size(23.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Jadwal Kerja",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
              )
            }

            // Menu 4: Peta Lokasi GPS
            Column(
              modifier = Modifier.weight(1f),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Surface(
                onClick = {
                  if (jaringanManager != null) {
                    try {
                      val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(jaringanManager.mapsUrl)
                      )
                      context.startActivity(intent)
                    } catch (_: Exception) {
                      jaringanManager.refreshGps()
                    }
                  }
                },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFCCFBF1),
                border = BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.25f)),
                shadowElevation = 1.dp,
                modifier = Modifier.size(56.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Peta Lokasi",
                    tint = Color(0xFF0D9488),
                    modifier = Modifier.size(23.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Peta Lokasi",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // 4. BANNER RINGKASAN SAYA BULAN INI DI BERANDA (INTERAKTIF KE TAB RIWAYAT)
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, BorderMuted),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedCalendar = Calendar.getInstance()
                selectedTab = 1
                muatDaftarIzinSaya()
              }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FreshGreenButtonBrush),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "Ringkasan Saya ($namaBulanNow)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "$hadirNowCount Hadir • $telatNowCount Telat • $izinNowCount Izin",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
              Surface(
                shape = CircleShape,
                color = BgLight,
                modifier = Modifier.size(28.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Lihat Detail",
                    tint = TealPrimary,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 5. KARTU PENGUMUMAN KANTOR RINGKAS (TANPA TOMBOL DUPLIKAT)
          val teksPengumumanBersih = remember(configKantor.pengumuman, configKantor.jamMasukKerja) {
            configKantor.pengumuman
              .replace(Regex("^(?i)pengumuman\\s*:\\s*"), "")
              .trim()
              .ifBlank { "Jam kerja resmi kantor dimulai pukul ${configKantor.jamMasukKerja} WIB." }
          }

          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SunsetOrangeBg),
            border = BorderStroke(1.dp, SunsetOrange.copy(alpha = 0.25f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.Top
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(SunsetOrange.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Campaign,
                  contentDescription = null,
                  tint = SunsetOrange,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Pengumuman Kantor",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = teksPengumumanBersih,
                  fontSize = 12.sp,
                  color = TextDark.copy(alpha = 0.85f),
                  lineHeight = 17.sp
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(24.dp))
        } else {
          // ========================================================
          // TAB 2: RIWAYAT KEHADIRAN DENGAN FILTER BULAN & 3 KARTU RINGKASAN
          // ========================================================

          // 1. BILAH NAVIGASI PEMILIH BULAN (INTERAKTIF & MODERN)
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, BorderMuted),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = {
                  val prev = selectedCalendar.clone() as Calendar
                  prev.add(Calendar.MONTH, -1)
                  selectedCalendar = prev
                },
                modifier = Modifier.size(38.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.ChevronLeft,
                  contentDescription = "Bulan Sebelumnya",
                  tint = TealDark,
                  modifier = Modifier.size(24.dp)
                )
              }

              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                  if (!isBulanSekarang) {
                    selectedCalendar = Calendar.getInstance()
                  }
                }
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = namaBulanTahun,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark
                  )
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (!isBulanSekarang) {
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = TealContainer,
                    modifier = Modifier.padding(top = 1.dp)
                  ) {
                    Text(
                      text = "Kembali ke Bulan Ini ↺",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = TealDark,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                  }
                } else {
                  Text(
                    text = "Periode Berjalan",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                  )
                }
              }

              IconButton(
                onClick = {
                  val next = selectedCalendar.clone() as Calendar
                  next.add(Calendar.MONTH, 1)
                  selectedCalendar = next
                },
                modifier = Modifier.size(38.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.ChevronRight,
                  contentDescription = "Bulan Berikutnya",
                  tint = TealDark,
                  modifier = Modifier.size(24.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // 2. TIGA KARTU RINGKASAN MANDIRI (HADIR, TERLAMBAT, IZIN)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Kartu 1: Hadir
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = CardWhite),
              border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.25f)),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              modifier = Modifier.weight(1f)
            ) {
              Column(
                modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(StatusGreenBg),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(18.dp)
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "$totalHadirBulan Hari",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = StatusGreen
                )
                Text(
                  text = "Hadir",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextMuted
                )
              }
            }

            // Kartu 2: Terlambat
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = CardWhite),
              border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.25f)),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              modifier = Modifier.weight(1f)
            ) {
              Column(
                modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(StatusAmberBg),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = StatusAmber,
                    modifier = Modifier.size(18.dp)
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "$totalTerlambatBulan Hari",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = StatusAmber
                )
                Text(
                  text = "Terlambat",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextMuted
                )
              }
            }

            // Kartu 3: Izin / Cuti
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = CardWhite),
              border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              modifier = Modifier.weight(1f)
            ) {
              Column(
                modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(TealContainer),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.EventNote,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(18.dp)
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "$totalIzinBulan Hari",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = TealPrimary
                )
                Text(
                  text = "Izin / Cuti",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextMuted
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 3. LOG KEHADIRAN HARIAN TERFILTER KHUSUS BULAN TERPILIH
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, BorderMuted),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(
                    text = "Log Kehadiran Harian",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                  )
                  Text(
                    text = "Periode: $namaBulanTahun",
                    fontSize = 11.sp,
                    color = TextMuted
                  )
                }
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = TealContainer
                ) {
                  Text(
                    text = "${riwayatBulanTerpilih.size} Hari",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealDark,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(10.dp))

              if (riwayatBulanTerpilih.isEmpty()) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                      imageVector = Icons.Default.EventBusy,
                      contentDescription = null,
                      tint = TextMuted.copy(alpha = 0.5f),
                      modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = "Tidak ada catatan kehadiran di $namaBulanTahun.",
                      fontSize = 12.sp,
                      color = TextMuted,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              } else {
                riwayatBulanTerpilih.forEachIndexed { idx, item ->
                  val jamInClean = item.jamMasuk.replace("WIB", "").trim().ifEmpty { "--:--" }
                  val jamOutClean = item.jamPulang.replace("WIB", "").trim().ifEmpty { "--:--" }
                  val isTepat = item.status == StatusAbsensi.TEPAT_WAKTU
                  val isIzin = item.status == StatusAbsensi.IZIN

                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    // Kolom Kiri: Tanggal
                    Column(modifier = Modifier.weight(1.1f)) {
                      Text(
                        text = item.hariTanggal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                      )
                      if (item.catatan.isNotBlank() && !item.catatan.startsWith("Swafoto")) {
                        Text(
                          text = item.catatan,
                          fontSize = 11.sp,
                          color = TextMuted
                        )
                      }
                    }

                    // Kolom Tengah: Jam Masuk - Pulang (1 Baris Simpel)
                    Text(
                      text = "$jamInClean — $jamOutClean",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = TealDark,
                      textAlign = TextAlign.Center,
                      modifier = Modifier.weight(0.9f)
                    )

                    // Kolom Kanan: Badge Status Simpel
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = when {
                        isIzin -> TealContainer
                        isTepat -> StatusGreenBg
                        else -> StatusAmberBg
                      }
                    ) {
                      Text(
                        text = when {
                          isIzin -> "Izin"
                          isTepat -> "Hadir"
                          else -> "Terlambat"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                          isIzin -> TealDark
                          isTepat -> StatusGreen
                          else -> StatusAmber
                        },
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                      )
                    }
                  }

                  if (idx < riwayatBulanTerpilih.size - 1) {
                    HorizontalDivider(color = BorderMuted)
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 3. STATUS PERMOHONAN IZIN & CUTI SAYA
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, BorderMuted),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Riwayat Izin & Cuti",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextDark
                )
                TextButton(
                  onClick = { muatDaftarIzinSaya() },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = TealPrimary)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Segarkan", fontSize = 11.sp, color = TealPrimary)
                }
              }

              if (daftarIzinSaya.isEmpty()) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text("Belum ada pengajuan izin atau cuti.", fontSize = 12.sp, color = TextMuted)
                }
              } else {
                daftarIzinSaya.take(10).forEachIndexed { idx, izin ->
                  val isApproved = izin.statusPersetujuan.startsWith("Disetujui", ignoreCase = true)
                  val isRejected = izin.statusPersetujuan.startsWith("Ditolak", ignoreCase = true)
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "${izin.jenisIzin} • ${izin.tanggalDiajukan}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                      )
                      if (izin.alasan.isNotBlank() && izin.alasan != "-") {
                        Text(izin.alasan, fontSize = 11.sp, color = TextMuted)
                      }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = if (isApproved) StatusGreenBg else if (isRejected) StatusRedBg else StatusAmberBg
                    ) {
                      Text(
                        text = if (isApproved) "Disetujui" else if (isRejected) "Ditolak" else "Menunggu",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isApproved) StatusGreen else if (isRejected) StatusRed else StatusAmber,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                      )
                    }
                  }
                  if (idx < minOf(daftarIzinSaya.size, 10) - 1) {
                    HorizontalDivider(color = BorderMuted)
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }

    // DIALOG PREVIEW SWAFOTO & CATATAN LOKASI SEBELUM DIKIRIM KE SERVER
    if (pendingSelfieBitmap != null) {
      val tipeAbsenLabel = if (isMasuk) "Presensi Pulang" else "Presensi Masuk"
      AlertDialog(
        onDismissRequest = { pendingSelfieBitmap = null },
        title = {
          Text(
            text = "Konfirmasi $tipeAbsenLabel",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
          )
        },
        text = {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Image(
              bitmap = pendingSelfieBitmap!!.asImageBitmap(),
              contentDescription = "Preview Swafoto",
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
            )
            OutlinedTextField(
              value = catatanPresensiInput,
              onValueChange = { catatanPresensiInput = it },
              label = { Text("Catatan Lokasi / Aktivitas (Opsional)") },
              placeholder = { Text("Contoh: WFO Kantor / Dinas Luar") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp)
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val bmp = pendingSelfieBitmap
              val note = catatanPresensiInput
              pendingSelfieBitmap = null
              if (bmp != null) {
                prosesKirimAbsensi(bmp, note)
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Kirim Presensi")
          }
        },
        dismissButton = {
          OutlinedButton(
            onClick = {
              pendingSelfieBitmap = null
              bukaKameraSafely()
            }
          ) {
            Text("Foto Ulang")
          }
        }
      )
    }

    // DIALOG MENU KEAMANAN (DARI IKON TOP BAR)
    if (showSecurityMenuDialog) {
      AlertDialog(
        onDismissRequest = { showSecurityMenuDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = TealPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pengaturan Keamanan", fontWeight = FontWeight.Bold, fontSize = 17.sp)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Metode kunci aktif saat ini: ${sessionManager.getAuthMethod().label}",
              fontSize = 12.sp,
              color = TextMuted
            )
            OutlinedButton(
              onClick = {
                showSecurityMenuDialog = false
                showUbahPinDialog = true
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Ubah 6 Digit PIN Keamanan")
            }
            if (onChangeAuthMethod != null) {
              OutlinedButton(
                onClick = {
                  showSecurityMenuDialog = false
                  showSecurityDialog = true
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ganti Metode Kunci (PIN / Biometrik)")
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showSecurityMenuDialog = false }) {
            Text("Tutup")
          }
        }
      )
    }

    // DIALOG KONFIRMASI KELUAR AKUN
    if (showLogoutConfirmDialog) {
      AlertDialog(
        onDismissRequest = { showLogoutConfirmDialog = false },
        title = { Text("Keluar dari Akun?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
          Text(
            "Anda perlu masuk kembali menggunakan NIP terdaftar untuk mengakses beranda presensi.",
            fontSize = 13.sp,
            color = TextMuted
          )
        },
        confirmButton = {
          Button(
            onClick = {
              showLogoutConfirmDialog = false
              onLogout()
            },
            colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
          ) {
            Text("Keluar")
          }
        },
        dismissButton = {
          TextButton(onClick = { showLogoutConfirmDialog = false }) {
            Text("Batal", color = TextMuted)
          }
        }
      )
    }

    // DIALOG FORM PENGAJUAN IZIN / CUTI (DENGAN VALIDASI KOLOM WAJIB & LAMPIRAN FOTO OPSIONAL)
    if (showIzinDialog) {
      val opsiIzin = listOf("Sakit", "Izin", "Cuti")
      var expandedIzin by remember { mutableStateOf(false) }
      var peringatanFormIzin by remember { mutableStateOf<String?>(null) }
      var errorTglMulai by remember { mutableStateOf(false) }
      var errorTglSelesai by remember { mutableStateOf(false) }
      var errorAlasan by remember { mutableStateOf(false) }

      AlertDialog(
        onDismissRequest = { if (!isSubmittingIzin) showIzinDialog = false },
        title = { Text("Pengajuan Izin & Cuti", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Banner Peringatan jika ada kolom wajib yang belum diisi
            if (peringatanFormIzin != null) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusRedBg,
                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = StatusRed,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = peringatanFormIzin ?: "",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusRed,
                    lineHeight = 16.sp
                  )
                }
              }
            }

            ExposedDropdownMenuBox(
              expanded = expandedIzin,
              onExpandedChange = { expandedIzin = !expandedIzin },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = jenisIzinInput,
                onValueChange = {},
                readOnly = true,
                label = { Text("Kategori") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIzin) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
              )
              ExposedDropdownMenu(
                expanded = expandedIzin,
                onDismissRequest = { expandedIzin = false }
              ) {
                opsiIzin.forEach { item ->
                  DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                      jenisIzinInput = item
                      expandedIzin = false
                    }
                  )
                }
              }
            }

            val bukaKalenderTanggal: (Boolean) -> Unit = { isMulai ->
              val cal = java.util.Calendar.getInstance()
              android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                  val formatted = "%02d/%02d/%04d".format(dayOfMonth, month + 1, year)
                  if (isMulai) {
                    tanggalMulaiInput = formatted
                    errorTglMulai = false
                    if (tanggalSelesaiInput.isBlank()) {
                      tanggalSelesaiInput = formatted
                      errorTglSelesai = false
                    }
                  } else {
                    tanggalSelesaiInput = formatted
                    errorTglSelesai = false
                  }
                  if (!errorTglMulai && !errorTglSelesai && !errorAlasan) {
                    peringatanFormIzin = null
                  }
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
              ).show()
            }

            OutlinedTextField(
              value = tanggalMulaiInput,
              onValueChange = {
                tanggalMulaiInput = it
                if (it.isNotBlank()) errorTglMulai = false
                if (!errorTglMulai && !errorTglSelesai && !errorAlasan) peringatanFormIzin = null
              },
              label = { Text("Tanggal Mulai *") },
              placeholder = { Text("DD/MM/YYYY") },
              isError = errorTglMulai,
              supportingText = if (errorTglMulai) {
                { Text("Tanggal mulai wajib diisi", fontSize = 11.sp, color = StatusRed) }
              } else null,
              trailingIcon = {
                IconButton(onClick = { bukaKalenderTanggal(true) }) {
                  Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal Mulai", tint = TealPrimary)
                }
              },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp)
            )

            OutlinedTextField(
              value = tanggalSelesaiInput,
              onValueChange = {
                tanggalSelesaiInput = it
                if (it.isNotBlank()) errorTglSelesai = false
                if (!errorTglMulai && !errorTglSelesai && !errorAlasan) peringatanFormIzin = null
              },
              label = { Text("Tanggal Selesai *") },
              placeholder = { Text("DD/MM/YYYY") },
              isError = errorTglSelesai,
              supportingText = if (errorTglSelesai) {
                { Text("Tanggal selesai wajib diisi", fontSize = 11.sp, color = StatusRed) }
              } else null,
              trailingIcon = {
                IconButton(onClick = { bukaKalenderTanggal(false) }) {
                  Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal Selesai", tint = TealPrimary)
                }
              },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp)
            )

            OutlinedTextField(
              value = alasanInput,
              onValueChange = {
                alasanInput = it
                if (it.isNotBlank()) errorAlasan = false
                if (!errorTglMulai && !errorTglSelesai && !errorAlasan) peringatanFormIzin = null
              },
              label = { Text("Keterangan / Alasan *") },
              placeholder = { Text("Tuliskan alasan pengajuan izin/sakit/cuti...") },
              isError = errorAlasan,
              supportingText = if (errorAlasan) {
                { Text("Keterangan / alasan wajib diisi sebelum mengirim", fontSize = 11.sp, color = StatusRed) }
              } else null,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp),
              maxLines = 3
            )

            // Bagian Lampiran Foto Surat Dokter / Bukti (100% Opsional)
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Lampiran Foto Surat / Bukti",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextDark
                )
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = TealContainer
                ) {
                  Text(
                    text = "Opsional (Tidak Wajib)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealDark,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                      try {
                        izinFotoLauncher.launch(null)
                      } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membuka kamera bukti izin", Toast.LENGTH_SHORT).show()
                      }
                    } else {
                      izinCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                  },
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (buktiFotoIzinBitmap != null) "Foto Ulang" else "Kamera",
                    fontSize = 12.sp
                  )
                }

                OutlinedButton(
                  onClick = {
                    try {
                      izinGaleriLauncher.launch("image/*")
                    } catch (e: Exception) {
                      Toast.makeText(context, "Gagal membuka galeri", Toast.LENGTH_SHORT).show()
                    }
                  },
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Pilih Galeri",
                    fontSize = 12.sp
                  )
                }
              }

              if (buktiFotoIzinBitmap != null) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StatusGreenBg)
                    .padding(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Image(
                    bitmap = buktiFotoIzinBitmap!!.asImageBitmap(),
                    contentDescription = "Bukti Izin",
                    modifier = Modifier
                      .size(40.dp)
                      .clip(RoundedCornerShape(6.dp))
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Foto bukti siap dilampirkan",
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = StatusGreen
                    )
                    Text(
                      text = "Lampiran bersifat opsional",
                      fontSize = 10.sp,
                      color = TextMuted
                    )
                  }
                  IconButton(
                    onClick = {
                      buktiFotoIzinBitmap = null
                      buktiFotoIzinBase64 = ""
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Hapus Foto",
                      tint = StatusRed,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              } else {
                Text(
                  text = "* Boleh dikosongkan jika tidak ada foto surat dokter / bukti.",
                  fontSize = 10.5.sp,
                  color = TextMuted
                )
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val tglMulaiBersih = tanggalMulaiInput.trim()
              val tglSelesaiBersih = tanggalSelesaiInput.trim()
              val alasanBersih = alasanInput.trim()

              val kolomKosong = mutableListOf<String>()
              errorTglMulai = tglMulaiBersih.isEmpty()
              if (errorTglMulai) kolomKosong.add("Tanggal Mulai")

              errorTglSelesai = tglSelesaiBersih.isEmpty()
              if (errorTglSelesai) kolomKosong.add("Tanggal Selesai")

              errorAlasan = alasanBersih.isEmpty()
              if (errorAlasan) kolomKosong.add("Keterangan / Alasan")

              if (kolomKosong.isNotEmpty()) {
                val daftarKolom = kolomKosong.joinToString(", ")
                peringatanFormIzin = "Mohon isi kolom wajib yang masih kosong: $daftarKolom."
                Toast.makeText(
                  context,
                  "⚠️ Kolom $daftarKolom wajib diisi!",
                  Toast.LENGTH_SHORT
                ).show()
                return@Button
              }

              peringatanFormIzin = null
              isSubmittingIzin = true
              val kategoriKirim = jenisIzinInput
              val fotoBase64Opsional = buktiFotoIzinBase64

              coroutineScope.launch {
                val res = XamppApiClient.kirimIzin(
                  idKaryawan = karyawan.id,
                  nip = karyawan.nip,
                  nama = karyawan.nama,
                  jenisIzin = kategoriKirim,
                  tanggalMulai = tglMulaiBersih,
                  tanggalSelesai = tglSelesaiBersih,
                  alasan = alasanBersih,
                  buktiFoto = fotoBase64Opsional
                )
                isSubmittingIzin = false
                if (res.isSuccess) {
                  showIzinDialog = false
                  statusPesan = "Permohonan $kategoriKirim berhasil dikirim."
                  isError = false
                  muatDaftarIzinSaya()
                  popupHasilIzinSukses = true
                  popupHasilIzinJudul = "Pengajuan Berhasil Dikirim!"
                  popupHasilIzinPesan = "Permohonan $kategoriKirim Anda untuk tanggal $tglMulaiBersih s/d $tglSelesaiBersih dengan alasan \"$alasanBersih\" telah berhasil dikirim ke HRD/Admin dan sedang menunggu persetujuan."
                } else {
                  val errMsg = res.exceptionOrNull()?.message ?: "Tidak dapat terhubung ke server."
                  peringatanFormIzin = "Gagal mengirim: $errMsg"
                  statusPesan = "Gagal mengirim permohonan: $errMsg"
                  isError = true
                  popupHasilIzinSukses = false
                  popupHasilIzinJudul = "Gagal Mengirim Pengajuan"
                  popupHasilIzinPesan = "Permohonan $kategoriKirim belum dapat dikirim ke server: $errMsg"
                }
              }
            },
            enabled = !isSubmittingIzin,
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            if (isSubmittingIzin) {
              CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Mengirim...")
            } else {
              Text("Kirim")
            }
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showIzinDialog = false },
            enabled = !isSubmittingIzin
          ) {
            Text("Batal")
          }
        }
      )
    }

    // POP-UP DIALOG KONFIRMASI HASIL KIRIM PENGAJUAN IZIN & CUTI
    if (popupHasilIzinJudul != null) {
      AlertDialog(
        onDismissRequest = { popupHasilIzinJudul = null },
        icon = {
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(if (popupHasilIzinSukses) StatusGreenBg else StatusRedBg),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (popupHasilIzinSukses) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = if (popupHasilIzinSukses) StatusGreen else StatusRed,
              modifier = Modifier.size(32.dp)
            )
          }
        },
        title = {
          Text(
            text = popupHasilIzinJudul ?: "",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = TextDark
          )
        },
        text = {
          Text(
            text = popupHasilIzinPesan,
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.fillMaxWidth()
          )
        },
        confirmButton = {
          Button(
            onClick = {
              val isOk = popupHasilIzinSukses
              popupHasilIzinJudul = null
              if (isOk) {
                selectedTab = 1
                muatDaftarIzinSaya()
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (popupHasilIzinSukses) TealPrimary else StatusRed
            ),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text(if (popupHasilIzinSukses) "Lihat Status Izin" else "Mengerti")
          }
        },
        dismissButton = if (popupHasilIzinSukses) {
          {
            TextButton(onClick = { popupHasilIzinJudul = null }) {
              Text("Tutup")
            }
          }
        } else null
      )
    }

    // DIALOG VERIFIKASI KEAMANAN SEBELUM GANTI METODE LOGIN
    if (showSecurityDialog) {
      var verifyPinInput by remember { mutableStateOf("") }
      var verifyError by remember { mutableStateOf<String?>(null) }

      AlertDialog(
        onDismissRequest = { showSecurityDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = TealPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Verifikasi PIN", fontWeight = FontWeight.Bold, fontSize = 18.sp)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              "Masukkan 6 digit PIN keamanan Anda untuk melanjutkan.",
              fontSize = 13.sp,
              color = TextMuted
            )
            OutlinedTextField(
              value = verifyPinInput,
              onValueChange = { if (it.length <= 6) verifyPinInput = it },
              label = { Text("PIN Keamanan") },
              placeholder = { Text("6 digit angka") },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("input_verify_pin_change")
            )
            if (verifyError != null) {
              Text(
                verifyError ?: "",
                color = StatusRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (sessionManager.verifyPin(verifyPinInput)) {
                showSecurityDialog = false
                onChangeAuthMethod?.invoke()
              } else {
                verifyError = "PIN yang Anda masukkan tidak sesuai."
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            modifier = Modifier.testTag("btn_confirm_verify_pin")
          ) {
            Text("Lanjutkan")
          }
        },
        dismissButton = {
          TextButton(onClick = { showSecurityDialog = false }) {
            Text("Batal", color = TextMuted)
          }
        }
      )
    }

    // DIALOG UBAH PIN
    if (showUbahPinDialog) {
      var oldPinInput by remember { mutableStateOf("") }
      var newPinInput by remember { mutableStateOf("") }
      var confirmPinInput by remember { mutableStateOf("") }
      var dialogError by remember { mutableStateOf<String?>(null) }

      AlertDialog(
        onDismissRequest = { showUbahPinDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = TealPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ubah PIN Keamanan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              "Masukkan PIN saat ini, lalu buat 6 digit PIN baru.",
              fontSize = 12.sp,
              color = TextMuted
            )

            OutlinedTextField(
              value = oldPinInput,
              onValueChange = { if (it.length <= 6) oldPinInput = it },
              label = { Text("PIN Saat Ini") },
              placeholder = { Text("6 digit angka") },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("input_dashboard_old_pin")
            )

            OutlinedTextField(
              value = newPinInput,
              onValueChange = { if (it.length <= 6) newPinInput = it },
              label = { Text("PIN Baru (6 Digit)") },
              placeholder = { Text("6 digit angka") },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("input_dashboard_new_pin")
            )

            OutlinedTextField(
              value = confirmPinInput,
              onValueChange = { if (it.length <= 6) confirmPinInput = it },
              label = { Text("Konfirmasi PIN Baru") },
              placeholder = { Text("Ulangi PIN baru") },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("input_dashboard_confirm_pin")
            )

            if (dialogError != null) {
              Text(
                dialogError ?: "",
                color = StatusRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (!sessionManager.verifyPin(oldPinInput)) {
                dialogError = "PIN saat ini tidak sesuai."
                return@Button
              }
              if (newPinInput.length != 6 || !newPinInput.all { it.isDigit() }) {
                dialogError = "PIN baru harus terdiri dari 6 digit angka."
                return@Button
              }
              if (newPinInput != confirmPinInput) {
                dialogError = "Konfirmasi PIN baru tidak cocok."
                return@Button
              }

              sessionManager.simpanSecurityPin(newPinInput)
              showUbahPinDialog = false
              Toast.makeText(context, "PIN berhasil diperbarui", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            modifier = Modifier.testTag("btn_save_dashboard_pin")
          ) {
            Text("Simpan")
          }
        },
        dismissButton = {
          TextButton(onClick = { showUbahPinDialog = false }) {
            Text("Batal", color = TextMuted)
          }
        }
      )
    }

    if (showJadwalDialog) {
      AlertDialog(
        onDismissRequest = { showJadwalDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Schedule,
              contentDescription = null,
              tint = SunsetOrange,
              modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Jadwal & Aturan Jam Kerja",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark
            )
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = BgLight,
              border = BorderStroke(1.dp, BorderMuted),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Departemen", fontSize = 12.sp, color = TextMuted)
                  Text(profilKaryawan.departemen, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
                HorizontalDivider(color = BorderMuted)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Jam Masuk Resmi", fontSize = 12.sp, color = TextMuted)
                  Text("${configKantor.jamMasukKerja} WIB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
                }
                HorizontalDivider(color = BorderMuted)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Batas Toleransi Terlambat", fontSize = 12.sp, color = TextMuted)
                  Text("${configKantor.batasTerlambat} WIB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                }
                HorizontalDivider(color = BorderMuted)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Jam Pulang Kerja", fontSize = 12.sp, color = TextMuted)
                  Text("${configKantor.jamPulangKerja} WIB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SunsetOrange)
                }
                HorizontalDivider(color = BorderMuted)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Lokasi Pusat Kantor", fontSize = 12.sp, color = TextMuted)
                  Text(configKantor.lokasiNama, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
                HorizontalDivider(color = BorderMuted)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Radius Presensi", fontSize = 12.sp, color = TextMuted)
                  Text(
                    "Maks. ${configKantor.radiusMeter} m (${if (configKantor.wajibDalamRadius) "Wajib Radius" else "Toleransi"})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (configKantor.wajibDalamRadius) StatusRed else StatusGreen
                  )
                }
              }
            }
            Text(
              text = "Pastikan GPS aktif dan Anda berada di area kantor saat mengambil swafoto presensi masuk maupun pulang.",
              fontSize = 11.sp,
              color = TextMuted,
              lineHeight = 16.sp
            )
          }
        },
        confirmButton = {
          Button(
            onClick = { showJadwalDialog = false },
            colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("Mengerti", color = Color.White, fontWeight = FontWeight.Bold)
          }
        },
        containerColor = CardWhite,
        shape = RoundedCornerShape(20.dp)
      )
    }
  }
}

/**
 * Komponen Jam Digital terisolasi agar hanya teks jam yang di-render ulang setiap detik
 * tanpa memicu recomposition seluruh layar dashboard (Sangat hemat CPU & baterai HP).
 */
@Composable
private fun LiveClockText() {
  var currentTimeString by remember {
    mutableStateOf(SimpleDateFormat("HH:mm:ss 'WIB'", Locale.getDefault()).format(Date()))
  }
  LaunchedEffect(Unit) {
    val sdf = SimpleDateFormat("HH:mm:ss 'WIB'", Locale.getDefault())
    while (true) {
      currentTimeString = sdf.format(Date())
      delay(1000L)
    }
  }
  Text(
    text = currentTimeString,
    fontSize = 26.sp,
    fontWeight = FontWeight.ExtraBold,
    color = Color.White,
    letterSpacing = 0.5.sp
  )
}

/**
 * Membuat Bitmap Swafoto simulasi berkualitas tinggi lengkap dengan
 * badge inisial avatar, nama, NIP, tanggal/waktu, titik GPS real-time, dan tanda verifikasi.
 * Memastikan presensi selalu bisa diuji dan tidak pernah crash di emulator/perangkat tanpa kamera.
 */
fun createSimulatedSelfieBitmap(
  nama: String,
  nip: String,
  tipe: String,
  lat: Double = -7.250445,
  lng: Double = 112.768845
): Bitmap {
  val width = 480
  val height = 640
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)

  val bgPaint = Paint().apply {
    color = android.graphics.Color.rgb(0, 105, 92)
  }
  canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

  val circlePaint = Paint().apply {
    color = android.graphics.Color.WHITE
    isAntiAlias = true
  }
  canvas.drawCircle(width / 2f, height / 2.5f, 110f, circlePaint)

  val avatarPaint = Paint().apply {
    color = android.graphics.Color.rgb(0, 77, 64)
    textSize = 64f
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
  }
  val initial = if (nama.length >= 2) nama.take(2).uppercase() else "KA"
  canvas.drawText(initial, width / 2f, height / 2.5f + 22f, avatarPaint)

  val textPaint = Paint().apply {
    color = android.graphics.Color.WHITE
    textSize = 21f
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
  }
  val gpsPaint = Paint().apply {
    color = android.graphics.Color.WHITE
    textSize = 17f
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
  }

  val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
  canvas.drawText(nama, width / 2f, height * 0.68f, textPaint)
  canvas.drawText("NIP: $nip", width / 2f, height * 0.74f, textPaint)
  canvas.drawText("Presensi $tipe • $timeStr", width / 2f, height * 0.80f, textPaint)
  canvas.drawText("📍 GPS: %.5f, %.5f".format(lat, lng), width / 2f, height * 0.86f, gpsPaint)
  canvas.drawText("✓ Swafoto & GPS Terverifikasi", width / 2f, height * 0.92f, textPaint)

  return bitmap
}
