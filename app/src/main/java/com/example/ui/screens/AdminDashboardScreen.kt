package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.network.XamppApiClient
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TabAdmin(val label: String, val icon: ImageVector) {
  KEHADIRAN("Kehadiran", Icons.Default.Assessment),
  MASTER_KARYAWAN("Karyawan", Icons.Default.People),
  PENGAJUAN_IZIN("Izin", Icons.Default.AssignmentTurnedIn)
}

enum class FilterStatusAbsensi(val label: String) {
  SEMUA("Semua"),
  HADIR("Hadir"),
  TERLAMBAT("Terlambat"),
  BELUM_ABSEN("Belum Absen")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
  onLogout: () -> Unit,
  jaringanManager: JaringanKantorManager? = null
) {
  val context = LocalContext.current
  val sessionManager = remember { SessionManager(context) }
  val activeJaringanManager = remember(jaringanManager) { jaringanManager ?: JaringanKantorManager(context) }
  val coroutineScope = rememberCoroutineScope()

  var selectedTab by remember { mutableStateOf(TabAdmin.KEHADIRAN) }
  var filterStatus by remember { mutableStateOf(FilterStatusAbsensi.SEMUA) }

  var configAdmin by remember { mutableStateOf(sessionManager.getKonfigurasiAdmin()) }
  var isEditModeOpen by remember { mutableStateOf(false) }
  var isTambahKaryawanOpen by remember { mutableStateOf(false) }
  var showXamppConfigDialog by remember { mutableStateOf(false) }
  var showPasswordAdminDialog by remember { mutableStateOf(false) }

  // State Edit Karyawan, Koreksi Absensi, Preview Foto & Konfirmasi Pengaman
  var karyawanDiedit by remember { mutableStateOf<DataKaryawan?>(null) }
  var nipLamaDiedit by remember { mutableStateOf("") }
  var absensiDikoreksi by remember { mutableStateOf<BarisAbsensiKaryawan?>(null) }
  var previewFotoJudul by remember { mutableStateOf("") }
  var previewFotoUrl by remember { mutableStateOf<String?>(null) }
  var previewFotoUrlKedua by remember { mutableStateOf<String?>(null) }
  var konfirmasiAksiJudul by remember { mutableStateOf<String?>(null) }
  var konfirmasiAksiPesan by remember { mutableStateOf("") }
  var onKonfirmasiAksi by remember { mutableStateOf<(() -> Unit)?>(null) }

  var isSyncing by remember { mutableStateOf(false) }
  var isConnectedToServer by remember { mutableStateOf(false) }
  var pesanStatusAdmin by remember { mutableStateOf<String?>(null) }

  // Otomatis sembunyikan banner pesan setelah 5 detik
  LaunchedEffect(pesanStatusAdmin) {
    if (pesanStatusAdmin != null) {
      delay(5000L)
      pesanStatusAdmin = null
    }
  }

  var daftarAbsensi by remember {
    mutableStateOf(sessionManager.getAdminDaftarAbsensi())
  }

  var masterKaryawanList by remember {
    mutableStateOf<List<DataKaryawan>>(sessionManager.getMasterKaryawanList())
  }
  var pengajuanIzinList by remember { mutableStateOf<List<ItemPengajuanIzinAdmin>>(emptyList()) }

  // State Dialog Edit Dashboard
  var inputJamMasuk by remember { mutableStateOf(configAdmin.jamMasukKerja) }
  var inputBatasTerlambat by remember { mutableStateOf(configAdmin.batasTerlambat) }
  var inputJamPulang by remember { mutableStateOf(configAdmin.jamPulangKerja) }
  var inputPengumuman by remember { mutableStateOf(configAdmin.pengumuman) }
  var inputLokasiNama by remember { mutableStateOf(configAdmin.lokasiNama) }
  var inputLokasiLat by remember { mutableStateOf(configAdmin.lokasiLatitude.toString()) }
  var inputLokasiLng by remember { mutableStateOf(configAdmin.lokasiLongitude.toString()) }
  var inputRadiusMeter by remember { mutableStateOf(configAdmin.radiusMeter.toString()) }
  var inputWajibRadius by remember { mutableStateOf(configAdmin.wajibDalamRadius) }
  var quickPasteKoordinat by remember { mutableStateOf("") }

  // State Dialog Tambah Karyawan Admin
  var nipBaru by remember { mutableStateOf("") }
  var namaBaru by remember { mutableStateOf("") }
  var jkBaru by remember { mutableStateOf("Laki-laki") }
  var expandedJkAdmin by remember { mutableStateOf(false) }
  var ttlBaru by remember { mutableStateOf("") }
  var alamatBaru by remember { mutableStateOf("") }
  var divisiBaru by remember { mutableStateOf("Produksi Pabrik") }
  var expandedDivisiAdmin by remember { mutableStateOf(false) }

  var serverUrlInput by remember { mutableStateOf(XamppApiClient.baseUrl) }

  fun muatDataDariServer() {
    coroutineScope.launch {
      isSyncing = true
      val dashResult = XamppApiClient.getDashboardAdminLengkap()
      if (dashResult.isSuccess) {
        val dataLengkap = dashResult.getOrNull()
        if (dataLengkap != null) {
          configAdmin = dataLengkap.config
          daftarAbsensi = dataLengkap.daftarAbsensi
          sessionManager.simpanKonfigurasiAdmin(dataLengkap.config)
          sessionManager.simpanAdminDaftarAbsensi(dataLengkap.daftarAbsensi)
          isConnectedToServer = true
        }
      } else {
        isConnectedToServer = false
      }

      val masterResult = XamppApiClient.getDaftarMasterKaryawan()
      if (masterResult.isSuccess) {
        val listMaster = masterResult.getOrNull().orEmpty()
        masterKaryawanList = listMaster
        sessionManager.simpanMasterKaryawan(listMaster)
      }

      val izinResult = XamppApiClient.getDaftarPengajuanIzin()
      if (izinResult.isSuccess) {
        pengajuanIzinList = izinResult.getOrNull().orEmpty()
      }

      isSyncing = false
    }
  }

  LaunchedEffect(Unit) {
    muatDataDariServer()
  }

  val totalHadir = remember(daftarAbsensi) {
    daftarAbsensi.count { it.status == StatusAbsensi.TEPAT_WAKTU || it.status == StatusAbsensi.TERLAMBAT }
  }
  val totalTerlambat = remember(daftarAbsensi) {
    daftarAbsensi.count { it.status == StatusAbsensi.TERLAMBAT }
  }

  val totalKaryawanTampil = remember(masterKaryawanList) {
    masterKaryawanList.size
  }

  // Daftar karyawan terdaftar yang belum memiliki catatan presensi/izin hari ini
  val belumAbsenList = remember(masterKaryawanList, daftarAbsensi) {
    masterKaryawanList.filter { k ->
      daftarAbsensi.none { a ->
        (k.nip.isNotBlank() && (a.nip.equals(k.nip, ignoreCase = true) || a.idKaryawan.equals(k.nip, ignoreCase = true))) ||
          a.idKaryawan.equals(k.id, ignoreCase = true) ||
          a.nama.equals(k.nama, ignoreCase = true)
      }
    }.map { k ->
      BarisAbsensiKaryawan(
        idKaryawan = k.id,
        nip = k.nip,
        nama = k.nama,
        departemen = k.departemen,
        jamMasuk = "-",
        jamPulang = "-",
        status = StatusAbsensi.BELUM_ABSEN
      )
    }
  }

  val totalBelumAbsen = remember(belumAbsenList, totalKaryawanTampil, totalHadir) {
    if (masterKaryawanList.isNotEmpty()) belumAbsenList.size else maxOf(0, totalKaryawanTampil - totalHadir)
  }

  var searchQuery by remember { mutableStateOf("") }
  var searchKaryawanQuery by remember { mutableStateOf("") }

  // State Asisten AI HRD (role "user" / "model" + teks)
  var showAsistenAi by remember { mutableStateOf(false) }
  val chatAiList = remember { mutableStateListOf<Pair<String, String>>() }
  var inputPertanyaanAi by remember { mutableStateOf("") }
  var isAiMenjawab by remember { mutableStateOf(false) }

  fun kirimPertanyaanAi(teks: String) {
    val pertanyaan = teks.trim()
    if (pertanyaan.isEmpty() || isAiMenjawab) return
    val riwayatSebelumnya = chatAiList.filter { it.first == "user" || it.first == "model" }.takeLast(6)
    chatAiList.add("user" to pertanyaan)
    inputPertanyaanAi = ""
    isAiMenjawab = true
    coroutineScope.launch {
      val res = XamppApiClient.tanyaAsistenAi(pertanyaan, riwayatSebelumnya)
      isAiMenjawab = false
      if (res.isSuccess) {
        chatAiList.add("model" to res.getOrNull().orEmpty())
      } else {
        chatAiList.add("error" to (res.exceptionOrNull()?.message ?: "Asisten AI gagal menjawab."))
      }
    }
  }

  val pendingIzinCount = remember(pengajuanIzinList) {
    pengajuanIzinList.count {
      !it.statusPersetujuan.startsWith("Disetujui", ignoreCase = true) &&
        !it.statusPersetujuan.startsWith("Ditolak", ignoreCase = true)
    }
  }

  val filteredMasterKaryawan = remember(masterKaryawanList, searchKaryawanQuery) {
    if (searchKaryawanQuery.isBlank()) masterKaryawanList else masterKaryawanList.filter {
      it.nama.contains(searchKaryawanQuery, ignoreCase = true) ||
        it.nip.contains(searchKaryawanQuery, ignoreCase = true) ||
        it.departemen.contains(searchKaryawanQuery, ignoreCase = true)
    }
  }

  val filteredAbsensi = remember(daftarAbsensi, belumAbsenList, filterStatus, searchQuery) {
    val list = when (filterStatus) {
      FilterStatusAbsensi.SEMUA -> daftarAbsensi
      FilterStatusAbsensi.HADIR -> daftarAbsensi.filter { it.status == StatusAbsensi.TEPAT_WAKTU || it.status == StatusAbsensi.TERLAMBAT }
      FilterStatusAbsensi.TERLAMBAT -> daftarAbsensi.filter { it.status == StatusAbsensi.TERLAMBAT }
      FilterStatusAbsensi.BELUM_ABSEN -> belumAbsenList
    }
    if (searchQuery.isBlank()) list else list.filter {
      it.nama.contains(searchQuery, ignoreCase = true) ||
        it.idKaryawan.contains(searchQuery, ignoreCase = true) ||
        it.nip.contains(searchQuery, ignoreCase = true)
    }
  }

  fun bagikanRekapHarian() {
    val tglStr = java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date())
    val sb = StringBuilder()
    sb.appendLine("📊 *REKAP PRESENSI HARIAN - PT. FADIYA KENCANA*")
    sb.appendLine("📅 Tanggal: $tglStr")
    sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━")
    sb.appendLine("• Total Karyawan : $totalKaryawanTampil")
    sb.appendLine("• Hadir          : $totalHadir")
    sb.appendLine("• Terlambat      : $totalTerlambat")
    sb.appendLine("• Belum Absen    : $totalBelumAbsen")
    sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━")
    if (daftarAbsensi.isNotEmpty()) {
      sb.appendLine("\n*Daftar Kehadiran & Izin Hari Ini:*")
      daftarAbsensi.forEachIndexed { idx, row ->
        val nipTampil = row.nip.ifBlank { row.idKaryawan }
        sb.appendLine("${idx + 1}. ${row.nama} ($nipTampil) - ${row.status.label} [Masuk: ${row.jamMasuk}, Pulang: ${row.jamPulang}]")
      }
    }
    if (belumAbsenList.isNotEmpty()) {
      sb.appendLine("\n*Belum Absen (${belumAbsenList.size}):*")
      belumAbsenList.forEachIndexed { idx, row ->
        sb.appendLine("${idx + 1}. ${row.nama} (${row.nip}) - ${row.departemen}")
      }
    }
    try {
      val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Rekap Presensi PT. Fadiya Kencana - $tglStr")
        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
      }
      context.startActivity(android.content.Intent.createChooser(sendIntent, "Bagikan Laporan Presensi via"))
    } catch (_: Exception) {
      pesanStatusAdmin = "Tidak ditemukan aplikasi untuk membagikan laporan."
    }
  }

  Scaffold(
    containerColor = BgLight,
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(FreshGreenButtonBrush),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Dashboard Admin",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isConnectedToServer) StatusGreen else StatusAmber)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (isConnectedToServer) "Online (Server Aktif)" else "Offline / Menghubungkan...",
                  fontSize = 10.5.sp,
                  color = if (isConnectedToServer) StatusGreen else StatusAmber,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        },
        actions = {
          IconButton(
            onClick = { showAsistenAi = true },
            modifier = Modifier.testTag("admin_ai_assistant_button")
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = "Asisten AI HRD",
              tint = SunsetAmber
            )
          }
          IconButton(
            onClick = { showPasswordAdminDialog = true },
            modifier = Modifier.testTag("admin_change_password_button")
          ) {
            Icon(
              imageVector = Icons.Default.Key,
              contentDescription = "Ubah Kata Sandi Admin",
              tint = TealPrimary
            )
          }
          IconButton(
            onClick = { showXamppConfigDialog = true },
            modifier = Modifier.testTag("admin_xampp_server_button")
          ) {
            Icon(
              imageVector = Icons.Default.Dns,
              contentDescription = "Pengaturan Server",
              tint = TealPrimary
            )
          }
          IconButton(
            onClick = { muatDataDariServer() },
            modifier = Modifier.testTag("admin_sync_button")
          ) {
            if (isSyncing) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = TealPrimary
              )
            } else {
              Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Sinkronkan Data Server",
                tint = TealPrimary
              )
            }
          }
          IconButton(onClick = onLogout, modifier = Modifier.testTag("admin_logout_button")) {
            Icon(
              imageVector = Icons.Default.Logout,
              contentDescription = "Keluar",
              tint = StatusRed
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgLight)
      )
    },
    bottomBar = {
      // NAVIGATION TABS V2 — FLOATING CAPSULE PILL BAR
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.Transparent)
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .testTag("admin_bottom_navigation"),
        contentAlignment = Alignment.BottomCenter
      ) {
        Surface(
          shape = RoundedCornerShape(32.dp),
          color = CardWhite,
          shadowElevation = 10.dp,
          border = BorderStroke(1.dp, BorderMuted),
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .height(62.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TabAdmin.values().forEach { tab ->
              val isSelected = selectedTab == tab
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(999.dp))
                  .then(
                    if (isSelected) Modifier.background(FreshGreenButtonBrush)
                    else Modifier.background(Color.Transparent)
                  )
                  .clickable { selectedTab = tab }
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = tab.icon,
                  contentDescription = tab.label,
                  tint = if (isSelected) Color.White else TextMuted,
                  modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = tab.label,
                  fontSize = 11.5.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else TextMuted,
                  maxLines = 1,
                  softWrap = false
                )
                if (tab == TabAdmin.PENGAJUAN_IZIN && pendingIzinCount > 0) {
                  Spacer(modifier = Modifier.width(4.dp))
                  Surface(
                    shape = CircleShape,
                    color = if (isSelected) Color.White else StatusRed
                  ) {
                    Text(
                      text = "$pendingIzinCount",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (isSelected) StatusRed else Color.White,
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                  }
                }
              }
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
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 680.dp)
          .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item(key = "top_spacer") {
          Spacer(modifier = Modifier.height(4.dp))
        }

        if (pesanStatusAdmin != null) {
          item(key = "admin_status_banner") {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = StatusGreenBg),
              border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.35f)),
              elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_status_banner")
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = StatusGreen,
                  modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Pembaruan Tersinkronisasi",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = StatusGreen
                  )
                  Text(
                    text = pesanStatusAdmin ?: "",
                    fontSize = 12.sp,
                    color = TextDark
                  )
                }
                IconButton(onClick = { pesanStatusAdmin = null }, modifier = Modifier.size(28.dp)) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }

        item(key = "admin_server_status_card") {
          if (!isConnectedToServer) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = StatusAmberBg),
              border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.4f)),
              elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
              modifier = Modifier.fillMaxWidth().testTag("admin_server_card")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = StatusAmber,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "Server Belum Terhubung",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = StatusAmber
                    )
                    Text(
                      text = "Periksa alamat server untuk memuat data terbaru",
                      fontSize = 11.sp,
                      color = TextMuted
                    )
                  }
                }
                FilledTonalButton(
                  onClick = { showXamppConfigDialog = true },
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = StatusAmber.copy(alpha = 0.2f),
                    contentColor = StatusAmber
                  )
                ) {
                  Text("Atur Server", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        when (selectedTab) {
          TabAdmin.KEHADIRAN -> {
            item(key = "admin_metrics") {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  AdminMetricCard(
                    judul = "Total Karyawan",
                    nilai = "$totalKaryawanTampil",
                    icon = Icons.Default.Groups,
                    warna = TealPrimary,
                    warnaBg = TealContainer,
                    modifier = Modifier.weight(1f)
                  )
                  AdminMetricCard(
                    judul = "Hadir",
                    nilai = "$totalHadir",
                    icon = Icons.Default.CheckCircle,
                    warna = StatusGreen,
                    warnaBg = StatusGreenBg,
                    modifier = Modifier.weight(1f)
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  AdminMetricCard(
                    judul = "Terlambat",
                    nilai = "$totalTerlambat",
                    icon = Icons.Default.Warning,
                    warna = StatusAmber,
                    warnaBg = StatusAmberBg,
                    modifier = Modifier.weight(1f)
                  )
                  AdminMetricCard(
                    judul = "Belum Absen",
                    nilai = "$totalBelumAbsen",
                    icon = Icons.Default.AccessTime,
                    warna = StatusRed,
                    warnaBg = StatusRedBg,
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }

            item(key = "admin_config_card") {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(24.dp))
                  .background(FreshGreenBannerBrush)
                  .border(BorderStroke(1.2.dp, Color(0xFF34D399).copy(alpha = 0.45f)), RoundedCornerShape(24.dp))
                  .testTag("admin_config_card")
              ) {
                // LAPISAN 1: DEKORASI GELOMBANG TOPOGRAFI & RESONANCE RINGS
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

                  // Garis Kontur 2 (Tengah)
                  val p2 = Path().apply {
                    moveTo(0f, h * 0.48f)
                    cubicTo(w * 0.35f, h * 0.30f, w * 0.65f, h * 0.58f, w, h * 0.28f)
                  }
                  drawPath(p2, Color(0xFFA7F3D0).copy(alpha = 0.58f), style = Stroke(width = 2.dp.toPx()))

                  // Garis Kontur 3 (Bawah)
                  val p3 = Path().apply {
                    moveTo(0f, h * 0.62f)
                    cubicTo(w * 0.38f, h * 0.44f, w * 0.65f, h * 0.72f, w, h * 0.45f)
                  }
                  drawPath(p3, Color(0xFF34D399).copy(alpha = 0.45f), style = Stroke(width = 2.dp.toPx()))

                  // Cincin Resonansi Topografi di Bagian Kanan Kartu
                  val ringCenter = Offset(w * 0.88f, h * 0.38f)
                  drawCircle(
                    color = Color(0xFF6EE7B7).copy(alpha = 0.42f),
                    radius = 55.dp.toPx(),
                    center = ringCenter,
                    style = Stroke(width = 1.8.dp.toPx())
                  )
                  drawCircle(
                    color = Color(0xFF34D399).copy(alpha = 0.52f),
                    radius = 36.dp.toPx(),
                    center = ringCenter,
                    style = Stroke(width = 1.8.dp.toPx())
                  )
                  drawCircle(
                    color = Color(0xFFA7F3D0).copy(alpha = 0.65f),
                    radius = 18.dp.toPx(),
                    center = ringCenter,
                    style = Stroke(width = 1.8.dp.toPx())
                  )
                  drawCircle(
                    color = Color.White.copy(alpha = 0.90f),
                    radius = 3.5.dp.toPx(),
                    center = ringCenter
                  )

                  // Titik Konstelasi Cahaya Digital (Sparkles)
                  val sparkles = listOf(
                    Offset(w * 0.78f, h * 0.14f),
                    Offset(w * 0.93f, h * 0.22f),
                    Offset(w * 0.64f, h * 0.28f),
                    Offset(w * 0.50f, h * 0.18f),
                    Offset(w * 0.72f, h * 0.60f)
                  )
                  for (sparkle in sparkles) {
                    drawCircle(Color(0xFF34D399).copy(alpha = 0.50f), 5.dp.toPx(), sparkle)
                    drawCircle(Color.White.copy(alpha = 0.95f), 2.dp.toPx(), sparkle)
                  }
                }

                // LAPISAN 2: KONTEN KARTU
                Column(modifier = Modifier.padding(18.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      modifier = Modifier.weight(1f),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier
                          .size(30.dp)
                          .clip(CircleShape)
                          .background(Color.White.copy(alpha = 0.15f))
                          .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), CircleShape),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          imageVector = Icons.Default.Campaign,
                          contentDescription = null,
                          tint = Color.White,
                          modifier = Modifier.size(16.dp)
                        )
                      }
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "Jadwal & Pengumuman",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                      onClick = {
                        inputJamMasuk = configAdmin.jamMasukKerja
                        inputBatasTerlambat = configAdmin.batasTerlambat
                        inputJamPulang = configAdmin.jamPulangKerja
                        inputPengumuman = configAdmin.pengumuman
                        inputLokasiNama = configAdmin.lokasiNama
                        inputLokasiLat = configAdmin.lokasiLatitude.toString()
                        inputLokasiLng = configAdmin.lokasiLongitude.toString()
                        inputRadiusMeter = configAdmin.radiusMeter.toString()
                        inputWajibRadius = configAdmin.wajibDalamRadius
                        quickPasteKoordinat = ""
                        isEditModeOpen = true
                      },
                      shape = RoundedCornerShape(999.dp),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.22f),
                        contentColor = Color.White
                      ),
                      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                      modifier = Modifier
                        .wrapContentWidth()
                        .defaultMinSize(minWidth = 68.dp)
                        .testTag("button_edit_dashboard")
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                      ) {
                        Icon(Icons.Default.Edit, contentDescription = "Ubah", modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = "Ubah",
                          fontSize = 11.5.sp,
                          fontWeight = FontWeight.Bold,
                          softWrap = false,
                          maxLines = 1
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(14.dp))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = CharcoalSurface,
                      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                      modifier = Modifier.weight(1f)
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Jam Masuk", fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f))
                        Text(text = configAdmin.jamMasukKerja, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                      }
                    }
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = CharcoalSurface,
                      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                      modifier = Modifier.weight(1f)
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Batas Terlambat", fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f))
                        Text(text = configAdmin.batasTerlambat, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SunsetAmber)
                      }
                    }
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = CharcoalSurface,
                      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                      modifier = Modifier.weight(1f)
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Jam Pulang", fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f))
                        Text(text = configAdmin.jamPulangKerja, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.LocationOn, contentDescription = null, tint = SunsetAmber, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "${configAdmin.lokasiNama}  •  Radius ${configAdmin.radiusMeter}m (${if (configAdmin.wajibDalamRadius) "Wajib Radius" else "Toleransi Jarak"})",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                      )
                    }
                  }

                  if (configAdmin.pengumuman.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                      text = "📢 ${configAdmin.pengumuman}",
                      fontSize = 12.sp,
                      color = Color.White.copy(alpha = 0.90f),
                      lineHeight = 17.sp
                    )
                  }
                }
              }
            }

            item(key = "admin_table_header") {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text(
                      text = "Kehadiran Hari Ini",
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark
                    )
                    Text(
                      text = "${daftarAbsensi.size} Tercatat • $totalBelumAbsen Belum Absen",
                      fontSize = 11.sp,
                      color = TextMuted,
                      fontWeight = FontWeight.Medium
                    )
                  }
                  OutlinedButton(
                    onClick = { bagikanRekapHarian() },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.5f))
                  ) {
                    Icon(
                      imageVector = Icons.Default.Share,
                      contentDescription = "Bagikan Rekap",
                      tint = TealPrimary,
                      modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Bagikan Rekap",
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = TealPrimary
                    )
                  }
                }

                OutlinedTextField(
                  value = searchQuery,
                  onValueChange = { searchQuery = it },
                  label = { Text("Cari Nama / NIP") },
                  leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TealPrimary) },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  colors = absensiTextFieldColors(),
                  modifier = Modifier.fillMaxWidth()
                )

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  FilterStatusAbsensi.values().forEach { filter ->
                    val isFilterSelected = filterStatus == filter
                    FilterChip(
                      selected = isFilterSelected,
                      onClick = { filterStatus = filter },
                      shape = RoundedCornerShape(999.dp),
                      label = {
                        val count = when (filter) {
                          FilterStatusAbsensi.SEMUA -> daftarAbsensi.size
                          FilterStatusAbsensi.HADIR -> totalHadir
                          FilterStatusAbsensi.TERLAMBAT -> totalTerlambat
                          FilterStatusAbsensi.BELUM_ABSEN -> totalBelumAbsen
                        }
                        Text("${filter.label} ($count)", fontSize = 11.5.sp, fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Normal)
                      },
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealContainer,
                        selectedLabelColor = TealDark
                      )
                    )
                  }
                }
              }
            }

            if (filteredAbsensi.isEmpty()) {
              item(key = "admin_empty_table") {
                Card(
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = CardWhite),
                  border = BorderStroke(1.dp, BorderMuted),
                  elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_empty_table_card")
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Box(
                      modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TealContainer.copy(alpha = 0.5f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                      )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                      text = if (daftarAbsensi.isEmpty() && filterStatus != FilterStatusAbsensi.BELUM_ABSEN) "Belum Ada Kehadiran Hari Ini" else "Tidak Ada Data yang Sesuai",
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = if (filterStatus == FilterStatusAbsensi.BELUM_ABSEN) "Semua karyawan terdaftar sudah melakukan presensi hari ini." else "Data presensi karyawan yang masuk hari ini akan ditampilkan di sini.",
                      fontSize = 12.sp,
                      color = TextMuted,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            } else {
              items(items = filteredAbsensi, key = { "${it.idKaryawan}_${it.status.name}" }) { row ->
                BarisTabelAbsensiCard(
                  row = row,
                  onPreviewFoto = { judul, urlMasuk, urlPulang ->
                    previewFotoJudul = judul
                    previewFotoUrl = urlMasuk.ifBlank { urlPulang }
                    previewFotoUrlKedua = if (urlMasuk.isNotBlank() && urlPulang.isNotBlank()) urlPulang else null
                  },
                  onEditAbsensi = { itemAbsen ->
                    absensiDikoreksi = itemAbsen
                  },
                  onHapusAbsensi = { itemAbsen ->
                    if (itemAbsen.recordId > 0) {
                      konfirmasiAksiJudul = "Hapus Log Presensi?"
                      konfirmasiAksiPesan = "Apakah Anda yakin ingin menghapus data presensi hari ini milik ${itemAbsen.nama} (${itemAbsen.nip})?"
                      onKonfirmasiAksi = {
                        coroutineScope.launch {
                          val res = XamppApiClient.hapusPresensiAdmin(itemAbsen.recordId)
                          if (res.isSuccess) {
                            pesanStatusAdmin = "Log presensi ${itemAbsen.nama} berhasil dihapus."
                            muatDataDariServer()
                          } else {
                            pesanStatusAdmin = "Gagal menghapus presensi: ${res.exceptionOrNull()?.message}"
                          }
                        }
                      }
                    }
                  }
                )
              }
            }
          }

          TabAdmin.MASTER_KARYAWAN -> {
            item(key = "master_karyawan_header") {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = CardWhite),
                  border = BorderStroke(1.dp, BorderMuted),
                  elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      modifier = Modifier.weight(1f),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier
                          .size(42.dp)
                          .clip(CircleShape)
                          .background(TealContainer)
                          .border(BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)), CircleShape),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          imageVector = Icons.Default.People,
                          contentDescription = null,
                          tint = TealDark,
                          modifier = Modifier.size(22.dp)
                        )
                      }
                      Spacer(modifier = Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "Data Karyawan",
                          fontSize = 15.sp,
                          fontWeight = FontWeight.Bold,
                          color = TextDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                          text = "${masterKaryawanList.size} Karyawan Terdaftar",
                          fontSize = 11.5.sp,
                          color = TextMuted
                        )
                      }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                      onClick = {
                        nipBaru = ""
                        namaBaru = ""
                        jkBaru = "Laki-laki"
                        ttlBaru = ""
                        alamatBaru = ""
                        divisiBaru = "Produksi Pabrik"
                        isTambahKaryawanOpen = true
                      },
                      shape = RoundedCornerShape(999.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                      Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(5.dp))
                      Text("Tambah", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CharcoalSurface,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.LocationOn, contentDescription = null, tint = SunsetAmber, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "${configAdmin.lokasiNama}  •  Radius ${configAdmin.radiusMeter}m (${if (configAdmin.wajibDalamRadius) "Wajib Radius" else "Toleransi Jarak"})",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                      )
                    }
                  }
                }

                OutlinedTextField(
                  value = searchKaryawanQuery,
                  onValueChange = { searchKaryawanQuery = it },
                  label = { Text("Cari Nama / NIP / Departemen") },
                  leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TealPrimary) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  colors = absensiTextFieldColors(),
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }

            if (filteredMasterKaryawan.isEmpty()) {
              item(key = "empty_master_karyawan") {
                Card(
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = CardWhite),
                  border = BorderStroke(1.dp, BorderMuted),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Icon(
                      imageVector = Icons.Default.PeopleOutline,
                      contentDescription = null,
                      tint = TextMuted,
                      modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                      text = if (masterKaryawanList.isEmpty()) "Belum Ada Karyawan Terdaftar" else "Karyawan Tidak Ditemukan",
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = if (masterKaryawanList.isEmpty()) "Tekan tombol Tambah di atas untuk mendaftarkan karyawan baru." else "Tidak ada karyawan dengan kata kunci '$searchKaryawanQuery'.",
                      fontSize = 12.sp,
                      color = TextMuted,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            } else {
              items(items = filteredMasterKaryawan, key = { it.id }) { karyawan ->
                Card(
                  shape = RoundedCornerShape(14.dp),
                  colors = CardDefaults.cardColors(containerColor = CardWhite),
                  border = BorderStroke(1.dp, BorderMuted),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                      Box(
                        modifier = Modifier
                          .size(40.dp)
                          .clip(CircleShape)
                          .background(TealContainer),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          text = karyawan.avatarInisial,
                          fontSize = 14.sp,
                          fontWeight = FontWeight.Bold,
                          color = TealDark
                        )
                      }
                      Spacer(modifier = Modifier.width(12.dp))
                      Column {
                        Text(
                          text = karyawan.nama,
                          fontSize = 14.sp,
                          fontWeight = FontWeight.Bold,
                          color = TextDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                          text = "NIP ${karyawan.nip} • ${karyawan.departemen}",
                          fontSize = 12.sp,
                          color = TealDark,
                          fontWeight = FontWeight.Medium
                        )
                        if (karyawan.alamat.isNotBlank() && karyawan.alamat != "-") {
                          Spacer(modifier = Modifier.height(2.dp))
                          Text(
                            text = karyawan.alamat,
                            fontSize = 11.sp,
                            color = TextMuted
                          )
                        }
                      }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                      IconButton(
                        onClick = {
                          nipLamaDiedit = karyawan.nip
                          karyawanDiedit = karyawan
                        },
                        modifier = Modifier.size(32.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Default.Edit,
                          contentDescription = "Edit Karyawan",
                          tint = TealPrimary,
                          modifier = Modifier.size(18.dp)
                        )
                      }
                      OutlinedButton(
                        onClick = {
                          konfirmasiAksiJudul = "Reset Perangkat HP?"
                          konfirmasiAksiPesan = "Perangkat yang terikat pada akun ${karyawan.nama} (NIP ${karyawan.nip}) akan dilepas agar karyawan dapat login di HP baru."
                          onKonfirmasiAksi = {
                            coroutineScope.launch {
                              val res = XamppApiClient.resetDeviceKaryawan(karyawan.nip)
                              if (res.isSuccess) {
                                pesanStatusAdmin = "Perangkat ${karyawan.nama} berhasil direset."
                                muatDataDariServer()
                              } else {
                                pesanStatusAdmin = "Gagal mereset perangkat: ${res.exceptionOrNull()?.message}"
                              }
                            }
                          }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                      ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Reset HP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                      }
                      IconButton(
                        onClick = {
                          konfirmasiAksiJudul = "Hapus Data Karyawan?"
                          konfirmasiAksiPesan = "Apakah Anda yakin ingin menghapus data ${karyawan.nama} (NIP ${karyawan.nip}) secara permanen?"
                          onKonfirmasiAksi = {
                            coroutineScope.launch {
                              val res = XamppApiClient.hapusKaryawanMaster(karyawan.nip)
                              if (res.isSuccess) {
                                pesanStatusAdmin = "Karyawan ${karyawan.nama} berhasil dihapus."
                                muatDataDariServer()
                              } else {
                                pesanStatusAdmin = "Gagal menghapus karyawan: ${res.exceptionOrNull()?.message}"
                              }
                            }
                          }
                        },
                        modifier = Modifier.size(32.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Default.DeleteOutline,
                          contentDescription = "Hapus Karyawan",
                          tint = StatusRed,
                          modifier = Modifier.size(18.dp)
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CharcoalSurface,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.LocationOn, contentDescription = null, tint = SunsetAmber, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "${configAdmin.lokasiNama}  •  Radius ${configAdmin.radiusMeter}m (${if (configAdmin.wajibDalamRadius) "Wajib Radius" else "Toleransi Jarak"})",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                      )
                    }
                  }
                }
              }
            }
          }

          TabAdmin.PENGAJUAN_IZIN -> {
            item(key = "pengajuan_izin_header") {
              Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderMuted),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(TealContainer)
                        .border(BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)), CircleShape),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.AssignmentTurnedIn,
                        contentDescription = null,
                        tint = TealDark,
                        modifier = Modifier.size(22.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Pengajuan Izin & Cuti",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = "Kelola permohonan izin, sakit, dan cuti karyawan",
                        fontSize = 11.5.sp,
                        color = TextMuted
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = TealContainer,
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f))
                  ) {
                    Text(
                      text = "${pengajuanIzinList.size} Data",
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = TealDark,
                      maxLines = 1,
                      softWrap = false,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                  }
                }
              }
            }

            if (pengajuanIzinList.isEmpty()) {
              item(key = "empty_izin") {
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = CardWhite),
                  border = BorderStroke(1.dp, BorderMuted),
                  elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 32.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Box(
                      modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(TealContainer)
                        .border(BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)), CircleShape),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.AssignmentTurnedIn,
                        contentDescription = null,
                        tint = TealDark,
                        modifier = Modifier.size(28.dp)
                      )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                      text = "Belum Ada Pengajuan Izin",
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = "Permohonan izin atau cuti yang diajukan karyawan akan tampil di sini.",
                      fontSize = 12.sp,
                      color = TextMuted,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            } else {
              items(items = pengajuanIzinList, key = { it.id }) { izin ->
                val isApproved = izin.statusPersetujuan.startsWith("Disetujui", ignoreCase = true)
                val isRejected = izin.statusPersetujuan.startsWith("Ditolak", ignoreCase = true)
                Card(
                  shape = RoundedCornerShape(14.dp),
                  colors = CardDefaults.cardColors(containerColor = CardWhite),
                  border = BorderStroke(1.dp, BorderMuted),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column {
                        Text(text = izin.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                        Text(text = "${izin.jenisIzin} • ${izin.tanggalDiajukan}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TealDark)
                      }
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                          shape = RoundedCornerShape(6.dp),
                          color = if (isApproved) StatusGreenBg else if (isRejected) StatusRedBg else StatusAmberBg
                        ) {
                          Text(
                            text = izin.statusPersetujuan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isApproved) StatusGreen else if (isRejected) StatusRed else StatusAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                          )
                        }
                        IconButton(
                          onClick = {
                            konfirmasiAksiJudul = "Hapus Pengajuan Izin?"
                            konfirmasiAksiPesan = "Data pengajuan izin milik ${izin.nama} akan dihapus secara permanen."
                            onKonfirmasiAksi = {
                              coroutineScope.launch {
                                val res = XamppApiClient.hapusIzinAdmin(izin.id)
                                if (res.isSuccess) {
                                  pesanStatusAdmin = "Data izin ${izin.nama} berhasil dihapus."
                                  muatDataDariServer()
                                } else {
                                  pesanStatusAdmin = "Gagal menghapus izin: ${res.exceptionOrNull()?.message}"
                                }
                              }
                            }
                          },
                          modifier = Modifier
                            .size(28.dp)
                            .padding(start = 4.dp)
                        ) {
                          Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Izin",
                            tint = StatusRed.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                          )
                        }
                      }
                    }
                    if (izin.alasan.isNotBlank()) {
                      Spacer(modifier = Modifier.height(6.dp))
                      Text(text = izin.alasan, fontSize = 12.sp, color = TextMuted)
                    }

                    if (izin.buktiFotoUrl.isNotBlank()) {
                      Spacer(modifier = Modifier.height(8.dp))
                      OutlinedButton(
                        onClick = {
                          previewFotoJudul = "Bukti ${izin.jenisIzin} — ${izin.nama}"
                          previewFotoUrl = izin.buktiFotoUrl
                          previewFotoUrlKedua = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                      ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = TealPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lihat Bukti Foto / Surat Dokter", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TealDark)
                      }
                    }

                    if (!isApproved && !isRejected) {
                      Spacer(modifier = Modifier.height(10.dp))
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Button(
                          onClick = {
                            coroutineScope.launch {
                              val res = XamppApiClient.updateStatusIzin(izin.id, "Disetujui")
                              if (res.isSuccess) {
                                pesanStatusAdmin = "Permohonan izin ${izin.nama} disetujui."
                                muatDataDariServer()
                              } else {
                                pesanStatusAdmin = "Gagal menyetujui izin: ${res.exceptionOrNull()?.message}"
                              }
                            }
                          },
                          shape = RoundedCornerShape(8.dp),
                          colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                          modifier = Modifier.weight(1f)
                        ) {
                          Text("Setujui", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                          onClick = {
                            coroutineScope.launch {
                              val res = XamppApiClient.updateStatusIzin(izin.id, "Ditolak")
                              if (res.isSuccess) {
                                pesanStatusAdmin = "Permohonan izin ${izin.nama} ditolak."
                                muatDataDariServer()
                              } else {
                                pesanStatusAdmin = "Gagal menolak izin: ${res.exceptionOrNull()?.message}"
                              }
                            }
                          },
                          shape = RoundedCornerShape(8.dp),
                          colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                          border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                          modifier = Modifier.weight(1f)
                        ) {
                          Text("Tolak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CharcoalSurface,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.LocationOn, contentDescription = null, tint = SunsetAmber, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "${configAdmin.lokasiNama}  •  Radius ${configAdmin.radiusMeter}m (${if (configAdmin.wajibDalamRadius) "Wajib Radius" else "Toleransi Jarak"})",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                      )
                    }
                  }
                }
              }
            }
          }
        }

        item(key = "bottom_spacer") {
          Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }

    // DIALOG ASISTEN AI HRD
    if (showAsistenAi) {
      val chatListState = rememberLazyListState()
      LaunchedEffect(chatAiList.size, isAiMenjawab) {
        val total = chatAiList.size + (if (isAiMenjawab) 1 else 0) + 1
        chatListState.animateScrollToItem(maxOf(0, total - 1))
      }
      val saranPertanyaan = listOf(
        "Siapa saja yang terlambat hari ini?",
        "Siapa yang terlambat lebih dari 2 kali minggu ini?",
        "Berapa persentase kehadiran tiap departemen bulan ini?",
        "Buatkan ringkasan laporan kehadiran bulan ini untuk Direktur."
      )

      AlertDialog(
        onDismissRequest = { showAsistenAi = false },
        containerColor = CardWhite,
        shape = RoundedCornerShape(22.dp),
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TealContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Asisten AI HRD", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
              Text("Data presensi & izin 62 hari terakhir", fontSize = 11.sp, color = TextMuted)
            }
            if (chatAiList.isNotEmpty()) {
              IconButton(onClick = { chatAiList.clear() }, enabled = !isAiMenjawab) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Bersihkan Percakapan", tint = TextMuted)
              }
            }
          }
        },
        text = {
          Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
              state = chatListState,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 400.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              item(key = "ai_sapaan") {
                Surface(
                  shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                  color = BgLight,
                  border = BorderStroke(1.dp, BorderMuted)
                ) {
                  Text(
                    text = "Halo! Saya Asisten AI HRD Fadiya Kencana. Silakan tanya tentang kehadiran, keterlambatan, atau izin karyawan.",
                    fontSize = 12.sp,
                    color = TextDark,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(10.dp)
                  )
                }
              }

              if (chatAiList.isEmpty()) {
                item(key = "ai_saran") {
                  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    saranPertanyaan.forEach { saran ->
                      Surface(
                        onClick = { kirimPertanyaanAi(saran) },
                        shape = RoundedCornerShape(20.dp),
                        color = TealContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f))
                      ) {
                        Text(
                          text = saran,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = TealDark,
                          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CharcoalSurface,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.LocationOn, contentDescription = null, tint = SunsetAmber, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "${configAdmin.lokasiNama}  •  Radius ${configAdmin.radiusMeter}m (${if (configAdmin.wajibDalamRadius) "Wajib Radius" else "Toleransi Jarak"})",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                      )
                    }
                  }
                }
              }

              items(chatAiList.size) { idx ->
                val (role, teks) = chatAiList[idx]
                val isUser = role == "user"
                val isErr = role == "error"
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                  Surface(
                    shape = if (isUser)
                      RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                    else
                      RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                    color = when {
                      isUser -> TealPrimary
                      isErr -> StatusRedBg
                      else -> BgLight
                    },
                    border = if (isUser) null else BorderStroke(1.dp, if (isErr) StatusRed.copy(alpha = 0.3f) else BorderMuted),
                    modifier = Modifier.widthIn(max = 290.dp)
                  ) {
                    Text(
                      text = if (isUser || isErr) AnnotatedString(teks) else teksMarkdownAi(teks),
                      fontSize = 12.sp,
                      lineHeight = 17.sp,
                      color = when {
                        isUser -> Color.White
                        isErr -> StatusRed
                        else -> TextDark
                      },
                      modifier = Modifier.padding(10.dp)
                    )
                  }
                }
              }

              if (isAiMenjawab) {
                item(key = "ai_loading") {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = TealPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menganalisis data kehadiran...", fontSize = 11.sp, color = TextMuted)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = inputPertanyaanAi,
              onValueChange = { if (it.length <= 1000) inputPertanyaanAi = it },
              placeholder = { Text("Tulis pertanyaan...", fontSize = 12.sp) },
              maxLines = 3,
              shape = RoundedCornerShape(14.dp),
              colors = absensiTextFieldColors(),
              trailingIcon = {
                IconButton(
                  onClick = { kirimPertanyaanAi(inputPertanyaanAi) },
                  enabled = !isAiMenjawab && inputPertanyaanAi.isNotBlank()
                ) {
                  Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Kirim",
                    tint = if (!isAiMenjawab && inputPertanyaanAi.isNotBlank()) TealPrimary else TextMuted
                  )
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_ai_input")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "🔒 Hanya nama, NIP, departemen, jam & status yang dikirim ke AI.",
              fontSize = 10.sp,
              color = TextMuted
            )
          }
        },
        confirmButton = {
          TextButton(onClick = { showAsistenAi = false }) {
            Text("Tutup", color = TextMuted, fontWeight = FontWeight.SemiBold)
          }
        }
      )
    }

    // DIALOG EDIT DASHBOARD: JAM KERJA & LOKASI KANTOR
    if (isEditModeOpen) {
      AlertDialog(
        onDismissRequest = { isEditModeOpen = false },
        title = { Text("Pengaturan Jam & Lokasi Kantor", fontWeight = FontWeight.Bold) },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text("1. Jam Kerja Kantor", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
            OutlinedTextField(
              value = inputJamMasuk,
              onValueChange = { inputJamMasuk = it },
              label = { Text("Jam Masuk (Contoh: 08:00)") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
              value = inputBatasTerlambat,
              onValueChange = { inputBatasTerlambat = it },
              label = { Text("Batas Terlambat (Contoh: 08:15)") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
              value = inputJamPulang,
              onValueChange = { inputJamPulang = it },
              label = { Text("Jam Pulang (Contoh: 17:00)") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
              value = inputPengumuman,
              onValueChange = { inputPengumuman = it },
              label = { Text("Broadcast Pengumuman Kantor") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp),
              maxLines = 3
            )

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = BorderMuted)
            Spacer(modifier = Modifier.height(4.dp))

            Text("2. Titik Lokasi Kantor Pusat & Radius (Geofence)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)

            // Tombol Cepat: Ambil GPS HP Saat Ini
            OutlinedButton(
              onClick = {
                activeJaringanManager.setTitikKantorKeLokasiSaatIni()
                inputLokasiLat = activeJaringanManager.kantorLatitude.toString()
                inputLokasiLng = activeJaringanManager.kantorLongitude.toString()
                Toast.makeText(context, "Titik GPS HP disalin: %.5f, %.5f".format(activeJaringanManager.kantorLatitude, activeJaringanManager.kantorLongitude), Toast.LENGTH_SHORT).show()
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = TealPrimary)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Ambil Titik GPS HP Saat Ini", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
            }

            // Tempel Koordinat dari HP / Maps
            OutlinedTextField(
              value = quickPasteKoordinat,
              onValueChange = { q ->
                quickPasteKoordinat = q
                val regex = Regex("""([+-]?\d+(?:\.\d+)?)\s*[,/ ]\s*([+-]?\d+(?:\.\d+)?)""")
                val match = regex.find(q)
                if (match != null) {
                  inputLokasiLat = match.groupValues[1]
                  inputLokasiLng = match.groupValues[2]
                  Toast.makeText(context, "Koordinat berhasil diekstrak!", Toast.LENGTH_SHORT).show()
                }
              },
              label = { Text("Tempel Cepat Koordinat (lat, lng)") },
              placeholder = { Text("Contoh: -7.58854, 110.79566") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp),
              singleLine = true
            )

            OutlinedTextField(
              value = inputLokasiNama,
              onValueChange = { inputLokasiNama = it },
              label = { Text("Nama Kantor / Titik Pusat") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = inputLokasiLat,
                onValueChange = { inputLokasiLat = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
              )
              OutlinedTextField(
                value = inputLokasiLng,
                onValueChange = { inputLokasiLng = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
              )
            }

            OutlinedTextField(
              value = inputRadiusMeter,
              onValueChange = { inputRadiusMeter = it },
              label = { Text("Radius Maksimal (Meter)") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = PillFieldBg,
              border = BorderStroke(1.dp, BorderMuted),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Wajib di Radius Kantor", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                  Text(
                    if (inputWajibRadius) "Tolak presensi di luar radius" else "Toleransi (catat jarak saja)",
                    fontSize = 10.sp,
                    color = TextMuted
                  )
                }
                Switch(
                  checked = inputWajibRadius,
                  onCheckedChange = { inputWajibRadius = it }
                )
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val latVal = inputLokasiLat.toDoubleOrNull() ?: configAdmin.lokasiLatitude
              val lngVal = inputLokasiLng.toDoubleOrNull() ?: configAdmin.lokasiLongitude
              val radVal = inputRadiusMeter.toIntOrNull() ?: configAdmin.radiusMeter
              val configBaru = configAdmin.copy(
                jamMasukKerja = inputJamMasuk.trim().ifEmpty { "08:00" },
                batasTerlambat = inputBatasTerlambat.trim().ifEmpty { "08:00" },
                jamPulangKerja = inputJamPulang.trim().ifEmpty { "17:00" },
                pengumuman = inputPengumuman.trim(),
                lokasiNama = inputLokasiNama.trim().ifEmpty { "Kantor Pusat PT Fadiya Kencana" },
                lokasiLatitude = latVal,
                lokasiLongitude = lngVal,
                radiusMeter = radVal,
                wajibDalamRadius = inputWajibRadius
              )
              configAdmin = configBaru
              sessionManager.simpanKonfigurasiAdmin(configBaru)
              activeJaringanManager.updatePengaturanGpsKantor(
                lat = latVal,
                lng = lngVal,
                radius = radVal,
                bypass = !inputWajibRadius
              )
              isEditModeOpen = false

              coroutineScope.launch {
                val res = XamppApiClient.updateKonfigurasiAdmin(configBaru)
                if (res.isSuccess) {
                  pesanStatusAdmin = "Pengaturan jam kerja & lokasi kantor berhasil disimpan ke server!"
                  muatDataDariServer()
                } else {
                  pesanStatusAdmin = "Pengaturan disimpan secara lokal (Server: ${res.exceptionOrNull()?.message ?: "Offline"})."
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            Text("Simpan Perubahan")
          }
        },
        dismissButton = {
          TextButton(onClick = { isEditModeOpen = false }) {
            Text("Batal")
          }
        }
      )
    }

    // DIALOG TAMBAH KARYAWAN MASTER
    if (isTambahKaryawanOpen) {
      val opsiJk = listOf("Laki-laki", "Perempuan")
      val opsiDivisi = listOf(
        "Produksi Pabrik",
        "IT & Sistem Informasi",
        "Human Resources (HR)",
        "Keuangan & Akuntansi",
        "Operasional & Logistik",
        "Quality Control (QC)",
        "Maintenance & Teknik",
        "Gudang & Material"
      )

      AlertDialog(
        onDismissRequest = { isTambahKaryawanOpen = false },
        title = { Text("Tambah Karyawan Baru", fontWeight = FontWeight.Bold) },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = nipBaru,
              onValueChange = { nipBaru = it },
              label = { Text("Nomor Induk Pegawai (NIP)") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
              value = namaBaru,
              onValueChange = { namaBaru = it },
              label = { Text("Nama Lengkap Karyawan") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )

            ExposedDropdownMenuBox(
              expanded = expandedJkAdmin,
              onExpandedChange = { expandedJkAdmin = !expandedJkAdmin },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = jkBaru,
                onValueChange = {},
                readOnly = true,
                label = { Text("Jenis Kelamin") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedJkAdmin) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              )
              ExposedDropdownMenu(
                expanded = expandedJkAdmin,
                onDismissRequest = { expandedJkAdmin = false }
              ) {
                opsiJk.forEach { item ->
                  DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                      jkBaru = item
                      expandedJkAdmin = false
                    }
                  )
                }
              }
            }

            OutlinedTextField(
              value = ttlBaru,
              onValueChange = { ttlBaru = it },
              label = { Text("Tempat, Tanggal Lahir") },
              placeholder = { Text("Contoh: Jakarta, 15 Jan 1998") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
              value = alamatBaru,
              onValueChange = { alamatBaru = it },
              label = { Text("Alamat Lengkap / Domisili") },
              placeholder = { Text("Contoh: Jl. Pemuda No. 12, Surabaya") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )

            ExposedDropdownMenuBox(
              expanded = expandedDivisiAdmin,
              onExpandedChange = { expandedDivisiAdmin = !expandedDivisiAdmin },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = divisiBaru,
                onValueChange = {},
                readOnly = true,
                label = { Text("Departemen / Divisi") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDivisiAdmin) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              )
              ExposedDropdownMenu(
                expanded = expandedDivisiAdmin,
                onDismissRequest = { expandedDivisiAdmin = false }
              ) {
                opsiDivisi.forEach { item ->
                  DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                      divisiBaru = item
                      expandedDivisiAdmin = false
                    }
                  )
                }
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (nipBaru.isNotBlank() && namaBaru.isNotBlank()) {
                val karyawanBaru = DataKaryawan(
                  id = "K-${System.currentTimeMillis()}",
                  nip = nipBaru.trim(),
                  nama = namaBaru.trim(),
                  jenisKelamin = jkBaru,
                  tempatTanggalLahir = ttlBaru.trim().ifEmpty { "-" },
                  alamat = alamatBaru.trim().ifEmpty { "-" },
                  departemen = divisiBaru,
                  avatarInisial = namaBaru.trim().take(2).uppercase()
                )
                isTambahKaryawanOpen = false

                coroutineScope.launch {
                  val res = XamppApiClient.tambahKaryawanMaster(karyawanBaru)
                  if (res.isSuccess) {
                    pesanStatusAdmin = "Karyawan ${karyawanBaru.nama} berhasil ditambahkan ke database server!"
                    muatDataDariServer()
                  } else {
                    val updatedList = masterKaryawanList + karyawanBaru
                    masterKaryawanList = updatedList
                    sessionManager.simpanMasterKaryawan(updatedList)
                    pesanStatusAdmin = "Disimpan lokal: ${res.exceptionOrNull()?.message ?: "Server offline"}"
                  }
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            Text("Simpan Karyawan")
          }
        },
        dismissButton = {
          TextButton(onClick = { isTambahKaryawanOpen = false }) {
            Text("Batal")
          }
        }
      )
    }

    // DIALOG PENGATURAN URL SERVER & REALTIME GPS
    if (showXamppConfigDialog) {
      DialogPengaturanXampp(
        jaringanManager = activeJaringanManager,
        onDismiss = {
          showXamppConfigDialog = false
          serverUrlInput = XamppApiClient.baseUrl
          muatDataDariServer()
        }
      )
    }

    // DIALOG KONFIRMASI PENGAMAN (Anti-Salah Pencet Hapus / Reset HP)
    if (konfirmasiAksiJudul != null) {
      AlertDialog(
        onDismissRequest = {
          konfirmasiAksiJudul = null
          onKonfirmasiAksi = null
        },
        title = {
          Text(konfirmasiAksiJudul ?: "Konfirmasi", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
          Text(konfirmasiAksiPesan, fontSize = 13.sp, color = TextMuted)
        },
        confirmButton = {
          Button(
            onClick = {
              val aksi = onKonfirmasiAksi
              konfirmasiAksiJudul = null
              onKonfirmasiAksi = null
              aksi?.invoke()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (konfirmasiAksiJudul?.contains("Hapus", ignoreCase = true) == true) StatusRed else TealPrimary
            )
          ) {
            Text("Ya, Lanjutkan", fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(
            onClick = {
              konfirmasiAksiJudul = null
              onKonfirmasiAksi = null
            }
          ) {
            Text("Batal")
          }
        }
      )
    }

    // DIALOG PREVIEW FOTO SWAFOTO & SURAT DOKTER LANGSUNG DI APLIKASI
    if (previewFotoUrl != null) {
      var activeUrl by remember(previewFotoUrl) { mutableStateOf(previewFotoUrl ?: "") }
      var bitmapPreview by remember(activeUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
      var isLoadingImg by remember(activeUrl) { mutableStateOf(true) }

      LaunchedEffect(activeUrl) {
        isLoadingImg = true
        bitmapPreview = XamppApiClient.downloadImageBitmap(activeUrl)
        isLoadingImg = false
      }

      AlertDialog(
        onDismissRequest = {
          previewFotoUrl = null
          previewFotoUrlKedua = null
        },
        title = {
          Text(previewFotoJudul, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (previewFotoUrlKedua != null) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                FilterChip(
                  selected = activeUrl == previewFotoUrl,
                  onClick = { activeUrl = previewFotoUrl ?: "" },
                  label = { Text("Foto Masuk", fontSize = 11.sp) },
                  modifier = Modifier.weight(1f)
                )
                FilterChip(
                  selected = activeUrl == previewFotoUrlKedua,
                  onClick = { activeUrl = previewFotoUrlKedua ?: "" },
                  label = { Text("Foto Pulang", fontSize = 11.sp) },
                  modifier = Modifier.weight(1f)
                )
              }
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgLight),
              contentAlignment = Alignment.Center
            ) {
              when {
                isLoadingImg -> {
                  CircularProgressIndicator(color = TealPrimary)
                }
                bitmapPreview != null -> {
                  Image(
                    bitmap = bitmapPreview!!.asImageBitmap(),
                    contentDescription = previewFotoJudul,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                  )
                }
                else -> {
                  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Gagal memuat pratinjau gambar.", fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center)
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          if (activeUrl.startsWith("http")) {
            OutlinedButton(
              onClick = {
                try {
                  val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(activeUrl)
                  )
                  context.startActivity(intent)
                } catch (_: Exception) {}
              }
            ) {
              Text("Buka di Browser", fontSize = 12.sp)
            }
          }
        },
        dismissButton = {
          Button(
            onClick = {
              previewFotoUrl = null
              previewFotoUrlKedua = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            Text("Tutup")
          }
        }
      )
    }

    // DIALOG EDIT DATA KARYAWAN OLEH ADMIN
    if (karyawanDiedit != null) {
      val kAwal = karyawanDiedit!!
      var editNip by remember(kAwal) { mutableStateOf(kAwal.nip) }
      var editNama by remember(kAwal) { mutableStateOf(kAwal.nama) }
      var editJk by remember(kAwal) {
        mutableStateOf(
          if (kAwal.jenisKelamin.equals("P", ignoreCase = true) || kAwal.jenisKelamin.equals("Perempuan", ignoreCase = true)) "Perempuan" else "Laki-laki"
        )
      }
      var expJkEdit by remember { mutableStateOf(false) }
      var editTtl by remember(kAwal) { mutableStateOf(kAwal.tempatTanggalLahir) }
      var editAlamat by remember(kAwal) { mutableStateOf(kAwal.alamat) }
      var editDivisi by remember(kAwal) { mutableStateOf(kAwal.departemen) }
      var expDivEdit by remember { mutableStateOf(false) }

      val opsiJk = listOf("Laki-laki", "Perempuan")
      val opsiDivisi = listOf(
        "Produksi Pabrik",
        "IT & Sistem Informasi",
        "Human Resources (HR)",
        "Keuangan & Akuntansi",
        "Operasional & Logistik",
        "Quality Control (QC)",
        "Maintenance & Teknik",
        "Gudang & Material"
      )

      AlertDialog(
        onDismissRequest = { karyawanDiedit = null },
        title = { Text("Edit Data Karyawan", fontWeight = FontWeight.Bold) },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = editNip,
              onValueChange = { editNip = it },
              label = { Text("Nomor Induk Pegawai (NIP)") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
              value = editNama,
              onValueChange = { editNama = it },
              label = { Text("Nama Lengkap Karyawan") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenuBox(
              expanded = expJkEdit,
              onExpandedChange = { expJkEdit = !expJkEdit },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = editJk,
                onValueChange = {},
                readOnly = true,
                label = { Text("Jenis Kelamin") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expJkEdit) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              )
              ExposedDropdownMenu(expanded = expJkEdit, onDismissRequest = { expJkEdit = false }) {
                opsiJk.forEach { item ->
                  DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                      editJk = item
                      expJkEdit = false
                    }
                  )
                }
              }
            }
            OutlinedTextField(
              value = editTtl,
              onValueChange = { editTtl = it },
              label = { Text("Tempat, Tanggal Lahir") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
              value = editAlamat,
              onValueChange = { editAlamat = it },
              label = { Text("Alamat Lengkap") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenuBox(
              expanded = expDivEdit,
              onExpandedChange = { expDivEdit = !expDivEdit },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = editDivisi,
                onValueChange = {},
                readOnly = true,
                label = { Text("Departemen / Divisi") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expDivEdit) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              )
              ExposedDropdownMenu(expanded = expDivEdit, onDismissRequest = { expDivEdit = false }) {
                opsiDivisi.forEach { item ->
                  DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                      editDivisi = item
                      expDivEdit = false
                    }
                  )
                }
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (editNip.isNotBlank() && editNama.isNotBlank()) {
                val updated = kAwal.copy(
                  nip = editNip.trim(),
                  nama = editNama.trim(),
                  jenisKelamin = editJk,
                  tempatTanggalLahir = editTtl.trim().ifEmpty { "-" },
                  alamat = editAlamat.trim().ifEmpty { "-" },
                  departemen = editDivisi,
                  avatarInisial = editNama.trim().take(2).uppercase()
                )
                val targetNipLama = nipLamaDiedit
                karyawanDiedit = null
                coroutineScope.launch {
                  val res = XamppApiClient.editKaryawanMaster(targetNipLama, updated)
                  if (res.isSuccess) {
                    pesanStatusAdmin = "Data ${updated.nama} berhasil diperbarui!"
                    muatDataDariServer()
                  } else {
                    pesanStatusAdmin = "Gagal mengedit karyawan: ${res.exceptionOrNull()?.message}"
                  }
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            Text("Simpan Perubahan")
          }
        },
        dismissButton = {
          TextButton(onClick = { karyawanDiedit = null }) {
            Text("Batal")
          }
        }
      )
    }

    // DIALOG KOREKSI KEHADIRAN MANUAL OLEH ADMIN
    if (absensiDikoreksi != null) {
      val rowAwal = absensiDikoreksi!!
      var editJamMasuk by remember(rowAwal) { mutableStateOf(rowAwal.jamMasuk.removeSuffix(" WIB").trim()) }
      var editJamPulang by remember(rowAwal) { mutableStateOf(rowAwal.jamPulang.removeSuffix(" WIB").trim()) }
      var editStatus by remember(rowAwal) {
        mutableStateOf(
          when (rowAwal.status) {
            StatusAbsensi.TERLAMBAT -> "TERLAMBAT"
            StatusAbsensi.IZIN -> "IZIN"
            else -> "TEPAT_WAKTU"
          }
        )
      }
      var expStatusEdit by remember { mutableStateOf(false) }
      var editCatatan by remember(rowAwal) { mutableStateOf(rowAwal.catatan) }
      val opsiStatus = listOf("TEPAT_WAKTU", "TERLAMBAT", "IZIN", "SAKIT", "CUTI")

      AlertDialog(
        onDismissRequest = { absensiDikoreksi = null },
        title = { Text("Koreksi Presensi — ${rowAwal.nama}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = editJamMasuk,
              onValueChange = { editJamMasuk = it },
              label = { Text("Jam Masuk (Contoh: 08:00 atau -)") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
              value = editJamPulang,
              onValueChange = { editJamPulang = it },
              label = { Text("Jam Pulang (Contoh: 17:00 atau -)") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenuBox(
              expanded = expStatusEdit,
              onExpandedChange = { expStatusEdit = !expStatusEdit },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = editStatus,
                onValueChange = {},
                readOnly = true,
                label = { Text("Status Kehadiran") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expStatusEdit) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              )
              ExposedDropdownMenu(expanded = expStatusEdit, onDismissRequest = { expStatusEdit = false }) {
                opsiStatus.forEach { st ->
                  DropdownMenuItem(
                    text = { Text(st) },
                    onClick = {
                      editStatus = st
                      expStatusEdit = false
                    }
                  )
                }
              }
            }
            OutlinedTextField(
              value = editCatatan,
              onValueChange = { editCatatan = it },
              label = { Text("Catatan Lokasi / Keterangan") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            if (rowAwal.recordId > 0) {
              Spacer(modifier = Modifier.height(4.dp))
              OutlinedButton(
                onClick = {
                  val recId = rowAwal.recordId
                  val namaTarget = rowAwal.nama
                  val nipTarget = rowAwal.nip
                  absensiDikoreksi = null
                  konfirmasiAksiJudul = "Hapus Log Presensi?"
                  konfirmasiAksiPesan = "Apakah Anda yakin ingin menghapus data presensi hari ini milik $namaTarget ($nipTarget)?"
                  onKonfirmasiAksi = {
                    coroutineScope.launch {
                      val res = XamppApiClient.hapusPresensiAdmin(recId)
                      if (res.isSuccess) {
                        pesanStatusAdmin = "Log presensi $namaTarget berhasil dihapus."
                        muatDataDariServer()
                      } else {
                        pesanStatusAdmin = "Gagal menghapus presensi: ${res.exceptionOrNull()?.message}"
                      }
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
              ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hapus Log Presensi Ini", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val recId = rowAwal.recordId
              val jm = editJamMasuk
              val jp = editJamPulang
              val st = editStatus
              val ct = editCatatan
              absensiDikoreksi = null
              coroutineScope.launch {
                val res = XamppApiClient.koreksiAbsensiManual(recId, jm, jp, st, ct)
                if (res.isSuccess) {
                  pesanStatusAdmin = "Presensi ${rowAwal.nama} berhasil dikoreksi."
                  muatDataDariServer()
                } else {
                  pesanStatusAdmin = "Gagal mengoreksi presensi: ${res.exceptionOrNull()?.message}"
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            Text("Simpan Koreksi")
          }
        },
        dismissButton = {
          TextButton(onClick = { absensiDikoreksi = null }) {
            Text("Batal")
          }
        }
      )
    }

    // DIALOG UBAH KATA SANDI ADMIN
    if (showPasswordAdminDialog) {
      var passLama by remember { mutableStateOf("") }
      var passBaru by remember { mutableStateOf("") }
      var errPass by remember { mutableStateOf<String?>(null) }

      AlertDialog(
        onDismissRequest = { showPasswordAdminDialog = false },
        title = { Text("Ubah Kata Sandi Admin", fontWeight = FontWeight.Bold) },
        text = {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (errPass != null) {
              Text(errPass!!, fontSize = 12.sp, color = StatusRed, fontWeight = FontWeight.Medium)
            }
            OutlinedTextField(
              value = passLama,
              onValueChange = {
                passLama = it
                errPass = null
              },
              label = { Text("Kata Sandi Lama") },
              visualTransformation = PasswordVisualTransformation(),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
              value = passBaru,
              onValueChange = {
                passBaru = it
                errPass = null
              },
              label = { Text("Kata Sandi Baru (Min. 4 Karakter)") },
              visualTransformation = PasswordVisualTransformation(),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp)
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (passLama.isBlank() || passBaru.length < 4) {
                errPass = "Masukkan sandi lama dan sandi baru minimal 4 karakter."
                return@Button
              }
              val oldP = passLama
              val newP = passBaru
              showPasswordAdminDialog = false
              coroutineScope.launch {
                val res = XamppApiClient.ubahPasswordAdmin(oldP, newP)
                if (res.isSuccess) {
                  pesanStatusAdmin = "Kata sandi Administrator berhasil diperbarui!"
                } else {
                  pesanStatusAdmin = "Gagal mengubah sandi: ${res.exceptionOrNull()?.message}"
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            Text("Simpan Sandi")
          }
        },
        dismissButton = {
          TextButton(onClick = { showPasswordAdminDialog = false }) {
            Text("Batal")
          }
        }
      )
    }
  }
}

@Composable
fun AdminMetricCard(
  judul: String,
  nilai: String,
  icon: ImageVector,
  warna: Color,
  warnaBg: Color,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = CardWhite),
    border = BorderStroke(1.dp, BorderMuted),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = judul, fontSize = 11.5.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = nilai, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
      }
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(warnaBg)
          .border(BorderStroke(1.dp, warna.copy(alpha = 0.25f)), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = warna,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
fun BarisTabelAbsensiCard(
  row: BarisAbsensiKaryawan,
  onPreviewFoto: (judul: String, urlMasuk: String, urlPulang: String) -> Unit = { _, _, _ -> },
  onEditAbsensi: (BarisAbsensiKaryawan) -> Unit = {},
  onHapusAbsensi: (BarisAbsensiKaryawan) -> Unit = {}
) {
  val context = LocalContext.current
  val badgeBg = when (row.status) {
    StatusAbsensi.TEPAT_WAKTU -> StatusGreenBg
    StatusAbsensi.TERLAMBAT -> StatusAmberBg
    StatusAbsensi.IZIN -> TealContainer
    StatusAbsensi.BELUM_ABSEN -> StatusRedBg
  }
  val badgeFg = when (row.status) {
    StatusAbsensi.TEPAT_WAKTU -> StatusGreen
    StatusAbsensi.TERLAMBAT -> StatusAmber
    StatusAbsensi.IZIN -> TealDark
    StatusAbsensi.BELUM_ABSEN -> StatusRed
  }
  val statusIcon = when (row.status) {
    StatusAbsensi.TEPAT_WAKTU -> Icons.Default.Check
    StatusAbsensi.TERLAMBAT -> Icons.Default.Warning
    StatusAbsensi.IZIN -> Icons.Default.EventNote
    StatusAbsensi.BELUM_ABSEN -> Icons.Default.Schedule
  }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = CardWhite),
    border = BorderStroke(1.dp, BorderMuted),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(badgeBg),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = statusIcon,
              contentDescription = null,
              tint = badgeFg,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(text = row.nama, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
            Text(
              text = "${row.departemen} • Masuk: ${row.jamMasuk} | Pulang: ${row.jamPulang}",
              fontSize = 11.sp,
              color = TextMuted
            )
            if (row.catatan.isNotBlank() && row.catatan != "-") {
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "📝 ${row.catatan}",
                fontSize = 11.sp,
                color = TealDark,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = badgeBg
        ) {
          Text(
            text = row.status.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = badgeFg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      val adaFoto = row.fotoMasukUrl.isNotBlank() || row.fotoPulangUrl.isNotBlank()
      val adaMaps = (row.mapsUrl.isNotBlank() && row.mapsUrl != "-") || (row.mapsUrlPulang.isNotBlank() && row.mapsUrlPulang != "-")
      val bisaKoreksi = row.recordId > 0

      if (adaFoto || adaMaps || bisaKoreksi) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (adaFoto) {
            OutlinedButton(
              onClick = {
                onPreviewFoto("Swafoto Presensi — ${row.nama}", row.fotoMasukUrl, row.fotoPulangUrl)
              },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("📷 Foto", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }

          if (row.mapsUrl.isNotBlank() && row.mapsUrl != "-") {
            OutlinedButton(
              onClick = {
                try {
                  val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(row.mapsUrl)
                  )
                  context.startActivity(intent)
                } catch (_: Exception) {}
              },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("📍 Maps Masuk", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }

          if (row.mapsUrlPulang.isNotBlank() && row.mapsUrlPulang != "-") {
            OutlinedButton(
              onClick = {
                try {
                  val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(row.mapsUrlPulang)
                  )
                  context.startActivity(intent)
                } catch (_: Exception) {}
              },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("📍 Maps Pulang", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }

          if (bisaKoreksi) {
            OutlinedButton(
              onClick = { onEditAbsensi(row) },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("✏️ Koreksi", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealDark)
            }
            OutlinedButton(
              onClick = { onHapusAbsensi(row) },
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("🗑️ Hapus", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusRed)
            }
          }
        }
      }
    }
  }
}

/**
 * Konversi teks dengan format Markdown sederhana (*miring*, **tebal**, `kode`)
 * menjadi AnnotatedString Compose agar balasan Asisten AI nyaman dibaca di layar HP.
 */
private fun teksMarkdownAi(teks: String): AnnotatedString {
  return buildAnnotatedString {
    var cursor = 0
    val regex = Regex("""\*\*(.+?)\*\*|\*([^*\n]+?)\*|`([^`\n]+?)`""")
    for (match in regex.findAll(teks)) {
      if (match.range.first > cursor) {
        append(teks.substring(cursor, match.range.first))
      }
      when {
        match.value.startsWith("**") -> {
          withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
          }
        }
        match.value.startsWith("*") -> {
          withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(match.groupValues[2])
          }
        }
        match.value.startsWith("`") -> {
          withStyle(SpanStyle(background = Color(0x1F000000))) {
            append(match.groupValues[3])
          }
        }
      }
      cursor = match.range.last + 1
    }
    if (cursor < teks.length) {
      append(teks.substring(cursor))
    }
  }
}
