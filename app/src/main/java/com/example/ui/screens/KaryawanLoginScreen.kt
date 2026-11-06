package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

// Layar Login Karyawan - Fresh Organic Green Curved Header & Pill UI (Sesuai Foto Foodie)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaryawanLoginScreen(
  onLoginSuccess: (DataKaryawan) -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val sessionManager = remember { SessionManager(context) }
  val jaringanManager = remember { JaringanKantorManager(context) }
  val coroutineScope = rememberCoroutineScope()

  var nipInput by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showServerDialog by remember { mutableStateOf(false) }
  val scrollState = rememberScrollState()

  Scaffold(
    containerColor = BgLight
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(scrollState)
        .imePadding()
    ) {
      // HEADER MELENGKUNG BERGRADASI HIJAU SEGAR DENGAN GARIS KONTUR CYBER
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
          .background(FreshGreenBannerBrush)
          .border(
            BorderStroke(1.2.dp, Color(0xFF34D399).copy(alpha = 0.40f)),
            RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)
          )
      ) {
        // LAPISAN 1: KANVAS DEKORASI GELOMBANG TOPOGRAFI DIGITAL & RESONANCE RINGS
        Canvas(modifier = Modifier.matchParentSize()) {
          val w = size.width
          val h = size.height

          // Shading gelombang
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
            moveTo(0f, h * 0.32f)
            cubicTo(w * 0.30f, h * 0.15f, w * 0.70f, h * 0.42f, w, h * 0.12f)
          }
          drawPath(p1, Color(0xFF6EE7B7).copy(alpha = 0.45f), style = Stroke(width = 2.dp.toPx()))

          // Garis Kontur 2 (Tengah)
          val p2 = Path().apply {
            moveTo(0f, h * 0.45f)
            cubicTo(w * 0.35f, h * 0.28f, w * 0.65f, h * 0.55f, w, h * 0.25f)
          }
          drawPath(p2, Color(0xFFA7F3D0).copy(alpha = 0.55f), style = Stroke(width = 2.dp.toPx()))

          // Garis Kontur 3 (Bawah)
          val p3 = Path().apply {
            moveTo(0f, h * 0.62f)
            cubicTo(w * 0.38f, h * 0.42f, w * 0.68f, h * 0.70f, w, h * 0.42f)
          }
          drawPath(p3, Color(0xFF34D399).copy(alpha = 0.40f), style = Stroke(width = 1.8.dp.toPx()))

          // Cincin Resonansi Topografi di Area Kanan
          val ringCenter = Offset(w * 0.88f, h * 0.35f)
          drawCircle(
            color = Color(0xFF6EE7B7).copy(alpha = 0.38f),
            radius = 55.dp.toPx(),
            center = ringCenter,
            style = Stroke(width = 1.8.dp.toPx())
          )
          drawCircle(
            color = Color(0xFF34D399).copy(alpha = 0.48f),
            radius = 35.dp.toPx(),
            center = ringCenter,
            style = Stroke(width = 1.8.dp.toPx())
          )
          drawCircle(
            color = Color(0xFFA7F3D0).copy(alpha = 0.60f),
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
            Offset(w * 0.80f, h * 0.14f),
            Offset(w * 0.92f, h * 0.22f),
            Offset(w * 0.64f, h * 0.25f),
            Offset(w * 0.72f, h * 0.58f)
          )
          for (sparkle in sparkles) {
            drawCircle(Color(0xFF34D399).copy(alpha = 0.50f), 5.dp.toPx(), sparkle)
            drawCircle(Color.White.copy(alpha = 0.95f), 2.dp.toPx(), sparkle)
          }
        }

        // LAPISAN 2: KONTEN HEADER
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 32.dp, start = 20.dp, end = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Baris Navigasi Atas
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .testTag("login_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = Color.White
              )
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = Color.White.copy(alpha = 0.18f)
            ) {
              Text(
                text = "PRESENSI ONLINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
              onClick = { showServerDialog = true },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .testTag("karyawan_login_server_button")
            ) {
              Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = "Pengaturan Server",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          // Logo Fadiya Kencana dalam Lingkaran Putih Bersih
          Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.8f))
          ) {
            Image(
              painter = painterResource(id = R.drawable.ic_fadiya_logo),
              contentDescription = "Logo Fadiya Kencana",
              modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Text(
            text = "Fadiya Kencana",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Presensi Digital GPS & Swafoto",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.85f)
          )
        }
      }

      // FORM LOGIN KARYAWAN DI ATAS LATAR SEJUK (Sesuai Foto 2)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 460.dp)
          .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Welcome Back",
          fontSize = 26.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Masukkan Nomor Induk Pegawai (NIP) Anda",
          fontSize = 13.sp,
          color = TextMuted,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (errorMessage != null) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StatusRedBg),
            border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
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
              if (errorMessage?.contains("Failed to connect") == true || errorMessage?.contains("Gagal") == true) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = "💡 Jika Anda menggunakan Paket Data (5G/4G), IP lokal Wi-Fi (192.168.x.x) tidak dapat diakses langsung. Silakan aktifkan Cloudflare Tunnel di PC dan gunakan URL publik (https://...trycloudflare.com), atau sambungkan HP ke Wi-Fi kantor.",
                  fontSize = 11.sp,
                  color = TextDark,
                  lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                  onClick = { showServerDialog = true },
                  shape = RoundedCornerShape(999.dp),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Icon(Icons.Default.Dns, contentDescription = null, tint = StatusRed, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Atur Alamat Server (Wi-Fi / Cloudflare)", fontSize = 11.5.sp, color = StatusRed, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(16.dp))
        }

        // Kolom Input NIP Kapsul
        OutlinedTextField(
          value = nipInput,
          onValueChange = {
            nipInput = it
            errorMessage = null
          },
          label = { Text("Nomor Induk Pegawai (NIP)") },
          placeholder = { Text("Contoh: 2024001") },
          leadingIcon = {
            Icon(Icons.Default.Badge, contentDescription = null, tint = TealPrimary)
          },
          singleLine = true,
          shape = RoundedCornerShape(999.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardWhite,
            unfocusedContainerColor = PillFieldBg,
            focusedBorderColor = TealLight,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextDark,
            unfocusedTextColor = TextDark
          ),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(26.dp))

        // TOMBOL MASUK KAPSUL GRADASI HIJAU SEGAR (Sesuai Tombol Log In di Foto 2)
        Button(
          onClick = {
            val cleanNip = nipInput.trim()
            if (cleanNip.isEmpty()) {
              errorMessage = "Masukkan NIP terlebih dahulu."
              return@Button
            }

            coroutineScope.launch {
              isLoading = true
              val deviceId = sessionManager.getOrCreateDeviceTestId()
              val res = XamppApiClient.cekAtauLoginKaryawan(cleanNip, deviceId)
              isLoading = false

              if (res.isSuccess) {
                val tokenStr = XamppApiClient.jwtToken
                if (!tokenStr.isNullOrBlank()) {
                  sessionManager.simpanJwtToken(tokenStr)
                }
                val karyawanKetemu = res.getOrNull()!!
                sessionManager.simpanKaryawanTerdaftar(karyawanKetemu)
                sessionManager.simpanInputTerakhir(
                  karyawanKetemu.nip,
                  karyawanKetemu.nama,
                  karyawanKetemu.departemen
                )
                errorMessage = null
                onLoginSuccess(karyawanKetemu)
              } else {
                errorMessage = res.exceptionOrNull()?.message ?: "NIP '$cleanNip' belum terdaftar."
              }
            }
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(999.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
          contentPadding = PaddingValues(),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(FreshGreenButtonBrush)
            .testTag("karyawan_login_submit_button")
        ) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Memproses...", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
            } else {
              Text(
                text = "Log In Presensi",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
        }
      }
    }

    if (showServerDialog) {
      DialogPengaturanXampp(
        jaringanManager = jaringanManager,
        onDismiss = {
          showServerDialog = false
        }
      )
    }
  }
}
