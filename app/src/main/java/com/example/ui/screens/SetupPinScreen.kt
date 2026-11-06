package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AuthMethod
import com.example.model.SessionManager
import com.example.ui.theme.*

@Composable
fun SetupPinScreen(
  onPinSetupSuccess: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val sessionManager = remember { SessionManager(context) }

  // Step 1: Buat PIN, Step 2: Konfirmasi PIN
  var step by remember { mutableStateOf(1) }
  var firstPin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val currentInput = if (step == 1) firstPin else confirmPin

  fun handleKey(key: String) {
    errorMessage = null
    when (key) {
      "DEL" -> {
        if (step == 1) {
          if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
        } else {
          if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
        }
      }
      "C" -> {
        if (step == 1) firstPin = "" else confirmPin = ""
      }
      else -> {
        if (step == 1) {
          if (firstPin.length < 6) {
            val updated = firstPin + key
            firstPin = updated
            if (updated.length == 6) {
              step = 2
            }
          }
        } else {
          if (confirmPin.length < 6) {
            val updated = confirmPin + key
            confirmPin = updated
            if (updated.length == 6) {
              if (updated == firstPin) {
                // PIN Cocok! Simpan ke SessionManager secara hashed
                sessionManager.simpanSecurityPin(updated)
                sessionManager.simpanAuthMethod(AuthMethod.PIN)
                Toast.makeText(context, "PIN Berhasil Dibuat!", Toast.LENGTH_SHORT).show()
                onPinSetupSuccess()
              } else {
                errorMessage = "PIN konfirmasi tidak cocok! Silakan ulangi pembuatan PIN."
                confirmPin = ""
                firstPin = ""
                step = 1
              }
            }
          }
        }
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
        // Tombol Kembali
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              if (step == 2) {
                step = 1
                confirmPin = ""
              } else {
                onBack()
              }
            },
            modifier = Modifier.testTag("btn_back_setup_pin")
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Kembali",
              tint = TextDark
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (step == 1) "Atur PIN Baru" else "Konfirmasi PIN",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logo
        Box(
          modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, TealContainer, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_fadiya_logo),
            contentDescription = "Logo",
            modifier = Modifier
              .size(70.dp)
              .clip(CircleShape)
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = if (step == 1) "Masukkan 6-Digit PIN Baru" else "Masukkan Ulang PIN Konfirmasi",
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = if (step == 1)
            "PIN ini akan digunakan setiap kali Anda membuka aplikasi presensi."
          else
            "Pastikan PIN cocok dengan yang Anda buat sebelumnya.",
          fontSize = 13.sp,
          color = TextMuted,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Step Indicator Pill
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = TealContainer.copy(alpha = 0.6f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              if (step == 1) Icons.Default.Lock else Icons.Default.CheckCircle,
              contentDescription = null,
              tint = TealDark,
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (step == 1) "Langkah 1 dari 2: Buat PIN" else "Langkah 2 dari 2: Konfirmasi PIN",
              fontSize = 12.sp,
              color = TealDark,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 6 Dots Indicator
        Row(
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.testTag("setup_pin_dots")
        ) {
          repeat(6) { index ->
            val isFilled = index < currentInput.length
            Box(
              modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (isFilled) TealPrimary else Color.Transparent)
                .border(2.dp, if (isFilled) TealPrimary else BorderMuted, CircleShape)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
          Text(
            text = errorMessage ?: "",
            fontSize = 12.sp,
            color = StatusRed,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .padding(horizontal = 12.dp)
              .testTag("setup_pin_error")
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
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          keypad.forEach { row ->
            Row(
              horizontalArrangement = Arrangement.spacedBy(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              row.forEach { key ->
                Box(
                  modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(if (key == "C") Color(0xFFF0F4F5) else CardWhite)
                    .border(1.dp, BorderMuted, CircleShape)
                    .clickable { handleKey(key) }
                    .testTag("keypad_setup_$key"),
                  contentAlignment = Alignment.Center
                ) {
                  when (key) {
                    "DEL" -> Icon(
                      Icons.AutoMirrored.Filled.Backspace,
                      contentDescription = "Hapus",
                      tint = TextDark
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
                      color = TextDark
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
