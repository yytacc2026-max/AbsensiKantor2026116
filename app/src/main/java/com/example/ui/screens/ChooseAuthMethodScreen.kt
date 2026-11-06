package com.example.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.model.AuthMethod
import com.example.model.SessionManager
import com.example.ui.theme.*

@Composable
fun ChooseAuthMethodScreen(
  onAuthMethodSelected: (AuthMethod) -> Unit,
  onNavigateToPinSetup: () -> Unit,
  onBack: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val sessionManager = remember { SessionManager(context) }
  val scrollState = rememberScrollState()

  var showUnavailDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

  // Cek ketersediaan sensor biometrik perangkat
  val biometricStatus = remember {
    val biometricManager = BiometricManager.from(context)
    biometricManager.canAuthenticate(
      BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
  }

  val isBiometricAvailable = biometricStatus == BiometricManager.BIOMETRIC_SUCCESS

  fun testAndSaveBiometric(method: AuthMethod, title: String) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        sessionManager.simpanAuthMethod(method)
        Toast.makeText(context, "Metode Login ${method.label} Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
        onAuthMethodSelected(method)
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
            errorCode != BiometricPrompt.ERROR_CANCELED) {
          Toast.makeText(context, errString.toString(), Toast.LENGTH_SHORT).show()
        }
      }
    }

    val biometricPrompt = BiometricPrompt(activity, executor, callback)

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle("Konfirmasi $title")
      .setSubtitle("Pindai sekarang untuk mengaktifkan metode ini sebagai kunci utama aplikasi")
      .setNegativeButtonText("Batal")
      .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK
      )
      .build()

    try {
      biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
      showUnavailDialog = Pair(
        "Gagal Membuka Sensor",
        "Terjadi kendala saat mengakses sensor biometrik: ${e.message}. Silakan gunakan metode PIN."
      )
    }
  }

  Scaffold(
    containerColor = BgLight
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
          .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (onBack != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("btn_back_choose_method")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextDark)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pengaturan Keamanan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Logo
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, TealContainer, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_fadiya_logo),
            contentDescription = "Logo",
            modifier = Modifier
              .size(80.dp)
              .clip(CircleShape)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Metode Keamanan",
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("title_choose_method")
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "Pilih metode autentikasi untuk membuka aplikasi",
          fontSize = 13.sp,
          color = TextMuted,
          textAlign = TextAlign.Center,
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Opsi 1: Sidik Jari (Fingerprint)
        MethodCard(
          icon = Icons.Default.Fingerprint,
          title = "Sidik Jari",
          description = "Masuk cepat menggunakan sensor sidik jari perangkat",
          badgeText = if (isBiometricAvailable) "Tersedia" else "Belum Aktif",
          badgeColor = if (isBiometricAvailable) StatusGreen else StatusAmber,
          badgeBg = if (isBiometricAvailable) StatusGreenBg else StatusAmberBg,
          testTag = "card_method_fingerprint",
          onClick = {
            if (isBiometricAvailable) {
              testAndSaveBiometric(AuthMethod.FINGERPRINT, "Sidik Jari")
            } else {
              showUnavailDialog = Pair(
                "Sidik Jari Belum Aktif",
                "Pastikan sensor sidik jari telah didaftarkan pada pengaturan perangkat Anda, atau gunakan metode PIN 6-Digit."
              )
            }
          }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Opsi 2: Pengenalan Wajah (Face Biometric)
        MethodCard(
          icon = Icons.Default.Face,
          title = "Pengenalan Wajah",
          description = "Masuk menggunakan pemindaian biometrik wajah perangkat",
          badgeText = if (isBiometricAvailable) "Tersedia" else "Tidak Tersedia",
          badgeColor = if (isBiometricAvailable) StatusGreen else StatusBlue,
          badgeBg = if (isBiometricAvailable) StatusGreenBg else StatusBlueBg,
          testTag = "card_method_face",
          onClick = {
            if (isBiometricAvailable) {
              testAndSaveBiometric(AuthMethod.FACE, "Pengenalan Wajah")
            } else {
              showUnavailDialog = Pair(
                "Pengenalan Wajah Tidak Tersedia",
                "Fitur biometrik wajah belum diaktifkan pada perangkat ini. Silakan gunakan metode Sidik Jari atau PIN 6-Digit."
              )
            }
          }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Opsi 3: PIN (6-Digit)
        MethodCard(
          icon = Icons.Default.Lock,
          title = "PIN 6-Digit",
          description = "Masuk menggunakan kode keamanan 6 angka",
          badgeText = "Direkomendasikan",
          badgeColor = TealDark,
          badgeBg = TealContainer,
          testTag = "card_method_pin",
          onClick = {
            onNavigateToPinSetup()
          }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
          text = "Metode keamanan dapat diubah kapan saja melalui menu Pengaturan.",
          fontSize = 12.sp,
          color = TextMuted,
          textAlign = TextAlign.Center
        )
      }
    }
  }

  // Dialog Informasi jika fitur biometrik tidak tersedia
  if (showUnavailDialog != null) {
    val (dialogTitle, dialogDesc) = showUnavailDialog ?: Pair("", "")
    AlertDialog(
      onDismissRequest = { showUnavailDialog = null },
      icon = {
        Icon(Icons.Default.Info, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(32.dp))
      },
      title = {
        Text(dialogTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
      },
      text = {
        Text(dialogDesc, fontSize = 13.sp, color = TextDark, lineHeight = 19.sp)
      },
      confirmButton = {
        Button(
          onClick = {
            showUnavailDialog = null
            onNavigateToPinSetup()
          },
          colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
        ) {
          Text("Gunakan Metode PIN")
        }
      },
      dismissButton = {
        TextButton(onClick = { showUnavailDialog = null }) {
          Text("Tutup", color = TextMuted)
        }
      }
    )
  }
}

@Composable
private fun MethodCard(
  icon: ImageVector,
  title: String,
  description: String,
  badgeText: String,
  badgeColor: Color,
  badgeBg: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = CardWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = BorderStroke(1.dp, BorderMuted),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(50.dp)
          .clip(CircleShape)
          .background(TealContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = TealDark, modifier = Modifier.size(26.dp))
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(text = description, fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp)

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = badgeBg
        ) {
          Text(
            text = badgeText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
  }
}
