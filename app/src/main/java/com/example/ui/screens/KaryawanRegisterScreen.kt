package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DataKaryawan
import com.example.model.JaringanKantorManager
import com.example.model.SessionManager
import com.example.network.XamppApiClient
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Form Registrasi Karyawan Baru
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaryawanRegisterScreen(
  onRegisterSuccess: (DataKaryawan) -> Unit,
  onBack: () -> Unit,
  jaringanManager: JaringanKantorManager? = null
) {
  val context = LocalContext.current
  val sessionManager = remember { SessionManager(context) }
  val coroutineScope = rememberCoroutineScope()
  val karyawanSudahTerdaftar = remember { sessionManager.getKaryawanTerdaftar() }
  var inputTerakhir by remember { mutableStateOf(sessionManager.getInputTerakhir()) }
  var isCheckingXampp by remember { mutableStateOf(false) }

  // Daftar Resmi Jabatan / Departemen Pabrik
  val opsiDepartemen = remember {
    listOf(
      "Produksi Pabrik",
      "IT & Sistem Informasi",
      "Human Resources (HR)",
      "Keuangan & Akuntansi",
      "Operasional & Logistik",
      "Quality Control (QC)",
      "Maintenance & Teknik",
      "Gudang & Material"
    )
  }

  // Field Input
  var nipInput by remember { mutableStateOf(inputTerakhir?.nip ?: "") }
  var namaInput by remember { mutableStateOf(inputTerakhir?.nama ?: "") }
  var jenisKelaminInput by remember { mutableStateOf("Laki-laki") }
  var tempatLahirInput by remember { mutableStateOf("") }
  var tanggalLahirInput by remember { mutableStateOf("") }
  var alamatInput by remember { mutableStateOf("") }
  var departemenInput by remember {
    mutableStateOf(
      if (opsiDepartemen.contains(inputTerakhir?.departemen)) inputTerakhir?.departemen!! else opsiDepartemen[0]
    )
  }

  // State Dropdown
  var expandedJk by remember { mutableStateOf(false) }
  var expandedDept by remember { mutableStateOf(false) }

  // State Dropdown Tempat Lahir
  var expandedTempatLahir by remember { mutableStateOf(false) }
  val daftarKotaIndonesia = remember {
    listOf(
      "Ambon", "Badung", "Balikpapan", "Banda Aceh", "Bandung", "Bandung Barat", "Banggai",
      "Bangkalan", "Bangli", "Banjar", "Banjarbaru", "Banjarmasin", "Banyumas", "Banyuwangi",
      "Batam", "Batang", "Batu", "Baubau", "Bekasi", "Bengkulu", "Berau", "Biak", "Bima",
      "Binjai", "Bitung", "Blitar", "Blora", "Bogor", "Bojonegoro", "Bondowoso", "Bontang",
      "Boyolali", "Brebes", "Bukittinggi", "Buleleng", "Ciamis", "Cianjur", "Cilacap",
      "Cilegon", "Cimahi", "Cirebon", "Dairi", "Deli Serdang", "Demak", "Denpasar", "Depok",
      "Dompu", "Dumai", "Ende", "Enrekang", "Garut", "Gianyar", "Gorontalo", "Gresik",
      "Grobogan", "Gunungkidul", "Gunungsitoli", "Indramayu", "Jakarta Barat", "Jakarta Pusat",
      "Jakarta Selatan", "Jakarta Timur", "Jakarta Utara", "Jambi", "Jayapura", "Jember",
      "Jepara", "Jombang", "Karanganyar", "Karangasem", "Karawang", "Kebumen", "Kediri",
      "Kendal", "Kendari", "Klaten", "Klungkung", "Kotamobagu", "Kudus", "Kulon Progo",
      "Kuningan", "Kupang", "Lamongan", "Lampung", "Langsa", "Lombok Barat", "Lombok Tengah",
      "Lombok Timur", "Lombok Utara", "Lumajang", "Madiun", "Magelang", "Magetan",
      "Majalengka", "Makassar", "Malang", "Manado", "Manokwari", "Maros", "Mataram",
      "Medan", "Merauke", "Metro", "Minahasa", "Mojokerto", "Nabire", "Nganjuk", "Ngawi",
      "Pacitan", "Padang", "Padang Panjang", "Padangsidimpuan", "Pagar Alam", "Palangka Raya",
      "Palembang", "Palopo", "Palu", "Pamekasan", "Pandeglang", "Pangandaran", "Pangkalpinang",
      "Parepare", "Pariaman", "Pasuruan", "Pati", "Payakumbuh", "Pekalongan", "Pekanbaru",
      "Pemalang", "Pematangsiantar", "Penajam Paser Utara", "Ponorogo", "Pontianak", "Poso",
      "Prabumulih", "Probolinggo", "Purbalingga", "Purwakarta", "Purworejo", "Rembang",
      "Sabang", "Salatiga", "Samarinda", "Sampang", "Sawahlunto", "Sekadau", "Semarang",
      "Serang", "Siak", "Sibolga", "Sidoarjo", "Sikka", "Simalungun", "Singkawang", "Sintang",
      "Situbondo", "Sleman", "Solok", "Soppeng", "Sorong", "Sragen", "Subang", "Sukabumi",
      "Sukoharjo", "Sumba Barat", "Sumba Timur", "Sumbawa", "Sumedang", "Sumenep",
      "Sungai Penuh", "Surakarta", "Surabaya", "Tabanan", "Tangerang", "Tangerang Selatan",
      "Tanggamus", "Tanjungbalai", "Tanjungpinang", "Tarakan", "Tasikmalaya", "Tebing Tinggi",
      "Tegal", "Temanggung", "Ternate", "Tidore Kepulauan", "Timika", "Toba", "Tojo Una-Una",
      "Tomohon", "Toraja Utara", "Trenggalek", "Tual", "Tuban", "Tulungagung", "Wajo",
      "Wonosobo", "Yogyakarta"
    )
  }

  val kotaFiltered = remember(tempatLahirInput) {
    if (tempatLahirInput.isBlank()) {
      daftarKotaIndonesia
    } else {
      daftarKotaIndonesia.filter { it.contains(tempatLahirInput, ignoreCase = true) }
    }
  }

  // State DatePicker Pop-up
  var showDatePickerDialog by remember { mutableStateOf(false) }
  val datePickerState = rememberDatePickerState(
    initialDisplayMode = DisplayMode.Picker
  )

  if (showDatePickerDialog) {
    DatePickerDialog(
      onDismissRequest = { showDatePickerDialog = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
              }
              tanggalLahirInput = sdf.format(Date(millis))
            }
            showDatePickerDialog = false
          }
        ) {
          Text("OK", color = TealPrimary, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePickerDialog = false }) {
          Text("Batal", color = TextMuted)
        }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }

  // Status feedback
  var errorMessage by remember { mutableStateOf<String?>(null) }
  val scrollState = rememberScrollState()

  Scaffold(
    containerColor = BgLight,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = if (karyawanSudahTerdaftar != null) "Perangkat Terikat" else "Registrasi Karyawan",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("register_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Kembali ke Pilih Peran",
              tint = TextDark
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgLight)
      )
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .imePadding(),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 500.dp)
          .verticalScroll(scrollState)
          .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // JIKA PERANGKAT SUDAH TERDAFTAR: TERKUNCI
        if (karyawanSudahTerdaftar != null) {
          Box(
            modifier = Modifier
              .size(80.dp)
              .clip(CircleShape)
              .background(TealContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Terkunci",
              tint = TealDark,
              modifier = Modifier.size(42.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = "Perangkat Sudah Terdaftar",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "1 perangkat smartphone hanya diperuntukkan bagi 1 karyawan resmi.",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
          )

          Spacer(modifier = Modifier.height(20.dp))

          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Text(
                text = "Akun yang Terdaftar di HP Ini:",
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = karyawanSudahTerdaftar.nama,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "NIP: ${karyawanSudahTerdaftar.nip} • ${karyawanSudahTerdaftar.departemen}",
                fontSize = 13.sp,
                color = TealDark,
                fontWeight = FontWeight.Medium
              )
            }
          }

          Spacer(modifier = Modifier.height(24.dp))

          Button(
            onClick = { onRegisterSuccess(karyawanSudahTerdaftar) },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("open_my_dashboard_button")
          ) {
            Text(
              text = "Buka Dashboard Saya",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
          ) {
            Text(text = "Kembali ke Beranda", color = TextDark)
          }

          return@Column
        }

        // Header Logo Fadiya Kencana
        Surface(
          modifier = Modifier.size(76.dp),
          shape = CircleShape,
          color = Color.White,
          shadowElevation = 3.dp,
          border = BorderStroke(2.dp, TealContainer)
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_fadiya_logo),
            contentDescription = "Logo Fadiya Kencana",
            modifier = Modifier
              .fillMaxSize()
              .clip(CircleShape)
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "Pendaftaran Karyawan",
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Lengkapi data diri Anda untuk membuat akun presensi",
          fontSize = 13.sp,
          color = TextMuted
        )

        Spacer(modifier = Modifier.height(18.dp))

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = CardWhite),
          border = BorderStroke(1.dp, BorderMuted),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            if (errorMessage != null) {
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = StatusRedBg),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Peringatan",
                    tint = StatusRed,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = errorMessage ?: "",
                    fontSize = 12.sp,
                    color = StatusRed,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
              Spacer(modifier = Modifier.height(14.dp))
            }

            // 1. INPUT NIK / NIP
            OutlinedTextField(
              value = nipInput,
              onValueChange = {
                nipInput = it
                errorMessage = null
              },
              label = { Text("NIP / NIK") },
              placeholder = { Text("Masukkan nomor induk pegawai") },
              leadingIcon = {
                Icon(Icons.Default.Badge, contentDescription = null, tint = TealPrimary)
              },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors = absensiTextFieldColors(),
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. INPUT NAMA LENGKAP
            OutlinedTextField(
              value = namaInput,
              onValueChange = {
                namaInput = it
                errorMessage = null
              },
              label = { Text("Nama Lengkap") },
              placeholder = { Text("Sesuai identitas resmi") },
              leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary)
              },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors = absensiTextFieldColors(),
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. INPUT JENIS KELAMIN
            ExposedDropdownMenuBox(
              expanded = expandedJk,
              onExpandedChange = { expandedJk = !expandedJk },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = jenisKelaminInput,
                onValueChange = {},
                readOnly = true,
                label = { Text("Jenis Kelamin") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedJk) },
                shape = RoundedCornerShape(14.dp),
                colors = absensiTextFieldColors(),
                modifier = Modifier
                  .menuAnchor()
                  .fillMaxWidth()
              )
              ExposedDropdownMenu(
                expanded = expandedJk,
                onDismissRequest = { expandedJk = false }
              ) {
                DropdownMenuItem(
                  text = { Text("Laki-laki") },
                  onClick = {
                    jenisKelaminInput = "Laki-laki"
                    expandedJk = false
                  }
                )
                DropdownMenuItem(
                  text = { Text("Perempuan") },
                  onClick = {
                    jenisKelaminInput = "Perempuan"
                    expandedJk = false
                  }
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. INPUT TEMPAT LAHIR & TANGGAL LAHIR
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              ExposedDropdownMenuBox(
                expanded = expandedTempatLahir && kotaFiltered.isNotEmpty(),
                onExpandedChange = { expandedTempatLahir = it },
                modifier = Modifier.weight(1f)
              ) {
                OutlinedTextField(
                  value = tempatLahirInput,
                  onValueChange = {
                    tempatLahirInput = it
                    expandedTempatLahir = true
                  },
                  label = { Text("Tempat Lahir") },
                  placeholder = { Text("Kota") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  colors = absensiTextFieldColors(),
                  modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                )

                if (kotaFiltered.isNotEmpty()) {
                  ExposedDropdownMenu(
                    expanded = expandedTempatLahir,
                    onDismissRequest = { expandedTempatLahir = false },
                    modifier = Modifier.heightIn(max = 220.dp)
                  ) {
                    kotaFiltered.take(10).forEach { kota ->
                      DropdownMenuItem(
                        text = { Text(kota) },
                        onClick = {
                          tempatLahirInput = kota
                          expandedTempatLahir = false
                        }
                      )
                    }
                  }
                }
              }

              OutlinedTextField(
                value = tanggalLahirInput,
                onValueChange = { tanggalLahirInput = it },
                label = { Text("Tanggal Lahir") },
                placeholder = { Text("DD/MM/YYYY") },
                trailingIcon = {
                  IconButton(onClick = { showDatePickerDialog = true }) {
                    Icon(
                      imageVector = Icons.Default.DateRange,
                      contentDescription = "Pilih Tanggal",
                      tint = TealPrimary
                    )
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = absensiTextFieldColors(),
                modifier = Modifier.weight(1f)
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. INPUT JABATAN / DEPARTEMEN
            ExposedDropdownMenuBox(
              expanded = expandedDept,
              onExpandedChange = { expandedDept = !expandedDept },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = departemenInput,
                onValueChange = {},
                readOnly = true,
                label = { Text("Departemen / Divisi") },
                leadingIcon = {
                  Icon(Icons.Default.Business, contentDescription = null, tint = TealPrimary)
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDept) },
                shape = RoundedCornerShape(14.dp),
                colors = absensiTextFieldColors(),
                modifier = Modifier
                  .menuAnchor()
                  .fillMaxWidth()
              )
              ExposedDropdownMenu(
                expanded = expandedDept,
                onDismissRequest = { expandedDept = false }
              ) {
                opsiDepartemen.forEach { dept ->
                  DropdownMenuItem(
                    text = { Text(dept) },
                    onClick = {
                      departemenInput = dept
                      expandedDept = false
                      errorMessage = null
                    }
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. INPUT ALAMAT LENGKAP
            OutlinedTextField(
              value = alamatInput,
              onValueChange = {
                alamatInput = it
                errorMessage = null
              },
              label = { Text("Alamat Domisili") },
              placeholder = { Text("Masukkan alamat tempat tinggal") },
              leadingIcon = {
                Icon(Icons.Default.Home, contentDescription = null, tint = TealPrimary)
              },
              shape = RoundedCornerShape(14.dp),
              colors = absensiTextFieldColors(),
              modifier = Modifier.fillMaxWidth(),
              maxLines = 2
            )

            Spacer(modifier = Modifier.height(20.dp))

            // TOMBOL SIMPAN & MASUK DASHBOARD (PENDAFTARAN BARU)
            Button(
              onClick = {
                val cleanNip = nipInput.trim()
                val cleanNama = namaInput.trim()
                val cleanDept = departemenInput.trim()
                val cleanTempat = tempatLahirInput.trim()
                val cleanTanggal = tanggalLahirInput.trim()
                val cleanAlamat = alamatInput.trim()
                val jkCode = if (jenisKelaminInput == "Perempuan") "P" else "L"

                if (cleanNip.isEmpty() || cleanNama.isEmpty() || cleanTempat.isEmpty() || cleanTanggal.isEmpty() || cleanAlamat.isEmpty() || cleanDept.isEmpty()) {
                  errorMessage = "Mohon lengkapi seluruh kolom data diri di atas."
                  return@Button
                }

                sessionManager.simpanInputTerakhir(cleanNip, cleanNama, cleanDept)

                coroutineScope.launch {
                  isCheckingXampp = true
                  val deviceId = sessionManager.getOrCreateDeviceTestId()
                  val xamppResult = XamppApiClient.registerKaryawan(
                    nip = cleanNip,
                    nama = cleanNama,
                    departemen = cleanDept,
                    jenisKelamin = jkCode,
                    tempatLahir = cleanTempat,
                    tanggalLahir = cleanTanggal,
                    alamat = cleanAlamat,
                    deviceId = deviceId
                  )
                  isCheckingXampp = false

                  if (xamppResult.isSuccess) {
                    val tokenStr = XamppApiClient.jwtToken
                    if (!tokenStr.isNullOrBlank()) {
                      sessionManager.simpanJwtToken(tokenStr)
                    }
                    val karyawanBaru = xamppResult.getOrNull()!!
                    sessionManager.simpanKaryawanTerdaftar(karyawanBaru)
                    errorMessage = null
                    onRegisterSuccess(karyawanBaru)
                  } else {
                    val serverErr = xamppResult.exceptionOrNull()?.message ?: "Gagal mendaftarkan akun."
                    errorMessage = serverErr
                  }
                }
              },
              enabled = !isCheckingXampp,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("register_submit_button")
            ) {
              if (isCheckingXampp) {
                CircularProgressIndicator(
                  color = Color.White,
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Memproses Pendaftaran...",
                  fontSize = 15.sp,
                  color = Color.White
                )
              } else {
                Text(
                  text = "Daftar Sekarang",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              }
            }
          }
        }
      }
    }
  }
}
