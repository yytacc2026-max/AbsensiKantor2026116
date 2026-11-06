package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DataKaryawan
import com.example.model.JaringanKantorManager
import com.example.ui.theme.*

// Layar utama pemilihan akses (Opening Screen) - Fresh Organic Green Curved Header & Pill UI (Sesuai Foto Foodie)
@Composable
fun RoleSelectionScreen(
  karyawanTerdaftar: DataKaryawan?,
  onOpenDashboard: () -> Unit,
  onRegisterNew: () -> Unit,
  onSelectLogin: () -> Unit,
  onSelectAdmin: () -> Unit,
  jaringanManager: JaringanKantorManager? = null
) {
  val scrollState = rememberScrollState()

  Scaffold(
    containerColor = BgLight
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(scrollState),
      horizontalAlignment = Alignment.CenterHorizontally
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
            .padding(top = 28.dp, bottom = 34.dp, start = 20.dp, end = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.18f)
          ) {
            Text(
              text = "PRESENSI DIGITAL",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Logo Aplikasi Fadiya Kencana
          Surface(
            modifier = Modifier.size(86.dp),
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

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "Fadiya Kencana",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.testTag("app_title")
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Sistem Presensi & Manajemen HRIS",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center
          )
        }
      }

      // KONTEN PILIH AKSES
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 500.dp)
          .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Pilih Akses Masuk",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
        )

        // Bagian Aksi Pengguna (Kartu Kapsul Membulat Halus)
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          if (karyawanTerdaftar != null) {
            KaryawanMainCard(
              testTag = "open_registered_dashboard_card",
              title = "Beranda Presensi",
              subtitle = "${karyawanTerdaftar.nama} • NIP ${karyawanTerdaftar.nip}",
              icon = Icons.Default.Person,
              badgeColor = TealPrimary,
              onClick = onOpenDashboard
            )
          } else {
            KaryawanMainCard(
              testTag = "role_karyawan_login_card",
              title = "Masuk Karyawan",
              subtitle = "Masuk menggunakan NIP yang sudah terdaftar",
              icon = Icons.Default.Person,
              badgeColor = TealPrimary,
              onClick = onSelectLogin
            )

            KaryawanMainCard(
              testTag = "role_karyawan_register_card",
              title = "Pendaftaran Karyawan",
              subtitle = "Registrasi akun baru untuk perangkat ini",
              icon = Icons.Default.Badge,
              badgeColor = TealLight,
              onClick = onRegisterNew
            )
          }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Akses Portal Administrator (Pill Shape Elegan)
        OutlinedButton(
          onClick = onSelectAdmin,
          shape = RoundedCornerShape(999.dp),
          border = BorderStroke(1.dp, BorderMuted),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CardWhite,
            contentColor = TextDark
          ),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
          modifier = Modifier.testTag("role_admin_card")
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Portal Administrator",
            tint = StatusAmber,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Masuk Portal Administrator (HRD)",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDark
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
          text = "PT Fadiya Kencana • v2.0 Realtime GPS",
          fontSize = 11.sp,
          color = TextMuted.copy(alpha = 0.7f),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

@Composable
fun KaryawanMainCard(
  testTag: String,
  title: String,
  subtitle: String,
  icon: ImageVector,
  badgeColor: Color,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag)
      .clickable { onClick() },
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = CardWhite),
    border = BorderStroke(1.dp, BorderMuted),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(50.dp)
          .clip(CircleShape)
          .background(badgeColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = badgeColor,
          modifier = Modifier.size(26.dp)
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = subtitle,
          fontSize = 12.sp,
          color = TextMuted,
          lineHeight = 16.sp
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = TextMuted.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp)
      )
    }
  }
}
