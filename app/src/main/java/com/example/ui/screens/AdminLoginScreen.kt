package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.XamppApiClient
import com.example.ui.theme.*
import kotlinx.coroutines.launch

// Form login operator / administrator - Modern Cyber Emerald & Topography UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
  onLoginSuccess: () -> Unit,
  onBack: () -> Unit
) {
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }

  var errorMessage by remember { mutableStateOf<String?>(null) }
  val context = androidx.compose.ui.platform.LocalContext.current
  val sessionManager = remember { com.example.model.SessionManager(context) }
  val scrollState = rememberScrollState()
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    containerColor = Color(0xFF03140E)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(scrollState)
        .imePadding()
    ) {
      // HEADER MELENGKUNG BERGRADASI CYBER EMERALD & TOPOGRAFI KONTUR
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
                .background(Color.White.copy(alpha = 0.18f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), CircleShape)
                .testTag("admin_login_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali ke Pilih Peran",
                tint = Color.White
              )
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = Color.White.copy(alpha = 0.18f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6EE7B7))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "PORTAL ADMIN",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          // Ikon Shield Admin Berkilau
          Box(
            modifier = Modifier
              .size(76.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.18f))
              .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.6f)), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.AdminPanelSettings,
              contentDescription = "Ikon Admin",
              tint = Color.White,
              modifier = Modifier.size(42.dp)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Text(
            text = "PT. Fadiya Kencana",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Pusat Kendali Presensi & HRIS",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.85f)
          )
        }
      }

      // FORM LOGIN CYBER EMERALD DARK
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 480.dp)
          .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Welcome Back",
          fontSize = 26.sp,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Masukkan kredensial administrator untuk melanjutkan",
          fontSize = 13.sp,
          color = Color.White.copy(alpha = 0.65f),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(22.dp))

        if (errorMessage != null) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3F0B13)),
            border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.5f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_login_error_card")
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = Color(0xFFFDA4AF),
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = errorMessage ?: "",
                fontSize = 12.sp,
                color = Color(0xFFFDA4AF),
                fontWeight = FontWeight.Medium
              )
            }
          }
          Spacer(modifier = Modifier.height(16.dp))
        }

        // Kolom Input Username Kapsul (Cyber Emerald)
        OutlinedTextField(
          value = username,
          onValueChange = {
            username = it
            errorMessage = null
          },
          label = { Text("Username Admin") },
          leadingIcon = {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF34D399))
          },
          singleLine = true,
          shape = RoundedCornerShape(999.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF062B21),
            unfocusedContainerColor = Color(0xFF042018),
            focusedBorderColor = Color(0xFF10B981),
            unfocusedBorderColor = Color(0xFF10B981).copy(alpha = 0.35f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color(0xFF34D399),
            unfocusedLabelColor = Color.White.copy(alpha = 0.65f),
            cursorColor = Color(0xFF10B981)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_username_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Kolom Input Kata Sandi Kapsul (Cyber Emerald)
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            errorMessage = null
          },
          label = { Text("Kata Sandi") },
          leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF34D399))
          },
          trailingIcon = {
            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
              Icon(
                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = "Lihat Kata Sandi",
                tint = Color(0xFFA7F3D0)
              )
            }
          },
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          singleLine = true,
          shape = RoundedCornerShape(999.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF062B21),
            unfocusedContainerColor = Color(0xFF042018),
            focusedBorderColor = Color(0xFF10B981),
            unfocusedBorderColor = Color(0xFF10B981).copy(alpha = 0.35f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color(0xFF34D399),
            unfocusedLabelColor = Color.White.copy(alpha = 0.65f),
            cursorColor = Color(0xFF10B981)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_password_input")
        )

        Spacer(modifier = Modifier.height(28.dp))

        // TOMBOL MASUK KAPSUL GRADASI CYBER EMERALD
        Button(
          onClick = {
            val userClean = username.trim()
            val passClean = password.trim()

            if (userClean.isBlank() || passClean.isBlank()) {
              errorMessage = "Username dan kata sandi wajib diisi."
              return@Button
            }

            isLoading = true
            coroutineScope.launch {
              val hasilLogin = XamppApiClient.verifikasiLoginAdmin(userClean, passClean)
              isLoading = false
              if (hasilLogin) {
                val tokenStr = XamppApiClient.jwtToken
                if (!tokenStr.isNullOrBlank()) {
                  sessionManager.simpanJwtToken(tokenStr)
                }
                errorMessage = null
                onLoginSuccess()
              } else {
                errorMessage = "Username atau kata sandi tidak sesuai, atau server belum terhubung."
              }
            }
          },
          shape = RoundedCornerShape(999.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
          contentPadding = PaddingValues(),
          enabled = !isLoading,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(FreshGreenButtonBrush)
            .border(BorderStroke(1.2.dp, Color(0xFF6EE7B7).copy(alpha = 0.6f)), RoundedCornerShape(999.dp))
            .testTag("admin_login_submit_button")
        ) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
              )
            } else {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Log In",
                  fontSize = 16.sp,
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
