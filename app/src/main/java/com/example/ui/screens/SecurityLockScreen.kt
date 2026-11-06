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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.model.AuthMethod
import com.example.model.SessionManager
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SecurityLockScreen(
  onUnlockSuccess: () -> Unit,
  onChangeAuthMethod: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val sessionManager = remember { SessionManager(context) }
  val authMethod = remember { sessionManager.getAuthMethod() }

  var inputPin by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var remainingLockout by remember { mutableStateOf(sessionManager.getLockoutRemainingSeconds()) }
  var showPinFallbackForBiometric by remember { mutableStateOf(authMethod == AuthMethod.PIN || authMethod == AuthMethod.NONE) }
  var showUbahPinDialog by remember { mutableStateOf(false) }

  // Countdown timer untuk proteksi 30 detik lockout setelah 5x salah
  LaunchedEffect(remainingLockout) {
    if (remainingLockout > 0) {
      while (remainingLockout > 0) {
        delay(1000L)
        remainingLockout = sessionManager.getLockoutRemainingSeconds()
      }
      errorMessage = null
    }
  }

  // Fungsi autentikasi biometrik (Sidik Jari atau Wajah)
  fun triggerBiometricPrompt(isFace: Boolean = false) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        sessionManager.resetFailedPinAttempts()
        onUnlockSuccess()
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
          // Pengguna memilih untuk memasukkan PIN cadangan
          showPinFallbackForBiometric = true
        } else if (errorCode != BiometricPrompt.ERROR_CANCELED) {
          errorMessage = errString.toString()
          showPinFallbackForBiometric = true
        }
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        errorMessage = if (isFace) "Wajah tidak cocok, silakan coba lagi." else "Sidik jari tidak cocok, silakan coba lagi."
      }
    }

    val biometricPrompt = BiometricPrompt(activity, executor, callback)

    val title = if (isFace) "Verifikasi Pengenalan Wajah" else "Verifikasi Sidik Jari"
    val subtitle = if (isFace) "Pindai wajah untuk membuka aplikasi" else "Sentuh sensor sidik jari untuk membuka aplikasi"

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setNegativeButtonText("Gunakan PIN")
      .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK
      )
      .build()

    try {
      biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
      errorMessage = "Biometrik tidak dapat diakses: ${e.message}"
      showPinFallbackForBiometric = true
    }
  }

  // Jika metode login adalah Sidik Jari atau Wajah, otomatis tampilkan prompt biometrik saat layar dibuka
  LaunchedEffect(authMethod) {
    if (authMethod == AuthMethod.FINGERPRINT) {
      triggerBiometricPrompt(isFace = false)
    } else if (authMethod == AuthMethod.FACE) {
      triggerBiometricPrompt(isFace = true)
    }
  }

  fun handleVerifyPin(pin: String) {
    if (remainingLockout > 0) return

    val isValid = sessionManager.verifyPin(pin)
    if (isValid) {
      errorMessage = null
      onUnlockSuccess()
    } else {
      inputPin = ""
      val isLockedNow = sessionManager.recordFailedPinAttempt()
      if (isLockedNow) {
        remainingLockout = sessionManager.getLockoutRemainingSeconds()
        errorMessage = "Terlalu banyak percobaan salah! Aplikasi terkunci sementara selama 30 detik."
      } else {
        val attempts = sessionManager.getFailedPinAttempts()
        val sisa = SessionManager.MAX_FAILED_ATTEMPTS - attempts
        errorMessage = "PIN Salah! Sisa percobaan: $sisa kali sebelum terkunci 30 detik."
      }
    }
  }

  Scaffold(
    containerColor = BgLight
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 420.dp)
          .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // Logo
        Box(
          modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, TealContainer, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_fadiya_logo),
            contentDescription = "Logo Perusahaan",
            modifier = Modifier
              .size(76.dp)
              .clip(CircleShape)
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "Keamanan Aplikasi",
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          modifier = Modifier.testTag("security_title")
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = when {
            remainingLockout > 0 -> "Aplikasi sedang terkunci sementara"
            authMethod == AuthMethod.FINGERPRINT && !showPinFallbackForBiometric -> "Pindai Sidik Jari untuk membuka aplikasi"
            authMethod == AuthMethod.FACE && !showPinFallbackForBiometric -> "Pindai Wajah untuk membuka aplikasi"
            else -> "Masukkan PIN 6-Digit untuk membuka aplikasi"
          },
          fontSize = 13.sp,
          color = if (remainingLockout > 0) StatusRed else TextMuted,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Jika metode adalah biometrik dan belum membuka fallback PIN
        if ((authMethod == AuthMethod.FINGERPRINT || authMethod == AuthMethod.FACE) && !showPinFallbackForBiometric && remainingLockout == 0L) {
          Box(
            modifier = Modifier
              .size(110.dp)
              .clip(CircleShape)
              .background(TealContainer)
              .clickable { triggerBiometricPrompt(isFace = authMethod == AuthMethod.FACE) },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              if (authMethod == AuthMethod.FACE) Icons.Default.Face else Icons.Default.Fingerprint,
              contentDescription = "Pindai Biometrik",
              tint = TealDark,
              modifier = Modifier.size(56.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          OutlinedButton(
            onClick = { triggerBiometricPrompt(isFace = authMethod == AuthMethod.FACE) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary),
            border = BorderStroke(1.dp, TealPrimary),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.testTag("btn_trigger_bio_again")
          ) {
            Text(if (authMethod == AuthMethod.FACE) "Pindai Ulang Wajah" else "Pindai Ulang Sidik Jari")
          }

          Spacer(modifier = Modifier.height(10.dp))

          TextButton(
            onClick = { showPinFallbackForBiometric = true },
            modifier = Modifier.testTag("btn_fallback_to_pin")
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextMuted)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Buka Menggunakan PIN", color = TextMuted, fontSize = 13.sp)
          }
        } else {
          // Tampilan PIN Keypad (Metode PIN atau Fallback Biometrik)
          if (remainingLockout > 0) {
            // Lockout Card
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = StatusRedBg,
              border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(Icons.Default.LockClock, contentDescription = null, tint = StatusRed, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Terkunci Sementara",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = StatusRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Silakan tunggu $remainingLockout detik lagi sebelum mencoba memasukkan PIN kembali.",
                  fontSize = 12.sp,
                  color = TextDark,
                  textAlign = TextAlign.Center
                )
              }
            }
          } else {
            // Indicator PIN (6 Dots)
            Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.testTag("pin_dots_row")
            ) {
              repeat(6) { index ->
                val isFilled = index < inputPin.length
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) TealPrimary else Color.Transparent)
                    .border(2.dp, if (isFilled) TealPrimary else BorderMuted, CircleShape)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          if (errorMessage != null && remainingLockout == 0L) {
            Text(
              text = errorMessage ?: "",
              fontSize = 12.sp,
              color = StatusRed,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
              modifier = Modifier
                .padding(horizontal = 8.dp)
                .testTag("pin_error_text")
            )
            Spacer(modifier = Modifier.height(10.dp))
          } else {
            Spacer(modifier = Modifier.height(10.dp))
          }

          // Numeric Keypad
          val keypad = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "DEL")
          )

          Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.testTag("numeric_keypad")
          ) {
            keypad.forEach { row ->
              Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                row.forEach { key ->
                  val isKeypadEnabled = remainingLockout == 0L
                  Box(
                    modifier = Modifier
                      .size(68.dp)
                      .clip(CircleShape)
                      .background(
                        if (!isKeypadEnabled) Color(0xFFEAEAEA)
                        else if (key == "C") Color(0xFFF0F4F5)
                        else CardWhite
                      )
                      .border(1.dp, BorderMuted, CircleShape)
                      .clickable(enabled = isKeypadEnabled) {
                        when (key) {
                          "DEL" -> {
                            if (inputPin.isNotEmpty()) {
                              inputPin = inputPin.dropLast(1)
                              errorMessage = null
                            }
                          }
                          "C" -> {
                            inputPin = ""
                            errorMessage = null
                          }
                          else -> {
                            if (inputPin.length < 6) {
                              val newPin = inputPin + key
                              inputPin = newPin
                              errorMessage = null
                              if (newPin.length == 6) {
                                handleVerifyPin(newPin)
                              }
                            }
                          }
                        }
                      }
                      .testTag("keypad_btn_$key"),
                    contentAlignment = Alignment.Center
                  ) {
                    when (key) {
                      "DEL" -> Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Hapus",
                        tint = if (isKeypadEnabled) TextDark else TextMuted
                      )
                      "C" -> Text(
                        text = "C",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                      )
                      else -> Text(
                        text = key,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isKeypadEnabled) TextDark else TextMuted
                      )
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Info Metode Aktif
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = TealContainer.copy(alpha = 0.5f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = TealDark, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Metode Aktif: ${authMethod.label}",
              fontSize = 11.sp,
              color = TealDark,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}
